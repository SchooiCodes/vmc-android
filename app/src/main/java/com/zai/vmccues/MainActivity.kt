package com.zai.vmccues

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.zai.vmccues.ui.SettingsScreen
import com.zai.vmccues.ui.theme.IosTheme
import com.zai.vmccues.ui.theme.VmcTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VmcTheme {
                // Fill the whole window with the iOS grouped background so
                // the status bar area matches (edge-to-edge).
                val colors = IosTheme.colors
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(colors.groupedBackground)
                ) {
                    SettingsScreen()
                }
            }
        }
    }
}
