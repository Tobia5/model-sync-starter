package example.consumer;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Test-owned loopback-only HTTP owner. */
public final class LocalOwner implements AutoCloseable {
  final AtomicReference<String> value = new AtomicReference<>("A");
  final AtomicInteger calls = new AtomicInteger();
  final AtomicInteger active = new AtomicInteger();
  final HttpServer server;
  final ExecutorService executor = Executors.newSingleThreadExecutor();
  LocalOwner() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 4);
    server.setExecutor(executor);
    server.createContext("/owner", exchange -> {
      active.incrementAndGet(); calls.incrementAndGet();
      try (exchange) {
        byte[] bytes = value.get().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("ETag", value.get());
        exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes);
      } finally { active.decrementAndGet(); }
    });
    server.start();
  }
  String url() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/owner"; }
  @Override public void close() throws Exception {
    server.stop(0); executor.shutdown();
    if (!executor.awaitTermination(3, TimeUnit.SECONDS) || active.get() != 0)
      throw new IllegalStateException("Synthetic owner did not stop physically");
  }
}
