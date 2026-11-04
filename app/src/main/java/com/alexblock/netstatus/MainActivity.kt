package com.alexblock.netstatus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.alexblock.netstatus.ui.NetStatusApp
import com.alexblock.netstatus.ui.theme.NetStatusTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NetStatusTheme {
                NetStatusApp()
            }
        }
    }
}
