package dev.abdullah.openlock

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

fun Activity.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
fun Activity.column(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(20), dp(16), dp(20), dp(20))
    setBackgroundColor(Color.rgb(10, 12, 16))
}
fun Activity.label(value: String, size: Float = 16f): TextView = TextView(this).apply {
    text = value; textSize = size; setTextColor(Color.rgb(237, 239, 243))
    setPadding(0, dp(8), 0, dp(8))
    if (size >= 24) setTypeface(null, Typeface.BOLD)
}
fun Activity.button(value: String, action: () -> Unit): Button = Button(this).apply {
    text = value; isAllCaps = false; setOnClickListener { action() }
}
fun Activity.fitInsets(view: View) {
    val left = view.paddingLeft; val top = view.paddingTop
    val right = view.paddingRight; val bottom = view.paddingBottom
    view.setOnApplyWindowInsetsListener { v, insets ->
        @Suppress("DEPRECATION")
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
            v.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom)
        } else {
            @Suppress("DEPRECATION")
            v.setPadding(left + insets.systemWindowInsetLeft, top + insets.systemWindowInsetTop, right + insets.systemWindowInsetRight, bottom + insets.systemWindowInsetBottom)
        }
        insets
    }
    view.requestApplyInsets()
}
