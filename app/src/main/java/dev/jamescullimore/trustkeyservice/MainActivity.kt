package dev.jamescullimore.trustkeyservice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.jamescullimore.trustkeyservice.ui.TrustKeyServiceApp
import dev.jamescullimore.trustkeyservice.ui.theme.TrustKeyServiceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrustKeyServiceTheme {
                TrustKeyServiceApp()
            }
        }
    }
}
