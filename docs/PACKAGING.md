# Building and distributing the starter

This repository supplies **CI plus manual development SNAPSHOT publishing**, not an approved stable release. Default CI has passed; remote publication/authentication remains NOT_RUN. Local39-file packaging and independent MVC/WebFlux consumption passed at source `551427b`; see [source-bound evidence](../validation/qualification/2026-10-26-independent-consumers/README.md) and the [current acceptance matrix](quality/ACCEPTANCE-MATRIX.md).

## 1. Three different artifacts

| Output | Purpose | Maven dependency resolution? |
|---|---|---|
| Source ZIP / patch | Review or transfer repository changes | No |
| GitHub Actions build artifact | Download generated JARs and test logs from one run | Not a Maven registry |
| Maven package registry | Distribute POMs, main/source/Javadoc JARs and metadata | Yes |

GitHub Actions is the build runner; GitHub Packages, Maven Central or an internal Nexus/Artifactory is the registry. GitHub is not mandatory: any trusted CI can run Maven. For this repository, Actions + GitHub Packages is a straightforward first distribution route. For broad public adoption, Maven Central generally removes GitHub-specific consumer authentication, but requires verified namespace/publishing setup.

## 2. Supplied profiles

`library-artifacts` attaches source and Javadoc JARs to modules with sources. It leaves Javadoc errors fatal. `github-packages` supplies a SNAPSHOT-only destination and pins Maven Deploy Plugin3.1.4 with deployAtEnd. The sample stays in builds/tests but has `maven.deploy.skip=true`.

```bash
# Compilation, unit/framework tests and source/Javadoc generation; no publication.
mvn -B -ntp -Plibrary-artifacts clean verify
python3 scripts/check-test-reports.py

# Local consumption only.
mvn -B -ntp -Plibrary-artifacts install
```

Deploy the parent POM and **all referenced library modules**, including runtime/adapter/autoconfigure/starter modules. Testkit, architecture/performance modules and the sample are test/demo artifacts and must not be published as library dependencies. Only publishing `model-sync-spring-boot-starter-oracle-jpa.jar` would leave its transitive dependencies unresolved.

`deployAtEnd` delays uploads until the reactor reaches deployment, but it does not provide transactional multi-package publication or rollback after a partial network failure.

## 3. Read-only verification workflow

`.github/workflows/model-sync-verify.yml` runs on PRs, main pushes and manual dispatch. It uses pinned official actions, a bounded run timeout, no persisted checkout credential and read-only repository permissions. It runs core/trigger contracts, architecture/docs checks, Python guard tests, Java syntax and a full Maven library-artifact build. The report gate requires executed, unskipped unit suites; report and JAR artifacts are retained for inspection.

This default lane deliberately does not claim Oracle integration coverage. The `full` profile and a dedicated schema are separate. See R02/R03 in TODO.

## 4. Manually approved development publication

1. Review/merge the change and obtain green CI.
2. Configure the GitHub Environment `model-sync-snapshots`: required reviewers and main-only deployment restrictions. Naming an environment in YAML does not configure its protection.
3. Run `Publish Model Sync development snapshot` from main and enter `PUBLISH_SNAPSHOT`.
4. The workflow checks the repository/ref/event/version/destination, validates all modules and keeps credentials absent from the initial verification process environment.
5. It publishes using the repository's short-lived `GITHUB_TOKEN` with `packages:write`, only during the explicit deploy step. Maven tests remain enabled on deploy. No long-lived publishing token is embedded in source.
6. Inspect uploaded modules and build a fresh external consumer; registry-backed consumption is still open; the separate local staged-artifact consumer proof does not exercise registry credentials or publication.

This is **not** automatic publication on every commit and **not** a stable-release/tag workflow. Main can advance after a manual run starts; the workflow publishes the SHA recorded for that reviewed run. Approval should refer to that SHA. Do not approve an unreviewed or superseded commit.

A human with broader repository privileges could edit these guardrails; they are workflow controls, not a substitute for branch/environment protection.

## 5. Consuming GitHub Maven packages

GitHub's Maven registry requires authentication for public packages as well. For a workstation, use a properly scoped token (read:packages as applicable) through local Maven settings/environment, never commit it. Consumer GitHub workflows may use their own `GITHUB_TOKEN` only when the registry/package access permits it.

Consuming POM:

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/tobia5/model-sync-starter</url>
    <snapshots><enabled>true</enabled></snapshots>
  </repository>
</repositories>
<dependencies>
  <dependency>
    <groupId>io.modelsync</groupId>
    <artifactId>model-sync-spring-boot-starter-oracle-jpa</artifactId>
    <version>0.2.0-SNAPSHOT</version>
  </dependency>
</dependencies>
```

Local `~/.m2/settings.xml` fragment (merge with existing settings):

```xml
<servers>
  <server>
    <id>github</id>
    <username>${env.GITHUB_USER}</username>
    <password>${env.GITHUB_PACKAGES_TOKEN}</password>
  </server>
</servers>
```

This fragment assumes the environment variables have been provisioned locally. It is not a request to paste a token into chat. Do not print effective settings with credentials in CI logs. Use lower-case `tobia5` in the registry URL.

Consumers must also configure the supported Boot platform and their own schema/application bindings. The library is not an executable server, and publishing it does not provision an Oracle database or IBM MDM account.

## 6. Stable/public release prerequisites

Before introducing an immutable version such as 0.1.0: close applicable release gates, test external consumers from an empty local repository, establish a protected tag/version match, verify all modules and source/Javadoc attachments, document compatibility, produce an SBOM/security review and retain release provenance. A release BOM may help align all Model Sync module versions without forcing a different Spring Boot platform into consumers.

The existing `io.modelsync` coordinates are not evidence that the maintainer controls the `modelsync.io` namespace. Verify or choose an owned namespace before Maven Central registration. Keep signing credentials and Central tokens in protected environment secrets. Do not copy legacy OSSRH publishing examples without checking current Central Portal guidance.

## Sources checked 2026-10-20

- [GitHub: Maven registry and authentication](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry)
- [GitHub: publishing Java packages](https://docs.github.com/actions/guides/publishing-java-packages-with-maven)
- [Maven Deploy Plugin](https://maven.apache.org/plugins/maven-deploy-plugin/deploy-mojo.html)
- [Maven Source Plugin lifecycle attachment](https://maven.apache.org/plugins/maven-source-plugin/usage.html)
- [Maven Javadoc Plugin](https://maven.apache.org/plugins/maven-javadoc-plugin/)
- [Maven Central namespace ownership](https://central.sonatype.org/register/namespace/)
