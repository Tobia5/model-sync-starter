package io.modelsync.performance;

import static org.junit.jupiter.api.Assertions.*;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Full-only bytecode is compiled after the default architecture reactor module. */
class PerformanceArchitectureTest {
  @Test
  void compiledFullOnlyHarnessHasNoNestedTypesOrProductionLeaks() throws Exception {
    Path classes = Path.of("target/test-classes");
    assertTrue(Files.isDirectory(classes), "Full-only test output absent");
    var imported = new ClassFileImporter().importPath(classes);
    assertTrue(imported.size() >= 7, "Full-only code was not imported");
    for (var owner : imported) {
      assertEquals("io.modelsync.performance", owner.getPackageName(),
          "Unclassified full-only package: " + owner.getName());
      assertFalse(owner.getName().contains("$"), "Nested type: " + owner.getName());
      for (var dependency : owner.getDirectDependenciesFromSelf()) {
        String target = dependency.getTargetClass().getPackageName();
        assertFalse(target.startsWith("io.modelsync.jdbc") || target.startsWith("io.modelsync.jpa")
            || target.startsWith("io.modelsync.autoconfigure") || target.startsWith("io.modelsync.rest")
            || target.startsWith("io.modelsync.webclient"),
            "Full-only harness depends on production adapter: " + owner.getName() + " -> " + target);
      }
    }
  }
}
