# Life360 Ads SDK (Android)
The Life360 Ads SDK is a mobile ads framework used to inject demand from Life360, Prebid and GAM. It can be used as a stand-alone integration, or it can be paired with Prebid Server and/or GAM, as needed.

## Features
**Life360 Ad Request Pipeline**
An additional bid request is sent to Life360 ad server as a demand source competing alongside Prebid Server. The SDK compares all bids and sends the winning bid to GAM.

**Owned & Operated Ads**
Direct ad campaigns are supported via an isOwnedOperated flag. When set, the ad bypasses the auction and is rendered immediately without going through Prebid Server or GAM.

**Life360 Ad Types**
Rendering support for all unique Life360 ad formats.

**Full-width Ad Rendering**
The rendering plugin handles dynamic expansion of ad creatives to full width/height using constraint-based layout, ensuring correct display across varying screen sizes.

**Geo/Location Data with Life360**
When a developer sets shareGeoLocationWithLife360 to true and the user grants location permission, the SDK conditionally appends ORTB geo parameters to the Life360 bid request.

**GAM Click Attribution for 3rd party ads (Life360 & Prebid)**
When using GAM as the ad server, clicks within a Life360 or Prebid ad are tracked back into the GAM platform, ensuring accurate click attribution and reporting.

## Improvements & Bug Fixes
Relative to upstream Prebid Mobile, this SDK includes the following fixes and improvements:

* **Viewability Tracking** — Scroll-based viewability tracking replacing the poll-based approach, for more accurate measurement
* **MRAID Expand** — Improved MRAID expand support with better animations and no glitching
* **iframe Handling** — Within expanded ad content, fix to allow iframes to load
* **Ad Refresh Handling** — Fix for ad refresh lifecycle management
* **bURL Tracker** — Fix for auction macro replacement in billing URL tracking
* **Rendering** — Background color to match system dark/light default
* **GAM Event Handlers** — Click and impression callbacks for GAM-rendered ads

## Ad Request Flow
The SDK orchestrates the following 9-step flow for each ad request:

1. SDK sends a bid request to the Life360 ad server
2. SDK checks for an Owned & Operated signal — if present, skip to step 9
3. SDK sends a bid request to Prebid Server
4. Prebid Server runs the header bidding auction across configured demand partners
5. SDK compares all bids (Life360 + Prebid) and selects the highest price, setting targeting keywords accordingly
6. GMA SDK sends a request to GAM for final decisioning
7. GMA SDK renders the winning bid (if GAM's own ad wins, the flow ends here)
8. If a Prebid or Life360 bid wins, GAM serves a passback creative signaling the SDK to take over rendering
9. The SDK rendering module renders the winning bid

## Initialization

With a Prebid Server:

```kotlin
PrebidMobile.initializeSdk(context, "https://your-prebid-server/openrtb2/auction") { status -> }
```

Without one — Nativo demand plus your own ad server only.

```kotlin
Life360Ads.initializeWithoutPrebid(context) { status -> }
```

## Repackaging

All public classes in this SDK are published under the `com.life360.ads` namespace rather than the upstream `org.prebid.mobile` namespace. The repackaging is done at build time using [JarJar](https://github.com/eed3si9n/jarjar-maven-plugin) — source files stay on `org.prebid.mobile` so the branch remains easy to merge with upstream Prebid releases.

The rule is defined in [`PrebidMobile/jarjar-rules.txt`](PrebidMobile/jarjar-rules.txt):


## Build from Source

To produce the repackaged `com.life360.ads` artifacts for all modules, run:

```
scripts/buildPrebidMobile.sh
```

Output JARs and AARs are written to `generated/`. To skip JAR extraction and only produce the repackaged AARs, pass `-nojar`:

```
scripts/buildPrebidMobile.sh -nojar
```


## Testing

Run unit tests and integration tests with:

```
scripts/testPrebidMobile.sh
```

## FAQ

**Does the  Life360 Ads SDK use OMID / OMSDK?**

Yes, but the SDK is not currently IAB certified. Without certification, the demand-side benefits of OMID measurement are not fully realized unless the publisher obtains their own certification.

**Does the  Life360 Ads SDK support multi-format bidding?**

Not currently. The SDK uses Prebid Rendering (rather than Bidding-only with GAM rendering), which does not support multi-format bidding at this time. This is an area of future exploration.
