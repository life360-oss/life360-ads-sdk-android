package com.life360.ads.state

/**
 * Lifecycle of one `BannerView` load cycle.
 *
 * One state variable rather than a flag per leg, so admission is decided in a single place and every
 * combination is enumerable and testable.
 *
 * The in-flight legs (Nativo, Prebid Server, ad server) are not distinguished: nothing needs to tell them
 * apart, and [canStartLoad] is the only property `BannerView` enforces.
 */
enum class BannerLoadState {

    /** No cycle has started, or the previous one was reset for a refresh. */
    IDLE,

    /** A bid request is outstanding on some leg. */
    LOADING,

    /** A creative has been attached, or is on screen. */
    SHOWING,

    /** The cycle ended without an ad. */
    FAILED,

    /** `destroy()` was called. Terminal — no further transition is permitted. */
    DESTROYED;

    /**
     * Whether a new load cycle may begin.
     *
     * A function rather than a `val` so it reads the same from `BannerView`, which is Java.
     */
    fun canStartLoad(): Boolean = this == IDLE || this == SHOWING || this == FAILED
}
