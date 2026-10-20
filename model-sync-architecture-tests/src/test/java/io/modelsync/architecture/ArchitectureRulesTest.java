package io.modelsync.architecture;

import static org.junit.jupiter.api.Assertions.*;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Imports bytecode from every source-bearing reactor module, including test bytecode. */
class ArchitectureRulesTest {
  private static final String CORE = "io.modelsync.core.";
  // These public boundary values remain on the existing classpath; each is explicitly
  // reviewed as a contract. A new generic core type must be assigned a layer first.
  private static final Set<String> CORE_CONTRACTS = Set.of(
      "BoundaryModelDescriptor", "BoundaryPage", "DatasetComparison", "DatasetManifest",
      "DatasetPort", "DatasetReceipt", "DatasetReceiptStage", "DatasetSlice", "FetchResult",
      "FetchStatus", "IdentityResolution", "IdentityResolutionStatus", "LeaseLost",
      "LocalModelView", "LocalRole", "Permanent", "Priority", "QuotaAdmission",
      "RateLimited", "RateLimitedAfter", "ReadRequest", "RefreshMode", "RetryLater",
      "SyncJobResult", "SyncJobStopReason", "SyncOptions", "SyncRequested", "VisitPolicy",
      "VisitResult", "WeightedPriorities", "WorkLease");
  private static final Set<String> PRODUCTION_PACKAGES = Set.of(
      "io.modelsync.autoconfigure", "io.modelsync.jdbc", "io.modelsync.jpa",
      "io.modelsync.reactor", "io.modelsync.rest", "io.modelsync.spring",
      "io.modelsync.webclient");
  private static final Set<String> FIELD_INJECTION = Set.of(
      "org.springframework.beans.factory.annotation.Autowired",
      "jakarta.inject.Inject", "jakarta.annotation.Resource");

  @Test
  void compiledReactorObeysLayerAndTypeRules() throws IOException {
    Path root = Path.of("..").toAbsolutePath().normalize();
    String pom = Files.readString(root.resolve("pom.xml"));
    Matcher declared = Pattern.compile("<modules>(.*?)</modules>", Pattern.DOTALL).matcher(pom);
    assertTrue(declared.find(), "Default reactor module list absent");
    Matcher names = Pattern.compile("<module>([^<]+)</module>").matcher(declared.group(1));
    List<Path> compiled = new ArrayList<>();
    List<String> missing = new ArrayList<>();
    int sourceModules = 0;
    while (names.find()) {
      String module = names.group(1);
      if (module.equals("model-sync-architecture-tests")) continue; // negative fixtures live here
      Path dir = root.resolve(module);
      assertTrue(Files.isRegularFile(dir.resolve("pom.xml")), "Missing declared module " + module);
      for (String lane : List.of("main", "test")) {
        Path source = dir.resolve("src/" + lane + "/java");
        if (!Files.isDirectory(source)) continue;
        try (var files = Files.walk(source)) {
          if (files.noneMatch(p -> p.toString().endsWith(".java"))) continue;
        }
        if (lane.equals("main")) sourceModules++;
        Path classes = dir.resolve("target/" + (lane.equals("main") ? "classes" : "test-classes"));
        if (!Files.isDirectory(classes)) { missing.add(module + ":" + lane + " compiled output absent"); continue; }
        try (var files = Files.walk(classes)) {
          List<Path> bytecode = files.filter(p -> p.toString().endsWith(".class")).toList();
          if (bytecode.isEmpty()) missing.add(module + ":" + lane + " contains zero classes");
          for (Path p : bytecode)
            if (p.getFileName().toString().contains("$"))
              missing.add("Hand-written nested/local/anonymous type: " + root.relativize(p));
        }
        if (lane.equals("main")) compiled.add(classes);
      }
    }
    assertTrue(sourceModules >= 8, "Expected production modules absent: " + sourceModules);
    assertTrue(missing.isEmpty(), String.join("\n", missing));
    JavaClasses classes = new ClassFileImporter().importPaths(compiled);
    assertTrue(classes.size() > 50, "ArchUnit imported too few project classes: " + classes.size());
    for (String layer : List.of("domain", "port", "application"))
      assertTrue(classes.stream().anyMatch(c -> c.getPackageName().startsWith(CORE + layer)),
          "Absent expected layer: " + layer);
    List<String> errors = inspect(classes);
    assertTrue(errors.isEmpty(), String.join("\n", errors));
  }

  @Test
  void negativeFixturesProveCriticalDependencyRules() {
    assertViolation("Domain depends outward", io.modelsync.core.domain.DomainLeak.class,
        io.modelsync.jdbc.FakeAdapter.class);
    assertViolation("Port depends outward", io.modelsync.core.port.PortLeak.class,
        io.modelsync.jdbc.FakeAdapter.class);
    assertViolation("Application depends on adapter", io.modelsync.core.application.ApplicationLeak.class,
        io.modelsync.jdbc.FakeAdapter.class);
    assertViolation("Adapter depends on adapter", io.modelsync.webclient.AdapterLeak.class,
        io.modelsync.rest.FakeRest.class);
    assertViolation("Field injection", io.modelsync.core.application.FieldInjectionLeak.class);
    assertViolation("Core contract depends on framework/persistence",
        io.modelsync.core.contractleak.EntityLeak.class);
    assertViolation("Package cycle", io.modelsync.core.cyclea.CycleA.class,
        io.modelsync.core.cycleb.CycleB.class);
    assertViolation("Unclassified production package", io.modelsync.core.unclassified.UnknownLayer.class);
  }

