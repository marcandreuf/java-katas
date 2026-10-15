# 29. HTTP/3 and the HttpClient

**Java 26** · stage 18 of [the release-ordered track](INDEX.md) · **needs JDK 26+ — not compiled
in this repo**

## Where it lands

**`httpclient/LocalHttp.java`** is the existing exercise and
[doc 10](10-sequenced-collections-and-httpclient.md) owns it. Finish its three TODOs first,
including TODO 3, `sendAsync()` — which also needs [doc 15](15-completable-future.md).

**There is no compiled exercise here**, and the reason is honest rather than editorial: this
machine has JDK 25 and JDK 27, and `HttpClient.Version.HTTP_3` does not exist on 25.
`<java.release>` is 25, so a file referencing it would not compile. The code below is correct
against JEP 517; it is in a document rather than in `src/main/java` so the build stays green.

To make it a real exercise: install a 26+ JDK, raise `<java.release>` to 26, and move the snippet
into `httpclient/`. [`env-setup.md`](env-setup.md) has the commands.

## What changed

HTTP/3 runs over **QUIC**, which runs over **UDP**. HTTP/2 and HTTP/1.1 run over TCP. That is the
whole reason this took a release of its own: the JDK had to acquire a QUIC implementation, not
just a new protocol parser.

The API change is deliberately almost nothing:

    // client-level
    HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_3)
            .build();

    // request-level
    HttpRequest request = HttpRequest.newBuilder(uri)
            .version(HttpClient.Version.HTTP_3)
            .GET()
            .build();

**The two are not the same, and this is the detail worth the document:**

| Where you set it | Behaviour when HTTP/3 is unavailable |
| --- | --- |
| on the **request** | tries HTTP/3 first, then **falls back** to HTTP/2 or 1.1 |
| on the **client** | **races** an HTTP/3 connection against an HTTP/2 or 1.1 one, first to connect wins |

So the client-level setting costs you a second connection attempt on every new origin, and buys
you a lower worst-case latency when HTTP/3 is blocked. The request-level setting costs you a
timeout on the HTTP/3 attempt before falling back. **Neither is the obviously right default**, and
being able to say which you would pick for a mobile client versus a service-to-service call inside
one datacentre is the useful version of knowing this feature.

That is a general shape worth noticing: HTTP/3 over UDP means **middleboxes and corporate
firewalls that permit TCP 443 frequently drop UDP 443**, so "HTTP/3 is unavailable" is not a rare
path. It is the normal path on a lot of enterprise networks, which is why the fallback semantics
got this much API design.

## Why `LocalHttp` cannot test this offline

[Doc 10](10-sequenced-collections-and-httpclient.md) makes a point of the HTTP exercise needing no
network: it starts a `com.sun.net.httpserver.HttpServer` inside the test JVM.
**`com.sun.net.httpserver` does not speak HTTP/3** — it is an HTTP/1.1 server, and the JDK ships
no QUIC *server*.

So an offline HTTP/3 test is not available, and the honest exercise is therefore a reading one.
Note what that tells you: **JEP 517 is a client-side feature only.** If you want to serve HTTP/3
you need Netty, Jetty, nginx or a load balancer in front, and that is unchanged by Java 26.

## Write the answers here

1. **Request-level or client-level** for a service-to-service call inside one datacentre where
   you control both ends? And for a public API consumed by mobile clients? _(fill in)_
2. **What does QUIC-over-UDP do to your observability?** Name one thing that stops working that
   worked over TCP. _(fill in)_
3. **`LocalHttp` keeps the whole track offline and cannot cover this.** Propose how you would test
   HTTP/3 behaviour in CI without depending on the public internet, and say what that costs.
   _(fill in)_
4. **The fallback is invisible by default.** How would you find out, in production, which protocol
   a given request actually used? _(fill in)_

## What got worse

- _(fill this in)_

Prompts: the client-level setting opens two connections per origin — at what request volume does
that stop being free? If HTTP/3 silently falls back, what class of bug does that hide until
someone looks at a packet capture? What is the argument for *not* turning this on?

Sources: [JEP 517](https://openjdk.org/jeps/517) ·
[JDK 26 heads-up](https://inside.java/2025/10/30/quality-heads-up/)
