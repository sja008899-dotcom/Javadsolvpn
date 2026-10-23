package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberDarkCard
import com.example.ui.theme.CyberErrorRed
import com.example.ui.theme.CyberNeonGreen
import com.example.ui.theme.CyberViolet
import com.example.vpn.VpnStatus

@Composable
fun CyberConnectButton(
    status: VpnStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (status == VpnStatus.CONNECTED || status == VpnStatus.CONNECTING) 1.15f else 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val (glowColor, mainGradient, statusLabel) = when (status) {
        VpnStatus.CONNECTED -> Triple(
            CyberNeonGreen,
            Brush.radialGradient(listOf(Color(0xFF00C853), Color(0xFF007E33))),
            "متصل"
        )
        VpnStatus.CONNECTING, VpnStatus.SEARCHING_CANDIDATES -> Triple(
            CyberCyan,
            Brush.radialGradient(listOf(CyberCyan, CyberBlue)),
            "در حال اتصال…"
        )
        VpnStatus.ERROR -> Triple(
            CyberErrorRed,
            Brush.radialGradient(listOf(CyberErrorRed, Color(0xFFB71C1C))),
            "خطا در اتصال"
        )
        else -> Triple(
            CyberBlue,
            Brush.radialGradient(listOf(Color(0xFF1E3A66), Color(0xFF0E1C36))),
            "برای اتصال ضربه بزنید"
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(200.dp)
                .testTag("connect_button")
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = 100.dp),
                    onClick = onClick
                )
        ) {
            // Animated Cyber Pulse Ring
            Canvas(
                modifier = Modifier
                    .size(190.dp)
                    .scale(pulseScale)
            ) {
                drawCircle(
                    color = glowColor.copy(alpha = 0.25f),
                    radius = size.minDimension / 2f
                )
            }

            // Orbital Ring drawing
            Canvas(
                modifier = Modifier
                    .size(180.dp)
            ) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(
                            glowColor,
                            CyberViolet,
                            CyberCyan,
                            glowColor
                        )
                    ),
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            // Main Core Button Surface
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .background(mainGradient)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (status == VpnStatus.CONNECTED) Icons.Default.Security else Icons.Default.PowerSettingsNew,
                        contentDescription = "دکمه اتصال VPN",
                        tint = if (status == VpnStatus.CONNECTED) Color.White else CyberCyan,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (status == VpnStatus.CONNECTED) "قطع اتصال" else "اتصال",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = statusLabel,
            color = if (status == VpnStatus.CONNECTED) CyberNeonGreen else Color(0xFFB0BEC5),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
