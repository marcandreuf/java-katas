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

## What you do in this doc

Read the **Theory** sections first. They change no code. Then do the **Practice** exercises, in
order. Each one ends with a **Done when** line.

| Exercise | What you do | Theory it uses | Tests |
| --- | --- | --- | --- |
| [1. `Http3`](#exercise-1-http3-five-todos-three-commits) | Fill in the five TODOs. Three commits. | T1–T5 | `Http3Test` is already written: delete its `@Disabled`. |
| [2. Write the answers](#exercise-2-write-the-answers-here) | Five written answers, using the measured numbers. | T3, T5, T6 | None. |
| [3. What got worse](#exercise-3-fill-in-what-got-worse) | Write down what HTTP/3 costs. | all | None. |
| [4. Review questions](#exercise-4-review-questions) | Answer without looking, then check against the theory. | all | None. |

## Theory

Read the six sections in order: each one builds on the one before. Here is the whole story in one
paragraph, so you know where it's going.

> HTTP/3 is the same HTTP you already send, over a different transport: QUIC, on UDP, instead of
> TCP (T1). The client treats every version you ask for as a wish, falls back without a word when
> it can't have it, and records what really happened in one place only: the response (T2). You
> have been watching that happen since doc 10, one version lower. *Where* you ask for HTTP/3, on
> the request or on the client, decides whether a fallback costs 3 seconds or nothing (T3). How the
> client finds an HTTP/3 server is a separate knob, the discovery mode (T4). The tests in this repo
> never send a single QUIC packet, for a reason that's in the javadoc, and the real production
> failure looks nothing like the exception the tests see (T5). And there is no HTTP/3 server in the
> JDK, so this is where "everything works offline" stops (T6).

The complete programs below run offline: each starts its own `com.sun.net.httpserver.HttpServer`
on `127.0.0.1` and a free port, as in [doc 10, T5](10-sequenced-collections-and-httpclient.md#t5-httpclient-a-client-a-request-a-response).
Save one as the class name plus `.java` and run it with `java Name.java`. Outputs marked
*verified* were run on JDK 26. **Nothing in this doc contacted a real HTTP/3 server**: anything
about a real HTTP/3 round trip comes from the JEP, the javadoc or the JDK source, and says so.

### T1. Three versions, two transports

`HttpClient` has spoken HTTP/1.1 and HTTP/2 since Java 11. Both run over **TCP**. JDK 26 adds a
third constant, and that one runs over something else:

- **QUIC** is a transport protocol that runs over **UDP**. HTTP/3 runs over QUIC. HTTP/1.1 and
  HTTP/2 run over TCP. That's why this took a JEP of its own: the JDK needed a QUIC implementation,
  not just a new parser.

The API change is almost nothing: one more `Version` constant, and one new request option.

```java
import java.net.http.HttpClient;
import java.net.http.HttpOption;
import java.net.http.HttpOption.Http3DiscoveryMode;
import java.util.Arrays;

public class Versions {
    public static void main(String[] args) {
        System.out.println("versions:        " + Arrays.toString(HttpClient.Version.values()));
        System.out.println("default client:  " + HttpClient.newHttpClient().version());
        System.out.println("option:          " + HttpOption.H3_DISCOVERY.name()
                + " of type " + HttpOption.H3_DISCOVERY.type().getSimpleName());
        System.out.println("discovery modes: " + Arrays.toString(Http3DiscoveryMode.values()));
    }
}
```

```text
versions:        [HTTP_1_1, HTTP_2, HTTP_3]              (verified 2026-11-23)
default client:  HTTP_2
option:          H3_DISCOVERY of type Http3DiscoveryMode
discovery modes: [ANY, ALT_SVC, HTTP_3_URI_ONLY]
```

Note the second line: a client you build without choosing a version already *prefers* HTTP/2.
That matters in T2. The option and its three modes are T4's subject.

The interesting part isn't the API. It's what happens when the other end, or the network in
between, doesn't speak QUIC. On a corporate network that allows TCP 443 and drops UDP 443, that's
the normal case, not a rare one.

### T2. What you asked for, and what you got

Two more words:

- the **requested version** is what you asked for, `HttpRequest.version()`, an `Optional`. The
  **negotiated version** is what was actually used, `HttpResponse.version()`.
- a **fallback** is the client giving up on the version it wanted and using an older one
  instead, without telling you.

You've already seen a fallback, in every HttpClient sample since doc 10, without noticing. A
default client prefers HTTP/2 (T1). Here it is talking to the JDK's own server, which speaks only
HTTP/1.1, with a handler that reports what the client asked for:

```java
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class QuietDowngrade {
    public static void main(String[] args) throws Exception {
        // A local server that speaks HTTP/1.1 only, and reports what the client asked for.
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String upgrade = exchange.getRequestHeaders().getFirst("Upgrade");
            byte[] body = ("client asked to upgrade to: " + upgrade).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");

        try (HttpClient client = HttpClient.newHttpClient()) {     // no version set anywhere
            HttpRequest request = HttpRequest.newBuilder(uri).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("client prefers:    " + client.version());
            System.out.println("request asked for: " + request.version());
            System.out.println("server says:       " + response.body());
            System.out.println("status:            " + response.statusCode());
            System.out.println("actually spoke:    " + response.version());
        } finally {
            server.stop(0);
        }
    }
}
```

```text
client prefers:    HTTP_2                                (verified 2026-11-23)
request asked for: Optional.empty
server says:       client asked to upgrade to: h2c
status:            200
actually spoke:    HTTP_1_1
```

Read it line by line. The client wanted HTTP/2, and over plain `http://` it asked for it with an
`Upgrade: h2c` header ("h2c" is HTTP/2 without TLS). The server ignored the header and answered in
HTTP/1.1. The client accepted that, returned status 200 and the body, and said nothing. The request
has no version of its own (`Optional.empty`), because the preference lived on the client. Only the
last line, `response.version()`, tells you what really happened.

HTTP/3 behaves the same way, and that gives the rule everything below follows from:

> **Asking for HTTP/3 is a preference, not a requirement, and the response's `version()` is the
> only record of what happened.** Nothing throws when you don't get it, and nothing logs it.

### T3. On the request or on the client: the 40-fold difference

The version can go in two places:

```java
HttpRequest.newBuilder(uri).version(HttpClient.Version.HTTP_3)   // on the request
HttpClient.newBuilder().version(HttpClient.Version.HTTP_3)       // on the client
```

They look interchangeable. They aren't. The JDK source explains the difference.
`jdk/internal/net/http/ExchangeImpl.java`, in JDK 26's `src.zip`, says that when HTTP/3 is set
**on the request**, the client tries HTTP/3 first and only starts a TCP connection "if the HTTP/3
connection fails, or doesn't succeed in the imparted timeout", which defaults to **2750 ms**. When
it's set **on the client**, it starts both at once and uses whichever connects first.

So the request-level setting *waits* for QUIC to fail before trying TCP. The client-level setting
*races* the two.

That's measurable offline, against an HTTPS server that speaks only HTTP/1.1 over TCP: a
`com.sun.net.httpserver.HttpsServer` on `127.0.0.1` with a self-signed certificate from
`keytool`. A new client for every row, after one warm-up request (verified on JDK 26, three
runs each):

| How HTTP/3 was asked for | Negotiated | Time |
| --- | --- | --- |
| on the request | `HTTP_1_1` | **2.82–2.90 s** |
| on the client | `HTTP_1_1` | **0.07 s** |
| on the request, `H3_DISCOVERY = ALT_SVC` | `HTTP_1_1` | **0.05 s** |
| on the request, `-Djdk.httpclient.http3.maxDirectConnectionTimeout=500` | `HTTP_1_1` | **0.65 s** |

Every row got the same answer, HTTP/1.1, and only the time tells them apart. The request-level
setting waited out the HTTP/3 attempt before falling back. The client-level one raced, and TCP won
at once. `ALT_SVC` never tried QUIC, because the server sent no `Alt-Svc` header (T4). And the last
row shows the 2750 ms is a knob.

**A correction to the old version:** it said this distinction "is not observable in these
tests", which is true of `Http3Test`. But it presented that as a limit of working offline, and
it isn't one. Over HTTPS it shows up as a 40-fold difference in latency, and that's the number to
bring to the written question in exercise 2.

(The timing program isn't reproduced here: it is, in effect, `Http3`'s TODOs 1 and 5 with a
stopwatch around them, so writing it is yours.)

### T4. Finding an HTTP/3 server: discovery modes and `Alt-Svc`

A client can't tell from a URL whether the server speaks HTTP/3. There are two ways to find out:
try QUIC directly and see, or wait for the server to say so. Two more words:

- **`Alt-Svc`** is a response header in which a server says "I also speak HTTP/3, over there".
  It's how browsers find HTTP/3 on the open web: the first request goes over TCP, and later ones
  can use QUIC.
- the **discovery mode** (`HttpOption.H3_DISCOVERY`, with a value from `Http3DiscoveryMode`) is a
  hint on the request about how to find an HTTP/3 server: `ANY`, `ALT_SVC` or `HTTP_3_URI_ONLY`.

Here is what the header looks like. The server below advertises HTTP/3 on UDP port 8443, and the
client just reads the header (over plain `http://`, so the client won't act on it: T5 says why):

```java
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class AltSvc {
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            // "I also speak HTTP/3, on UDP port 8443, and you may remember that for an hour."
            exchange.getResponseHeaders().add("Alt-Svc", "h3=\":8443\"; ma=3600");
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<Void> response = client.send(
                    HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.discarding());
            System.out.println("Alt-Svc: " + response.headers().firstValue("alt-svc").orElse("(none)"));
        } finally {
            server.stop(0);
        }
    }
}
```

```text
Alt-Svc: h3=":8443"; ma=3600                             (verified 2026-11-23)
```

The three modes, summarised from the `Http3DiscoveryMode` javadoc in JDK 26's `src.zip`:

| Mode | How the client finds HTTP/3 | When it can't have HTTP/3 |
| --- | --- | --- |
| `ANY` | implementation-specific: uses any `Alt-Svc` it has already seen, otherwise tries QUIC at the URI's own host and port; may race QUIC against TLS over TCP | uses whichever connection succeeds first |
| `ALT_SVC` | only once the server has advertised `h3` with `Alt-Svc`; until then, requests go over HTTP/1.1 or HTTP/2 | falls back to TCP; with no advert, never tries QUIC (T3's third row) |
| `HTTP_3_URI_ONLY` | QUIC at the URI's own host and port, and nothing else; `Alt-Svc` is not used | **throws**: the fallback is forbidden |

`HTTP_3_URI_ONLY` is the only way to make "I asked for HTTP/3" a requirement instead of a
preference. T5 shows what that costs.

### T5. Why the tests never try HTTP/3, and what production actually sees

**A correction to the old version, and the main one in this doc.** It said the echo server falls
back because it "does not speak HTTP/3", and that the strict-mode exception "is the production
failure" a firewall dropping UDP 443 would cause. Neither is right.

The tests use `http://127.0.0.1:...`, and the `HttpClient` javadoc in JDK 26 says: *"a request
whose URI scheme is not `https` will never be sent over HTTP/3. In this implementation, HTTP/3 is
not used if a proxy is selected."* So no QUIC packet is ever sent. The client decides from the URI
alone, without touching the network, and "plain connections" in the strict-mode message
means **plain `http://`**, not "the server lacked HTTP/3".

What a firewall that drops UDP actually produces, verified against the HTTPS server from T3 with
`HTTP_3_URI_ONLY` and nothing listening for QUIC:

| Request timeout | Exception | After |
| --- | --- | --- |
| `timeout(Duration.ofSeconds(20))` | `HttpConnectTimeoutException: HTTP connect timed out` | 20 s |
| `timeout(Duration.ofSeconds(90))` | `ConnectException: No response from peer for 30 seconds` | 60 s |

So the production failure is a **slow connect timeout**, not a fast, clearly named refusal. All
three exceptions (`UnsupportedProtocolVersionException`, `HttpConnectTimeoutException`,
`ConnectException`) are `IOException`s. Every existing `catch (IOException e)` catches them,
and `the_strict_failure_is_an_io_exception` pins that.

### T6. What can't be tested here

**There's no HTTP/3 server in the JDK.** `jdk.httpserver` has no HTTP/3 or QUIC code at all, and
the JDK's QUIC implementation lives in `java.base` under `jdk.internal.net.quic`, which is
exported to `java.net.http` and nothing else (verified: no HTTP/3 or QUIC sources under
`jdk.httpserver` in `src.zip`, and `java --describe-module java.base` says `qualified exports
jdk.internal.net.quic to java.net.http`). JEP 517 is a client feature. Serving HTTP/3 still needs
something outside the JDK: Netty, Jetty, nginx, or a load balancer in front. So there's no offline
HTTP/3 round trip, and every test here, and every row of the worked example, is a test of the
fallback. This is where the track's "everything works offline" stops, and it stops because of
what the JEP chose to include.

## Practice

### Exercise 1: `Http3`, five TODOs, three commits

Delete the `@Disabled`, and all nine tests fail (verified): six error with
`UnsupportedOperationException`, and three fail because they expected a different exception.

Commits, in this order:

1. **TODOs 1–3: `get`, `negotiatedVersion`, `requestedVersion`.** Ask for HTTP/3 on the request.
   The body comes back as `"echo:/hello"`, and nothing warns. The test worth reading is
   `requested_and_negotiated_versions_disagree`. Before you run it, write down what you expect each
   of the two methods to return (T2).
2. **TODO 4: `strictGet` and `negotiatedVersionWith`.** `HTTP_3_URI_ONLY` forbids the fallback, and
   the test expects `UnsupportedProtocolVersionException` with "HTTP/3" and "plain connections" in
   its message. Read the message when you get it, then reread T5.
3. **TODO 5: `clientLevelNegotiatedVersion`.** The test looks just like TODO 2's, because here
   both fall back at once. T3 is where the difference shows.

**Done when:** all nine `Http3Test` tests pass with the `@Disabled` gone, the three commits are in
that order, and you wrote your prediction for `requested_and_negotiated_versions_disagree` before
you ran it.

### Exercise 2: write the answers here

1. **Request-level or client-level**, for a service-to-service call inside one datacentre where
   you control both ends? And for a public API used by mobile clients? Use the 2.8 s and 0.07 s
   from the worked example (T3). _(fill in)_
2. **Would you ever ship `HTTP_3_URI_ONLY`?** Name the deployment where a 20-second connect
   timeout beats a silent downgrade. _(fill in)_
3. **What does QUIC over UDP do to your observability?** Name one thing that worked over TCP and
   stops working. _(fill in)_
4. **Your service already retries on `IOException`.** With `HTTP_3_URI_ONLY` behind a firewall
   that drops UDP, how long does one call now take, counting the retries? Is that right?
   _(fill in)_
5. **Propose a CI test for real HTTP/3 behaviour** that doesn't depend on the public internet,
   and say what it costs. _(fill in)_

**Done when:** all five are filled in, and answers 1 and 4 use the measured numbers from T3 and T5
rather than guesses.

### Exercise 3: fill in "What got worse"

- _(fill this in)_

Prompts: the client-level setting opens two connections per new origin. At what request volume
does that stop being free? If HTTP/3 falls back without saying so, what kind of problem stays
hidden until someone looks at a packet capture? The request-level setting adds about three seconds
to every new connection on a network that drops UDP: who notices, and how? What's the argument
for not turning this on at all?

### Exercise 4: review questions

Answer each one in a line or two, without looking. Then check against the section in brackets.
Any you got wrong, reread that section.

1. HTTP/3 runs over which transport, and that over which protocol? Why did that need a JEP of its
   own? (T1)
2. You build a client with `HttpClient.newHttpClient()` and set no version anywhere. Which version
   does it prefer? (T1)
3. In `QuietDowngrade`, what did the client ask for, how did it ask, and what did it get? Which
   one line of code told you? (T2)
4. What type does `HttpRequest.version()` return, and why does it return that for a default
   request? (T2)
5. HTTP/3 set on the request versus on the client: which one waits and which one races, and what
   is the default wait? (T3)
6. Why did `ALT_SVC` fall back in 0.05 s against the HTTPS test server? (T3, T4)
7. Which discovery mode is the only one that can throw, and why? (T4)
8. `Http3Test` uses `http://127.0.0.1`. Does the client send any QUIC packets? Where does that
   rule come from? (T5)
9. A firewall drops UDP 443 and you use `HTTP_3_URI_ONLY`. What exception do you get, and how
   soon? (T5)
10. Why can't this repo test a real HTTP/3 round trip offline? (T6)

**Done when:** you can answer all ten without looking.

Sources: [JEP 517](https://openjdk.org/jeps/517) · the `HttpClient` and `HttpOption` javadoc, and
`jdk/internal/net/http/ExchangeImpl.java`, in JDK 26's `lib/src.zip`
