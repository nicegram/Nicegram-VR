package org.telegram.vr.quest.speech;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class HttpSpeechToTextTest {
    // Android's unit-test compile classpath has no jdk.httpserver module. A loopback-only
    // HTTP fixture exercises the real transport with the java.net API available on both.
    private static class Server implements AutoCloseable {
        final ServerSocket socket = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"));
        final AtomicInteger redirects = new AtomicInteger();
        volatile String request, authorization;
        volatile byte[] audio;
        volatile Throwable failure;
        final Thread worker;
        Server(boolean redirect) throws IOException {
            worker = new Thread(() -> {
                try {
                    while (!socket.isClosed()) {
                        try (Socket client = socket.accept()) {
                            client.setSoTimeout(3000);
                            InputStream in = client.getInputStream();
                            String first = line(in);
                            int length = 0;
                            String header;
                            while (!(header = line(in)).isEmpty()) {
                                if (header.toLowerCase().startsWith("content-length:")) length = Integer.parseInt(header.substring(15).trim());
                                if (header.toLowerCase().startsWith("authorization:")) authorization = header.substring(14).trim();
                            }
                            byte[] bytes = new byte[length];
                            new DataInputStream(in).readFully(bytes);
                            String response;
                            if (first.contains("/other")) {
                                redirects.incrementAndGet();
                                response = "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
                            } else {
                                request = first; audio = bytes;
                                if (redirect) response = "HTTP/1.1 302 Found\r\nLocation: /other\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
                                else {
                                    String json = "{\"results\":[{\"alternatives\":[{\"transcript\":\"hello\"}]}]}";
                                    response = "HTTP/1.1 200 OK\r\nContent-Length: "+json.length()+"\r\nConnection: close\r\n\r\n"+json;
                                }
                            }
                            client.getOutputStream().write(response.getBytes(StandardCharsets.UTF_8));
                        }
                    }
                } catch (Throwable error) { if (!socket.isClosed()) failure = error; }
            });
            worker.setDaemon(true);
            worker.start();
        }
        static String line(InputStream in) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (int c; (c=in.read()) != -1 && c!='\n';) if (c!='\r') out.write(c);
            return out.toString("UTF-8");
        }
        String url() { return "http://127.0.0.1:"+socket.getLocalPort()+"/asr"; }
        public void close() throws IOException { socket.close(); }
    }

    @Test public void redirectDoesNotContactAnotherRecipient() throws Exception {
        try (Server server = new Server(true)) {
            SpeechToText.Result r = new HttpSpeechToText(server.url(), "test-token", "en")
                    .recognize(new byte[]{1,2}, "audio/l16", 16000, "en");
            assertFalse(r.ok()); assertEquals(0, server.redirects.get()); assertNull(server.failure);
        }
    }

    @Test public void postsAudioAndKeepsTokenOutOfUrl() throws Exception {
        try (Server server = new Server(false)) {
            SpeechToText.Result r = new HttpSpeechToText(server.url(), "test-token", "en")
                    .recognize(new byte[]{1,2}, "audio/l16", 16000, "en");
            assertTrue(r.ok()); assertEquals("hello", r.text);
            assertEquals("Bearer test-token", server.authorization);
            assertFalse(server.request.contains("test-token"));
            assertArrayEquals(new byte[]{1,2},server.audio); assertNull(server.failure);
        }
    }
}
