package app.oslo.repertoire

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.Intent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import app.oslo.repertoire.navigation.RepertoireApp
import app.oslo.repertoire.ui.theme.GuideTheme
import app.oslo.repertoire.ui.theme.isAppInDarkTheme

class MainActivity : ComponentActivity() {
    /** Shared-list text from the launching intent, handed to the model once. */
    private var incoming by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) incoming = sharedText(intent)
        setContent {
            val model: GuideViewModel = viewModel()
            LaunchedEffect(incoming) {
                incoming?.let { model.offerImport(it) }
                incoming = null
            }
            val dark = isAppInDarkTheme(model.themeMode)
            // System bar icons follow the app's theme choice, not just the system setting.
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            GuideTheme(mode = model.themeMode, dynamicColor = model.dynamicColor) {
                RepertoireApp(model)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sharedText(intent)?.let { incoming = it }
    }

    private fun sharedText(intent: Intent?): String? = when (intent?.action) {
        Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
        Intent.ACTION_VIEW -> intent.dataString
        else -> null
    }
}
