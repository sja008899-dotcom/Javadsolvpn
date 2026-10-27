package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ClipDescription
import android.os.PersistableBundle
import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.DiscoverySourceEntity
import com.example.data.local.ServerConfigEntity
import com.example.ui.MainViewModel
import com.example.ui.components.ServerCard
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberDarkBackground
import com.example.ui.theme.CyberDarkCard
import com.example.ui.theme.CyberNeonGreen
import com.example.ui.theme.CyberViolet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun DiscoveryScreen(
    viewModel: MainViewModel,
    onStartVpnPermissionFlow: ((ServerConfigEntity?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sources by viewModel.allSources.collectAsStateWithLifecycle()
    val discoveryProgress by viewModel.discoveryProgress.collectAsStateWithLifecycle()
    val isTestingPing by viewModel.isTestingPing.collectAsStateWithLifecycle()
    val isFetchingRepository by viewModel.isFetchingRepository.collectAsStateWithLifecycle()
    val activeServer by viewModel.activeServer.collectAsStateWithLifecycle()
    val selectedServer by viewModel.selectedServer.collectAsStateWithLifecycle()
    val vpnStatus by viewModel.vpnStatus.collectAsStateWithLifecycle()

    var targetDiscoveryProtocol by remember { mutableStateOf("ALL") }
    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }

    var showAddSourceDialog by remember { mutableStateOf(false) }
    var newSourceName by remember { mutableStateOf("") }
    var newSourceUrl by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CyberDarkBackground)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "موتور کشف و اسکن سرورها",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Server Discovery & Realtime Candidate Crawler",
                        fontSize = 11.sp,
                        color = CyberCyan
                    )
                }
                Icon(
                    imageVector = Icons.Default.Radar,
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Animated Radar & Live Discovery Console
        item {
            DiscoveryRadarCard(
                progress = discoveryProgress,
                selectedProtocol = targetDiscoveryProtocol,
                onProtocolChange = { targetDiscoveryProtocol = it },
                onStartScan = { viewModel.runDiscovery(targetDiscoveryProtocol) },
                onPingAll = { viewModel.pingAll() },
                isTestingPing = isTestingPing,
                onManualImport = { showImportDialog = true },
                onDownloadRepository = { viewModel.fetchLatestRepositoryConfigs() },
                isFetchingRepository = isFetchingRepository,
                onExportConfigs = {
                    val exported = viewModel.allServers.value
                        .map { it.rawLink.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString("\n")
                    if (exported.isBlank()) {
                        viewModel.bannerMessage.value = "کانفیگی برای اکسپورت وجود ندارد."
                    } else {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Javadsolvpn configs", exported)
                        // Config links contain credentials. Mark the clipboard content sensitive
                        // so Android 13+ can avoid showing full links in its clipboard preview.
                        clip.description.extras = PersistableBundle().apply {
                            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                        }
                        clipboard.setPrimaryClip(clip)
                        viewModel.bannerMessage.value = "تعداد ${exported.lines().size} خط کانفیگ در کلیپ‌بورد کپی شد."
                    }
                }
            )
        }

        // Live Discovered Highlight (if any)
        if (discoveryProgress.latestDiscovered != null) {
            item {
                Column {
                    Text(
                        text = "آخرین نود کشف‌شده",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberNeonGreen
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val discovered = discoveryProgress.latestDiscovered!!
                    ServerCard(
                        server = discovered,
                        isSelected = selectedServer?.id == discovered.id,
                        isConnected = activeServer?.id == discovered.id,
                        onSelect = {
                            viewModel.selectServer(discovered)
                            onStartVpnPermissionFlow?.invoke(discovered)
                        },
                        onToggleFavorite = { viewModel.toggleFavorite(discovered) },
                        onDelete = { viewModel.deleteServer(discovered) }
                    )
                }
            }
        }

        // Live Log Terminal
        item {
            LiveLogConsole(logs = discoveryProgress.recentLogs)
        }

        // Sources Registry Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "منابع ثبت‌شده در هاب (${sources.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Button(
                    onClick = { showAddSourceDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("افزودن منبع", fontSize = 12.sp, color = CyberCyan, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Sources List
        items(sources, key = { it.id }) { source ->
            SourceCard(
                source = source,
                onToggle = { viewModel.toggleSource(source) },
                onDelete = { viewModel.deleteSource(source) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Manual Import Dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("وارد کردن لینک کانفیگ", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Column {
                    Text(
                        text = "می‌توانید لینک‌های vless, vmess, trojan, ss یا سابسکریپشن base64 را وارد کنید:",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        placeholder = { Text("vless://... یا vmess://...", fontSize = 12.sp, color = TextSecondary) },
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CyberDarkCard,
                            unfocusedContainerColor = CyberDarkCard,
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = CyberBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipText = clipboard.primaryClip
                                ?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)
                                ?.coerceToText(context)
                                ?.toString()
                                .orEmpty()
                            if (clipText.isBlank()) {
                                viewModel.bannerMessage.value = "کلیپ‌بورد خالی است یا متن قابل خواندن ندارد."
                            } else {
                                importText = clipText
                                viewModel.bannerMessage.value = "متن کلیپ‌بورد برای بررسی و واردکردن آماده شد."
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("خواندن از کلیپ‌بورد", color = TextPrimary)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importText.isNotBlank()) {
                            viewModel.importConfig(importText)
                            importText = ""
                            showImportDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                ) {
                    Text("افزودن به انبار", color = Color(0xFF001E2E), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showImportDialog = false }) {
                    Text("انصراف", color = TextSecondary)
                }
            },
            containerColor = CyberDarkCard
        )
    }

    // Add Source Dialog
    if (showAddSourceDialog) {
        AlertDialog(
            onDismissRequest = { showAddSourceDialog = false },
            title = { Text("افزودن منبع جدید به هاب", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newSourceName,
                        onValueChange = { newSourceName = it },
                        label = { Text("نام منبع") },
                        placeholder = { Text("هاب تلگرام یا کانفیگ‌های جدید") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CyberDarkCard,
                            unfocusedContainerColor = CyberDarkCard,
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = CyberBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    OutlinedTextField(
                        value = newSourceUrl,
                        onValueChange = { newSourceUrl = it },
                        label = { Text("آدرس URL منبع") },
                        placeholder = { Text("https://raw.githubusercontent.com/...") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CyberDarkCard,
                            unfocusedContainerColor = CyberDarkCard,
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = CyberBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newSourceUrl.isNotBlank()) {
                            viewModel.addSource(newSourceName, newSourceUrl)
                            newSourceName = ""
                            newSourceUrl = ""
                            showAddSourceDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                ) {
                    Text("ذخیره منبع", color = Color(0xFF001E2E), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddSourceDialog = false }) {
                    Text("انصراف", color = TextSecondary)
                }
            },
            containerColor = CyberDarkCard
        )
    }
}

@Composable
private fun DiscoveryRadarCard(
    progress: com.example.data.discovery.DiscoveryProgress,
    selectedProtocol: String,
    onProtocolChange: (String) -> Unit,
    onStartScan: () -> Unit,
    onPingAll: () -> Unit,
    isTestingPing: Boolean,
    onManualImport: () -> Unit,
    onDownloadRepository: () -> Unit,
    isFetchingRepository: Boolean,
    onExportConfigs: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CyberDarkCard)
            .border(1.dp, CyberBorder, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF07101E))
                            .border(1.dp, CyberBorder, CircleShape)
                    ) {
                        Canvas(modifier = Modifier.size(38.dp)) {
                            drawCircle(
                                color = CyberCyan.copy(alpha = 0.3f),
                                style = Stroke(width = 1.dp.toPx())
                            )
                            drawCircle(
                                color = CyberCyan.copy(alpha = 0.6f),
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                            if (progress.isScanning) {
                                drawArc(
                                    brush = Brush.sweepGradient(listOf(Color.Transparent, CyberCyan)),
                                    startAngle = sweepAngle,
                                    sweepAngle = 60f,
                                    useCenter = true
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = null,
                            tint = if (progress.isScanning) CyberCyan else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = if (progress.isScanning) "اسکن زنده در حال اجرا…" else "آماده برای کشف و اسکن",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (progress.isScanning) CyberCyan else TextPrimary
                        )
                        Text(
                            text = if (progress.currentSource.isNotBlank()) progress.currentSource else "همه کانال‌ها و سابسکریپشن‌های فعال",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            maxLines = 1
                        )
                    }
                }

                if (progress.isScanning) {
                    Text(
                        text = "${progress.sourcesProcessed}/${progress.totalSources}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan
                    )
                }
            }

            // Stat Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricTile(
                    title = "خام کشف‌شده",
                    value = "${progress.totalFoundRaw}",
                    color = CyberCyan,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    title = "نودهای معتبر",
                    value = "${progress.totalValidated}",
                    color = CyberNeonGreen,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    title = "تکراری‌های حذف‌شده",
                    value = "${progress.totalDuplicatesRemoved}",
                    color = Color(0xFFFFAB00),
                    modifier = Modifier.weight(1f)
                )
            }

            // Protocol Selector Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val protoList = listOf("ALL" to "همه", "VLESS" to "VLESS", "VMESS" to "VMess", "TROJAN" to "Trojan", "SS" to "Shadowsocks")
                protoList.forEach { (code, label) ->
                    val isSel = selectedProtocol == code
                    FilterChip(
                        selected = isSel,
                        onClick = { onProtocolChange(code) },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                            selectedLabelColor = CyberCyan,
                            containerColor = Color(0xFF07101E),
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSel,
                            borderColor = if (isSel) CyberCyan else CyberBorder
                        )
                    )
                }
            }

            // Main Scan Action
            Button(
                onClick = onStartScan,
                enabled = !progress.isScanning,
                colors = ButtonDefaults.buttonColors(containerColor = CyberBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("start_server_discovery_button")
            ) {
                if (progress.isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("در حال خزش و اعتبارسنجی سرورها…", fontSize = 13.sp)
                } else {
                    Icon(Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("اسکن و کشف خودکار سرورها", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Secondary Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onPingAll,
                    enabled = !isTestingPing,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp), tint = CyberNeonGreen)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تست پینگ همگانی", fontSize = 11.sp, color = TextPrimary)
                }

                OutlinedButton(
                    onClick = onManualImport,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp), tint = CyberCyan)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("وارد کردن دستی", fontSize = 11.sp, color = TextPrimary)
                }
            }

            Button(
                onClick = onDownloadRepository,
                enabled = !isFetchingRepository,
                colors = ButtonDefaults.buttonColors(containerColor = CyberNeonGreen),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isFetchingRepository) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.Black,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (isFetchingRepository) "در حال دریافت از مخزن…" else "دریافت کانفیگ‌های برتر از مخزن (سهمیه ۱۱۰ روزانه)",
                    fontSize = 12.sp,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = onExportConfigs,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp), tint = CyberCyan)
                Spacer(modifier = Modifier.width(6.dp))
                Text("اکسپورت کانفیگ‌ها به کلیپ‌بورد", fontSize = 12.sp, color = TextPrimary)
            }
        }
    }
}

