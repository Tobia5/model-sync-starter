package io.modelsync.sample;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Test-owned loopback relay. Never records Oracle protocol bytes or credentials. */
final class OracleFaultProxy implements AutoCloseable {
  private final ServerSocket listener;
  private final URI upstream;
  private final java.util.concurrent.ExecutorService tasks = Executors.newThreadPerTaskExecutor(
      Thread.ofVirtual().name("modelsync-fault-proxy-", 0).factory());
  private final CopyOnWriteArrayList<Socket> sockets = new CopyOnWriteArrayList<>();
  private volatile boolean holdUp, holdDown;
  private final CountDownLatch release = new CountDownLatch(1);
  final CountDownLatch heldPacket = new CountDownLatch(1);
  final AtomicInteger activePumps = new AtomicInteger();
  private volatile boolean closed;
  private volatile boolean reject;
  OracleFaultProxy() throws Exception {
    upstream = upstream(System.getenv("MODELSYNC_ORACLE_URL"));
    listener = new ServerSocket(0, 8, InetAddress.getLoopbackAddress());
    tasks.submit(this::accept);
  }
  static URI upstream(String url) {
    if (url == null || !url.startsWith("jdbc:oracle:thin:@")) throw new IllegalStateException("Fault proxy requires explicit approved service URL");
    String address = url.substring("jdbc:oracle:thin:@".length());
    if (address.startsWith("//")) address = address.substring(2);
    URI value = URI.create("tcp://" + address);
    if (value.getHost() == null || value.getPort() < 1 || value.getPort() > 65535
        || value.getUserInfo() != null || value.getQuery() != null || value.getFragment() != null
        || value.getPath() == null || !value.getPath().matches("/[A-Za-z0-9_]+"))
      throw new IllegalStateException("Fault proxy requires a credential-free host:port/service URL");
    return value;
  }
  String url() { return "jdbc:oracle:thin:@//127.0.0.1:" + listener.getLocalPort() + upstream.getPath(); }
  void rejectConnections() { reject = true; }
  void arm(boolean upstreamDirection) { holdUp = upstreamDirection; holdDown = !upstreamDirection; }
  void release() { holdUp = false; holdDown = false; release.countDown(); }
  private void accept() {
    try {
      while (!closed) {
        Socket client = listener.accept(); sockets.add(client);
        if (reject) { client.setSoLinger(true, 0); client.close(); continue; }
        Socket server = new Socket(); sockets.add(server);
        server.connect(new java.net.InetSocketAddress(upstream.getHost(), upstream.getPort()), 1500);
        tasks.submit(() -> pump(client, server, true)); tasks.submit(() -> pump(server, client, false));
      }
    } catch (java.io.IOException expectedOnClose) { if (!closed) throw new IllegalStateException("Owned relay failed", expectedOnClose); }
  }
  private void pump(Socket from, Socket to, boolean up) {
    activePumps.incrementAndGet();
    try {
      byte[] buffer = new byte[16384]; int count;
      while ((count = from.getInputStream().read(buffer)) != -1) {
        if (up ? holdUp : holdDown) { heldPacket.countDown(); release.await(); }
        to.getOutputStream().write(buffer, 0, count); to.getOutputStream().flush();
      }
    } catch (java.io.IOException expectedDisconnect) { /* isolated transport fault */ }
      catch (InterruptedException stopped) { Thread.currentThread().interrupt(); }
    finally {
      try { from.close(); } catch (java.io.IOException ignored) { }
      try { to.close(); } catch (java.io.IOException ignored) { }
      activePumps.decrementAndGet();
    }
  }
  @Override public void close() throws Exception {
    closed = true; listener.close();
    for (Socket socket : sockets) socket.close();
    release();
    tasks.shutdown();
    if (!tasks.awaitTermination(5, TimeUnit.SECONDS) || activePumps.get() != 0)
      throw new AssertionError("Owned proxy tasks remain active");
  }
}
