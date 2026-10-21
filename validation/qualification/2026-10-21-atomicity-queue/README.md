# Atomicity and queue qualification evidence

Tested source **`1a9187ce0886912c14a2f77dbcccd3287a37e4a8`**, tree **`0276124dd146d4fc9ae89dfe5ebddf7154840374`**. Full run `8a2204e2-1371-4cce-8b7a-7ee5c573ae14`, attempt `a47ddfb5-db5b-42f9-bb2e-7f2b86b79b2e`: **PASS**, Maven 0 / wrapper 0, 4,762.84 seconds. Production remains **NO-GO**. See [root QM](../../../QM.md).

- [Raw evidence ZIP](raw-evidence.zip): **2,823,792 bytes**, SHA-256 **`4805e6f2a1aa111cd280f29ccdcc06a106f1112d473b11ff08b470c8230343cd`**.
- [Artifact metadata](artifact.json): 706 files in the ZIP, 13 distinct run records; `evidence-index.json` inside the ZIP binds every file hash to its archived run/source. Default/full runs retain their own `target/qm/QM.md`, context, raw-manifest and reports. The explicitly selected diagnostic retains its JSON summary, context, archive manifest and raw output; it is full NOT_RUN.
- Includes 63 passing Surefire / 13 passing Oracle tests, PIT XML/HTML (133/135 killed), six Gatling report trees (367 HTTP requests / zero errors), native Oracle plans/counters/phases, transaction/runtime-classpath proof, configuration, resource and cleanup observations, and hashes of 11 library build outputs. No JARs are published here.
- Earlier failed runs on `dc5daf4`, `95f975f` and `1a1905c` remain FAILED; the separate selected diagnostic remains full NOT_RUN. Partial measurements are not promoted to a later run's results. One optional read-only SYS observer timeout is recorded separately from passing required suites.
- Generated credentials and sensitive system properties were checked in UTF-8/UTF-16 and nested CI report ZIPs before publication. Containers/volumes were removed and independently verified; public dependency/image caches remain.

The [4f44080 baseline archive](../2026-10-20/raw-evidence.zip) remains unchanged: 1,928,952 bytes, SHA-256 `ab7ff892a1d8ceafab878deb977d1eacb7becf53bdca5f3dab71ef06eebbf92c`. This new archive does not replace it. No merge, package publication or production release is implied.
