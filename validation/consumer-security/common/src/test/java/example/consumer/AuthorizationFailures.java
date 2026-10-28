package example.consumer;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.function.Executable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

/** Recognize the pinned framework's direct/reactive denial and MVC SpEL wrapper only. */
public final class AuthorizationFailures {
  private AuthorizationFailures() {}
  static RuntimeException denied(Executable operation) {
    RuntimeException failure=assertThrows(RuntimeException.class,operation);
    if(failure instanceof AccessDeniedException || failure instanceof AuthenticationCredentialsNotFoundException)return failure;
    assertEquals(IllegalArgumentException.class,failure.getClass(),"Unexpected failure is not authorization proof");
    Throwable cause=failure;
    for(int depth=0;depth<8&&cause!=null;depth++,cause=cause.getCause())
      if(cause instanceof AuthenticationCredentialsNotFoundException)return failure;
    fail("MVC expression failure lacked the required missing-authentication cause");return failure;
  }
}
