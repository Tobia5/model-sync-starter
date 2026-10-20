package io.modelsync.spring;

import io.modelsync.core.Priority;
import java.lang.annotation.*;

/**
 * Public synchronous service boundary: business mutation and intent are committed together.
 * Identity expressions use input #p0/#a0 variables, not #result. Intent is registered before
 * business writes. Do not combine with @Transactional on this method/class; this advice owns
 * REQUIRED semantics.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequestSync {
  String binding();

  String key();

  String tenant();

  Priority priority() default Priority.NORMAL;

  boolean force() default false;
}
