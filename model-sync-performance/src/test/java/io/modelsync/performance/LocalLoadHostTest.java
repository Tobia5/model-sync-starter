package io.modelsync.performance;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Bounded socket regression for the largest declared simultaneous local load burst. */
public class LocalLoadHostTest {
  @Test
  void acceptsLargestDeclaredBurstWithoutConnectionRefusals() throws Exception {
    try (var host = new LocalLoadHost("fresh")) {
      host.start();
      try (var clients = Executors.newVirtualThreadPerTaskExecutor()) {
        var ready = new CountDownLatch(200);
        var start = new CountDownLatch(1);
        var results = new ArrayList<Future<?>>();
        for (int i = 0; i < 200; i++) results.add(clients.submit(() -> {
          ready.countDown();
          assertTrue(start.await(5, TimeUnit.SECONDS));
          try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 18088), 5000);
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(("GET /visit HTTP/1.1\r\nHost: localhost\r\n"
                + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(response.startsWith("HTTP/1.1 200"), response);
            assertTrue(response.endsWith("verified"), response);
          }
          return null;
        }));
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        for (var result : results) result.get(10, TimeUnit.SECONDS);
      }
      host.assertOutcome();
    }
  }

  /** Allows the bounded regression to run before provisioning the expensive Oracle fixture. */
  public static void main(String[] args) throws Exception {
    new LocalLoadHostTest().acceptsLargestDeclaredBurstWithoutConnectionRefusals();
    System.out.println("Local host regression: 200 simultaneous sockets passed; not a Gatling qualification.");
  }
}
