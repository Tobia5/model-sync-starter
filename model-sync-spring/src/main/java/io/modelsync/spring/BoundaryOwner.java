package io.modelsync.spring;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import java.lang.annotation.*;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * Identifies an optional catalog/inventory provider for a bounded context. A qualifier only:
 * explicit ModelBinding defines the route.
 */
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Qualifier
public @interface BoundaryOwner {
  String value();
}
