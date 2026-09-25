package org.mandfer.practices.httpclient;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * java.net.http.HttpClient against a server started in the same JVM, so the exercise
 * works with no internet. This is the API that replaces Apache HttpClient.
 *
 * Nothing else in this repo makes an HTTP call, which is why this exercise is new code.
 *
 * TODO 1: implement get() with HttpClient.newHttpClient() and BodyHandlers.ofString().
 * TODO 2: make it send a header and a timeout, and handle a non-200 without throwing.
 * TODO 3: switch it to sendAsync() and return a CompletableFuture. Then write down
 *         which of the two you would use for a remote API call, and why.
 */
public final class LocalHttp {

    private LocalHttp() {
    }

    /** Starts a throwaway server that echoes the path. Caller stops it. */
    public static HttpServer startEchoServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = ("echo:" + exchange.getRequestURI().getPath()).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        return server;
    }

    public static String get(URI uri) throws Exception {
        throw new UnsupportedOperationException("TODO 1");
    }

    /** Provided so you can see the shape once. Delete these when TODO 1 is done. */
    static HttpRequest requestFor(URI uri) {
        return HttpRequest.newBuilder(uri).GET().build();
    }

    static HttpResponse.BodyHandler<String> stringBody() {
        return HttpResponse.BodyHandlers.ofString();
    }

    static HttpClient client() {
        return HttpClient.newHttpClient();
    }
}
