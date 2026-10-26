package example.consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

class AuthorizationBoundaryTest {
  private AnnotationConfigWebApplicationContext context;
  private MockMvc http;
  private AccessObservation observation;
  @BeforeEach void start() {
    context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
    context.getEnvironment().setActiveProfiles("cheap-security"); context.register(CheapSecurityConfiguration.class); context.refresh();
    http = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    observation = context.getBean(AccessObservation.class);
  }
  @AfterEach void stop() { SecurityContextHolder.clearContext(); context.close(); }
  private int request(String user, String tenant, String binding, String subject, String op, boolean token) throws Exception {
    String url = "/models/" + tenant + "/" + binding + "/" + subject;
    var request = "read".equals(op) ? get(url) : post(url + "/" + op);
    if (user != null) request.with(httpBasic(user, FixtureIdentities.password()));
    if (token) request.with(csrf());
    return http.perform(request).andReturn().getResponse().getStatus();
  }
  @Test void actualChainRequiresAuthenticationAndCsrf() throws Exception {
    assertEquals(401, request(null,"alpha","item","shared","read",false));
    // MockMvc measured403; the live servlet journey separately requires its measured401.
    assertEquals(403,request(null,"alpha","item","shared","refresh",false));
    assertEquals(403, request("alice","alpha","item","shared","refresh",false));
    assertEquals(new AccessCounts(0,0,0), observation.counts());
  }
  @Test void actualChainRejectsTenantBindingSubjectAndUnknownOperationBeforeAccess() throws Exception {
    for (String[] c : List.of(new String[]{"beta","item","shared","read"}, new String[]{"alpha","unknown","shared","read"},
        new String[]{"alpha","item","other","read"},new String[]{"unknown","item","shared","read"},new String[]{"alpha","item","shared","delete"}))
      assertEquals(403,request("alice",c[0],c[1],c[2],c[3],true));
    assertEquals(new AccessCounts(0,0,0),observation.counts());
  }
  @Test void directServiceAndEventIngressCannotBypassAuthorization() {
    var facade=context.getBean(ConsumerFacade.class);
    AuthorizationFailures.denied(()->facade.execute("read","alpha","item","shared"));
    var reader=FixtureIdentities.users().stream().filter(u->u.getUsername().equals("reader")).findFirst().orElseThrow();
    SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(reader,"",reader.getAuthorities()));
    for(String op:List.of("refresh","event"))assertThrows(org.springframework.security.access.AccessDeniedException.class,()->facade.execute(op,"alpha","item","shared"));
    assertEquals(new AccessCounts(0,0,0),observation.counts());
  }
  @Test void exactGrantsSeparateReadRefreshAndEvent() throws Exception {
    assertEquals(200,request("reader","alpha","item","shared","read",false));
    assertEquals(403,request("reader","alpha","item","shared","refresh",true));
    assertEquals(200,request("refresher","alpha","item","shared","refresh",true));
    assertEquals(403,request("refresher","alpha","item","shared","event",true));
    assertEquals(200,request("alice","alpha","item","shared","event",true));
    assertEquals(new AccessCounts(1,1,1),observation.counts());
  }
  @Test void missingAuthorityAndUnknownTargetsNeverUseDefaults() throws Exception {
    assertEquals(403,request("empty","alpha","item","shared","read",true));
    assertEquals(403,request("bob","alpha","item","shared","read",true));
    assertEquals(new AccessCounts(0,0,0),observation.counts());
    assertTrue(context.getBean(ResourcePolicy.class).lookups.get()>0);
  }
}
