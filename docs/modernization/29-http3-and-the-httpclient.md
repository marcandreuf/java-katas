# 29. HTTP/3 and the HttpClient

**Java 26** · stage 18 of [the release-ordered track](INDEX.md) · **needs `--release 26`**

## Where it lands

**`httpclient/LocalHttp.java`** is the existing exercise and
[doc 10](10-sequenced-collections-and-httpclient.md) owns it. Finish its three TODOs first,
including TODO 3, `sendAsync()`, which also needs [doc 15](15-completable-future.md).

New code: **`httpclient/Http3.java`**, five TODOs, running against
`LocalHttp.startEchoServer()` — the same in-JVM `com.sun.net.httpserver` instance doc 10 uses.

## What changed

HTTP/3 runs over **QUIC**, which runs over **UDP**. HTTP/2 and HTTP/1.1 run over TCP. That is why
this took a release of its own: the JDK had to acquire a QUIC implementation, not just a new
protocol parser.

The API change is deliberately almost nothing — one more `Version` constant:

    HttpRequest.newBuilder(uri).version(HttpClient.Version.HTTP_3)   // on the request
    HttpClient.newBuilder().version(HttpClient.Version.HTTP_3)       // on the client

**The two are not the same**, and this is the detail to take away:

| Where you set it | Behaviour when HTTP/3 is unavailable |
| --- | --- |
| on the **request** | tries HTTP/3 first, then **falls back** to HTTP/2 or 1.1 |
| on the **client** | **races** an HTTP/3 connection against an HTTP/2 or 1.1 one, first to connect wins |

So client-level costs a second connection attempt per new origin and buys a lower worst-case
latency when HTTP/3 is blocked; request-level costs a timeout on the HTTP/3 attempt before
falling back. **Neither is obviously the right default.** Being able to say which you would pick
for a mobile client versus a service-to-service call inside one datacentre is the useful version
of knowing this feature.

That distinction is **not observable in these tests** — against a loopback HTTP/1.1 server both
land on HTTP/1.1 immediately. TODO 5 makes you write both anyway, so that the one thing you
cannot test is the one thing you had to look up.

## The exercise

### TODOs 1–3: the fallback is silent

`Http3.get(uri)` asks for HTTP/3 and returns `"echo:/hello"`. It works. Nothing warns.

    Http3.requestedVersion(uri)    ->  Optional[HTTP_3]     what you asked for
    Http3.negotiatedVersion(uri)   ->  HTTP_1_1             what you got

**`HttpResponse.version()` is the only way to find out.** The test
`requested_and_negotiated_versions_disagree` is the assertion worth the document: the JDK keeps
the request's answer and the response's answer separate on purpose, because they routinely
differ and code that assumes otherwise is wrong in a way no exception reports.

This is why the written question "how would you find out, in production, which protocol a given
request actually used" has a one-method answer, and why you should expect to need it: HTTP/3 over
UDP means **middleboxes and corporate firewalls that permit TCP 443 frequently drop UDP 443**, so
"HTTP/3 is unavailable" is the normal path on a lot of enterprise networks, not a rare one.

### TODO 4: turning the silence off

JEP 517 also adds `HttpOption`, and one option that matters:

    .setOption(HttpOption.H3_DISCOVERY, Http3DiscoveryMode.HTTP_3_URI_ONLY)

| Mode | Against this HTTP/1.1 server |
| --- | --- |
| `ANY` (default) | falls back, HTTP/1.1, 200 |
| `ALT_SVC` | falls back, HTTP/1.1, 200 |
| `HTTP_3_URI_ONLY` | **throws** |

The exception is specific and worth reading:

    java.net.http.UnsupportedProtocolVersionException:
        HTTP/3 is not supported on plain connections

Two things in that:

1. **It is the production failure.** A firewall dropping UDP 443 under
   `HTTP_3_URI_ONLY` produces this instead of a quiet downgrade. Choosing between the two is
   choosing between a loud failure and a silent performance change, which is the same trade
   [stage 19](30-upgrade-hazards.md) describes for flags.
2. **`UnsupportedProtocolVersionException extends IOException`**, so every existing
   `catch (IOException e)` already catches it and will report it as a network blip. The test
   `the_strict_failure_is_an_io_exception` pins that, because it is how this gets misdiagnosed.

`ALT_SVC` is the one worth understanding for real use: it only upgrades if the server advertised
HTTP/3 in an `Alt-Svc` response header, which is how HTTP/3 is actually discovered on the open
web — you connect over TCP, the server tells you it also speaks QUIC, and the *next* request
uses it.

## What cannot be tested here, and why that is worth knowing

**`com.sun.net.httpserver` does not speak HTTP/3, and the JDK ships no QUIC server at all.** So
there is no offline HTTP/3 round trip available, and every test in `Http3Test` is a test of the
fallback.

That is not a gap in the exercise, it is the finding: **JEP 517 is a client-side feature only.**
If you want to *serve* HTTP/3 you need Netty, Jetty, nginx or a load balancer in front, and Java
26 does not change that. The track's offline property — stated in
[doc 10](10-sequenced-collections-and-httpclient.md) and true everywhere else — stops exactly
here, and it stops because of a decision in the JEP rather than anything about this repo.

## Write the answers here

1. **Request-level or client-level** for a service-to-service call inside one datacentre where
   you control both ends? And for a public API consumed by mobile clients? _(fill in)_
2. **Would you ever ship `HTTP_3_URI_ONLY`?** Name the deployment where a loud failure beats a
   silent downgrade. _(fill in)_
3. **What does QUIC-over-UDP do to your observability?** Name one thing that worked over TCP and
   stops. _(fill in)_
4. **`UnsupportedProtocolVersionException` is an `IOException`.** Your service already retries
   `IOException`. What does it now do, and is that right? _(fill in)_
5. **Propose a CI test for real HTTP/3 behaviour** that does not depend on the public internet,
   and say what it costs. _(fill in)_

## What got worse

- _(fill this in)_

Prompts: the client-level setting opens two connections per origin — at what request volume does
that stop being free? If HTTP/3 silently falls back, what class of bug does that hide until
somebody looks at a packet capture? What is the argument for *not* turning this on at all?

Sources: [JEP 517](https://openjdk.org/jeps/517) ·
[JDK 26 heads-up](https://inside.java/2025/10/30/quality-heads-up/)
