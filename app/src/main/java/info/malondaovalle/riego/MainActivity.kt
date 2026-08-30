package info.malondaovalle.riego

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import info.malondaovalle.riego.ui.navigation.RiegoNavHost
import info.malondaovalle.riego.ui.theme.RiegoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            RiegoTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RiegoNavHost()
                }
            }
        }
    }
}
