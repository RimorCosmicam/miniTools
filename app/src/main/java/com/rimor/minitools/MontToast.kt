package com.rimor.minitools

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/**
 * A word, on the panel the gesture happened on.
 *
 * Android's own Toast is no use here: a service in the background cannot reliably raise one on
 * modern Android, and even when it can it lands on whichever display the framework fancies. Since
 * miniTools already owns overlay windows on the cover panel, it draws its own — which also means
 * the message is a Mont surface rather than a rounded grey capsule from another language.
 */
object MontToast {
    private val handler = Handler(Looper.getMainLooper())
    private var showing: View? = null
    private val hide = Runnable { dismiss() }

    fun show(service: Context, display: Display, text: String) {
        dismiss()
        runCatching {
            val context = service.createDisplayContext(display)
                .createWindowContext(display, TYPE, null)
            val label = TextView(context).apply {
                setText(text.uppercase())
                setTextColor(0xEBFFFFFF.toInt())
                textSize = 15f
                typeface = androidx.core.content.res.ResourcesCompat
                    .getFont(context, R.font.poppins_black)
                letterSpacing = 0.03f
                setBackgroundColor(0xEB000000.toInt())
                // 22 left, 14 right, the way every Mont surface is padded.
                setPadding(dp(context, 22), dp(context, 14), dp(context, 14), dp(context, 14))
            }
            context.getSystemService(WindowManager::class.java).addView(label, params())
            showing = label
            handler.removeCallbacks(hide)
            handler.postDelayed(hide, DURATION_MS)
        }
    }

    fun dismiss() {
        val view = showing ?: return
        showing = null
        handler.removeCallbacks(hide)
        runCatching {
            view.context.getSystemService(WindowManager::class.java).removeView(view)
        }
    }

    private fun dp(context: Context, value: Int) =
        (value * context.resources.displayMetrics.density).toInt()

    private fun params() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        TYPE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        // Full width, held off the top edge by the cover tier's inset. A Mont surface is a
        // full-width rectangle; it does not float in the middle as a capsule.
        gravity = Gravity.TOP or Gravity.START
        y = 0
        title = "miniTools/TOAST"
    }

    private const val TYPE = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
    private const val DURATION_MS = 1_400L
}
