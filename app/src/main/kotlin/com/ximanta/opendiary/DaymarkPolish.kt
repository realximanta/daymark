// language: Kotlin, file: DaymarkPolish.kt, target: Android API 26-35
package com.ximanta.opendiary

import android.app.Activity
import android.content.res.ColorStateList
import android.view.View
import android.widget.GridView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Purely visual helpers — no DB, sync, or navigation.
 *
 * Palette lives in res/values/colors.xml and res/values-night/colors.xml,
 * so the badge tint is simply the resolved `R.color.badge_*`. No manual
 * darkening — the night file already carries the dark variants.
 */
object DaymarkPolish {

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

    /** Aligned 1:1 with [descriptions]. Compile-time references, no getIdentifier. */
    private val badgeColors = intArrayOf(
        R.color.badge_doing,
        R.color.badge_food,
        R.color.badge_idea,
        R.color.badge_task,
        R.color.badge_regret,
        R.color.badge_success,
        R.color.badge_travel,
        R.color.badge_plan,
        R.color.badge_watched
    )

    fun enhanceHome(activity: Activity) {
        // dateText: "TODAY · MONDAY, 17 NOVEMBER" — informative, still anchored.
        val today = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date())
        activity.findViewById<TextView>(R.id.dateText)?.text =
            "TODAY · ${today.uppercase(Locale.getDefault())}"

        // 3 columns on tablets / foldables, 2 on phones.
        activity.findViewById<GridView>(R.id.gridButtons)?.numColumns =
            if (activity.resources.configuration.screenWidthDp >= 600) 3 else 2
    }

    fun bindCategory(view: View, position: Int) {
        if (position !in descriptions.indices) return

        view.findViewById<TextView>(R.id.itemDescription)?.text = descriptions[position]

        val iconView = view.findViewById<TextView>(R.id.itemIcon)
        val labelView = view.findViewById<TextView>(R.id.itemLabel)

        if (iconView != null && position in badgeColors.indices) {
            // getColor(id, theme) honors the current Configuration — -night
            // qualifiers apply automatically. Pass the theme so any future
            // theme-attribute colors resolve cleanly.
            val tint = view.resources.getColor(badgeColors[position], view.context.theme)
            iconView.backgroundTintList = ColorStateList.valueOf(tint)
        }

        // TalkBack reads emoji + label + description as one unit.
        val label = labelView?.text?.toString().orEmpty()
        val emoji = iconView?.text?.toString().orEmpty()
        view.contentDescription = "$emoji $label — ${descriptions[position]}"
    }
}
