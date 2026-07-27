package com.life360.ads.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import android.widget.ImageView
import java.util.concurrent.atomic.AtomicLong

object NativoUtils {
    fun debounceAction(intervalMs: Long, action: () -> Unit): () -> Unit {
        val lastCall = AtomicLong(0L)
        return {
            val now = SystemClock.elapsedRealtime()
            val previous = lastCall.get()
            if (now - previous >= intervalMs) {
                lastCall.set(now)
                action()
            }
        }
    }

    /**
     * Runs [action] once [view] has a valid, up-to-date layout. This is necessary because
     * neither attachment nor `View.post` guarantees a completed layout pass — attach happens
     * synchronously in `addView`, and a plain post can execute before the pending layout frame,
     * so sizes read there can be zero or stale. Equivalent to core-ktx `doOnLayout`, which
     * this module doesn't depend on.
     */
    @JvmStatic
    fun runOnLaidOut(view: View, action: (View) -> Unit) {
        if (view.isLaidOut && !view.isLayoutRequested) {
            action(view)
        } else {
            view.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                override fun onLayoutChange(
                    v: View,
                    left: Int, top: Int, right: Int, bottom: Int,
                    oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
                ) {
                    v.removeOnLayoutChangeListener(this)
                    v.post { action(v) }
                }
            })
        }
    }

    /**
     * Captures a rasterized snapshot of the provided View and returns it as an ImageView.
     *
     * @param view The View to capture
     * @return ImageView containing the rasterized snapshot of the view
     */
    @JvmStatic
    fun captureViewSnapshot(view: View): ImageView {
        // Create a bitmap with the same dimensions as the view
        val bitmap = Bitmap.createBitmap(
            view.width,
            view.height,
            Bitmap.Config.ARGB_8888
        )
        
        // Create a canvas to draw the view onto the bitmap
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        
        // Create an ImageView and set the captured bitmap
        val imageView = ImageView(view.context)
        imageView.setImageBitmap(bitmap)
        
        return imageView
    }
}
