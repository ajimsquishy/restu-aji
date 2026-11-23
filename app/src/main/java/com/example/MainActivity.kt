package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.data.firebase.configureAppCheck
import com.example.ui.navigation.MeasureLabApp
import com.example.ui.theme.MeasureLabTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // STEP 1: Configure App Check with launch intent before any UI, ViewModel, or Firebase call
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        val appContainer = (application as MeasureLabApplication).container
        setContent {
            MeasureLabTheme {
                MeasureLabApp(container = appContainer)
            }
        }
    }
}
