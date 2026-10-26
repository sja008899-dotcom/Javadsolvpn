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
import com.example.ui.theme.CyberBlue
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
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, CyberCyan.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
            color = CyberDarkBackground
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
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
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(CyberCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "عیب‌یابی حالت امن (Safe Mode)",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "ارزیابی تونل لوپ‌بک، FD و هندشیک Xray",
                                fontSize = 11.sp,
                                color = TextSecondary
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

                Spacer(modifier = Modifier.height(14.dp))

                // Status Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // File Descriptor Badge
                    StatusBadge(
                        label = "وضعیت FD",
                        value = if (report.isFdBound) "متصل (FD: ${report.fdNumber})" else "آزاد / غیرمتصل",
                        isSuccess = report.isFdBound,
                        modifier = Modifier.weight(1f)
                    )

                    // Handshake Badge
                    StatusBadge(
                        label = "هندشیک SOCKS5",
                        value = when (report.handshakeResponseCode) {
                            0 -> "موفق 0x00 (${report.handshakeLatencyMs}ms)"
                            -1 -> "انجام‌نشده"
                            else -> "خطا: ${report.handshakeResponseCode}"
                        },
                        isSuccess = report.handshakeResponseCode == 0,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Diagnostic Steps List
                Text(
                    text = "مراحل ارزیابی سلامت هسته:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CyberDarkCard)
                        .border(1.dp, CyberBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    report.steps.forEach { step ->
                        DiagnosticStepRow(step = step)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Real-time Console Log
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ترمینال لاگ زنده هسته (Raw Callback & FD):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = {
                                val fullLog = buildString {
                                    appendLine("=== گزارش عیب‌یابی حالت امن Javadsolvpn ===")
                                    appendLine("نسخه هسته: ${report.coreVersion}")
                                    appendLine("فایل دیسکریپتور: FD=${report.fdNumber} (Bound=${report.isFdBound})")
                                    appendLine("کد هندشیک: ${report.handshakeResponseCode}")
                                    appendLine("خلاصه: ${report.summary}")
                                    appendLine("\n--- لاگ‌های روال عیب‌یابی ---")
                                    report.logs.forEach { log ->
                                        appendLine("[${log.timestamp}][${log.tag}] ${log.message}")
                                    }
                                }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Javadsolvpn Diagnostic", fullLog))
                                Toast.makeText(context, "گزارش کامل در کلیپ‌بورد کپی شد", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp).testTag("copy_diagnostic_report_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "کپی لاگ",
                                tint = CyberCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
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
                        .padding(10.dp)
                ) {
                    if (report.logs.isEmpty()) {
                        Text(
                            text = "برای شروع آزمون لوپ‌بک و رهگیری کدهای خطا، دکمه «اجرای عیب‌یابی» را بزنید.",
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
                                        fontSize = 10.sp,
                                        color = TextSecondary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "[${log.tag}] ",
                                        fontSize = 10.sp,
                                        color = CyberCyan,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = log.message,
                                        fontSize = 10.sp,
                                        color = if (log.isError) CyberErrorRed else TextPrimary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Actions Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                    ) {
                        Text("بستن", fontSize = 13.sp)
                    }

                    Button(
                        onClick = onRunDiagnostic,
                        enabled = !report.isRunning,
                        modifier = Modifier
                            .weight(2f)
                            .height(46.dp)
                            .testTag("run_safe_mode_diagnostic_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                    ) {
                        if (report.isRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color(0xFF001E2E),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("در حال عیب‌یابی…", color = Color(0xFF001E2E), fontWeight = FontWeight.Bold)
                        } else {
                            Icon(
                                imageVector = if (report.steps.any { it.state != DiagnosticStepState.PENDING }) Icons.Default.Refresh else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color(0xFF001E2E),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (report.steps.any { it.state != DiagnosticStepState.PENDING }) "اجرای مجدد عیب‌یابی" else "شروع تست حالت امن",
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
            .clip(RoundedCornerShape(10.dp))
            .background(CyberDarkCard)
            .border(
                1.dp,
                if (isSuccess) CyberNeonGreen.copy(alpha = 0.4f) else CyberBorder,
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column {
            Text(text = label, fontSize = 10.sp, color = TextSecondary)
            Text(
                text = value,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSuccess) CyberNeonGreen else TextPrimary
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
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E293B))
                )
            }
            DiagnosticStepState.RUNNING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = CyberCyan,
                    strokeWidth = 2.dp
                )
            }
            DiagnosticStepState.PASSED -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "موفق",
                    tint = CyberNeonGreen,
                    modifier = Modifier.size(18.dp)
                )
            }
            DiagnosticStepState.FAILED -> {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "خطا",
                    tint = CyberErrorRed,
                    modifier = Modifier.size(18.dp)
                )
            }
            DiagnosticStepState.SKIPPED -> {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF475569))
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = step.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (step.state == DiagnosticStepState.FAILED) CyberErrorRed else TextPrimary
            )
            if (step.detailMessage.isNotBlank()) {
                Text(
                    text = step.detailMessage,
                    fontSize = 10.sp,
                    color = if (step.state == DiagnosticStepState.FAILED) CyberErrorRed.copy(alpha = 0.8f) else TextSecondary
                )
            }
        }

        if (step.errorCode != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(CyberErrorRed.copy(alpha = 0.2f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = step.errorCode,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberErrorRed,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
