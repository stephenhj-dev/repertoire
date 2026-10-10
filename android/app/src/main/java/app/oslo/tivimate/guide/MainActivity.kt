package app.oslo.tivimate.guide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import app.oslo.tivimate.guide.ui.GuideApp
import app.oslo.tivimate.guide.ui.GuideTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GuideTheme {
                val model: GuideViewModel = viewModel()
                GuideApp(model)
            }
        }
    }
}
