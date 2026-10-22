# 29. HTTP/3 and the HttpClient

**Java 26** (HTTP/3 for the HTTP Client API, JEP 517, final) · stage 18 of
[the release-ordered track](INDEX.md) · needs `--release 26`

New code with its tests already written: **`httpclient/Http3.java`**, five TODOs, and `Http3Test`,
which is `@Disabled` until you begin. It runs against `LocalHttp.startEchoServer()`, the same
in-JVM server [doc 10](10-sequenced-collections-and-httpclient.md) uses. **Finish `LocalHttp`'s
three TODOs first**: doc 10 owns them, and TODO 3 (`sendAsync`) also needs
[doc 15](15-completable-future.md).

It needs 26 specifically: JDK 25's javac rejects `Http3.java` with `package
java.net.http.HttpOption does not exist` (verified).

## What this stage is actually about

Five words, defined once:

- **QUIC** is a transport protocol that runs over **UDP**. HTTP/3 runs over QUIC. HTTP/1.1 and
  HTTP/2 run over TCP. That's why this took a JEP of its own: the JDK needed a QUIC implementation,
  not just a new parser.
- the **requested version** is what you asked for, `HttpRequest.version()`, an `Optional`. The
  **negotiated version** is what was actually used, `HttpResponse.version()`.
- a **fallback** is the client giving up on HTTP/3 and using HTTP/2 or HTTP/1.1 over TCP
  instead, without telling you.
- the **discovery mode** (`HttpOption.H3_DISCOVERY`, with a value from `Http3DiscoveryMode`) is a
  hint on the request about how to find an HTTP/3 server: `ANY`, `ALT_SVC` or `HTTP_3_URI_ONLY`.
- **`Alt-Svc`** is a response header in which a server says "I also speak HTTP/3, over there".
  It's how browsers find HTTP/3 on the open web: the first request goes over TCP, and later ones
  can use QUIC.

That gives one rule, and everything below follows from it: **asking for HTTP/3 is a preference,
not a requirement, and the response's `version()` is the only record of what happened.** Nothing
throws when you don't get it, and nothing logs it.

## The worked example: request-level versus client-level, measured

The version can go in two places:

    HttpRequest.newBuilder(uri).version(HttpClient.Version.HTTP_3)   // on the request
    HttpClient.newBuilder().version(HttpClient.Version.HTTP_3)       // on the client

The JDK source explains the difference. `jdk/internal/net/http/ExchangeImpl.java`, in JDK 26's
`src.zip`, says that when HTTP/3 is set **on the request**, the client tries HTTP/3 first and only
starts a TCP connection "if the HTTP/3 connection fails, or doesn't succeed in the imparted
timeout", which defaults to **2750 ms**. When it's set **on the client**, it starts both at once
and uses whichever connects first.

That's measurable offline, against an HTTPS server that speaks only HTTP/1.1 over TCP: a
`com.sun.net.httpserver.HttpsServer` on `127.0.0.1` with a self-signed certificate from
`keytool`. A new client for every row (verified on JDK 26, on a loaded machine, so read the
ratios rather than the exact numbers):

| How HTTP/3 was asked for | Negotiated | Time |
| --- | --- | --- |
| on the request | `HTTP_1_1` | about **2.8–3.0 s** |
| on the client | `HTTP_1_1` | about **0.08 s** |
| on the request, `H3_DISCOVERY = ALT_SVC` | `HTTP_1_1` | about **0.06 s** |
| on the request, `-Djdk.httpclient.http3.maxDirectConnectionTimeout=500` | `HTTP_1_1` | about **0.9 s** |

The request-level setting waited out the HTTP/3 attempt before falling back. The client-level one
raced, and TCP won at once. `ALT_SVC` never tried QUIC, because the server sent no `Alt-Svc`
header. And the last row shows the 2750 ms is a knob.

**A correction to the old version:** it said this distinction "is not observable in these
tests", which is true of `Http3Test`. But it presented that as a limit of working offline, and
it isn't one. Over HTTPS it shows up as a 30-fold difference in latency, and that's the number to
bring to the written question below.

## Part one: `Http3`

Delete the `@Disabled`, and all nine tests fail (verified): six error with
`UnsupportedOperationException`, and three fail because they expected a different exception.

Commits, in this order:

1. **TODOs 1–3: `get`, `negotiatedVersion`, `requestedVersion`.** Ask for HTTP/3 on the request.
   The body comes back as `"echo:/hello"`, and nothing warns. The test worth reading is
   `requested_and_negotiated_versions_disagree`. Before you run it, write down what you expect each
   of the two methods to return.
2. **TODO 4: `strictGet` and `negotiatedVersionWith`.** `HTTP_3_URI_ONLY` forbids the fallback, and
   the test expects `UnsupportedProtocolVersionException` with "HTTP/3" and "plain connections" in
   its message. Read the message when you get it.
3. **TODO 5: `clientLevelNegotiatedVersion`.** The test looks just like TODO 2's, because here
   both fall back at once. The worked example is where the difference shows.

### Why the tests fall back at once: they never try HTTP/3

**A correction to the old version, and the main one in this doc.** It said the echo server falls
back because it "does not speak HTTP/3", and that the strict-mode exception "is the production
failure" a firewall dropping UDP 443 would cause. Neither is right.

The tests use `http://127.0.0.1:...`, and the `HttpClient` javadoc in JDK 26 says: *"a request
whose URI scheme is not `https` will never be sent over HTTP/3. In this implementation, HTTP/3 is
not used if a proxy is selected."* So no QUIC packet is ever sent. The client decides from the URI
alone, without touching the network, and "plain connections" in the strict-mode message
means **plain `http://`**, not "the server lacked HTTP/3".

