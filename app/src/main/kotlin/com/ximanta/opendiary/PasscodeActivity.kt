package com.ximanta.opendiary

import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

/**
 * One-time passcode gate.
 *
 * On first launch, the user sets a passcode that is stored locally and sent
 * with every backend request via the `x-daymark-passcode` header. On subsequent
 * launches the screen is skipped entirely.
 */
class PasscodeActivity : Activity() {

    private lateinit var input: EditText
    private lateinit var errorView: TextView
    private lateinit var button: Button

    private fun rid(name: String, type: String): Int =
        resources.getIdentifier(name, type, packageName)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyThemeIfNeeded()

        // If a passcode has already been set, skip straight to MainActivity.
        if (PasscodeStore.hasPasscode(this)) {
            goToMain()
            return
        }

        setContentView(rid("activity_passcode", "layout"))
        input = findViewById(rid("passcodeInput", "id"))
        errorView = findViewById(rid("passcodeError", "id"))
        button = findViewById(rid("savePasscodeBtn", "id"))

        button.setOnClickListener { submit() }
    }

    private fun submit() {
        val code = input.text.toString().trim()

        if (code.length < 4) {
            showError("Passcode must be at least 4 characters.")
            return
        }

        PasscodeStore.save(this, code)
        Toast.makeText(this, "Passcode saved", Toast.LENGTH_SHORT).show()
        goToMain()
    }

    private fun showError(message: String) {
        errorView.text = message
        errorView.visibility = View.VISIBLE
        shake(input)
    }

    /** Gentle horizontal shake to signal invalid input. */
    private fun shake(view: View) {
        view.animate().cancel()
        view.translationX = 0f
        val anim = ObjectAnimator.ofFloat(
            view, "translationX",
            0f, -18f, 18f, -12f, 12f, -6f, 6f, 0f
        )
        anim.duration = 360
        anim.start()
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun applyThemeIfNeeded() {
        val mode = getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE)
            .getString(Config.KEY_THEME, Config.THEME_SYSTEM) ?: Config.THEME_SYSTEM
        val dark = mode == Config.THEME_DARK ||
            (mode == Config.THEME_SYSTEM && (resources.configuration.uiMode and 0x30) == 0x20)
        val styleId = resources.getIdentifier(
            if (dark) "AppTheme_Dark" else "AppTheme", "style", packageName
        )
        if (styleId != 0) setTheme(styleId)
    }
}

/**
 * Lightweight storage for the API passcode.
 *
 * Uses a dedicated SharedPreferences file so it can be wiped independently if
 * the user ever wants to reset it. A production build would additionally wrap
 * this with the Android Keystore; we keep it plain here so the no-AndroidX
 * build script continues to work unchanged.
 */
object PasscodeStore {
    private const val FILE = "daymark_secure"
    private const val KEY = "api_passcode"

    fun hasPasscode(ctx: Context): Boolean = !get(ctx).isNullOrEmpty()

    fun get(ctx: Context): String? =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)

    fun save(ctx: Context, code: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY, code).apply()
    }

    /** Future "Reset passcode" toggle in Settings can call this. */
    fun clear(ctx: Context) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}
