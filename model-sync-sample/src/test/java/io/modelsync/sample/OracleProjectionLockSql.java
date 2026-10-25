package io.modelsync.sample;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Narrow lexer/matcher for one simple projection SELECT and its terminal Oracle lock clause. */
final class OracleProjectionLockSql {
  private OracleProjectionLockSql() {}

  static boolean isNowaitSelect(String sql, String table) {
    List<String> t = tokens(sql);
    if (t.isEmpty() || !t.getFirst().equals("SELECT")) return false;
    if (t.getLast().equals(";")) t.removeLast();
    if (t.contains(";") || java.util.Collections.frequency(t, "SELECT") != 1) return false;
    int from = t.indexOf("FROM"), lock = t.indexOf("FOR");
    if (from < 0 || from + 1 >= t.size() || lock < from || lock + 2 >= t.size()) return false;
    int at = from + 1;
    if (at + 2 < t.size() && t.get(at + 1).equals(".")) at += 2;
    if (!identifier(t.get(at)).equals(table.toUpperCase(Locale.ROOT))) return false;
    if (!t.get(lock + 1).equals("UPDATE") || !t.getLast().equals("NOWAIT")) return false;
    at = lock + 2;
    if (at == t.size() - 1) return true;
    if (!t.get(at++).equals("OF")) return false;
    while (at < t.size() - 1) {
      if (!isIdentifier(t.get(at++))) return false;
      if (at < t.size() - 1 && t.get(at).equals(".")) {
        if (++at >= t.size() - 1 || !isIdentifier(t.get(at++))) return false;
      }
      if (at == t.size() - 1) return true;
      if (!t.get(at++).equals(",")) return false;
    }
    return false;
  }

  private static boolean isIdentifier(String s) {
    return s.startsWith("Q:") || s.matches("[A-Z_][A-Z0-9_$#]*")
        && !List.of("NOWAIT", "WAIT", "SKIP", "LOCKED", "FOR", "UPDATE", "OF").contains(s);
  }
  private static String identifier(String s) { return s.startsWith("Q:") ? s.substring(2) : s; }

  private static List<String> tokens(String sql) {
    var out = new ArrayList<String>();
    if (sql == null || sql.length() > 8192) return out;
    for (int i = 0; i < sql.length();) {
      char c = sql.charAt(i);
      if (Character.isWhitespace(c)) { i++; continue; }
      if (sql.startsWith("--", i)) {
        int end = sql.indexOf('\n', i + 2); i = end < 0 ? sql.length() : end + 1; continue;
      }
      if (sql.startsWith("/*", i)) {
        int end = sql.indexOf("*/", i + 2); if (end < 0) return new ArrayList<>();
        i = end + 2; continue;
      }
      int q = (c == 'q' || c == 'Q') ? i : (c == 'n' || c == 'N')
          && i + 1 < sql.length() && (sql.charAt(i + 1) == 'q' || sql.charAt(i + 1) == 'Q') ? i + 1 : -1;
      if (q >= 0 && q + 2 < sql.length() && sql.charAt(q + 1) == '\'') {
        char open = sql.charAt(q + 2);
        char close = switch (open) { case '[' -> ']'; case '(' -> ')'; case '{' -> '}'; case '<' -> '>'; default -> open; };
        int end = sql.indexOf("" + close + '\'', q + 3);
        if (end < 0) return new ArrayList<>(); i = end + 2; out.add("<LITERAL>"); continue;
      }
      if (c == '\'' || c == '"') {
        char quote = c; var value = new StringBuilder(); boolean ended = false; i++;
        while (i < sql.length()) {
          c = sql.charAt(i++);
          if (c == quote) {
            if (i < sql.length() && sql.charAt(i) == quote) { value.append(quote); i++; }
            else { ended = true; break; }
          } else value.append(c);
        }
        if (!ended) return new ArrayList<>();
        out.add(quote == '"' ? "Q:" + value : "<LITERAL>"); continue;
      }
      if (Character.isLetter(c) || c == '_') {
        int start = i++;
        while (i < sql.length() && (Character.isLetterOrDigit(sql.charAt(i)) || "_$#".indexOf(sql.charAt(i)) >= 0)) i++;
        out.add(sql.substring(start, i).toUpperCase(Locale.ROOT));
      } else { out.add(String.valueOf(c)); i++; }
    }
    return out;
  }
}
