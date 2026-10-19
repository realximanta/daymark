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

    private fun rid(name: String, type: String): Int = resources.getIdentifier(name, type, packageName)

    override fun onCreate(savedInstanceState: Bundle?) {
        // 🔥 FIX: Apply theme before super.onCreate
        applyTheme()

        super.onCreate(savedInstanceState)
        setContentView(rid("activity_passcode", "layout"))
        
        input = findViewById(rid("passcodeInput", "id"))
        errorView = findViewById(rid("passcodeError", "id"))
        button = findViewById(rid("savePasscodeBtn", "id"))

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
        val mode = getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE).getString(Config.KEY_THEME, Config.THEME_SYSTEM) ?: Config.THEME_SYSTEM
        val dark = mode == Config.THEME_DARK || (mode == Config.THEME_SYSTEM && (resources.configuration.uiMode and 0x30) == 0x20)
        val styleId = resources.getIdentifier(if (dark) "AppTheme_Dark" else "AppTheme", "style", packageName)
        if (styleId != 0) setTheme(styleId)
    }
}
