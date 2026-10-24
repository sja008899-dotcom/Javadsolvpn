package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.MainViewModel
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberDarkBackground
import com.example.ui.theme.CyberDarkCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val preferredDns by viewModel.preferredDns.collectAsStateWithLifecycle()
    val autoConnect by viewModel.autoConnectOnLaunch.collectAsStateWithLifecycle()
    val autoFailover by viewModel.autoFailover.collectAsStateWithLifecycle()
    val language by viewModel.appLanguage.collectAsStateWithLifecycle()

    var showAboutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CyberDarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "تنظیمات برنامه",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary
        )

        // DNS Section
        SettingsCard(title = "سرور DNS امن", icon = Icons.Default.Dns) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DnsOption(
                    title = "Cloudflare (1.1.1.1) - سرعت بالا و حریم خصوصی",
                    value = "1.1.1.1",
                    selected = preferredDns == "1.1.1.1",
                    onSelect = { viewModel.preferredDns.value = "1.1.1.1" }
                )
                DnsOption(
                    title = "Google DNS (8.8.8.8) - پایداری سراسری",
                    value = "8.8.8.8",
                    selected = preferredDns == "8.8.8.8",
                    onSelect = { viewModel.preferredDns.value = "8.8.8.8" }
                )
                DnsOption(
                    title = "Quad9 (9.9.9.9) - مسدودسازی بدافزار و فیشینگ",
                    value = "9.9.9.9",
                    selected = preferredDns == "9.9.9.9",
                    onSelect = { viewModel.preferredDns.value = "9.9.9.9" }
                )
            }
        }

        // Automation Section
        SettingsCard(title = "هوشمندی و خودکارسازی", icon = Icons.Default.Sync) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ToggleOption(
                    title = "اتصال خودکار هنگام باز شدن برنامه",
                    subtitle = "به محض ورود سریع‌ترین کانفیگ فعال متصل می‌شود",
                    checked = autoConnect,
                    onCheckedChange = { viewModel.autoConnectOnLaunch.value = it }
                )

                ToggleOption(
                    title = "جابجایی خودکار به کانفیگ بعدی (Failover)",
                    subtitle = "در صورت بروز اختلال در سرور، کانفیگ بعدی امتحان می‌شود",
                    checked = autoFailover,
                    onCheckedChange = { viewModel.autoFailover.value = it }
                )
            }
        }

        // Privacy & Protection
        SettingsCard(title = "امنیت و حریم خصوصی", icon = Icons.Default.Security) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = "پروتکل‌های مورد پشتیبانی", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(text = "VLESS Reality • VMess • Trojan • Shadowsocks", fontSize = 11.sp, color = TextSecondary)
                    }
                }
                Text(
                    text = "هیچ‌گونه تاریخچه وب‌گردی یا ترافیکی ذخیره یا ارسال نمی‌شود. تمامی کانفیگ‌ها در انبار محلی دستگاه نگهداری می‌شوند.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
            }
        }

        // Language Section
        SettingsCard(title = "زبان برنامه / Language", icon = Icons.Default.Language) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = if (language == "FA") "فارسی (راست‌چین)" else "English", fontSize = 13.sp, color = TextPrimary)
                Button(
                    onClick = {
                        viewModel.appLanguage.value = if (language == "FA") "EN" else "FA"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(text = if (language == "FA") "تغییر به English" else "تغییر به فارسی", fontSize = 12.sp, color = CyberCyan)
                }
            }
        }

        // About Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CyberDarkCard)
                .border(1.dp, CyberBorder, RoundedCornerShape(14.dp))
                .clickable { showAboutDialog = true }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = "درباره Javadsolvpn", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Text(text = "نسخه ۱.۰", fontSize = 12.sp, color = TextSecondary)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.javadsolvpn_orbit),
                        contentDescription = null,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Javadsolvpn", fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "اپلیکیشن پیشرفته، مستقل و خودکار فیلترشکن و VPN برای دسترسی امن و آزاد به اینترنت.",
                        fontSize = 13.sp,
                        color = TextPrimary,
                        lineHeight = 20.sp
                    )
                    Text(
                        text = "• کشف خودکار کانفیگ‌ها از منابع عمومی و هاب اختصاصی\n• انبار محلی رمزنگاری‌شده در دستگاه\n• سازگار با VLESS Reality, VMess, Trojan و Shadowsocks\n• بدون ذخیره داده‌های شخصی یا ترافیک",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showAboutDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                ) {
                    Text("بستن", color = Color(0xFF001E2E), fontWeight = FontWeight.Bold)
                }
            },
            containerColor = CyberDarkCard
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CyberDarkCard)
            .border(1.dp, CyberBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun DnsOption(
    title: String,
    value: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = CyberCyan, unselectedColor = TextSecondary)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = title, fontSize = 12.sp, color = if (selected) TextPrimary else TextSecondary)
    }
}

@Composable
private fun ToggleOption(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(text = subtitle, fontSize = 11.sp, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CyberCyan,
                checkedTrackColor = CyberBlue.copy(alpha = 0.5f),
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = Color(0xFF07101E)
            )
        )
    }
}