@Composable
private fun MetricTile(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF07101E))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = value, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Text(text = title, fontSize = 10.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun LiveLogConsole(logs: List<com.example.data.discovery.DiscoveryLogEntry>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF050B14))
            .border(1.dp, CyberBorder, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Terminal, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "کنسول رخدادهای زنده کشف",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Text(text = "LIVE STREAM", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = CyberNeonGreen)
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (logs.isEmpty()) {
                Text(
                    text = "منتظر آغاز اسکن… برای شروع بر روی 'اسکن و کشف خودکار سرورها' ضربه بزنید.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    logs.take(6).forEach { entry ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = "${entry.timestamp} ",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextSecondary
                            )
                            val logColor = when (entry.level) {
                                "SUCCESS" -> CyberNeonGreen
                                "WARN" -> Color(0xFFFFD600)
                                "ERROR" -> Color(0xFFFF5252)
                                else -> CyberCyan
                            }
                            Text(
                                text = "[${entry.level}] ",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = logColor
                            )
                            Text(
                                text = entry.message,
                                fontSize = 11.sp,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceCard(
    source: DiscoverySourceEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CyberDarkCard)
            .border(1.dp, CyberBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = source.url,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "وضعیت: ${source.lastStatus}",
                    fontSize = 11.sp,
                    color = if (source.lastStatus.contains("موفق")) CyberNeonGreen else CyberCyan
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = source.isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CyberCyan,
                        checkedTrackColor = CyberBlue.copy(alpha = 0.5f),
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = Color(0xFF07101E)
                    )
                )

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "حذف منبع",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
