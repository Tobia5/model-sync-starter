package io.modelsync.spring;

import java.lang.annotation.*;
import org.springframework.beans.factory.annotation.Qualifier;
/** Identifies an authoritative reader bean; does not grant write permissions. A qualifier only: explicit ModelBinding defines the route. */
@Target({ElementType.TYPE,ElementType.METHOD,ElementType.PARAMETER,ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Qualifier
public @interface DataOwner { String value(); }
