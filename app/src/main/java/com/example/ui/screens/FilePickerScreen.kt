package com.example.ui.screens

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.model.FileCategory
import com.example.model.FileSortOption
import com.example.model.FileViewMode
import com.example.model.TransferItem
import com.example.ui.common.NaqlTopBar
import com.example.ui.theme.NaqlCyanPrimary
import com.example.ui.theme.NaqlError
import com.example.ui.theme.NaqlNavyDark
import com.example.ui.theme.NaqlSuccess
import com.example.ui.theme.NaqlTealSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale

@Composable
fun FilePickerScreen(
    currentCategory: FileCategory,
    availableItems: List<TransferItem>,
    selectedItems: Map<String, TransferItem>,
    isLoading: Boolean,
    viewMode: FileViewMode,
    sortOption: FileSortOption,
    searchQuery: String,
    previewItem: TransferItem?,
    hasStoragePermission: Boolean,
    onRequestStoragePermission: () -> Unit,
    onCategorySelected: (FileCategory) -> Unit,
    onViewModeChanged: (FileViewMode) -> Unit,
    onSortOptionChanged: (FileSortOption) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onOpenPreview: (TransferItem) -> Unit,
    onClosePreview: () -> Unit,
    onOpenFile: (TransferItem) -> Unit,
    onItemToggle: (TransferItem) -> Unit,
    onSelectAll: (List<TransferItem>) -> Unit,
    onDeselectAll: () -> Unit,
    onRefresh: () -> Unit,
    onCustomUrisSelected: (List<Uri>) -> Unit,
    onNextClick: () -> Unit,
    onBackClick: () -> Unit
) {
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onCustomUrisSelected(uris)
        }
    }

    var isSearchExpanded by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var isSelectedTrayExpanded by remember { mutableStateOf(false) }

    val selectedCount = selectedItems.size
    val totalSelectedSize = selectedItems.values.sumOf { it.size }

    // Sort Selection Dialog
    if (showSortDialog) {
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            shape = RoundedCornerShape(22.dp),
            title = {
                Text(
                    text = "ترتيب الملفات حسب",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FileSortOption.values().forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onSortOptionChanged(option)
                                    showSortDialog = false
                                }
                                .padding(vertical = 6.dp, horizontal = 8.dp)
                        ) {
                            RadioButton(
                                selected = option == sortOption,
                                onClick = {
                                    onSortOptionChanged(option)
                                    showSortDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = NaqlCyanPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = option.titleAr,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (option == sortOption) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showSortDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = NaqlCyanPrimary)
                ) {
                    Text("تم", color = NaqlNavyDark, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Advanced Detail / Preview Dialog
    if (previewItem != null) {
        EnhancedFilePreviewDialog(
            item = previewItem,
            isSelected = selectedItems.containsKey(previewItem.id),
            onToggleSelect = { onItemToggle(previewItem) },
            onOpenFile = { onOpenFile(previewItem) },
            onDismiss = onClosePreview
        )
    }

    Scaffold(
        topBar = {
            NaqlTopBar(
                title = "تحديد الملفات للإرسال",
                subtitle = "NAQL • تصفح ومشاركة دون إنترنت",
                showBrandLogo = true,
                onBackClick = onBackClick,
                actions = {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "تحديث القائمة",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            isSearchExpanded = !isSearchExpanded
                            if (!isSearchExpanded) onSearchQueryChanged("")
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "بحث",
                            tint = if (searchQuery.isNotEmpty()) NaqlCyanPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { showSortDialog = true },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "ترتيب",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = selectedCount > 0,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 18.dp,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, NaqlCyanPrimary.copy(alpha = 0.35f))
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 680.dp)
                                .animateContentSize()
                        ) {
                            // Expandable Selected Files Chips Tray
                            if (isSelectedTrayExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "الملفات المحددة ($selectedCount)",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        Text(
                                            text = "إلغاء تحديد الكل",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = NaqlError,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable { onDeselectAll() }
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        contentPadding = PaddingValues(vertical = 4.dp)
                                    ) {
                                        items(selectedItems.values.toList(), key = { it.id }) { item ->
                                            SelectedFileChip(
                                                item = item,
                                                onRemove = { onItemToggle(item) }
                                            )
                                        }
                                    }
                                }
                            }

                            // Main Actions Bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { isSelectedTrayExpanded = !isSelectedTrayExpanded }
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "$selectedCount ملفات محددة",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = if (isSelectedTrayExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                            contentDescription = null,
                                            tint = NaqlCyanPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Text(
                                        text = "الحجم الكلي: ${TransferItem.formatBytes(totalSelectedSize)}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = NaqlCyanPrimary
                                    )
                                }

                                Button(
                                    onClick = onNextClick,
                                    colors = ButtonDefaults.buttonColors(containerColor = NaqlCyanPrimary),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.testTag("file_picker_next_button")
                                ) {
                                    Text(
                                        text = "البحث عن أجهزة",
                                        fontWeight = FontWeight.Bold,
                                        color = NaqlNavyDark,
                                        fontSize = 15.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = NaqlNavyDark,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
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
                    .testTag("file_picker_screen")
            ) {
                // Missing Permission Banner
                if (!hasStoragePermission) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "إذن الوصول إلى الملفات غير ممنوح",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "يسمح الإذن بعرض الصور والفيديوهات والتطبيقات مباشرة داخل التطبيق.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = onRequestStoragePermission,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("منح الإذن", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Search Input Field (Animated)
                AnimatedVisibility(visible = isSearchExpanded, enter = fadeIn(), exit = fadeOut()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChanged,
                        placeholder = { Text("ابحث عن أي ملف أو تطبيق بالاسم...") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = NaqlCyanPrimary)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChanged("") }) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "مسح")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 6.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NaqlCyanPrimary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                    )
                }

                // 7 Categories Filter Tabs
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(FileCategory.values()) { category ->
                        val isSelected = category == currentCategory
                        FilterChip(
                            selected = isSelected,
                            onClick = { onCategorySelected(category) },
                            leadingIcon = {
                                Icon(
                                    imageVector = getCategoryVector(category),
                                    contentDescription = null,
                                    tint = if (isSelected) NaqlNavyDark else NaqlCyanPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = category.titleAr,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NaqlCyanPrimary,
                                selectedLabelColor = NaqlNavyDark
                            ),
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                }

                // Controls Bar: View Modes Toggle + Multi-Selection Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Selection Actions
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.clickable { onSelectAll(availableItems) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SelectAll,
                                    contentDescription = null,
                                    tint = NaqlCyanPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "تحديد الكل",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }

                        if (selectedCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.clickable { onDeselectAll() }
                            ) {
                                Text(
                                    text = "إلغاء التحديد",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = NaqlCyanPrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // 3 View Modes Toggle Group
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ViewModeIconButton(
                            icon = Icons.AutoMirrored.Filled.ViewList,
                            label = "قائمة",
                            isSelected = viewMode == FileViewMode.LIST,
                            onClick = { onViewModeChanged(FileViewMode.LIST) }
                        )
                        ViewModeIconButton(
                            icon = Icons.Default.TableRows,
                            label = "تفصيلية",
                            isSelected = viewMode == FileViewMode.DETAILED_LIST,
                            onClick = { onViewModeChanged(FileViewMode.DETAILED_LIST) }
                        )
                        ViewModeIconButton(
                            icon = Icons.Default.GridView,
                            label = "شبكة",
                            isSelected = viewMode == FileViewMode.GRID,
                            onClick = { onViewModeChanged(FileViewMode.GRID) }
                        )
                    }
                }

                // Storage Access Framework (SAF) Custom File Picker Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 4.dp)
                        .clickable { filePickerLauncher.launch(arrayOf("*/*")) }
                        .testTag("file_picker_saf_browse_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    border = BorderStroke(1.dp, NaqlCyanPrimary.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(NaqlCyanPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = NaqlCyanPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "استعراض من ذاكرة الهاتف (SAF)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "اختر أي ملفات من الذاكرة الداخلية أو بطاقة SD",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = NaqlCyanPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Items Content View
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = NaqlCyanPrimary)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "جارٍ جلب الملفات من ذاكرة الهاتف...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (availableItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(28.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = getCategoryVector(currentCategory),
                                    contentDescription = null,
                                    tint = NaqlCyanPrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = if (searchQuery.isNotEmpty())
                                    "لا توجد نتائج تطابق '$searchQuery'"
                                else
                                    "لا توجد ملفات في قسم ${currentCategory.titleAr}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "يمكنك استعراض ملفات أخرى من ذاكرة الهاتف مباشرة عبر مدير الملفات",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = onRefresh,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("تحديث")
                                }

                                Button(
                                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                                    colors = ButtonDefaults.buttonColors(containerColor = NaqlCyanPrimary),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, tint = NaqlNavyDark, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("استعراض الملفات", color = NaqlNavyDark, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    when (viewMode) {
                        FileViewMode.LIST -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(availableItems, key = { it.id }) { item ->
                                    val isSelected = selectedItems.containsKey(item.id)
                                    FileItemListRow(
                                        item = item,
                                        isSelected = isSelected,
                                        onToggle = { onItemToggle(item) },
                                        onPreview = { onOpenPreview(item) }
                                    )
                                }
                            }
                        }

                        FileViewMode.DETAILED_LIST -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(availableItems, key = { it.id }) { item ->
                                    val isSelected = selectedItems.containsKey(item.id)
                                    FileItemDetailedRow(
                                        item = item,
                                        isSelected = isSelected,
                                        onToggle = { onItemToggle(item) },
                                        onPreview = { onOpenPreview(item) }
                                    )
                                }
                            }
                        }

                        FileViewMode.GRID -> {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 110.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(availableItems, key = { it.id }) { item ->
                                    val isSelected = selectedItems.containsKey(item.id)
                                    FileItemGridCard(
                                        item = item,
                                        isSelected = isSelected,
                                        onToggle = { onItemToggle(item) },
                                        onPreview = { onOpenPreview(item) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Chip in expandable selected tray
@Composable
fun SelectedFileChip(
    item: TransferItem,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, NaqlCyanPrimary.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileThumbnailOrIcon(item = item, sizeDp = 26)
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 100.dp)
                )
                Text(
                    text = item.formattedSize,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = NaqlCyanPrimary
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onRemove() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "حذف",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
fun ViewModeIconButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) NaqlCyanPrimary else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) NaqlNavyDark else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

// 1. Standard List Row
@Composable
fun FileItemListRow(
    item: TransferItem,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onPreview: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = if (isSelected)
            BorderStroke(1.5.dp, NaqlCyanPrimary)
        else
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.clickable { onPreview() }) {
                FileThumbnailOrIcon(item = item, sizeDp = 44)
            }
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = item.formattedSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (item.formattedDuration.isNotEmpty()) {
                        Text(
                            text = "• ${item.formattedDuration}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NaqlSuccess
                        )
                    }
                }
            }

            IconButton(onClick = onPreview, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "معاينة وتفاصيل",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(19.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))
            SelectionCheckbox(isSelected = isSelected)
        }
    }
}

