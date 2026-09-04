package com.life360.ads.om

/**
 * One Open Measurement verification resource, in the shape `VerificationScriptResource` needs.
 */
data class NativeOMResource(

    /** URL of the vendor's verification script. */
    val url: String,

    /** Vendor identifier the verification script is registered under. */
    val vendorKey: String,

    /** Opaque string handed back to the vendor's script when it runs. */
    val verificationParameters: String,
)
