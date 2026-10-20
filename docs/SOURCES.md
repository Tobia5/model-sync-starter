# Official design references

Consulted 2026-10-19. These references support framework/API decisions, not a claim that this project has passed a full framework or Oracle build. Project-specific algorithms and limitations are documented separately.

| ID | Primary source | Used for |
|---|---|---|
| S1 | https://docs.spring.io/spring-boot/system-requirements.html | Boot/JDK/Maven compatibility baseline |
| S2 | https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html | Starter split, conditional defaults, imports, test patterns |
| S3 | https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/orm/jpa/JpaTransactionManager.html | Shared local JPA and JDBC transaction support |
| S4 | https://docs.spring.io/spring-framework/reference/core/aop/proxying.html | Proxy boundaries and self-invocation limitations |
| S5 | https://projectreactor.io/docs/core/release/reference/faq.html | Blocking wrapper and bounded scheduler pattern |
| S6 | https://docs.spring.io/spring-framework/reference/integration/rest-clients.html | RestClient, WebClient and HTTP service interfaces |
| S7 | https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/SELECT.html | SELECT FOR UPDATE / SKIP LOCKED / row-limiting restrictions |
| S8 | https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired-qualifiers.html | Custom qualifier annotations |
| S9 | https://docs.spring.io/spring-modulith/reference/events.html | Transactional publication and durability distinction |
| S10 | https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html | Hibernate/Envers scope, sessions and bulk-operation limitations |
| S11 | https://jakarta.ee/specifications/persistence/3.2/jakarta-persistence-spec-3.2.html | JPA lifecycle callback constraints |
| S13 | https://docs.spring.io/spring-boot/reference/using/build-systems.html | Client and Jackson starter dependency descriptors |
| S12 | https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-exchange.html | WebClient response body lifecycle |

Additional API checks:

https://docs.spring.io/spring-boot/api/java/org/springframework/boot/hibernate/autoconfigure/HibernateJpaAutoConfiguration.html

https://docs.spring.io/spring-boot/api/java/org/springframework/boot/jdbc/autoconfigure/DataSourceAutoConfiguration.html

https://docs.spring.io/spring-boot/api/java/org/springframework/boot/autoconfigure/condition/ConditionalOnSingleCandidate.html

The BOM is pinned to `4.1.1`; dynamic documentation links may show a newer release later. Re-run the build/tests before changing that baseline. Spring Boot 3.x, GraalVM native images and alternative transaction managers are not claimed as tested compatibility targets.
