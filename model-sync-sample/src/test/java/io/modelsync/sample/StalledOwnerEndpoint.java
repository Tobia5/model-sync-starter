package io.modelsync.sample;

import io.modelsync.core.FetchResult;
import io.modelsync.core.ReadRequest;
import io.modelsync.core.port.BlockingOwner;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Real isolated owner socket; latch explicitly controls when the response can leave. */
final class StalledOwnerEndpoint implements BlockingOwner<Customer>, AutoCloseable {
  private final ServerSocket listener;
  private final Thread server;
  private final int readTimeout;
  private volatile Socket accepted;
  final CountDownLatch received = new CountDownLatch(1), release = new CountDownLatch(1), exited = new CountDownLatch(1);
  final AtomicInteger calls = new AtomicInteger(), activeClients = new AtomicInteger();
  StalledOwnerEndpoint(int readTimeout) throws Exception {
    this.readTimeout = readTimeout;
    listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
    server = new Thread(this::serve, "modelsync-owned-owner-endpoint"); server.start();
  }
  private void serve() {
    try (Socket socket = listener.accept()) {
      accepted = socket; socket.getInputStream().read(); received.countDown();
      if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Owner gate was not released");
      socket.getOutputStream().write(1); socket.getOutputStream().flush();
    } catch (java.io.IOException expectedDisconnect) { }
      catch (InterruptedException stopped) { Thread.currentThread().interrupt(); }
    finally { exited.countDown(); }
  }
  @Override public FetchResult<Customer> fetch(ReadRequest request) throws Exception {
    calls.incrementAndGet(); activeClients.incrementAndGet();
    try (Socket socket = new Socket()) {
      socket.connect(new java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), listener.getLocalPort()), 1000);
      socket.setSoTimeout(readTimeout); socket.getOutputStream().write(1);
      if (socket.getInputStream().read() != 1) throw new java.io.IOException("Owner response absent");
      return FetchResult.found(new Customer("late", "ACTIVE"), "late");
    } finally { activeClients.decrementAndGet(); }
  }
  @Override public void close() throws Exception {
    release.countDown(); listener.close(); if (accepted != null) accepted.close();
    server.join(5000); if (server.isAlive()) throw new AssertionError("Owned owner endpoint still active");
  }
}
