package org.mandfer.practices.httpclient;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.UnsupportedProtocolVersionException;
import java.net.http.HttpOption.Http3DiscoveryMode;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Exercise: docs/modernization/29-http3-and-the-httpclient.md. Delete this line to begin.")
class Http3Test {

    private HttpServer server;
    private URI uri;

    @BeforeEach
    void startServer() throws IOException {
        server = LocalHttp.startEchoServer();
        uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/hello");
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    /** The fallback is transparent: you asked for HTTP/3, you got a 200 and the right body. */
    @Test
    void falling_back_still_returns_the_body() throws Exception {
        assertEquals("echo:/hello", Http3.get(uri));
    }

    @Test
    void the_request_asked_for_http_3() {
        assertEquals(Optional.of(HttpClient.Version.HTTP_3), Http3.requestedVersion(uri));
    }

    /**
     * The assertion worth the document. The server speaks HTTP/1.1 and nothing else, so the
     * HTTP/3 request silently became an HTTP/1.1 request. Nothing threw, nothing warned, and
     * the only way to find out is to ask the response.
     */
    @Test
    void the_response_came_back_over_http_1_1() throws Exception {
        assertEquals(HttpClient.Version.HTTP_1_1, Http3.negotiatedVersion(uri));
    }

    @Test
    void requested_and_negotiated_versions_disagree() throws Exception {
        assertNotEquals(
                Http3.requestedVersion(uri).orElseThrow(),
                Http3.negotiatedVersion(uri),
                "if these ever agree here, something is serving QUIC on loopback");
    }

    /** Setting the preference on the client rather than the request falls back too. */
    @Test
    void client_level_preference_also_falls_back() throws Exception {
        assertEquals(HttpClient.Version.HTTP_1_1, Http3.clientLevelNegotiatedVersion(uri));
    }

    /**
     * HTTP_3_URI_ONLY forbids the fallback, so the call fails instead of quietly downgrading.
     * This is the exception a firewall dropping UDP 443 would produce in production.
     */
    @Test
    void strict_mode_refuses_to_fall_back() {
        UnsupportedProtocolVersionException thrown =
                assertThrows(UnsupportedProtocolVersionException.class, () -> Http3.strictGet(uri));

        assertTrue(thrown.getMessage().contains("HTTP/3"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("plain connections"), thrown.getMessage());
    }

    /** UnsupportedProtocolVersionException is an IOException, so existing catch blocks see it. */
    @Test
    void the_strict_failure_is_an_io_exception() {
        assertThrows(IOException.class, () -> Http3.strictGet(uri));
    }

    @Test
    void permissive_discovery_modes_fall_back() throws Exception {
        assertEquals(HttpClient.Version.HTTP_1_1,
                Http3.negotiatedVersionWith(uri, Http3DiscoveryMode.ANY));
        assertEquals(HttpClient.Version.HTTP_1_1,
                Http3.negotiatedVersionWith(uri, Http3DiscoveryMode.ALT_SVC));
    }

    @Test
    void the_strict_discovery_mode_is_the_only_one_that_throws() {
        assertThrows(UnsupportedProtocolVersionException.class,
                () -> Http3.negotiatedVersionWith(uri, Http3DiscoveryMode.HTTP_3_URI_ONLY));
    }
}
