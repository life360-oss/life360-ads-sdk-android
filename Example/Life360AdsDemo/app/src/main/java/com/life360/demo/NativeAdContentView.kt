package com.life360.demo

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import org.prebid.mobile.PrebidNativeAd

private const val PLACEHOLDER_FILL = "#EEEEEE"
private const val SECONDARY_TEXT = "#666666"
private const val CTA_TINT = "#3478F6"


class NativeAdContentView(context: Context) : LinearLayout(context) {

    private val iconImage = ImageView(context)
    private val sponsoredLabel = TextView(context)
    private val titleLabel = TextView(context)
    private val mainImage = ImageView(context)
    private val bodyLabel = TextView(context)
    private val ctaLabel = TextView(context)

    /** Views the SDK attaches its click handler to. Reported separately from the card as a whole,
     * since registering every view would make the feed impossible to scroll past without clicking. */
    val clickableViews: List<View> get() = listOf(mainImage, titleLabel, ctaLabel)

    init {
        orientation = VERTICAL
        setUpSubviews()
    }

    /** Populates the layout from the bid's native assets. Every getter on [PrebidNativeAd] returns
     * `""` rather than null for an asset the bid didn't include, so blank — not null — is the check. */
    fun bind(ad: PrebidNativeAd) {
        titleLabel.text = ad.title
        bodyLabel.text = ad.description
        sponsoredLabel.text = ad.sponsoredBy.takeIf { it.isNotEmpty() }?.let { "Sponsored · $it" } ?: "Sponsored"
        ctaLabel.text = ad.callToAction.takeIf { it.isNotEmpty() } ?: "Learn more"

        iconImage.visibility = if (ad.iconUrl.isEmpty()) View.GONE else View.VISIBLE
        if (ad.iconUrl.isNotEmpty()) Glide.with(context).load(ad.iconUrl).into(iconImage)

        mainImage.visibility = if (ad.imageUrl.isEmpty()) View.GONE else View.VISIBLE
        if (ad.imageUrl.isNotEmpty()) Glide.with(context).load(ad.imageUrl).into(mainImage)
    }

    private fun setUpSubviews() {
        val spacing = dp(8)

        iconImage.scaleType = ImageView.ScaleType.CENTER_CROP
        iconImage.setBackgroundColor(Color.parseColor(PLACEHOLDER_FILL))

        sponsoredLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        sponsoredLabel.setTextColor(Color.parseColor(SECONDARY_TEXT))

        titleLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        titleLabel.setTypeface(titleLabel.typeface, Typeface.BOLD)
        titleLabel.maxLines = 2

        mainImage.scaleType = ImageView.ScaleType.CENTER_CROP
        mainImage.setBackgroundColor(Color.parseColor(PLACEHOLDER_FILL))

        bodyLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        bodyLabel.setTextColor(Color.parseColor(SECONDARY_TEXT))
        bodyLabel.maxLines = 3

        ctaLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        ctaLabel.setTypeface(ctaLabel.typeface, Typeface.BOLD)
        ctaLabel.setTextColor(Color.parseColor(CTA_TINT))

        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(iconImage, LayoutParams(dp(32), dp(32)).apply { marginEnd = spacing })
            addView(sponsoredLabel, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }

        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(titleLabel, topMarginParams(spacing))
        addView(mainImage, LayoutParams(LayoutParams.MATCH_PARENT, dp(180)).apply { topMargin = spacing })
        addView(bodyLabel, topMarginParams(spacing))
        addView(ctaLabel, topMarginParams(spacing))
    }

    private fun topMarginParams(margin: Int) =
        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = margin }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
}
