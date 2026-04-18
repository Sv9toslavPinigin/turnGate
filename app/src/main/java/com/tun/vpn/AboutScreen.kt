package com.tun.vpn

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val theme = LocalTgTheme.current
    val t = LocalStrings.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        TopAppBar(
            title = { Text(t.about, color = theme.textPrimary) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = theme.accent)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // TG monogram instead of shield
            Canvas(modifier = Modifier.size(80.dp)) {
                val s = size.width
                val strokeW = s * 0.104f
                val cx = s / 2f
                val cy = s / 2f
                val gRadius = s * 0.333f
                // G arc
                drawArc(
                    color = theme.accent,
                    startAngle = 0f,
                    sweepAngle = 306f,
                    useCenter = false,
                    topLeft = Offset(cx - gRadius, cy - gRadius),
                    size = Size(gRadius * 2, gRadius * 2),
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
                // G bar
                drawLine(
                    color = theme.accent,
                    start = Offset(cx + gRadius, cy),
                    end = Offset(cx + gRadius * 0.375f, cy),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
                // T crossbar
                drawLine(
                    color = theme.textPrimary.copy(alpha = 0.92f),
                    start = Offset(s * 0.292f, s * 0.292f),
                    end = Offset(s * 0.708f, s * 0.292f),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
                // T stem
                drawLine(
                    color = theme.textPrimary.copy(alpha = 0.92f),
                    start = Offset(cx, s * 0.292f),
                    end = Offset(cx, s * 0.708f),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "TurnGate",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = theme.textPrimary
            )

            Text(
                text = "${t.verPrefix} ${BuildConfig.VERSION_NAME}",
                fontSize = 14.sp,
                color = theme.textSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = t.aboutDesc,
                fontSize = 14.sp,
                color = theme.textSecondary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/cacggghp/vk-turn-proxy")))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(t.srcProxy, color = theme.textPrimary)
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/nullcstring/turnbridge")))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(t.srcBridge, color = theme.textPrimary)
            }
        }
    }
}
