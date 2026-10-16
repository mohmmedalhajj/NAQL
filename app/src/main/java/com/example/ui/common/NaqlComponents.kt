package com.example.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.ConnectionRequest
import com.example.model.FileCategory
import com.example.model.TransferItem
import com.example.ui.theme.NaqlCyanPrimary
import com.example.ui.theme.NaqlError
import com.example.ui.theme.NaqlNavyDark
import com.example.ui.theme.NaqlSuccess
import com.example.ui.theme.NaqlTealSecondary

@Composable
fun NaqlTopBar(
    title: String,
    subtitle: String? = null,
    showBrandLogo: Boolean = true,
    onBackClick: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        shadowElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Navigation Back Button
            if (onBackClick != null) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("top_bar_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            } else {
                Spacer(modifier = Modifier.width(6.dp))
            }

            // Brand Logo (Compact & Crisp)
            if (showBrandLogo) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(NaqlCyanPrimary.copy(alpha = 0.25f), Color(0xFF003852))
                            )
                        )
                        .border(1.dp, NaqlCyanPrimary.copy(alpha = 0.45f), RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_naql_logo),
                        contentDescription = "شعار NAQL",
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(7.dp))
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Title & Subtitle Container (Takes all remaining width, wraps gracefully without clipping or truncation)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        lineHeight = 20.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Start,
                    maxLines = 3,
                    softWrap = true
                )
                if (!subtitle.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = NaqlCyanPrimary,
                        textAlign = TextAlign.Start,
                        maxLines = 2,
                        softWrap = true
                    )
                }
            }

            // Actions Area (Trailing items like search, sort, refresh)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                actions()
            }
        }
    }
}

@Composable
fun BrandBadgeHeader(
    modifier: Modifier = Modifier,
    title: String = "NAQL | نقل",
    subtitle: String = "مشاركة ونقل الملفات مباشرة دون إنترنت"
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    )
                )
            )
            .border(1.dp, NaqlCyanPrimary.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(NaqlNavyDark)
                .border(1.5.dp, NaqlCyanPrimary.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_naql_logo),
                contentDescription = "شعار التطبيق",
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = NaqlCyanPrimary
            )
        }
    }
}

@Composable
fun RadarAnimation(
    modifier: Modifier = Modifier,
    centerContent: @Composable () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar_waves")

    val pulse1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse1"
    )

    val pulse2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, delayMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse2"
    )

    val pulse3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, delayMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse3"
    )

    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val maxRadius = size.minDimension / 2f

            fun drawWave(fraction: Float) {
                val radius = maxRadius * fraction
                val alpha = (1f - fraction).coerceIn(0f, 0.7f)
                drawCircle(
                    color = NaqlCyanPrimary.copy(alpha = alpha * 0.45f),
                    radius = radius,
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }

            // Draw concentric guide circles
            drawCircle(
                color = NaqlCyanPrimary.copy(alpha = 0.08f),
                radius = maxRadius * 0.35f,
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = NaqlCyanPrimary.copy(alpha = 0.08f),
                radius = maxRadius * 0.7f,
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = NaqlCyanPrimary.copy(alpha = 0.08f),
                radius = maxRadius * 0.95f,
                style = Stroke(width = 1.dp.toPx())
            )

            // Dynamic pulses
            drawWave(pulse1)
            drawWave(pulse2)
            drawWave(pulse3)

            // Scanning radar beam sweep
            val angleRad = Math.toRadians(sweepAngle.toDouble())
            val endX = center.x + (maxRadius * 0.95f) * Math.cos(angleRad).toFloat()
            val endY = center.y + (maxRadius * 0.95f) * Math.sin(angleRad).toFloat()

            drawLine(
                brush = Brush.linearGradient(
                    listOf(NaqlCyanPrimary.copy(alpha = 0.8f), Color.Transparent),
                    start = center,
                    end = Offset(endX, endY)
                ),
                start = center,
                end = Offset(endX, endY),
                strokeWidth = 2.dp.toPx()
            )
        }

        centerContent()
    }
}

@Composable
fun FileCategoryIcon(
    category: FileCategory,
    modifier: Modifier = Modifier,
    sizeDp: Int = 42
) {
    val (icon: ImageVector, bgBrush: Brush) = when (category) {
        FileCategory.IMAGE -> Pair(
            Icons.Default.Image,
            Brush.linearGradient(listOf(Color(0xFF00B4D8), Color(0xFF0077B6)))
        )
        FileCategory.VIDEO -> Pair(
            Icons.Default.Movie,
            Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF5B21B6)))
        )
        FileCategory.AUDIO -> Pair(
            Icons.Default.Audiotrack,
            Brush.linearGradient(listOf(Color(0xFFEC4899), Color(0xFF9D174D)))
        )
        FileCategory.DOCUMENT -> Pair(
            Icons.Default.Description,
            Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF065F46)))
        )
        FileCategory.APP -> Pair(
            Icons.Default.Android,
            Brush.linearGradient(listOf(Color(0xFF059669), Color(0xFF047857)))
        )
        else -> Pair(
            Icons.Default.Folder,
            Brush.linearGradient(listOf(Color(0xFF64748B), Color(0xFF334155)))
        )
    }

    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgBrush)
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = category.titleAr,
            tint = Color.White,
            modifier = Modifier.size((sizeDp * 0.52).dp)
        )
    }
}

@Composable
fun StorageProgressBar(
    availableBytes: Long,
    totalBytes: Long,
    modifier: Modifier = Modifier
) {
    val usedBytes = (totalBytes - availableBytes).coerceAtLeast(0L)
    val fraction = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
    val usedPercent = (fraction * 100).toInt()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "سعة التخزين على الجهاز",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "متبقي ${TransferItem.formatBytes(availableBytes)}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = NaqlCyanPrimary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = NaqlCyanPrimary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "مستخدم: $usedPercent%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "إجمالي: ${TransferItem.formatBytes(totalBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ConnectionRequestDialog(
    request: ConnectionRequest,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onReject,
        modifier = Modifier.testTag("connection_request_dialog"),
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(NaqlCyanPrimary.copy(alpha = 0.15f))
                        .border(1.5.dp, NaqlCyanPrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_naql_logo),
                        contentDescription = null,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "طلب استلام ملفات",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "يرغب الجهاز التالي في نقل ملفات إليك:",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NaqlCyanPrimary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = request.endpointName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = NaqlCyanPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "رمز الأمان المشترك:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = request.authenticationDigits,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 4.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = NaqlCyanPrimary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = NaqlSuccess),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("dialog_accept_button")
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("قبول واستقبال", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onReject,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("dialog_reject_button")
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = NaqlError, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("رفض", color = NaqlError, fontWeight = FontWeight.Bold)
            }
        }
    )
}
