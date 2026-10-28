package io.modelsync.core.application;

import org.springframework.beans.factory.annotation.Autowired;

public final class FieldInjectionLeak {
  @Autowired public Object dependency;
}
