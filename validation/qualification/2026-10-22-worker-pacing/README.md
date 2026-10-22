# Worker pacing and cursor qualification evidence

Tested source `fad6874c9fa0e0a16ed7d542cace11ea89a991e1`, tree `a530a260738510da84ed7f68fd26a8a75b803d67`; full run
`1afffb7d-52ca-4d4a-8857-23c3cefb8f4c`, wrapper attempt `d61f7d92-b0e8-4604-8823-a817a71f6ee7`.
Configured local full **PASS**, Maven/wrapper/supervisor/collector exits 0. Production **NO-GO**.

- [Root report](../../../QM.md) separates current results, historical baselines and remaining gates.
- [Raw evidence ZIP](raw-evidence.zip): 3,607,197 bytes / 758 files /
  13 separately classified components.
- SHA-256: `af3434238eafdd9f3064012a5d41bf7012c19499e02146fadff7072129afe7aa`.
- [Per-file index](artifact-index.json) and [artifact receipt](artifact.json).

Current full: 113 Surefire / 27 Oracle tests, zero failures/errors/skips; PIT160/162; six
Gatling simulations/367 HTTP requests/zero errors. Ten actual-worker phases passed.
The million-row fixture completed200 measured reconciliations,210 with warmup, not one million.

The ZIP contains run-scoped QM/JSON/XML/HTML/logs, all original cursor endpoints, workload/plan
diagnostics, supervisor/infrastructure/independent-cleanup observations, configuration provenance,
11 library JAR hashes (no binaries), current default/CI/diagnostic evidence and separate failed or
blocked attempts. Unattributed development logs do not qualify any source. CI tested merge33e114c
with the identical tree. No old report supplies current suite counts.

Hash/ZIP-CRC checks passed. The credential-shape/sensitive-XML scan examined756 inputs with zero
findings. This is a bounded publication scan, not a general security certification. The three
earlier baseline ZIP hashes were independently verified unchanged; they remain in their original
directories. Source for this qualification is the named code commit, not a later report commit.
