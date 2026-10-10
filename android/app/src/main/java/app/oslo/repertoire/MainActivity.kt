package app.oslo.repertoire

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import app.oslo.repertoire.navigation.RepertoireApp
import app.oslo.repertoire.ui.theme.GuideTheme
import app.oslo.repertoire.ui.theme.isAppInDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val model: GuideViewModel = viewModel()
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
}
