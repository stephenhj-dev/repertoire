package app.oslo.repertoire

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.oslo.repertoire.navigation.RepertoireApp
import app.oslo.repertoire.splash.CollageStore
import app.oslo.repertoire.splash.LaunchCollage
import app.oslo.repertoire.ui.theme.GuideTheme
import app.oslo.repertoire.ui.theme.Motion
import app.oslo.repertoire.ui.theme.isAppInDarkTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {
    /** Shared-list text from the launching intent, handed to the model once. */
    private var incoming by mutableStateOf<String?>(null)

    /** Cold-start intro: null while its posters are decoding, then the posters (maybe none). */
    private var introPosters by mutableStateOf<List<ImageBitmap>?>(null)
    private var intro by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) incoming = sharedText(intent)
        // The intro plays on a fresh launch from the launcher, not for shared lists or when
        // animations are turned off.
        intro = savedInstanceState == null && incoming == null && intent?.action == Intent.ACTION_MAIN && animationsOn()
        if (intro) {
            lifecycleScope.launch {
                // Keep the system splash up while thumbnails decode (normally ~50 ms).
                introPosters = withTimeoutOrNull(700) { CollageStore.launchPosters(applicationContext) } ?: emptyList()
            }
            splash.setKeepOnScreenCondition { introPosters == null }
        }
        splash.setOnExitAnimationListener { provider ->
            provider.view.animate().alpha(0f).setDuration(200).withEndAction { provider.remove() }.start()
        }
        CollageStore.refreshInBackground(applicationContext)

        setContent {
            val model: GuideViewModel = viewModel()
            LaunchedEffect(incoming) {
                incoming?.let { model.offerImport(it) }
                incoming = null
            }
            val dark = isAppInDarkTheme(model.themeMode)
            // System bar icons follow the app's theme choice (light icons over the intro).
            DisposableEffect(dark, intro) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark || intro }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            val scope = rememberCoroutineScope()
            val rise = remember { Animatable(if (intro) 0f else 1f) }
            GuideTheme(mode = model.themeMode, dynamicColor = model.dynamicColor) {
                Box(Modifier.fillMaxSize()) {
                    // The app is composed under the intro from the start, so nothing waits on it.
                    Box(
                        Modifier.fillMaxSize().graphicsLayer {
                            val r = rise.value
                            alpha = r.coerceIn(0f, 1f)
                            translationY = (1f - r) * 56.dp.toPx()
                            val s = 0.97f + 0.03f * r
                            scaleX = s
                            scaleY = s
                        },
                    ) { RepertoireApp(model) }
                    val posters = introPosters
                    if (intro && posters != null) {
                        LaunchCollage(
                            posters = posters,
                            onReveal = { scope.launch { rise.animateTo(1f, Motion.gentle()) } },
                            onFinished = {
                                intro = false
                                introPosters = emptyList()
                            },
                        )
                    }
                }
            }
        }
    }

    private fun animationsOn(): Boolean =
        runCatching { Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f) > 0f

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
