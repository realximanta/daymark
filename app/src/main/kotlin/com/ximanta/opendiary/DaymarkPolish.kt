package com.ximanta.opendiary

import android.app.Activity
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.view.View
import android.widget.GridView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Purely visual enhancements — does not touch DB, sync, or navigation.
 *
 * Responsibilities:
 *  - Populate the hero panel (date · greeting · subtitle) on the home screen.
 *  - Bind per-category descriptions and colored icon badges on the grid cards.
 *  - Adapt the grid column count for tablets.
 *
 * Safe to call from [MainActivity.onCreate] and from the grid adapter's getView.
 */
object DaymarkPolish {

    // ─── Category descriptions (shown on the home grid cards) ───
    private val descriptions = arrayOf(
        "The present moment",
        "Meals & memories",
        "Capture a spark",
        "Keep life on track",
        "Learn and let go",
        "Small and big wins",
        "Places and paths",
        "What comes next",
        "Stories worth sharing"
    )

    // ─── Badge color resource names (resolved at bind time from @color/badge_*) ───
    private val badgeColorNames = arrayOf(
        "badge_doing", "badge_food", "badge_idea",
        "badge_task", "badge_regret", "badge_success",
        "badge_travel", "badge_plan", "badge_watched"
    )

    // ─── Hero panel ───
    fun enhanceHome(activity: Activity) {
        val locale = Locale.getDefault()

        // Date strip
        val today = SimpleDateFormat("EEEE, d MMMM", locale).format(Date())
        activity.findViewById<TextView>(R.id.dateText)?.text = today.uppercase(locale)

        // Greeting by time-of-day
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 5..8   -> "Start softly."
            in 9..11  -> "A fresh beginning."
            in 12..14 -> "Pause and reflect."
            in 15..17 -> "A quiet moment."
            in 18..20 -> "A moment for you."
            in 21..23 -> "Unwind your thoughts."
            else      -> "Rest well."
        }
        activity.findViewById<TextView>(R.id.greetingText)?.text = greeting

        // Responsive grid: 3 columns on 600dp+ (tablets, foldables), else 2.
        activity.findViewById<GridView>(R.id.gridButtons)?.numColumns =
            if (activity.resources.configuration.screenWidthDp >= 600) 3 else 2
    }

    // ─── Category card ───
    fun bindCategory(view: View, position: Int) {
        if (position !in descriptions.indices) return

        // Description subtitle
        view.findViewById<TextView>(R.id.itemDescription)?.text = descriptions[position]

        // Icon badge tint
        val iconView = view.findViewById<TextView>(R.id.itemIcon)
        val labelView = view.findViewById<TextView>(R.id.itemLabel)
        if (iconView != null && position in badgeColorNames.indices) {
            val resId = view.resources.getIdentifier(
                badgeColorNames[position], "color", view.context.packageName
            )
            if (resId != 0) {
                val isDark = (view.resources.configuration.uiMode and
                        Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                val base = view.resources.getColor(resId, null)
                val tint = if (isDark) darken(base, 0.62f) else base
                iconView.backgroundTintList = ColorStateList.valueOf(tint)
            }
        }

        // Accessibility: read emoji + label + description together.
        val label = labelView?.text?.toString().orEmpty()
        val emoji = iconView?.text?.toString().orEmpty()
        view.contentDescription = "$emoji $label — ${descriptions[position]}"
    }

    /**
     * Blend a color toward black by [factor] (0f = unchanged, 1f = fully black).
     * Used to derive dark-theme badge tints from the light-mode source colors.
     */
    private fun darken(color: Int, factor: Float): Int {
        val f = factor.coerceIn(0f, 1f)
        val r = (Color.red(color) * (1f - f)).toInt()
        val g = (Color.green(color) * (1f - f)).toInt()
        val b = (Color.blue(color) * (1f - f)).toInt()
        return Color.rgb(r, g, b)
    }
}
