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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.NearbyDevice
import com.example.model.TransferItem
import com.example.ui.common.NaqlTopBar
import com.example.ui.common.RadarAnimation
import com.example.ui.theme.NaqlCyanPrimary
import com.example.ui.theme.NaqlNavyDark
import com.example.ui.theme.NaqlSuccess

@Composable
fun DiscoveryScreen(
    discoveredDevices: List<NearbyDevice>,
    selectedItemsCount: Int,
    totalBytes: Long,
    selectedEndpoints: Set<String>,
    onToggleDeviceSelection: (NearbyDevice) -> Unit,
    onDeviceSelected: (NearbyDevice) -> Unit,
    onSendToSelectedDevices: (List<NearbyDevice>) -> Unit,
    onBackClick: () -> Unit
) {
    val selectedDevicesList = discoveredDevices.filter { selectedEndpoints.contains(it.endpointId) }

    Scaffold(
        topBar = {
            NaqlTopBar(
                title = "البحث عن أجهزة قريبة",
                subtitle = "NAQL • رادار الاكتشاف المباشر",
                showBrandLogo = true,
                onBackClick = onBackClick
            )
        },
        bottomBar = {
            if (selectedDevicesList.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 16.dp,
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, NaqlCyanPrimary.copy(alpha = 0.35f))
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 680.dp)
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "${selectedDevicesList.size} أجهزة محددة",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "$selectedItemsCount ملفات • ${TransferItem.formatBytes(totalBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NaqlCyanPrimary
                                )
                            }

                            Button(
                                onClick = { onSendToSelectedDevices(selectedDevicesList) },
                                colors = ButtonDefaults.buttonColors(containerColor = NaqlCyanPrimary),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = null,
                                    tint = NaqlNavyDark,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (selectedDevicesList.size > 1) "إرسال للجميع (${selectedDevicesList.size})" else "إرسال الآن",
                                    fontWeight = FontWeight.Bold,
                                    color = NaqlNavyDark
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .padding(horizontal = 20.dp)
                    .testTag("discovery_screen"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                // High-Tech Radar Scanning Animation with Logo
                RadarAnimation(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp),
                    centerContent = {
                        Box(
                            modifier = Modifier
                                .size(74.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(NaqlNavyDark)
                                .border(2.dp, NaqlCyanPrimary, RoundedCornerShape(20.dp))
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_naql_logo),
                                contentDescription = "شعار NAQL",
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "جاري البحث عن أجهزة قريبة…",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "المحدد: $selectedItemsCount ملفات (${TransferItem.formatBytes(totalBytes)})",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = NaqlCyanPrimary
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Discovered Devices List
                if (discoveredDevices.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = NaqlCyanPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "بانتظار ظهور الأجهزة القريبة",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "تأكد من فتح تطبيق NAQL على الهواتف الأخرى واختيار 'استقبال' لجعلها مرئية.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "الأجهزة المتاحة (${discoveredDevices.size})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "يمكنك تحديد أكثر من جهاز",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(discoveredDevices, key = { it.endpointId }) { device ->
                            val isChecked = selectedEndpoints.contains(device.endpointId)
                            DeviceItemCard(
                                device = device,
                                isChecked = isChecked,
                                onToggleCheck = { onToggleDeviceSelection(device) },
                                onConnectDirect = { onDeviceSelected(device) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun DeviceItemCard(
    device: NearbyDevice,
    isChecked: Boolean,
    onToggleCheck: () -> Unit,
    onConnectDirect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleCheck() }
            .testTag("device_item_${device.endpointId}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isChecked)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        border = if (isChecked)
            androidx.compose.foundation.BorderStroke(1.5.dp, NaqlCyanPrimary)
        else
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection Circle Checkbox
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isChecked) NaqlCyanPrimary else Color.Transparent)
                    .border(
                        width = 1.8.dp,
                        color = if (isChecked) NaqlCyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isChecked) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "محدد",
                        tint = NaqlNavyDark,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NaqlCyanPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    tint = NaqlCyanPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(NaqlSuccess)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "جاهز للاستلام المباشر",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = onConnectDirect,
                colors = ButtonDefaults.buttonColors(containerColor = NaqlCyanPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "إرسال",
                    fontWeight = FontWeight.Bold,
                    color = NaqlNavyDark
                )
            }
        }
    }
}
