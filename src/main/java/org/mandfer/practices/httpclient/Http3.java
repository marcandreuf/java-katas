package org.mandfer.practices.httpclient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpOption;
import java.net.http.HttpOption.Http3DiscoveryMode;
import java.util.Optional;

/**
 * HTTP/3 (JEP 517, Java 26), and the fallback that is the thing you actually have to reason
 * about.
 *
 * HTTP/3 runs over QUIC, which runs over UDP. HTTP/2 and HTTP/1.1 run over TCP. That is why
 * this took a release of its own: the JDK had to acquire a QUIC implementation, not just a new
 * protocol parser. The API change is almost nothing -- one more Version constant -- and the
 * interesting part is what happens when the other end does not speak it, which on a corporate
 * network that permits TCP 443 and drops UDP 443 is the normal case rather than a rare one.
 *
 * These exercises run against LocalHttp.startEchoServer(), the same in-JVM
 * com.sun.net.httpserver instance docs/modernization/10 uses. That server speaks HTTP/1.1 and nothing else:
 * the JDK ships no QUIC server, so there is no such thing as an offline HTTP/3 round trip
 * here. What you CAN test offline, and what matters in production, is the fallback.
 *
 * See docs/modernization/29-http3-and-the-httpclient.md. NOTE: needs --release 26.
 *
 * TODO 1: get() -- ask for HTTP/3 on the REQUEST, and return the body. It will succeed. Write
 *         down what it actually spoke before you look at TODO 2.
 * TODO 2: negotiatedVersion() -- HttpResponse.version(), which reports what was really used,
 *         not what you asked for. The test asserts these two disagree, and that disagreement
 *         is the whole document.
 * TODO 3: requestedVersion() -- HttpRequest.version(), which is an Optional. Note that the
 *         JDK keeps the request and the response answers separate on purpose.
 * TODO 4: strictGet() -- the same call with
 *         .setOption(HttpOption.H3_DISCOVERY, Http3DiscoveryMode.HTTP_3_URI_ONLY), which
 *         forbids the fallback. This one throws. Read the exception message: it names exactly
 *         why. It is not what a firewall dropping UDP would give you; doc 29 shows that one.
 * TODO 5: clientLevelNegotiatedVersion() -- set the version on the CLIENT builder instead of
 *         the request. It also falls back here, so the test looks the same. The difference is
 *         not visible offline and you have to know it: see the table in docs/modernization/29.
 */
public final class Http3 {

    private Http3() {
    }

    /** GETs the body, asking for HTTP/3 at the request level. */
    public static String get(URI uri) throws Exception {
        throw new UnsupportedOperationException("TODO 1");
    }

    /** The protocol version the response actually arrived over. */
    public static HttpClient.Version negotiatedVersion(URI uri) throws Exception {
        throw new UnsupportedOperationException("TODO 2");
    }

    /** The protocol version the request asked for, if it asked for one. */
    public static Optional<HttpClient.Version> requestedVersion(URI uri) {
        throw new UnsupportedOperationException("TODO 3");
    }

    /** GETs the body with the fallback forbidden. Throws when HTTP/3 is not available. */
    public static String strictGet(URI uri) throws Exception {
        throw new UnsupportedOperationException("TODO 4");
    }

    /** The version actually used when the preference is set on the client, not the request. */
    public static HttpClient.Version clientLevelNegotiatedVersion(URI uri) throws Exception {
        throw new UnsupportedOperationException("TODO 5");
    }

    /** The version actually used under an explicit discovery mode. */
    public static HttpClient.Version negotiatedVersionWith(URI uri, Http3DiscoveryMode mode)
            throws Exception {
        throw new UnsupportedOperationException("TODO 4");
    }
}
