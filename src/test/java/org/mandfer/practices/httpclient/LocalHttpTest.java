package org.mandfer.practices.httpclient;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalHttpTest {

    @Test
    void http_client_against_a_server_in_this_jvm() throws Exception {
        HttpServer server = LocalHttp.startEchoServer();
        try {
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/ping");
            assertEquals("echo:/ping", LocalHttp.get(uri));
        } finally {
            server.stop(0);
        }
    }
}
