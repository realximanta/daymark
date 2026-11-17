// language: Kotlin, file: PasscodeActivity.kt, target: Android API 26-35
package com.ximanta.opendiary

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class PasscodeActivity : Activity() {

    private lateinit var input: EditText
    private lateinit var errorView: TextView
    private lateinit var button: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_passcode)

        input = findViewById(R.id.passcodeInput)
        errorView = findViewById(R.id.passcodeError)
        button = findViewById(R.id.savePasscodeBtn)

        // Preload the existing passcode so the user can verify what is stored.
        // Once PasscodeStore moves to EncryptedSharedPreferences, this still
        // resolves — same get() surface.
        PasscodeStore.get(this)?.let { input.setText(it) }

        button.setOnClickListener { submit() }
    }

    private fun submit() {
        val code = input.text.toString().trim()
        if (code.length < 4) {
            showError("Passcode must be at least 4 characters.")
            return
        }
        PasscodeStore.save(this, code)
        Toast.makeText(this, "Passcode saved. Sync is now enabled.", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun showError(message: String) {
        errorView.text = message
        errorView.visibility = android.view.View.VISIBLE
        input.requestFocus()
    }

    private fun applyTheme() {
        val mode = getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE)
            .getString(Config.KEY_THEME, Config.THEME_SYSTEM) ?: Config.THEME_SYSTEM
        val dark = mode == Config.THEME_DARK ||
            (mode == Config.THEME_SYSTEM && (resources.configuration.uiMode and 0x30) == 0x20)
        setTheme(if (dark) R.style.AppTheme_Dark else R.style.AppTheme)
    }
}

/**
 * Lightweight storage for the API passcode.
 *
 * Currently plaintext in SharedPreferences. Migration to EncryptedSharedPreferences
 * is a separate change that adds the androidx.security dependency — not silently
 * folded into this file.
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

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}
