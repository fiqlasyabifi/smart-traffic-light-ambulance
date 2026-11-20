
package com.example.smarttrafficlight

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.smarttrafficlight.navigation.AppNavigation
import com.example.smarttrafficlight.ui.theme.SmartTrafficLightTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            SmartTrafficLightTheme {
                AppNavigation()
            }
        }
    }
}