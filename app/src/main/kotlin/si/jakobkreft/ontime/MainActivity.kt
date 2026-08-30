package si.jakobkreft.ontime

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import si.jakobkreft.ontime.ui.OnTimeApp
import si.jakobkreft.ontime.ui.OnTimeTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Every screen sits on a dark or saturated ground, so the system bars always want light
        // icons; letting the framework choose would flip them as the phase colour changes.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            OnTimeTheme {
                OnTimeApp()
            }
        }
    }
}
