package com.example.data

import android.content.Context
import android.os.Build
import com.example.model.FileSortOption
import com.example.model.FileViewMode

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("naql_settings", Context.MODE_PRIVATE)

    var deviceName: String
        get() {
            val defaultName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
            return prefs.getString("device_name", defaultName) ?: defaultName
        }
        set(value) = prefs.edit().putString("device_name", value).apply()

    var autoAccept: Boolean
        get() = prefs.getBoolean("auto_accept", false)
        set(value) = prefs.edit().putBoolean("auto_accept", value).apply()

    var vibrateOnCompletion: Boolean
        get() = prefs.getBoolean("vibrate_on_completion", true)
        set(value) = prefs.edit().putBoolean("vibrate_on_completion", value).apply()

    var fileViewMode: FileViewMode
        get() {
            val name = prefs.getString("file_view_mode", FileViewMode.LIST.name) ?: FileViewMode.LIST.name
            return try {
                FileViewMode.valueOf(name)
            } catch (e: Exception) {
                FileViewMode.LIST
            }
        }
        set(value) = prefs.edit().putString("file_view_mode", value.name).apply()

    var fileSortOption: FileSortOption
        get() {
            val name = prefs.getString("file_sort_option", FileSortOption.DATE_DESC.name) ?: FileSortOption.DATE_DESC.name
            return try {
                FileSortOption.valueOf(name)
            } catch (e: Exception) {
                FileSortOption.DATE_DESC
            }
        }
        set(value) = prefs.edit().putString("file_sort_option", value.name).apply()
}
