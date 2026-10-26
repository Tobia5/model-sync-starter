package example.consumer;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class SecurityOwner implements AutoCloseable {
  final Map<String,String> values = new ConcurrentHashMap<>(Map.of("alpha","alpha-A","beta","beta-A"));
  final AtomicInteger calls = new AtomicInteger();
  final AtomicInteger active = new AtomicInteger();
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final HttpServer server;
  SecurityOwner() throws Exception {
    server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),4);server.setExecutor(executor);
    server.createContext("/owner", exchange->{
      active.incrementAndGet();calls.incrementAndGet();
      try(exchange){
        String query=exchange.getRequestURI().getRawQuery();
        String tenant="tenant=alpha&subject=shared".equals(query)?"alpha":"tenant=beta&subject=shared".equals(query)?"beta":null;
        if(tenant==null){exchange.sendResponseHeaders(400,-1);return;}
        String value=values.get(tenant);byte[] bytes=value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("ETag",value);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);
      }finally{active.decrementAndGet();}
    });server.start();
  }
  String url(){return "http://127.0.0.1:"+server.getAddress().getPort()+"/owner";}
  @Override public void close() throws Exception {
    server.stop(0);executor.shutdown();
    if(!executor.awaitTermination(3,TimeUnit.SECONDS)||active.get()!=0)throw new IllegalStateException("Owned HTTP endpoint not drained");
  }
}
