package org.telegram.vr.quest.speech;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

public class HttpSpeechToTextTest {
    @Test public void redirectDoesNotContactAnotherRecipient() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger redirected = new AtomicInteger();
        server.createContext("/asr", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Location", "/other");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/other", exchange -> {
            redirected.incrementAndGet();
            exchange.sendResponseHeaders(200, -1); exchange.close();
        });
        server.start();
        try {
            SpeechToText.Result r = new HttpSpeechToText("http://127.0.0.1:"+server.getAddress().getPort()+"/asr", "test-token", "en")
                    .recognize(new byte[]{1,2}, "audio/l16", 16000, "en");
            assertFalse(r.ok());
            assertEquals(0, redirected.get());
        } finally { server.stop(0); }
    }

    @Test public void postsAudioAndKeepsTokenOutOfUrl() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> uri = new AtomicReference<>(), auth = new AtomicReference<>();
        AtomicReference<byte[]> body = new AtomicReference<>();
        server.createContext("/asr", exchange -> {
            uri.set(exchange.getRequestURI().toString());
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(exchange.getRequestBody().readAllBytes());
            byte[] response = "{\"results\":[{\"alternatives\":[{\"transcript\":\"hello\"}]}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            SpeechToText.Result r = new HttpSpeechToText("http://127.0.0.1:"+server.getAddress().getPort()+"/asr", "test-token", "en")
                    .recognize(new byte[]{1,2}, "audio/l16", 16000, "en");
            assertTrue(r.ok()); assertEquals("hello", r.text);
            assertEquals("Bearer test-token", auth.get());
            assertFalse(uri.get().contains("test-token"));
            assertArrayEquals(new byte[]{1,2},body.get());
        } finally { server.stop(0); }
    }
}
