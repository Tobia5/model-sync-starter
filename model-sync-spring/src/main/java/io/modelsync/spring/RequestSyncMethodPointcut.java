package io.modelsync.spring;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import java.lang.reflect.Method;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.core.annotation.AnnotatedElementUtils;

/** Selects methods that explicitly register synchronization intent. */
final class RequestSyncMethodPointcut extends StaticMethodMatcherPointcut {
  @Override
  public boolean matches(Method method, Class<?> targetClass) {
    Method actual = AopUtils.getMostSpecificMethod(method, targetClass);
    return AnnotatedElementUtils.hasAnnotation(actual, RequestSync.class);
  }
}
