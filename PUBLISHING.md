# Publishing the Life360 Ads SDK to Maven Central

All artifacts publish under the **`com.life360`** namespace to the
[Central Publisher Portal](https://central.sonatype.com/). Publishing is driven by
`scripts/Maven/deployPrebidMobile-life.sh`, which builds via `scripts/buildPrebidMobile.sh`,
signs every artifact with GPG, uploads to the Central staging API, and (for releases)
promotes the deployment to `VALIDATED` for manual publish.

## Published artifacts

| Gradle module            | Maven coordinate                                        | Packaging |
|--------------------------|---------------------------------------------------------|-----------|
| PrebidMobile             | `com.life360:life360-ads-sdk`                           | jar (umbrella) |
| PrebidMobile-core        | `com.life360:prebid-sdk-core`                           | aar |
| PrebidMobile-gamEventHandlers | `com.life360:life360-ads-gam-event-handlers`       | jar |
| PrebidMobile-admobAdapters    | `com.life360:life360-ads-admob-adapters`           | jar |
| PrebidMobile-maxAdapters      | `com.life360:life360-ads-max-adapters`             | jar |
| omsdk-android (vendored) | `com.life360:life360-ads-open-measurement-sdk`          | jar |

## One-time environment setup

- [ ] **Install Maven** — `brew install maven` (the deploy uses `mvn gpg:sign-and-deploy-file`).
- [ ] **Java 17+** available
- [ ] **GPG key present** — `gpg --list-secret-keys --keyid-format=long`.
- [ ] **Create `~/.m2/settings.xml`** with a `<server id="central">` holding a Central Portal
      **user token** (Account → Generate User Token — *not* your login password):

      ```xml
      <settings>
        <servers>
          <server>
            <id>central</id>
            <username>CENTRAL_TOKEN_USERNAME</username>
            <password>CENTRAL_TOKEN_PASSWORD</password>
          </server>
        </servers>
      </settings>
      ```
      The `id` must be `central` (matches `REPO_ID` in the deploy scripts).
- [ ] **Confirm namespace ownership** — the `com.life360` namespace is verified for your
      Central Portal account.

## Per-release checklist

- [ ] Bump `prebidSdkVersionName` in `build.gradle` (and `omSdkVersion` if the vendored OM SDK changed).
- [ ] Commit the version bump and the current uncommitted fixes.
- [ ] Export the GPG key id: `export GPG_KEYNAME=<your-key-id>`.
- [ ] From `scripts/Maven/`, run the deploy (you'll be prompted for the GPG passphrase):

      ```bash
      cd scripts/Maven
      # dry run to the snapshots repo first (recommended):
      ./deployPrebidMobile-life.sh --version 1.1.0-SNAPSHOT
      # real release:
      ./deployPrebidMobile-life.sh --version 1.1.0
      ```
- [ ] For a release, the script uploads and marks the deployment `VALIDATED`, then prints the
      portal URL. Open <https://central.sonatype.com/publishing/deployments> and click
      **Publish** (or Drop) to finish. Snapshots are consumable immediately, no manual step.
- [ ] Verify the artifacts resolve (e.g. add `com.life360:life360-ads-sdk:<version>` in a test project).
- [ ] Tag the release in git.

## What each script does

- `scripts/buildPrebidMobile.sh` — assembles each module, runs JarJar to relocate
  `org.prebid.mobile.**` → `com.life360.ads.**`, and emits AAR / JAR / sources / javadoc /
  POM into `generated/` under the `Life360AdsSDK-*` naming.
- `scripts/Maven/deployPrebidMobile-life.sh` — **canonical full release**: builds, then signs +
  deploys all five modules **and** the OM SDK to Central, then finalizes.
- `scripts/Maven/deployPrebidMobile.sh` — deploys only the five SDK modules (no OM SDK).
- `scripts/Maven/deployOpenMeasurement.sh` — deploys only the vendored OM SDK. Use when
  `omSdkVersion` changes independently of an SDK release.

## Notes / not blocking a publish

- **Central metadata requirements are met**: every POM has name, description, url, license,
  developer, and scm pointing at `github.com/life360-oss/life360-ads-sdk-android`, and all
  artifacts ship signed with sources + javadoc jars.
- The vendored OM SDK deploys with an empty `stub.jar` for its sources/javadoc (standard for a
  vendored binary). Central accepts this.
- `.github/workflows/upload-docs.yml` builds `:PrebidMobile:combinedJavadoc`, whose title HTML
  still contains Prebid marketing copy — cosmetic, doc-site only, not part of artifact publishing.
- `.github/workflows/issue_prioritization.yml` sets `ORGANIZATION: prebid` — issue-triage
  automation inherited from the fork; unrelated to publishing (review separately if desired).
