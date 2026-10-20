import com.sun.source.util.JavacTask;
import java.nio.file.*;
import java.util.*;
import javax.tools.*;

/**
 * Syntax-only parse of every Java source; intentionally no dependency linking or type-analysis
 * claim.
 */
public class SourceSyntaxCheck {
  public static void main(String[] args) throws Exception {
    Path root = Path.of(args[0]);
    List<Path> paths;
    try (var walk = Files.walk(root)) {
      paths =
          walk.filter(p -> p.toString().endsWith(".java") && !p.toString().contains("/target/"))
              .sorted()
              .toList();
    }
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    try (var files =
        compiler.getStandardFileManager(
            diagnostics, null, java.nio.charset.StandardCharsets.UTF_8)) {
      var sources = files.getJavaFileObjectsFromPaths(paths);
      JavacTask task =
          (JavacTask)
              compiler.getTask(
                  null,
                  files,
                  diagnostics,
                  List.of("--release", "21", "-proc:none"),
                  null,
                  sources);
      task.parse();
      for (var d : diagnostics.getDiagnostics())
        if (d.getKind() == Diagnostic.Kind.ERROR) System.err.println(d);
      long errors =
          diagnostics.getDiagnostics().stream()
              .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
              .count();
      System.out.println(
          "JAVA SYNTAX: "
              + paths.size()
              + " files parsed; "
              + errors
              + " syntax errors. No framework linking performed.");
      if (errors != 0) System.exit(1);
    }
  }
}