What a firewall that drops UDP actually produces, verified against the HTTPS server above with
`HTTP_3_URI_ONLY` and nothing listening for QUIC:

| Request timeout | Exception | After |
| --- | --- | --- |
| `timeout(Duration.ofSeconds(20))` | `HttpConnectTimeoutException: HTTP connect timed out` | 20 s |
| `timeout(Duration.ofSeconds(90))` | `ConnectException: No response from peer for 30 seconds` | 60 s |

So the production failure is a **slow connect timeout**, not a fast, clearly named refusal. All
three exceptions (`UnsupportedProtocolVersionException`, `HttpConnectTimeoutException`,
`ConnectException`) are `IOException`s. Every existing `catch (IOException e)` catches them,
and `the_strict_failure_is_an_io_exception` pins that.

## What can't be tested here

**There's no HTTP/3 server in the JDK.** `jdk.httpserver` has no HTTP/3 or QUIC code at all, and
the JDK's QUIC implementation lives in `java.base` under `jdk.internal.net.quic`, which is
exported to `java.net.http` and nothing else (verified: no HTTP/3 or QUIC sources under
`jdk.httpserver` in `src.zip`, and `java --describe-module java.base` says `qualified exports
jdk.internal.net.quic to java.net.http`). JEP 517 is a client feature. Serving HTTP/3 still needs
something outside the JDK: Netty, Jetty, nginx, or a load balancer in front. So there's no offline
HTTP/3 round trip, and every test here, and every row of the worked example, is a test of the
fallback. This is where the track's "everything works offline" stops, and it stops because of
what the JEP chose to include.

## Write the answers here

1. **Request-level or client-level**, for a service-to-service call inside one datacentre where
   you control both ends? And for a public API used by mobile clients? Use the 2.8 s and 0.08 s
   from the worked example. _(fill in)_
2. **Would you ever ship `HTTP_3_URI_ONLY`?** Name the deployment where a 20-second connect
   timeout beats a silent downgrade. _(fill in)_
3. **What does QUIC over UDP do to your observability?** Name one thing that worked over TCP and
   stops working. _(fill in)_
4. **Your service already retries on `IOException`.** With `HTTP_3_URI_ONLY` behind a firewall
   that drops UDP, how long does one call now take, counting the retries? Is that right?
   _(fill in)_
5. **Propose a CI test for real HTTP/3 behaviour** that doesn't depend on the public internet,
   and say what it costs. _(fill in)_

## What got worse

- _(fill this in)_

Prompts: the client-level setting opens two connections per new origin. At what request volume
does that stop being free? If HTTP/3 falls back without saying so, what kind of problem stays
hidden until someone looks at a packet capture? The request-level setting adds about three seconds
to every new connection on a network that drops UDP: who notices, and how? What's the argument
for not turning this on at all?

Sources: [JEP 517](https://openjdk.org/jeps/517) · the `HttpClient` and `HttpOption` javadoc, and
`jdk/internal/net/http/ExchangeImpl.java`, in JDK 26's `lib/src.zip`
