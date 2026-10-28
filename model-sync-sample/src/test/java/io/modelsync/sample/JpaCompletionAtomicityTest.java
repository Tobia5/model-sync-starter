package io.modelsync.sample;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = H2AtomicityConfiguration.class, properties = {
    "spring.profiles.active=atomicity-h2", "spring.jpa.hibernate.ddl-auto=create-drop",
    "model-sync.enabled=true", "model-sync.worker-enabled=false", "model-sync.aop.enabled=false",
    "model-sync.quotas.atomicity-owner=1ms"})
class JpaCompletionAtomicityTest extends AtomicityChecks {}
