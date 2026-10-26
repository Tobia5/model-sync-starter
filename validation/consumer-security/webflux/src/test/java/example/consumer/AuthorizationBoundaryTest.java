package example.consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

class AuthorizationBoundaryTest {
  private AnnotationConfigApplicationContext context;
  private WebTestClient http;
  private AccessObservation observation;
  @BeforeEach void start() {
    context=new AnnotationConfigApplicationContext();context.getEnvironment().setActiveProfiles("cheap-security");context.register(CheapSecurityConfiguration.class);context.refresh();
    http=WebTestClient.bindToApplicationContext(context).apply(springSecurity()).configureClient().build();
    observation=context.getBean(AccessObservation.class);
  }
  @AfterEach void stop(){context.close();}
  private int request(String user,String tenant,String binding,String subject,String op,boolean token) {
    var client=token?http.mutateWith(csrf()):http;
    String url="/models/"+tenant+"/"+binding+"/"+subject;
    var request="read".equals(op)?client.get().uri(url):client.post().uri(url+"/"+op);
    if(user!=null)request.headers(h->h.setBasicAuth(user,FixtureIdentities.password()));
    // Do not use assertion diagnostics which would print authentication headers.
    return request.exchange().returnResult(String.class).getStatus().value();
  }
  @Test void actualChainRequiresAuthenticationAndCsrf() {
    assertEquals(401,request(null,"alpha","item","shared","read",false));
    assertEquals(403,request("alice","alpha","item","shared","refresh",false));
    assertEquals(new AccessCounts(0,0,0),observation.counts());
  }
  @Test void actualChainRejectsTenantBindingSubjectAndUnknownOperationBeforeAccess() {
    for(String[] c:List.of(new String[]{"beta","item","shared","read"},new String[]{"alpha","unknown","shared","read"},
        new String[]{"alpha","item","other","read"},new String[]{"unknown","item","shared","read"},new String[]{"alpha","item","shared","delete"}))
      assertEquals(403,request("alice",c[0],c[1],c[2],c[3],true));
    assertEquals(new AccessCounts(0,0,0),observation.counts());
  }
  @Test void directServiceAndEventIngressCannotBypassAuthorization() {
    var facade=context.getBean(ConsumerFacade.class);
    AuthorizationFailures.denied(()->facade.execute("read","alpha","item","shared").block());
    var reader=FixtureIdentities.users().stream().filter(u->u.getUsername().equals("reader")).findFirst().orElseThrow();
    var auth=UsernamePasswordAuthenticationToken.authenticated(reader,"",reader.getAuthorities());
    for(String op:List.of("refresh","event"))assertThrows(org.springframework.security.access.AccessDeniedException.class,
        ()->facade.execute(op,"alpha","item","shared").contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)).block());
    assertEquals(new AccessCounts(0,0,0),observation.counts());
  }
  @Test void exactGrantsSeparateReadRefreshAndEvent() {
    assertEquals(200,request("reader","alpha","item","shared","read",false));
    assertEquals(403,request("reader","alpha","item","shared","refresh",true));
    assertEquals(200,request("refresher","alpha","item","shared","refresh",true));
    assertEquals(403,request("refresher","alpha","item","shared","event",true));
    assertEquals(200,request("alice","alpha","item","shared","event",true));
    assertEquals(new AccessCounts(1,1,1),observation.counts());
  }
  @Test void missingAuthorityAndUnknownTargetsNeverUseDefaults() {
    assertEquals(403,request("empty","alpha","item","shared","read",true));
    assertEquals(403,request("bob","alpha","item","shared","read",true));
    assertEquals(new AccessCounts(0,0,0),observation.counts());
  }
  @Test void concurrentReactiveIdentitiesSurviveRealSchedulerHops() {
    var facade=context.getBean(ConsumerFacade.class);var users=FixtureIdentities.users();
    var values=Flux.range(0,32).flatMap(i->{
      var user=users.get(i%2);String tenant=i%2==0?"alpha":"beta";
      var auth=UsernamePasswordAuthenticationToken.authenticated(user,"",user.getAuthorities());
      return facade.execute("read",tenant,"item","shared")
          .doOnNext(v->assertEquals(tenant+":shared",v))
          .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
    },8).collectList().block();
    assertEquals(32,values.size());assertEquals(32,observation.reads.get());
    assertTrue(observation.accepted.stream().allMatch(a->a.thread().startsWith("consumer-security-test")
        && a.tenant().equals(a.principal().equals("alice")?"alpha":"beta")));
  }
}
