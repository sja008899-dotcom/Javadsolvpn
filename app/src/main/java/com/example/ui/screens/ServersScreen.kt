package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.ServerConfigEntity
import com.example.ui.MainViewModel
import com.example.ui.components.ServerCard
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberDarkBackground
import com.example.ui.theme.CyberDarkCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.vpn.VpnStatus

@Composable
fun ServersScreen(
    viewModel: MainViewModel,
    onStartVpnPermissionFlow: (ServerConfigEntity?) -> Unit,
    modifier: Modifier = Modifier
) {
    val servers by viewModel.filteredServers.collectAsStateWithLifecycle()
    val allServers by viewModel.allServers.collectAsStateWithLifecycle()
    val selectedServer by viewModel.selectedServer.collectAsStateWithLifecycle()
    val activeServer by viewModel.activeServer.collectAsStateWithLifecycle()
    val vpnStatus by viewModel.vpnStatus.collectAsStateWithLifecycle()

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedProtocol by viewModel.selectedProtocol.collectAsStateWithLifecycle()
    val selectedCountry by viewModel.selectedCountry.collectAsStateWithLifecycle()
    val onlyFavorites by viewModel.onlyFavorites.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CyberDarkBackground)
            .padding(top = 12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "سرورها و کانفیگ‌ها",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                Text(
                    text = "${allServers.size} کانفیگ موجود در انبار محلی",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            IconButton(
                onClick = { viewModel.pingAll() },
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(CyberDarkCard)
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = "تست پینگ همه",
                    tint = CyberCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.searchQuery.value = it },
            placeholder = { Text("جستجو بر اساس نام، کشور یا سرور…", fontSize = 13.sp, color = TextSecondary) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CyberCyan) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "پاک کردن", tint = TextSecondary)
                    }
                }
            },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CyberDarkCard,
                unfocusedContainerColor = CyberDarkCard,
                focusedBorderColor = CyberCyan,
                unfocusedBorderColor = CyberBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .testTag("search_servers")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Protocol Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val protocols = listOf("ALL" to "همه", "VLESS" to "VLESS", "VMESS" to "VMess", "TROJAN" to "Trojan", "SS" to "Shadowsocks", "OPENVPN" to "OpenVPN")
            protocols.forEach { (code, label) ->
                val isSelected = selectedProtocol == code
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.selectedProtocol.value = code },
                    label = { Text(label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                        selectedLabelColor = CyberCyan,
                        containerColor = CyberDarkCard,
                        labelColor = TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = if (isSelected) CyberCyan else CyberBorder
                    )
                )
            }

            // Favorite Chip
            FilterChip(
                selected = onlyFavorites,
                onClick = { viewModel.onlyFavorites.value = !onlyFavorites },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = if (onlyFavorites) Color(0xFFFFD600) else TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text("علاقه‌مندی‌ها", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0x33FFD600),
                    selectedLabelColor = Color(0xFFFFD600),
                    containerColor = CyberDarkCard,
                    labelColor = TextSecondary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = onlyFavorites,
                    borderColor = if (onlyFavorites) Color(0xFFFFD600) else CyberBorder
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Country Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val countries = listOf(
                "ALL" to "همه کشورها",
                "US" to "🇺🇸 آمریکا",
                "DE" to "🇩🇪 آلمان",
                "JP" to "🇯🇵 ژاپن",
                "NL" to "🇳🇱 هلند",
                "FI" to "🇫🇮 فنلاند",
                "CA" to "🇨🇦 کانادا",
                "GB" to "🇬🇧 بریتانیا",
                "SG" to "🇸🇬 سنگاپور"
            )
            countries.forEach { (code, label) ->
                val isSelected = selectedCountry == code
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.selectedCountry.value = code },
                    label = { Text(label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyberBlue.copy(alpha = 0.2f),
                        selectedLabelColor = CyberCyan,
                        containerColor = CyberDarkCard,
                        labelColor = TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = if (isSelected) CyberBlue else CyberBorder
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Servers List or Empty State
        if (servers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "هیچ کانفیگی مطابق فیلتر یافت نشد",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "می‌توانید فیلترها را ریست کنید یا از بخش هوشمند هاب را بروزرسانی کنید.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(servers, key = { it.id }) { server ->
                    val isSelected = selectedServer?.id == server.id
                    val isConnected = activeServer?.id == server.id && vpnStatus == VpnStatus.CONNECTED

                    ServerCard(
                        server = server,
                        isSelected = isSelected,
                        isConnected = isConnected,
                        onSelect = {
                            viewModel.selectServer(server)
                            onStartVpnPermissionFlow(server)
                        },
                        onToggleFavorite = { viewModel.toggleFavorite(server) },
                        onDelete = { viewModel.deleteServer(server) }
                    )
                }
            }
        }
    }
}
