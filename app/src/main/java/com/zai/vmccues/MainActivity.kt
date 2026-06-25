package com.zai.vmccues

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.zai.vmccues.ui.SettingsScreen
import com.zai.vmccues.ui.theme.VmcTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VmcTheme {
                Surface(Modifier.fillMaxSize()) {
                    SettingsScreen()
                }
            }
        }
    }
}
