package me.rerere.rikkahub.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.utils.LocaleHelper

class CodexOAuthRedirectActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, RouteActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(RouteActivity.EXTRA_OPEN_CODEX_SETTINGS, true)
            }
        )
        finish()
    }
}
