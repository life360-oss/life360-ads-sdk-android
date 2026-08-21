# AppHarbr receives the protocol object by reference rather than looking it up by name, so names are
# only kept to keep stack traces from this bridge readable.
-keepnames class com.life360.ads.appharbr.**

# initAdQualityService() calls AppHarbr's Life360 adapter, which has no Maven coordinate and so cannot
# be a declared dependency: an app that does not ship AH-Life360-Adapter-*.aar would otherwise fail R8
# with a missing class instead of taking the guarded path at runtime.
-dontwarn com.appharbr.adapter.life360.**
