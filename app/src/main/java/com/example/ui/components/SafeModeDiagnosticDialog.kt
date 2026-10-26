package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.vpn.diagnostic.DiagnosticStepItem
import com.example.vpn.diagnostic.DiagnosticStepState
import com.example.vpn.diagnostic.SafeModeDiagnosticReport
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberDarkBackground
import com.example.ui.theme.CyberDarkCard
import com.example.ui.theme.CyberNeonGreen
import com.example.ui.theme.CyberErrorRed
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SafeModeDiagnosticDialog(
    report: SafeModeDiagnosticReport,
    onRunDiagnostic: () -> Unit,
    onDismiss: () -> Unit,
    onTestNextServer: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    // Auto-scroll logs as new entries arrive
    LaunchedEffect(report.logs.size) {
        if (report.logs.isNotEmpty()) {
            listState.animateScrollToItem(report.logs.size - 1)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.94f)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, CyberCyan.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
            color = CyberDarkBackground
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .testTag("safe_mode_diagnostic_dialog")
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(CyberCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "عیب‌یابی حالت امن (Safe Mode)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (report.targetServerName.isNotBlank()) "آزمایش سرور: ${report.targetServerName}" else "تحلیل و ریشه‌یابی خطای اتصال",
                                fontSize = 11.sp,
                                color = CyberCyan
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_diagnostic_dialog_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "بستن",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Truthful Status Badges (Local TUN, Server Port, Live Internet 204)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Badge 1: Local TUN File Descriptor (Local only!)
                    StatusBadge(
                        label = "تونل محلی دستگاه",
                        value = if (report.isFdBound) "تخصیص FD: ${report.fdNumber}" else "آماده‌سازی نشده",
                        isSuccess = report.isFdBound,
                        modifier = Modifier.weight(1f)
                    )

                    // Badge 2: Remote Server Port Reachability
                    val step2State = report.steps.getOrNull(2)?.state
                    StatusBadge(
                        label = "پورت سرور راه دور",
                        value = when {
                            report.tcpReachable -> "باز (${report.tcpPingMs}ms)"
                            step2State == DiagnosticStepState.FAILED -> "مسدود در شبکه"
                            step2State == DiagnosticStepState.RUNNING -> "در حال تست…"
                            else -> "در انتظار"
                        },
                        isSuccess = report.tcpReachable,
                        modifier = Modifier.weight(1f)
                    )

                    // Badge 3: Live Internet (Generate 204)
                    val step4State = report.steps.getOrNull(4)?.state
                    StatusBadge(
                        label = "اینترنت آزاد (204)",
                        value = when {
                            report.internetCode204Passed -> "تأیید شد (${report.internetLatencyMs}ms)"
                            step4State == DiagnosticStepState.FAILED -> "ناموفق (ارور 204)"
                            step4State == DiagnosticStepState.RUNNING -> "در حال تست…"
                            else -> "در انتظار"
                        },
                        isSuccess = report.internetCode204Passed,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Diagnostic Steps List
                Text(
                    text = "مراحل تفکیک‌شده ارزیابی سلامت اتصال:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CyberDarkCard)
                        .border(1.dp, CyberBorder, RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    report.steps.forEach { step ->
                        DiagnosticStepRow(step = step)
                    }
                }

                // Summary Banner if available
                if (report.summary.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (report.overallPassed == true) CyberNeonGreen.copy(alpha = 0.12f)
                                else CyberErrorRed.copy(alpha = 0.12f)
                            )
                            .border(
                                1.dp,
                                if (report.overallPassed == true) CyberNeonGreen.copy(alpha = 0.4f)
                                else CyberErrorRed.copy(alpha = 0.4f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    ) {
                        Text(
                            text = report.summary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (report.overallPassed == true) CyberNeonGreen else CyberErrorRed
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Real-time Console Log
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ترمینال لاگ زنده و کدهای خطا (Xray & Network):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    IconButton(
                        onClick = {
                            val fullLog = buildString {
                                appendLine("=== گزارش عیب‌یابی حالت امن Javadsolvpn ===")
                                appendLine("سرور: ${report.targetServerName}")
                                appendLine("نسخه هسته: ${report.coreVersion}")
                                appendLine("فایل دیسکریپتور: FD=${report.fdNumber} (Bound=${report.isFdBound})")
                                appendLine("پورت سرور در دسترس: ${report.tcpReachable} (${report.tcpPingMs}ms)")
                                appendLine("اینترنت آزاد 204: ${report.internetCode204Passed} (${report.internetLatencyMs}ms)")
                                appendLine("خلاصه: ${report.summary}")
                                appendLine("\n--- لاگ‌ها ---")
                                report.logs.forEach { log ->
                                    appendLine("[${log.timestamp}][${log.tag}] ${log.message}")
                                }
                            }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Javadsolvpn Diagnostic", fullLog))
                            Toast.makeText(context, "گزارش در کلیپ‌بورد کپی شد", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(24.dp).testTag("copy_diagnostic_report_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "کپی لاگ",
                            tint = CyberCyan,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF030712))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                        .padding(8.dp)
                ) {
                    if (report.logs.isEmpty()) {
                        Text(
                            text = "برای شروع آزمون و تشخیص علت ارور 204، دکمه «شروع تست عیب‌یابی» را بزنید.",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(report.logs) { log ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Text(
                                        text = "${log.timestamp} ",
                                        fontSize = 9.sp,
                                        color = TextSecondary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "[${log.tag}] ",
                                        fontSize = 9.sp,
                                        color = CyberCyan,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = log.message,
                                        fontSize = 9.sp,
                                        color = if (log.isError) CyberErrorRed else TextPrimary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Actions Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(0.9f).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                    ) {
                        Text("بستن", fontSize = 12.sp)
                    }

                    // Test Next Server Button (if available and current server failed)
                    if (onTestNextServer != null && report.nextCandidateId != null && report.overallPassed == false) {
                        Button(
                            onClick = onTestNextServer,
                            enabled = !report.isRunning,
                            modifier = Modifier
                                .weight(1.3f)
                                .height(44.dp)
                                .testTag("test_next_server_diagnostic_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEAB308))
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = null,
                                tint = Color(0xFF1E293B),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "تست سرور بعدی",
                                fontSize = 11.sp,
                                color = Color(0xFF1E293B),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Button(
                        onClick = onRunDiagnostic,
                        enabled = !report.isRunning,
                        modifier = Modifier
                            .weight(1.5f)
                            .height(44.dp)
                            .testTag("run_safe_mode_diagnostic_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                    ) {
                        if (report.isRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color(0xFF001E2E),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("در حال آزمایش…", fontSize = 12.sp, color = Color(0xFF001E2E), fontWeight = FontWeight.Bold)
                        } else {
                            Icon(
                                imageVector = if (report.steps.any { it.state != DiagnosticStepState.PENDING }) Icons.Default.Refresh else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color(0xFF001E2E),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (report.steps.any { it.state != DiagnosticStepState.PENDING }) "اجرای مجدد عیب‌یابی" else "شروع تست عیب‌یابی",
                                fontSize = 12.sp,
                                color = Color(0xFF001E2E),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(
    label: String,
    value: String,
    isSuccess: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CyberDarkCard)
            .border(
                1.dp,
                if (isSuccess) CyberNeonGreen.copy(alpha = 0.5f) else CyberBorder,
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column {
            Text(text = label, fontSize = 9.sp, color = TextSecondary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSuccess) CyberNeonGreen else if (value.contains("مسدود") || value.contains("ناموفق")) CyberErrorRed else TextPrimary
            )
        }
    }
}

@Composable
private fun DiagnosticStepRow(step: DiagnosticStepItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (step.state) {
            DiagnosticStepState.PENDING -> {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E293B))
                )
            }
            DiagnosticStepState.RUNNING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = CyberCyan,
                    strokeWidth = 2.dp
                )
            }
            DiagnosticStepState.PASSED -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "موفق",
                    tint = CyberNeonGreen,
                    modifier = Modifier.size(16.dp)
                )
            }
            DiagnosticStepState.FAILED -> {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "خطا",
                    tint = CyberErrorRed,
                    modifier = Modifier.size(16.dp)
                )
            }
            DiagnosticStepState.SKIPPED -> {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF475569))
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = step.title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (step.state == DiagnosticStepState.FAILED) CyberErrorRed else TextPrimary
            )
            if (step.detailMessage.isNotBlank()) {
                Text(
                    text = step.detailMessage,
                    fontSize = 9.5.sp,
                    color = if (step.state == DiagnosticStepState.FAILED) CyberErrorRed.copy(alpha = 0.9f) else TextSecondary
                )
            }
        }

        if (step.errorCode != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(CyberErrorRed.copy(alpha = 0.2f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = step.errorCode,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberErrorRed,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
