package example.consumer;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

/** Uses actual HTTP Basic and the framework's CSRF endpoint/session, never a custom token protocol. */
public final class SecurityHttpClient implements AutoCloseable {
  private final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL))
      .connectTimeout(Duration.ofSeconds(2)).build();
  private final String endpoint;
  private final String user;
  private String csrfHeader;
  private String csrfToken;
  SecurityHttpClient(String endpoint,String user) throws Exception {
    this.endpoint=endpoint;this.user=user;
    if(user!=null){
      var response=send("GET","/csrf",false);
      org.junit.jupiter.api.Assertions.assertEquals(200,response.statusCode(),"CSRF endpoint status");
      var tree=new tools.jackson.databind.ObjectMapper().readTree(response.body());
      csrfHeader=tree.get("headerName").asString();csrfToken=tree.get("token").asString();
    }
  }
  private HttpRequest request(String method,String path,boolean csrf){
    var builder=HttpRequest.newBuilder(URI.create(endpoint+path)).timeout(Duration.ofSeconds(5));
    if(user!=null)builder.header("Authorization","Basic "+Base64.getEncoder().encodeToString((user+":"+FixtureIdentities.password()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    if(csrf)builder.header(csrfHeader,csrfToken);
    return builder.method(method,HttpRequest.BodyPublishers.noBody()).build();
  }
  HttpResponse<String> send(String method,String path,boolean csrf)throws Exception{return http.send(request(method,path,csrf),HttpResponse.BodyHandlers.ofString());}
  CompletableFuture<HttpResponse<String>> asyncRead(String path){return http.sendAsync(request("GET",path,false),HttpResponse.BodyHandlers.ofString());}
  @Override public void close(){http.close();}
}