// 2. Detailed List Row
@Composable
fun FileItemDetailedRow(
    item: TransferItem,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onPreview: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = if (isSelected)
            BorderStroke(1.5.dp, NaqlCyanPrimary)
        else
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.clickable { onPreview() }) {
                FileThumbnailOrIcon(item = item, sizeDp = 50)
            }
            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = NaqlCyanPrimary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = item.fileExtension,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                            color = NaqlCyanPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = item.formattedSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (item.formattedDate.isNotEmpty()) {
                        Text(
                            text = "• ${item.formattedDate}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    if (item.formattedDuration.isNotEmpty()) {
                        Text(
                            text = "• ${item.formattedDuration}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NaqlSuccess
                        )
                    }
                }
            }

            IconButton(onClick = onPreview, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "معاينة وتفاصيل",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))
            SelectionCheckbox(isSelected = isSelected)
        }
    }
}

// 3. Grid Card
@Composable
fun FileItemGridCard(
    item: TransferItem,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onPreview: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = if (isSelected)
            BorderStroke(1.5.dp, NaqlCyanPrimary)
        else
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.TopEnd
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onPreview() },
                    contentAlignment = Alignment.Center
                ) {
                    FileThumbnailOrIcon(item = item, sizeDp = 76, isLarge = true)
                }

                // Checkbox pinned at top right
                Box(modifier = Modifier.padding(4.dp)) {
                    SelectionCheckbox(isSelected = isSelected)
                }

                // Extension badge pinned at bottom left
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.65f)
                    ) {
                        Text(
                            text = item.fileExtension,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                // Video Duration pill if video
                if (item.category == FileCategory.VIDEO && item.formattedDuration.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Black.copy(alpha = 0.65f)
                        ) {
                            Text(
                                text = item.formattedDuration,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                color = NaqlCyanPrimary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = item.name,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Text(
                text = item.formattedSize,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Thumbnail, App Icon or Custom Document Type Icon
@Composable
fun FileThumbnailOrIcon(item: TransferItem, sizeDp: Int, isLarge: Boolean = false) {
    val context = LocalContext.current

    if (item.category == FileCategory.IMAGE && item.uri != null) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
        )
    } else if (item.category == FileCategory.VIDEO && (item.uri != null || item.localPath != null)) {
        Box(
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            val videoModel = remember(item.uri, item.localPath) {
                val builder = ImageRequest.Builder(context)
                if (item.uri != null) {
                    builder.data(item.uri)
                } else if (!item.localPath.isNullOrEmpty()) {
                    builder.data(File(item.localPath))
                }
                builder.videoFrameMillis(1000L)
                builder.decoderFactory(VideoFrameDecoder.Factory())
                builder.crossfade(true)
                builder.build()
            }
            AsyncImage(
                model = videoModel,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            )
            Box(
                modifier = Modifier
                    .size((sizeDp * 0.42).dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size((sizeDp * 0.28).dp)
                )
            }
        }
    } else if (item.category == FileCategory.APP && !item.appPackageName.isNullOrEmpty()) {
        val appDrawable = remember(item.appPackageName) {
            try {
                context.packageManager.getApplicationIcon(item.appPackageName)
            } catch (e: Exception) {
                null
            }
        }

        if (appDrawable != null) {
            val bitmap = remember(appDrawable) { appDrawable.toBitmap() }
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = item.name,
                modifier = Modifier
                    .size(sizeDp.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        } else {
            SpecificDocumentOrCategoryIcon(item = item, sizeDp = sizeDp)
        }
    } else {
        SpecificDocumentOrCategoryIcon(item = item, sizeDp = sizeDp)
    }
}

// Stylized Color-Coded Icon for Specific Documents & Media
@Composable
fun SpecificDocumentOrCategoryIcon(item: TransferItem, sizeDp: Int) {
    val ext = item.fileExtension.uppercase(Locale.ROOT)
    val (icon: ImageVector, bgBrush: Brush, badgeText: String) = when {
        item.category == FileCategory.IMAGE -> Triple(
            Icons.Default.Image,
            Brush.linearGradient(listOf(Color(0xFF00B4D8), Color(0xFF0077B6))),
            ext
        )
        item.category == FileCategory.VIDEO -> Triple(
            Icons.Default.Movie,
            Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF5B21B6))),
            ext
        )
        item.category == FileCategory.AUDIO -> Triple(
            Icons.Default.Audiotrack,
            Brush.linearGradient(listOf(Color(0xFFEC4899), Color(0xFF9D174D))),
            ext
        )
        item.category == FileCategory.APP || ext == "APK" -> Triple(
            Icons.Default.Android,
            Brush.linearGradient(listOf(Color(0xFF059669), Color(0xFF047857))),
            "APK"
        )
        ext == "PDF" -> Triple(
            Icons.Default.Description,
            Brush.linearGradient(listOf(Color(0xFFDC2626), Color(0xFF991B1B))),
            "PDF"
        )
        ext == "DOC" || ext == "DOCX" -> Triple(
            Icons.Default.Description,
            Brush.linearGradient(listOf(Color(0xFF2563EB), Color(0xFF1D4ED8))),
            "DOC"
        )
        ext == "XLS" || ext == "XLSX" || ext == "CSV" -> Triple(
            Icons.Default.TableRows,
            Brush.linearGradient(listOf(Color(0xFF16A34A), Color(0xFF15803D))),
            "XLS"
        )
        ext == "PPT" || ext == "PPTX" -> Triple(
            Icons.Default.Description,
            Brush.linearGradient(listOf(Color(0xFFEA580C), Color(0xFFC2410C))),
            "PPT"
        )
        ext == "ZIP" || ext == "RAR" || ext == "7Z" || ext == "TAR" || ext == "GZ" -> Triple(
            Icons.Default.Folder,
            Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFF6D28D9))),
            "ZIP"
        )
        ext == "TXT" || ext == "LOG" || ext == "JSON" || ext == "XML" -> Triple(
            Icons.Default.Description,
            Brush.linearGradient(listOf(Color(0xFF0891B2), Color(0xFF0E7490))),
            "TXT"
        )
        else -> Triple(
            Icons.Default.Folder,
            Brush.linearGradient(listOf(Color(0xFF475569), Color(0xFF334155))),
            if (ext.length <= 4) ext else "FILE"
        )
    }

    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgBrush)
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size((sizeDp * 0.44).dp)
            )
            if (sizeDp >= 40 && badgeText.isNotEmpty()) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = (sizeDp * 0.17).coerceAtLeast(8.0).sp
                    ),
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
fun SelectionCheckbox(isSelected: Boolean) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (isSelected) NaqlCyanPrimary else Color.Black.copy(alpha = 0.3f))
            .border(
                width = 1.8.dp,
                color = if (isSelected) NaqlCyanPrimary else Color.White.copy(alpha = 0.6f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "محدد",
                tint = NaqlNavyDark,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

// Enhanced In-App Preview & Inspection Dialog
@Composable
fun EnhancedFilePreviewDialog(
    item: TransferItem,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onOpenFile: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var calculatedSha256 by remember { mutableStateOf(item.sha256Checksum) }
    var isCalculatingHash by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "معاينة وتفاصيل الملف",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = NaqlCyanPrimary.copy(alpha = 0.18f)
                ) {
                    Text(
                        text = "${item.category.titleAr} • ${item.fileExtension}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = NaqlCyanPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Media / Thumbnail Banner
                if (item.category == FileCategory.IMAGE && item.uri != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = item.uri,
                            contentDescription = item.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else if (item.category == FileCategory.VIDEO && (item.uri != null || item.localPath != null)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.35f))
                            .clickable { onOpenFile() },
                        contentAlignment = Alignment.Center
                    ) {
                        val videoPreviewModel = remember(item.uri, item.localPath) {
                            val builder = ImageRequest.Builder(context)
                            if (item.uri != null) {
                                builder.data(item.uri)
                            } else if (!item.localPath.isNullOrEmpty()) {
                                builder.data(File(item.localPath))
                            }
                            builder.videoFrameMillis(1000L)
                            builder.decoderFactory(VideoFrameDecoder.Factory())
                            builder.crossfade(true)
                            builder.build()
                        }
                        AsyncImage(
                            model = videoPreviewModel,
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.65f))
                                .border(1.5.dp, NaqlCyanPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "تشغيل",
                                tint = NaqlCyanPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        FileThumbnailOrIcon(item = item, sizeDp = 76)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Metadata Details Box
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PreviewDetailRow(label = "الحجم الكلي", value = "${item.formattedSize} (${item.size} بايت)")
                        PreviewDetailRow(label = "النوع والصيغة", value = "${item.category.titleAr} (${item.fileExtension})")
                        if (item.formattedDate.isNotEmpty()) {
                            PreviewDetailRow(label = "تاريخ التعديل", value = item.formattedDate)
                        }
                        if (item.formattedDuration.isNotEmpty()) {
                            PreviewDetailRow(label = "مدة المقطع", value = item.formattedDuration)
                        }
                        if (!item.appPackageName.isNullOrEmpty()) {
                            PreviewDetailRow(label = "اسم الحزمة", value = item.appPackageName)
                        }

                        // SHA-256 Hash Display
                        if (calculatedSha256.isNotEmpty()) {
                            PreviewDetailRow(label = "بصمة SHA-256", value = calculatedSha256.take(16) + "...")
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "فحص بصمة الملف",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (isCalculatingHash) "جارٍ الحساب..." else "حساب SHA-256",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = NaqlCyanPrimary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            if (!isCalculatingHash) {
                                                isCalculatingHash = true
                                                coroutineScope.launch {
                                                    val hash = computeItemHash(item)
                                                    calculatedSha256 = hash
                                                    isCalculatingHash = false
                                                }
                                            }
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Open with System Viewer Button
                OutlinedButton(
                    onClick = onOpenFile,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (item.category) {
                            FileCategory.IMAGE -> "فتح في عارض الصور"
                            FileCategory.VIDEO -> "تشغيل في مشغل الفيديو"
                            FileCategory.AUDIO -> "تشغيل المقطع الصوتي"
                            FileCategory.APP -> "فحص وتثبيت ملف APK"
                            FileCategory.DOCUMENT -> "فتح وقراءة المستند"
                            else -> "فتح الملف في التطبيق المناسب"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onToggleSelect()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.error else NaqlCyanPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (isSelected) "إلغاء تحديد هذا الملف" else "تحديد هذا الملف للإرسال",
                    color = if (isSelected) Color.White else NaqlNavyDark,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text("إغلاق")
            }
        }
    )
}

suspend fun computeItemHash(item: TransferItem): String = withContext(Dispatchers.IO) {
    try {
        val digest = MessageDigest.getInstance("SHA-256")
        if (item.localPath != null) {
            val file = File(item.localPath)
            if (file.exists()) {
                file.inputStream().use { stream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (stream.read(buffer).also { bytesRead = it } != -1) {
                        digest.update(buffer, 0, bytesRead)
                    }
                }
                val mdBytes = digest.digest()
                val sb = StringBuilder()
                for (b in mdBytes) {
                    sb.append(String.format(Locale.US, "%02x", b))
                }
                return@withContext sb.toString()
            }
        }
        ""
    } catch (e: Exception) {
        ""
    }
}

@Composable
fun PreviewDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

fun getCategoryVector(category: FileCategory): ImageVector {
    return when (category) {
        FileCategory.ALL -> Icons.Default.FolderOpen
        FileCategory.IMAGE -> Icons.Default.Image
        FileCategory.VIDEO -> Icons.Default.Movie
        FileCategory.AUDIO -> Icons.Default.Audiotrack
        FileCategory.DOCUMENT -> Icons.Default.Description
        FileCategory.APP -> Icons.Default.Android
        FileCategory.OTHER -> Icons.Default.Folder
    }
}
