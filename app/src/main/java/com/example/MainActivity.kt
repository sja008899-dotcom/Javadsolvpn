package com.example

import android.app.Activity
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.ServerConfigEntity
import com.example.ui.MainViewModel
import com.example.ui.components.BottomNav
import com.example.ui.screens.ActivityScreen
import com.example.ui.screens.DiscoveryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ServersScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.CyberDarkBackground
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                var pendingServerToConnect by remember { mutableStateOf<ServerConfigEntity?>(null) }

                val vpnPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        viewModel.connect(this, pendingServerToConnect)
                    } else {
                        viewModel.bannerMessage.value = "دسترسی ایجاد VPN از سوی کاربر رد شد."
                    }
                    pendingServerToConnect = null
                }

                fun requestVpnAndConnect(targetServer: ServerConfigEntity?) {
                    pendingServerToConnect = targetServer
                    val vpnIntent = VpnService.prepare(this)
                    if (vpnIntent != null) {
                        vpnPermissionLauncher.launch(vpnIntent)
                    } else {
                        viewModel.connect(this, targetServer)
                    }
                }

                val language by viewModel.appLanguage.collectAsStateWithLifecycle()
                val layoutDirection = if (language == "FA") LayoutDirection.Rtl else LayoutDirection.Ltr

                CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()

                    BackHandler(enabled = currentTab != 0) {
                        viewModel.currentTab.value = 0
                    }

                    Scaffold(
                        bottomBar = {
                            BottomNav(
                                currentTab = currentTab,
                                onTabSelected = { viewModel.currentTab.value = it }
                            )
                        },
                        containerColor = CyberDarkBackground,
                        contentWindowInsets = WindowInsets.safeDrawing,
                        modifier = Modifier.fillMaxSize()
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .background(CyberDarkBackground)
                        ) {
                            when (currentTab) {
                                0 -> HomeScreen(
                                    viewModel = viewModel,
                                    onNavigateToServers = { viewModel.currentTab.value = 1 },
                                    onStartVpnPermissionFlow = { server ->
                                        requestVpnAndConnect(server)
                                    }
                                )
                                1 -> ServersScreen(
                                    viewModel = viewModel,
                                    onStartVpnPermissionFlow = { server ->
                                        requestVpnAndConnect(server)
                                    }
                                )
                                2 -> DiscoveryScreen(
                                    viewModel = viewModel,
                                    onStartVpnPermissionFlow = { server ->
                                        requestVpnAndConnect(server)
                                    }
                                )
                                3 -> ActivityScreen(viewModel = viewModel)
                                4 -> SettingsScreen(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}
