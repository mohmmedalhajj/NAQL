package com.example.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.example.model.FileCategory
import com.example.model.TransferItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class FileManager(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver
    private val categoryCache = ConcurrentHashMap<FileCategory, List<TransferItem>>()

    fun getCachedItems(category: FileCategory): List<TransferItem>? = categoryCache[category]

    fun putCachedItems(category: FileCategory, items: List<TransferItem>) {
        categoryCache[category] = items
    }

    fun invalidateCache(category: FileCategory? = null) {
        if (category == null) {
            categoryCache.clear()
        } else {
            categoryCache.remove(category)
            categoryCache.remove(FileCategory.ALL)
        }
    }

    fun onNewFileReceived(file: File) {
        try {
            if (!file.exists() || !file.canRead()) return
            val mime = getMimeType(file)
            val cat = determineCategory(file.name, mime)
            val item = TransferItem(
                id = "local_${file.absolutePath.hashCode()}",
                name = file.name,
                size = file.length(),
                mimeType = mime,
                category = cat,
                uri = Uri.fromFile(file),
                localPath = file.absolutePath,
                dateModified = file.lastModified()
            )

            val catList = (categoryCache[cat] ?: emptyList()).toMutableList()
            catList.removeAll { it.name == item.name && it.size == item.size }
            catList.add(0, item)
            categoryCache[cat] = catList

            val allList = (categoryCache[FileCategory.ALL] ?: emptyList()).toMutableList()
            allList.removeAll { it.name == item.name && it.size == item.size }
            allList.add(0, item)
            categoryCache[FileCategory.ALL] = allList
        } catch (_: Exception) {}
    }

    fun getReceivedFilesDir(): File {
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val naqlPublic = File(publicDownloads, "NAQL")
        if (naqlPublic.exists() || naqlPublic.mkdirs()) {
            return naqlPublic
        }
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        val naqlDir = File(baseDir, "NAQL_Files")
        if (!naqlDir.exists()) {
            naqlDir.mkdirs()
        }
        return naqlDir
    }

    fun getAvailableStorageBytes(): Long {
        return try {
            val stat = StatFs(getReceivedFilesDir().path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            0L
        }
    }

    fun getTotalStorageBytes(): Long {
        return try {
            val stat = StatFs(getReceivedFilesDir().path)
            stat.blockCountLong * stat.blockSizeLong
        } catch (e: Exception) {
            0L
        }
    }

    suspend fun calculateSha256(file: File): String = withContext(Dispatchers.IO) {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val mdBytes = digest.digest()
            val sb = StringBuilder()
            for (b in mdBytes) {
                sb.append(String.format(Locale.US, "%02x", b))
            }
            sb.toString()
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun calculateSha256(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            } ?: return@withContext ""
            val mdBytes = digest.digest()
            val sb = StringBuilder()
            for (b in mdBytes) {
                sb.append(String.format(Locale.US, "%02x", b))
            }
            sb.toString()
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun getTransferItemFromUri(uri: Uri): TransferItem? = withContext(Dispatchers.IO) {
        var name = "file_${System.currentTimeMillis()}"
        var size = 0L
        var mimeType = contentResolver.getType(uri) ?: "*/*"
        var dateModified = System.currentTimeMillis()

        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex) ?: name
                    }
                    if (sizeIndex != -1) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            // fallback
        }

        if (size <= 0L) {
            try {
                contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    size = pfd.statSize
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        val category = determineCategory(name, mimeType)
        val sha256 = calculateSha256(uri)

        TransferItem(
            id = UUID.randomUUID().toString(),
            name = name,
            size = size,
            mimeType = mimeType,
            category = category,
            uri = uri,
            sha256Checksum = sha256,
            dateModified = dateModified
        )
    }

    fun isFileAccessible(item: TransferItem): Boolean {
        return try {
            when {
                item.uri != null -> {
                    contentResolver.openFileDescriptor(item.uri, "r")?.use { true } ?: false
                }
                item.localPath != null -> {
                    val file = File(item.localPath)
                    file.exists() && file.canRead()
                }
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun determineCategory(name: String, mimeType: String): FileCategory {
        val lowerMime = mimeType.lowercase(Locale.ROOT)
        val lowerName = name.lowercase(Locale.ROOT)
        val ext = lowerName.substringAfterLast('.', "")

        // 1. Images
        if (lowerMime.startsWith("image/") || ext in setOf(
                "jpg", "jpeg", "png", "webp", "gif", "bmp", "svg",
                "heic", "heif", "ico", "tiff", "tif", "raw", "dng", "cr2", "nef"
            )
        ) {
            return FileCategory.IMAGE
        }

        // 2. Videos
        if (lowerMime.startsWith("video/") || ext in setOf(
                "mp4", "mkv", "avi", "mov", "3gp", "webm", "flv", "wmv",
                "m4v", "ts", "mpeg", "mpg", "vob", "ogv", "m2ts"
            )
        ) {
            return FileCategory.VIDEO
        }

        // 3. Audio
        if (lowerMime.startsWith("audio/") || ext in setOf(
                "mp3", "wav", "m4a", "flac", "aac", "ogg", "opus", "wma",
                "amr", "mid", "midi", "aiff", "m4b", "m4p"
            )
        ) {
            return FileCategory.AUDIO
        }

        // 4. Apps / APK files
        if (ext in setOf("apk", "xapk", "apks") || lowerMime.contains("android.package-archive")) {
            return FileCategory.APP
        }

        // 5. Documents (Comprehensive office, text, sheets, presentations, ebooks)
        val isDocMime = lowerMime.contains("pdf") ||
                lowerMime.contains("document") ||
                lowerMime.contains("text/") ||
                lowerMime.contains("msword") ||
                lowerMime.contains("sheet") ||
                lowerMime.contains("presentation") ||
                lowerMime.contains("officedocument") ||
                lowerMime.contains("opendocument") ||
                lowerMime.contains("epub") ||
                lowerMime.contains("rtf") ||
                lowerMime.contains("csv")
        val isDocExt = ext in setOf(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "rtf", "csv", "odt", "ods", "odp", "epub",
            "md", "tex", "pages", "numbers", "key", "tsv", "log", "mobi", "djvu"
        )
        if (isDocMime || isDocExt) {
            return FileCategory.DOCUMENT
        }

        // 6. Otherwise -> FileCategory.OTHER (ZIP, RAR, 7Z, ISO, BIN, JSON, XML, fonts, unknown)
        return FileCategory.OTHER
    }

    fun getAppIcon(packageName: String): Drawable? {
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getInstalledApps(): List<TransferItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<TransferItem>()
        val seenPackages = mutableSetOf<String>()
        val seenPaths = mutableSetOf<String>()
        try {
            val pm = context.packageManager
            // 1. Query all launcher apps
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val launchableApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(mainIntent, 0)
            }

            for (resolveInfo in launchableApps) {
                val appInfo = resolveInfo.activityInfo?.applicationInfo ?: continue
                if (appInfo.packageName == context.packageName) continue
                if (seenPackages.contains(appInfo.packageName)) continue

                val appFile = File(appInfo.sourceDir)
                if (appFile.exists() && appFile.length() > 0 && appFile.canRead()) {
                    val appLabel = resolveInfo.loadLabel(pm).toString()
                    val fileName = "${appLabel.replace(" ", "_").replace("/", "_")}.apk"
                    seenPackages.add(appInfo.packageName)
                    seenPaths.add(appFile.absolutePath)
                    list.add(
                        TransferItem(
                            id = "app_${appInfo.packageName}",
                            name = fileName,
                            size = appFile.length(),
                            mimeType = "application/vnd.android.package-archive",
                            category = FileCategory.APP,
                            uri = Uri.fromFile(appFile),
                            localPath = appFile.absolutePath,
                            dateModified = appFile.lastModified(),
                            appPackageName = appInfo.packageName
                        )
                    )
                }
            }

            // 2. Query other installed non-system applications
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
            }

            for (appInfo in packages) {
                if (seenPackages.contains(appInfo.packageName)) continue
                if (appInfo.packageName == context.packageName) continue

                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                if (!isSystem || isUpdatedSystem) {
                    val appFile = File(appInfo.sourceDir)
                    if (appFile.exists() && appFile.length() > 0 && appFile.canRead()) {
                        val appLabel = pm.getApplicationLabel(appInfo).toString()
                        val fileName = "${appLabel.replace(" ", "_").replace("/", "_")}.apk"
                        seenPackages.add(appInfo.packageName)
                        seenPaths.add(appFile.absolutePath)
                        list.add(
                            TransferItem(
                                id = "app_${appInfo.packageName}",
                                name = fileName,
                                size = appFile.length(),
                                mimeType = "application/vnd.android.package-archive",
                                category = FileCategory.APP,
                                uri = Uri.fromFile(appFile),
                                localPath = appFile.absolutePath,
                                dateModified = appFile.lastModified(),
                                appPackageName = appInfo.packageName
                            )
                        )
                    }
                }
            }

            // 3. Scan standalone APK files from Downloads, Documents, and NAQL received folder
            try {
                val searchDirs = listOfNotNull(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    getReceivedFilesDir()
                )
                for (dir in searchDirs) {
                    if (dir.exists() && dir.isDirectory) {
                        dir.walkTopDown().maxDepth(2).filter { it.isFile && it.extension.equals("apk", ignoreCase = true) && it.canRead() }.forEach { apkFile ->
                            if (!seenPaths.contains(apkFile.absolutePath)) {
                                seenPaths.add(apkFile.absolutePath)
                                list.add(
                                    TransferItem(
                                        id = "local_apk_${apkFile.absolutePath.hashCode()}",
                                        name = apkFile.name,
                                        size = apkFile.length(),
                                        mimeType = "application/vnd.android.package-archive",
                                        category = FileCategory.APP,
                                        uri = Uri.fromFile(apkFile),
                                        localPath = apkFile.absolutePath,
                                        dateModified = apkFile.lastModified()
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        } catch (e: Exception) {
            // Log or ignore
        }
        list.sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    suspend fun getMediaImages(): List<TransferItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<TransferItem>()
        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.DATE_MODIFIED
            )
            val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)

                var count = 0
                while (cursor.moveToNext() && count < 250) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "image_$id.jpg"
                    val size = cursor.getLong(sizeCol)
                    val mime = cursor.getString(mimeCol) ?: "image/jpeg"
                    val dateSec = cursor.getLong(dateCol)
                    val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())

                    if (size > 0) {
                        list.add(
                            TransferItem(
                                id = "img_$id",
                                name = name,
                                size = size,
                                mimeType = mime,
                                category = FileCategory.IMAGE,
                                uri = uri,
                                dateModified = dateSec * 1000L
                            )
                        )
                        count++
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        list
    }

    suspend fun getMediaVideos(): List<TransferItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<TransferItem>()
        val seenPaths = mutableSetOf<String>()
        try {
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.DATE_MODIFIED,
                MediaStore.Video.Media.DURATION
            )
            val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
            contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val mimeCol = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
                val dateCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)

                var count = 0
                while (cursor.moveToNext() && count < 1500) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "video_$id.mp4"
                    val size = cursor.getLong(sizeCol)
                    val mime = if (mimeCol != -1) cursor.getString(mimeCol) ?: "video/mp4" else "video/mp4"
                    val dateSec = if (dateCol != -1) cursor.getLong(dateCol) else 0L
                    val durationMs = if (durCol != -1) cursor.getLong(durCol) else 0L
                    val uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString())

                    if (size > 0) {
                        seenPaths.add(name + "_" + size)
                        list.add(
                            TransferItem(
                                id = "vid_$id",
                                name = name,
                                size = size,
                                mimeType = mime,
                                category = FileCategory.VIDEO,
                                uri = uri,
                                dateModified = dateSec * 1000L,
                                durationMs = durationMs
                            )
                        )
                        count++
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        // Also scan video files from public Movies, DCIM, Downloads, NAQL folders
        try {
            val videoDirs = listOfNotNull(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                getReceivedFilesDir()
            )
            for (dir in videoDirs) {
                if (dir.exists()) {
                    val scanned = scanDirectoryForFiles(dir, FileCategory.VIDEO)
                    for (item in scanned) {
                        if (!seenPaths.contains(item.name + "_" + item.size)) {
                            seenPaths.add(item.name + "_" + item.size)
                            list.add(item)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        list.distinctBy { it.name + "_" + it.size }.sortedByDescending { it.dateModified }
    }

    suspend fun getMediaAudio(): List<TransferItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<TransferItem>()
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.DURATION
            )
            val sortOrder = "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val dateCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
                val durCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)

                var count = 0
                while (cursor.moveToNext() && count < 1500) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "audio_$id.mp3"
                    val size = cursor.getLong(sizeCol)
                    val mime = if (mimeCol != -1) cursor.getString(mimeCol) ?: "audio/mpeg" else "audio/mpeg"
                    val dateSec = if (dateCol != -1) cursor.getLong(dateCol) else 0L
                    val durationMs = if (durCol != -1) cursor.getLong(durCol) else 0L
                    val uri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString())

                    if (size > 0) {
                        list.add(
                            TransferItem(
                                id = "aud_$id",
                                name = name,
                                size = size,
                                mimeType = mime,
                                category = FileCategory.AUDIO,
                                uri = uri,
                                dateModified = dateSec * 1000L,
                                durationMs = durationMs
                            )
                        )
                        count++
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        list.distinctBy { it.name + "_" + it.size }.sortedByDescending { it.dateModified }
    }

    private fun scanDirectoryForFiles(dir: File, targetCategory: FileCategory, maxDepth: Int = 3): List<TransferItem> {
        val results = mutableListOf<TransferItem>()
        if (!dir.exists() || !dir.canRead() || !dir.isDirectory) return results

        fun walk(current: File, depth: Int) {
            if (depth > maxDepth) return
            val files = current.listFiles() ?: return
            for (file in files) {
                if (file.name.startsWith(".")) continue
                if (file.isDirectory) {
                    walk(file, depth + 1)
                } else if (file.isFile && file.length() > 0 && file.canRead()) {
                    val mime = getMimeType(file)
                    val cat = determineCategory(file.name, mime)
                    if (targetCategory == FileCategory.ALL || cat == targetCategory) {
                        results.add(
                            TransferItem(
                                id = "local_${file.absolutePath.hashCode()}",
                                name = file.name,
                                size = file.length(),
                                mimeType = mime,
                                category = cat,
                                uri = Uri.fromFile(file),
                                localPath = file.absolutePath,
                                dateModified = file.lastModified()
                            )
                        )
                    }
                }
            }
        }
        walk(dir, 0)
        return results
    }

    suspend fun getMediaDocuments(): List<TransferItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<TransferItem>()
        val seenPaths = mutableSetOf<String>()
        try {
            val filesUri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.DATE_MODIFIED
            )

            val selection = "${MediaStore.Files.FileColumns.SIZE} > 0"
            val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

            contentResolver.query(
                filesUri,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val mimeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                val dateCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)

                var count = 0
                while (cursor.moveToNext() && count < 1500) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: ""
                    val size = cursor.getLong(sizeCol)
                    val mime = if (mimeCol != -1) cursor.getString(mimeCol) ?: "" else ""
                    val dateSec = if (dateCol != -1) cursor.getLong(dateCol) else 0L

                    val cat = determineCategory(name, mime)
                    if (cat == FileCategory.DOCUMENT) {
                        val uri = Uri.withAppendedPath(filesUri, id.toString())
                        seenPaths.add(name + "_" + size)
                        list.add(
                            TransferItem(
                                id = "doc_$id",
                                name = name,
                                size = size,
                                mimeType = if (mime.isNotEmpty()) mime else "application/pdf",
                                category = FileCategory.DOCUMENT,
                                uri = uri,
                                dateModified = dateSec * 1000L
                            )
                        )
                        count++
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback or ignore
        }

        // Also scan public Downloads, Documents, and NAQL received directories
        try {
            val docDirs = listOfNotNull(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                getReceivedFilesDir()
            )
            for (dir in docDirs) {
                if (dir.exists()) {
                    val scanned = scanDirectoryForFiles(dir, FileCategory.DOCUMENT)
                    for (item in scanned) {
                        if (!seenPaths.contains(item.name + "_" + item.size)) {
                            seenPaths.add(item.name + "_" + item.size)
                            list.add(item)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        list.distinctBy { it.name + "_" + it.size }.sortedByDescending { it.dateModified }
    }

    suspend fun getOtherFiles(): List<TransferItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<TransferItem>()
        val seenPaths = mutableSetOf<String>()
        try {
            val filesUri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.DATE_MODIFIED
            )
            val selection = "${MediaStore.Files.FileColumns.SIZE} > 0"
            val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

            contentResolver.query(
                filesUri,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val mimeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                val dateCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)

                var count = 0
                while (cursor.moveToNext() && count < 1500) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: ""
                    val size = cursor.getLong(sizeCol)
                    val mime = if (mimeCol != -1) cursor.getString(mimeCol) ?: "" else ""
                    val dateSec = if (dateCol != -1) cursor.getLong(dateCol) else 0L

                    val cat = determineCategory(name, mime)
                    if (cat == FileCategory.OTHER) {
                        val uri = Uri.withAppendedPath(filesUri, id.toString())
                        seenPaths.add(name + "_" + size)
                        list.add(
                            TransferItem(
                                id = "oth_$id",
                                name = name,
                                size = size,
                                mimeType = if (mime.isNotEmpty()) mime else "*/*",
                                category = FileCategory.OTHER,
                                uri = uri,
                                dateModified = dateSec * 1000L
                            )
                        )
                        count++
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        // Also scan public Downloads, Documents, and NAQL received folder for other files (zip, rar, 7z, bin, iso, etc.)
        try {
            val otherDirs = listOfNotNull(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                getReceivedFilesDir()
            )
            for (dir in otherDirs) {
                if (dir.exists()) {
                    val scanned = scanDirectoryForFiles(dir, FileCategory.OTHER)
                    for (item in scanned) {
                        if (!seenPaths.contains(item.name + "_" + item.size)) {
                            seenPaths.add(item.name + "_" + item.size)
                            list.add(item)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        list.distinctBy { it.name + "_" + it.size }.sortedByDescending { it.dateModified }
    }

    suspend fun getAllAvailableFiles(): List<TransferItem> = withContext(Dispatchers.IO) {
        val images = getMediaImages()
        val videos = getMediaVideos()
        val audio = getMediaAudio()
        val docs = getMediaDocuments()
        val apps = getInstalledApps()
        val others = getOtherFiles()

        val combined = (images + videos + audio + docs + apps + others)
            .distinctBy { it.name + "_" + it.size }
            .sortedByDescending { it.dateModified }
        combined
    }

    fun previewOrOpenFile(item: TransferItem) {
        try {
            if (item.uri != null && item.uri.scheme == "content") {
                openUri(item.uri, item.mimeType)
            } else if (item.localPath != null) {
                val file = File(item.localPath)
                if (file.exists()) {
                    openFile(file)
                } else if (item.uri != null) {
                    openUri(item.uri, item.mimeType)
                }
            } else if (item.uri != null) {
                openUri(item.uri, item.mimeType)
            }
        } catch (e: Exception) {
            // Fallback
        }
    }

    fun openFile(file: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            val mimeType = getMimeType(file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val authority = "${context.packageName}.fileprovider"
                val uri = FileProvider.getUriForFile(context, authority, file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (ex: Exception) {
                // Ignore
            }
        }
    }

    fun openUri(uri: Uri, mimeType: String = "*/*") {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun shareFile(file: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            val mimeType = getMimeType(file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "مشاركة الملف عبر").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun getVideoThumbnail(uri: Uri, width: Int = 256, height: Int = 256): android.graphics.Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.loadThumbnail(uri, android.util.Size(width, height), null)
            } else {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val bmp = retriever.getFrameAtTime(1000000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                retriever.release()
                bmp
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getMimeType(file: File): String {
        val extension = file.extension.lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
    }

    fun getUniqueFile(destDir: File, originalName: String): File {
        var target = File(destDir, originalName)
        if (!target.exists()) return target

        val dotIndex = originalName.lastIndexOf('.')
        val baseName = if (dotIndex != -1) originalName.substring(0, dotIndex) else originalName
        val extension = if (dotIndex != -1) originalName.substring(dotIndex) else ""

        var counter = 1
        while (target.exists()) {
            target = File(destDir, "${baseName}_$counter$extension")
            counter++
        }
        return target
    }
}
