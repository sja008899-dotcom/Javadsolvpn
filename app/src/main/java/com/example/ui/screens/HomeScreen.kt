package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.ServerConfigEntity
import com.example.data.parser.CountryDetector
import com.example.ui.MainViewModel
import com.example.ui.components.CyberConnectButton
import com.example.ui.components.TrafficCard
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberDarkBackground
import com.example.ui.theme.CyberDarkCard
import com.example.ui.theme.CyberNeonGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.vpn.VpnStatus

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToServers: () -> Unit,
    onStartVpnPermissionFlow: (ServerConfigEntity?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vpnStatus by viewModel.vpnStatus.collectAsStateWithLifecycle()
    val activeServer by viewModel.activeServer.collectAsStateWithLifecycle()
    val selectedServer by viewModel.selectedServer.collectAsStateWithLifecycle()
    val trafficStats by viewModel.trafficStats.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()
    val isDiscovering by viewModel.isDiscovering.collectAsStateWithLifecycle()
    val isTestingPing by viewModel.isTestingPing.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    val currentDisplayServer = activeServer ?: selectedServer

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CyberDarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.javadsolvpn_orbit),
                    contentDescription = "لوگوی Javadsolvpn",
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Javadsolvpn",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "اتصال امن و هوشمند",
                        fontSize = 11.sp,
                        color = CyberCyan
                    )
                }
            }

            // Connection Status Pill
            val statusColor = when (vpnStatus) {
                VpnStatus.CONNECTED -> CyberNeonGreen
                VpnStatus.CONNECTING -> CyberCyan
                VpnStatus.ERROR -> Color(0xFFFF5252)
                else -> TextSecondary
            }
            val statusTitle = when (vpnStatus) {
                VpnStatus.CONNECTED -> "متصل"
                VpnStatus.CONNECTING -> "در حال اتصال"
                VpnStatus.ERROR -> "خطا"
                else -> "قطع"
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(statusColor.copy(alpha = 0.15f))
                    .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    text = statusTitle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }
        }

        // Notification Banner if any
        if (bannerMessage != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F2647))
                    .border(1.dp, CyberCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .clickable { viewModel.clearBanner() }
                    .padding(12.dp)
            ) {
                Text(
                    text = bannerMessage ?: "",
                    fontSize = 12.sp,
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Persistent Error Banner
        if (errorMessage != null && vpnStatus == VpnStatus.ERROR) {
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF3B151E))
                    .border(1.dp, Color(0xFFFF5252), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        fontSize = 12.sp,
                        color = Color(0xFFFFD1D1),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Large Cyber Connect Button
        CyberConnectButton(
            status = vpnStatus,
            onClick = {
                if (vpnStatus == VpnStatus.CONNECTED) {
                    viewModel.disconnect(context)
                } else {
                    onStartVpnPermissionFlow(currentDisplayServer)
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Selected Server Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CyberDarkCard)
                .border(1.dp, CyberBorder, RoundedCornerShape(16.dp))
                .clickable { onNavigateToServers() }
                .padding(14.dp)
                .testTag("home_selected_server_card")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val country = currentDisplayServer?.let {
                        CountryDetector.detect(it.host, it.countryCode)
                    } ?: CountryDetector.detect("auto")

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF07101E))
                            .border(1.dp, CyberBorder, CircleShape)
                    ) {
                        Text(text = country.flag, fontSize = 20.sp)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = currentDisplayServer?.name ?: "انتخاب خودکار هوشمند",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (currentDisplayServer != null) {
                                "${currentDisplayServer.protocol.uppercase()} • ${currentDisplayServer.countryNameFa}"
                            } else {
                                "برای تغییر سرور ضربه بزنید"
                            },
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "تغییر",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "انتخاب سرور",
                        tint = CyberCyan,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Live Traffic Card
        TrafficCard(
            stats = trafficStats,
            isConnected = vpnStatus == VpnStatus.CONNECTED
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Quick Actions Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickActionButton(
                title = "اتصال خودکار",
                icon = Icons.Default.AutoAwesome,
                onClick = {
                    onStartVpnPermissionFlow(null)
                },
                modifier = Modifier.weight(1f)
            )

            QuickActionButton(
                title = if (isDiscovering) "در حال هاب…" else "بروزرسانی هاب",
                icon = Icons.Default.CloudSync,
                isLoading = isDiscovering,
                onClick = { viewModel.refreshDiscovery() },
                modifier = Modifier.weight(1f)
            )

            QuickActionButton(
                title = if (isTestingPing) "تست…" else "تست پینگ",
                icon = Icons.Default.Speed,
                isLoading = isTestingPing,
                onClick = { viewModel.pingAll() },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun QuickActionButton(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CyberDarkCard)
            .border(1.dp, CyberBorder, RoundedCornerShape(12.dp))
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = CyberCyan,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = CyberCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
