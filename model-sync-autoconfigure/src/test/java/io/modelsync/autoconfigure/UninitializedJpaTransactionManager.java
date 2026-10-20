package io.modelsync.autoconfigure;

import org.springframework.orm.jpa.JpaTransactionManager;

/** Only datasource identity is under test; no entity-manager factory or connection is opened. */
final class UninitializedJpaTransactionManager extends JpaTransactionManager {
  @Override public void setBeanFactory(org.springframework.beans.factory.BeanFactory beanFactory) {}
  @Override public void afterPropertiesSet() {}
}