  private static void assertViolation(String expected, Class<?>... fixtures) {
    String messages = String.join("\n", inspect(new ClassFileImporter().importClasses(fixtures)));
    assertTrue(messages.contains(expected), "Missing negative detection for " + expected + ": " + messages);
  }

  static List<String> inspect(JavaClasses classes) {
    List<String> errors = new ArrayList<>();
    Map<String, Set<String>> packages = new HashMap<>();
    for (JavaClass owner : classes) {
      String from = owner.getPackageName();
      if (!owner.getName().startsWith("io.modelsync.")) continue;
      if (!classified(owner)) errors.add("Unclassified production package: " + owner.getName());
      if (owner.getName().contains("$")) errors.add("Nested type: " + owner.getName());
      for (JavaField field : owner.getFields())
        for (JavaAnnotation<JavaField> annotation : field.getAnnotations())
          if (FIELD_INJECTION.contains(annotation.getRawType().getName())
              && !from.equals("io.modelsync.sample"))
            errors.add("Field injection: " + owner.getName() + "." + field.getName());
      for (Dependency dependency : owner.getDirectDependenciesFromSelf()) {
        String target = dependency.getTargetClass().getPackageName();
        if (target.equals(from)) continue;
        if (target.startsWith("io.modelsync."))
          packages.computeIfAbsent(from, k -> new HashSet<>()).add(target);
        if (from.startsWith(CORE + "domain") && !target.startsWith("java.")
            && !target.startsWith(CORE + "domain"))
          errors.add("Domain depends outward: " + owner.getName() + " -> " + target);
        if (from.startsWith(CORE + "port") &&
            (target.startsWith(CORE + "application") || isAdapter(target)))
          errors.add("Port depends outward: " + owner.getName() + " -> " + target);
        if (from.equals("io.modelsync.core") &&
            (target.startsWith(CORE + "application") || target.startsWith(CORE + "port")
                || isAdapter(target) || target.startsWith("io.modelsync.autoconfigure")))
          errors.add("Core contract depends outward: " + owner.getName() + " -> " + target);
        if (from.equals("io.modelsync.core.http") &&
            (target.startsWith(CORE + "application") || target.startsWith(CORE + "port")
                || isAdapter(target)))
          errors.add("HTTP utility depends outward: " + owner.getName() + " -> " + target);
        if (from.startsWith(CORE + "application") && isAdapter(target))
          errors.add("Application depends on adapter: " + owner.getName() + " -> " + target);
        if (isAdapter(from) && isAdapter(target) && !adapter(from).equals(adapter(target)))
          errors.add("Adapter depends on adapter: " + owner.getName() + " -> " + target);
        if (from.startsWith(CORE) && (target.startsWith("jakarta.persistence")
            || target.startsWith("org.hibernate") || target.startsWith("org.springframework")
            || target.startsWith("reactor.") || target.startsWith("java.sql")))
          errors.add("Core contract depends on framework/persistence: " + owner.getName() + " -> " + target);
        if (!from.equals("io.modelsync.testkit") && !from.equals("io.modelsync.sample")
            && target.startsWith("io.modelsync.testkit"))
          errors.add("Production depends on testkit: " + owner.getName() + " -> " + target);
      }
    }
    for (String p : packages.keySet())
      if (cycle(p, p, packages, new HashSet<>())) errors.add("Package cycle includes " + p);
    return errors;
  }

  private static boolean cycle(String origin, String current, Map<String, Set<String>> edges, Set<String> seen) {
    if (!seen.add(current)) return false;
    for (String next : edges.getOrDefault(current, Set.of()))
      if (next.equals(origin) || cycle(origin, next, edges, seen)) return true;
    return false;
  }

  private static boolean isAdapter(String name) {
    return name.startsWith("io.modelsync.") && Set.of("jdbc", "jpa", "rest", "webclient", "reactor", "spring")
        .contains(adapter(name));
  }

  private static boolean classified(JavaClass owner) {
    String name = owner.getPackageName();
    if (name.equals("io.modelsync.core")) return CORE_CONTRACTS.contains(owner.getSimpleName());
    if (name.equals("io.modelsync.core.application") || name.equals("io.modelsync.core.domain")
        || name.equals("io.modelsync.core.port") || name.equals("io.modelsync.core.http")) return true;
    return PRODUCTION_PACKAGES.contains(name) || name.equals("io.modelsync.testkit")
        || name.equals("io.modelsync.sample");
  }

  private static String adapter(String name) {
    String[] pieces = name.split("\\.");
    return pieces.length > 2 ? pieces[2] : "";
  }
}
