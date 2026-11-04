package com.alexblock.netstatus.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.alexblock.netstatus.ui.wlan.WlanScreen
import com.alexblock.netstatus.ui.wwan.WwanScreen

enum class Section { WLAN, WWAN }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetStatusApp() {
    var section by rememberSaveable { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NetStatus", fontWeight = FontWeight.SemiBold) },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = section == 0,
                    onClick = { section = 0 },
                    icon = { Icon(Icons.Outlined.Wifi, contentDescription = null) },
                    label = { Text("WLAN") },
                )
                NavigationBarItem(
                    selected = section == 1,
                    onClick = { section = 1 },
                    icon = { Icon(Icons.Outlined.SignalCellularAlt, contentDescription = null) },
                    label = { Text("移动数据") },
                )
            }
        },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (section) {
                0 -> WlanScreen()
                else -> WwanScreen()
            }
        }
    }
}
