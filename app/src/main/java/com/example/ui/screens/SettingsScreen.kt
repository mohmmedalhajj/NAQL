package com.example.ui.screens

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.TransferItem
import com.example.ui.common.NaqlTopBar
import com.example.ui.common.StorageProgressBar
import com.example.ui.theme.NaqlCyanPrimary
import com.example.ui.theme.NaqlError
import com.example.ui.theme.NaqlNavyDark
import com.example.ui.theme.NaqlSuccess

@Composable
fun SettingsScreen(
    currentDeviceName: String,
    availableStorage: Long,
    totalStorage: Long,
    storagePath: String,
    missingPermissions: List<String>,
    onSaveDeviceName: (String) -> Unit,
    onRequestPermissions: () -> Unit,
    onBackClick: () -> Unit
) {
    var deviceNameInput by remember { mutableStateOf(currentDeviceName) }
    var isSavedSnackbar by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            NaqlTopBar(
                title = "الإعدادات والصلاحيات",
                subtitle = "NAQL • إعدادات التطبيق",
                showBrandLogo = true,
                onBackClick = onBackClick
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .padding(horizontal = 20.dp)
                    .testTag("settings_screen"),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(6.dp))

                    // Brand Overview Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Brush.horizontalGradient(
                                listOf(NaqlCyanPrimary.copy(alpha = 0.4f), Color.Transparent)
                            )
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(NaqlNavyDark)
                                    .border(2.dp, NaqlCyanPrimary, RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_naql_logo),
                                    contentDescription = "شعار NAQL",
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column {
                                Text(
                                    text = "NAQL | نقل",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "الإصدار 1.0 • نقل فائق السرعة دون إنترنت",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NaqlCyanPrimary
                                )
                            }
                        }
                    }
                }

                // Device Name Customization
                item {
                    Text(
                        text = "اسم الجهاز",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            OutlinedTextField(
                                value = deviceNameInput,
                                onValueChange = {
                                    deviceNameInput = it
                                    isSavedSnackbar = false
                                },
                                label = { Text("اسم الجهاز الذي يظهر في رادار الهواتف القريبة") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_device_name_input"),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSavedSnackbar) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = NaqlSuccess,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "تم الحفظ بنجاح",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NaqlSuccess
                                        )
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }

                                Button(
                                    onClick = {
                                        onSaveDeviceName(deviceNameInput)
                                        isSavedSnackbar = true
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = NaqlCyanPrimary),
                                    modifier = Modifier.testTag("settings_save_name_button")
                                ) {
                                    Text("حفظ الاسم", color = NaqlNavyDark, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Storage Details
                item {
                    Text(
                        text = "مساحة التخزين ومجلد الحفظ",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            StorageProgressBar(
                                availableBytes = availableStorage,
                                totalBytes = totalStorage
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(NaqlCyanPrimary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = NaqlCyanPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "مجلد حفظ الملفات المستلمة:",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = storagePath,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // Permissions Checklist
                item {
                    Text(
                        text = "صلاحيات الاتصال المباشر",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            PermissionStatusRow(
                                title = "البلوتوث (Bluetooth)",
                                subtitle = "لاكتشاف الأجهزة القريبة والتفاوض",
                                isGranted = !missingPermissions.any { it.contains("BLUETOOTH", ignoreCase = true) }
                            )

                            PermissionStatusRow(
                                title = "الأجهزة القريبة و Wi-Fi Direct",
                                subtitle = "لنقل الملفات الكبيرة بسرعة فائقة",
                                isGranted = !missingPermissions.any { it.contains("WIFI", ignoreCase = true) }
                            )

                            PermissionStatusRow(
                                title = "الموقع الجغرافي (Location)",
                                subtitle = "مطلوب بنظام أندرويد لاكتشاف الشبكات المحلية",
                                isGranted = !missingPermissions.any { it.contains("LOCATION", ignoreCase = true) }
                            )

                            if (missingPermissions.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = onRequestPermissions,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("settings_grant_permissions_button"),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = NaqlCyanPrimary)
                                ) {
                                    Text("منح الصلاحيات المطلوبة", color = NaqlNavyDark, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // 100% Offline Security & Privacy Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NaqlCyanPrimary.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NaqlCyanPrimary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = NaqlCyanPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "خصوصية مطلقة دون أي اتصال بالإنترنت",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "تطبيق NAQL ينقل الملفات مباشرة نقطة-إلى-نقطة (P2P) بين الهاتفين عبر موجات الراديو المحلية (Wi-Fi Direct / BLE) دون مرور أي بايت عبر الإنترنت أو الخوادم السحابية.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun PermissionStatusRow(
    title: String,
    subtitle: String,
    isGranted: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (isGranted) NaqlSuccess.copy(alpha = 0.15f) else NaqlError.copy(alpha = 0.15f))
                .border(
                    1.dp,
                    if (isGranted) NaqlSuccess.copy(alpha = 0.4f) else NaqlError.copy(alpha = 0.4f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = if (isGranted) NaqlSuccess else NaqlError,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
