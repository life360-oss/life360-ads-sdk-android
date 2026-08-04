# Migrating from Prebid Mobile Android to Life360 Ads SDK Android

The Life360 Ads SDK is a fork of [Prebid Mobile Android](https://github.com/prebid/prebid-mobile-android). It keeps the Prebid Mobile public API surface intact and adds Nativo as a competing demand source. For the vast majority of integrations **migration is mechanical**: swap the dependency, relocate the package in your imports, and rebuild. Every Prebid Mobile type (`PrebidMobile`, `TargetingParams`, `BannerView`, `InterstitialAdUnit`, `GamBannerEventHandler`, …) keeps its original simple name and sub-package structure — only the top-level package prefix changes.

---

## 1. Swap the dependency

Group ID changes from `org.prebid` to `com.life360`, and the artifact IDs are renamed. The current release is **1.0.0**. Minimum SDK is **API 19**, unchanged from upstream.

| Prebid Mobile Android artifact                       | Life360 Ads SDK artifact                              |
| ---------------------------------------------------- | ----------------------------------------------------- |
| `org.prebid:prebid-mobile-sdk`                       | `com.life360:life360-ads-sdk`                         |
| `org.prebid:prebid-mobile-sdk-gam-event-handlers`    | `com.life360:life360-ads-gam-event-handlers`          |
| `org.prebid:prebid-mobile-sdk-admob-adapters`        | `com.life360:life360-ads-admob-adapters`              |
| `org.prebid:prebid-mobile-sdk-max-adapters`          | `com.life360:life360-ads-max-adapters`                |
| `org.prebid:prebid-mobile-sdk-open-measurement`      | `com.life360:life360-ads-open-measurement-sdk`        |

> The left column shows the standard upstream Prebid Mobile Android coordinates — match the rows against whatever you currently declare. `life360-ads-sdk` is the only required dependency; it pulls in the core (`com.life360:prebid-sdk-core`) and open-measurement artifacts transitively. Add the event-handler/adapter artifacts only if you use them. There is **no MoPub adapter** on Android (MoPub was removed upstream long ago).

### Groovy DSL (`build.gradle`)

```diff
 dependencies {
-    implementation 'org.prebid:prebid-mobile-sdk:x.x.x'
-    implementation 'org.prebid:prebid-mobile-sdk-gam-event-handlers:x.x.x'
-    implementation 'org.prebid:prebid-mobile-sdk-admob-adapters:x.x.x'
-    implementation 'org.prebid:prebid-mobile-sdk-max-adapters:x.x.x'
+    implementation 'com.life360:life360-ads-sdk:1.0.0'
+    implementation 'com.life360:life360-ads-gam-event-handlers:1.0.0'
+    implementation 'com.life360:life360-ads-admob-adapters:1.0.0'
+    implementation 'com.life360:life360-ads-max-adapters:1.0.0'
 }
```

### Kotlin DSL (`build.gradle.kts`)

```diff
 dependencies {
-    implementation("org.prebid:prebid-mobile-sdk:x.x.x")
-    implementation("org.prebid:prebid-mobile-sdk-gam-event-handlers:x.x.x")
+    implementation("com.life360:life360-ads-sdk:1.0.0")
+    implementation("com.life360:life360-ads-gam-event-handlers:1.0.0")
 }
```

### Repositories

Releases are published to Maven Central, so `mavenCentral()` is sufficient.
---

## 2. Relocate the package in your imports

A single find-and-replace across your Kotlin/Java sources covers almost the entire migration. The class names and sub-package structure are unchanged — only the `org.prebid.mobile` prefix becomes `com.life360.ads`:

```diff
-import org.prebid.mobile.PrebidMobile
+import com.life360.ads.PrebidMobile

-import org.prebid.mobile.TargetingParams
+import com.life360.ads.TargetingParams

-import org.prebid.mobile.api.rendering.BannerView
+import com.life360.ads.api.rendering.BannerView

-import org.prebid.mobile.eventhandlers.GamBannerEventHandler
+import com.life360.ads.eventhandlers.GamBannerEventHandler
```

Mechanically, replace the prefix everywhere:

```
org.prebid.mobile.   →   com.life360.ads.
```

> **Fully-qualified references & reflection:** the same prefix swap applies to any fully-qualified names in code, and to any `-keep` rules in your ProGuard/R8 configuration that reference `org.prebid.mobile.**` — update those to `com.life360.ads.**`.

---

## 3. Source-level API changes

The fork does **not rename or remove** any existing Prebid Mobile public type or signature — after the package relocation in section 2, your existing Prebid Mobile code compiles unchanged.

### New public API

There is a new flag to allow sharing a user's location with Nativo, alongside the existing Prebid geo-sharing control:

```kotlin
// Share location with Nativo:
PrebidMobile.setShareGeoLocationWithNativo(true)

// Continue to use this for sharing with Prebid Server & partners:
PrebidMobile.setShareGeoLocation(true)
```

For base Prebid Mobile API documentation, see the [Prebid docs](https://docs.prebid.org/prebid-mobile/pbm-api/android/code-integration-android.html).
