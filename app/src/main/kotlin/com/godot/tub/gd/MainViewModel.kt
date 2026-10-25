
package com.godot.tub.gd

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.godot.tub.gd.utils.SourceMergeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class AppSettings(
    val themeMode: Int = 0,
    val outputPath: String = "/storage/emulated/0/Documents/",
    val format: String = "MARKDOWN",
    val exportMode: String = "DIR_ONLY",
    val promptMode: String = "GENERAL", // 三档模式: NONE, GENERAL, TUB_STYLE
    val customExcludeExts: Set<String> = emptySet(),
    val maxFileSizeKb: Int = 100
)

data class ExportResult(
    val fileName: String, val filePath: String, val charCount: Int,
    val tokenCount: Int, val lineCount: Int, val fullText: String,
    val godotVersion: String = "4.x",
    val timestamp: Long = System.currentTimeMillis()
)

class MainViewModel : ViewModel() {
    private lateinit var prefs: SharedPreferences
    
    val settings = MutableStateFlow(AppSettings())
    val currentScreen = MutableStateFlow("home")
    val result = MutableStateFlow<ExportResult?>(null)
    val historyList = MutableStateFlow<List<ExportResult>>(emptyList())
    val isLoading = MutableStateFlow(false)

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences("TUB_GD_SETTINGS", Context.MODE_PRIVATE)
        
        val customExtsString = prefs.getString("customExcludeExts", "") ?: ""
        val customExts = if (customExtsString.isBlank()) emptySet() else customExtsString.split(",").toSet()
        
        val savedPath = prefs.getString("outputPath", null)
        val finalPath = if (savedPath.isNullOrBlank()) "/storage/emulated/0/Documents/" else savedPath

        settings.value = AppSettings(
            themeMode = prefs.getInt("themeMode", 0),
            outputPath = finalPath,
            format = prefs.getString("format", "MARKDOWN") ?: "MARKDOWN",
            exportMode = prefs.getString("exportMode", "DIR_ONLY") ?: "DIR_ONLY",
            promptMode = prefs.getString("promptMode", "GENERAL") ?: "GENERAL",
            customExcludeExts = customExts,
            maxFileSizeKb = prefs.getInt("maxFileSizeKb", 100)
        )
        loadHistory()
    }

    fun updateSettings(update: (AppSettings) -> AppSettings) {
        val newSettings = update(settings.value)
        settings.value = newSettings
        if (::prefs.isInitialized) {
            prefs.edit().apply {
                putInt("themeMode", newSettings.themeMode)
                putString("outputPath", newSettings.outputPath)
                putString("format", newSettings.format)
                putString("exportMode", newSettings.exportMode)
                putString("promptMode", newSettings.promptMode)
                putString("customExcludeExts", newSettings.customExcludeExts.joinToString(","))
                putInt("maxFileSizeKb", newSettings.maxFileSizeKb)
                apply()
            }
        }
    }

    fun generateFromTreeUri(context: Context, uri: Uri) {
        isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cfg = settings.value
                val scanResult = SourceMergeUtils.scanDirectory(
                    context, uri, cfg.format, cfg.exportMode, cfg.promptMode, cfg.customExcludeExts, cfg.maxFileSizeKb
                )
                val rootDoc = DocumentFile.fromTreeUri(context, uri)
                val folderName = rootDoc?.name ?: "Project"
                saveResult(folderName, scanResult.content, scanResult.godotVersion)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading.value = false
            }
        }
    }

    fun generateFromMultipleFiles(context: Context, uris: List<Uri>) {
        isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cfg = settings.value
                val scanResult = SourceMergeUtils.processMultipleFiles(
                    context, uris, cfg.format, cfg.promptMode, cfg.customExcludeExts, cfg.maxFileSizeKb
                )
                saveResult("MultiFiles", scanResult.content, scanResult.godotVersion)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading.value = false
            }
        }
    }

    private fun saveResult(baseName: String, text: String, godotVersion: String) {
        val cfg = settings.value
        val outDir = File(cfg.outputPath)
        if (!outDir.exists()) outDir.mkdirs()
        val ext = if (cfg.format == "MARKDOWN") "md" else cfg.format.lowercase()
        val outFile = File(outDir, "${baseName}_source.$ext")
        outFile.writeText(text)
        
        val newResult = ExportResult(
            fileName = outFile.name, filePath = outDir.absolutePath,
            charCount = text.length, tokenCount = text.length / 4,
            lineCount = text.lines().size, fullText = text, godotVersion = godotVersion
        )
        result.value = newResult
        addHistory(newResult)
        currentScreen.value = "result"
    }

    private fun addHistory(item: ExportResult) {
        val list = historyList.value.toMutableList()
        list.add(0, item)
        if (list.size > 20) list.removeAt(list.size - 1)
        historyList.value = list
        
        val jsonArray = JSONArray()
        list.forEach {
            val obj = JSONObject()
            obj.put("fileName", it.fileName)
            obj.put("filePath", it.filePath)
            obj.put("charCount", it.charCount)
            obj.put("tokenCount", it.tokenCount)
            obj.put("lineCount", it.lineCount)
            obj.put("fullText", it.fullText)
            obj.put("godotVersion", it.godotVersion)
            obj.put("timestamp", it.timestamp)
            jsonArray.put(obj)
        }
        prefs.edit().putString("history", jsonArray.toString()).apply()
    }

    private fun loadHistory() {
        val jsonStr = prefs.getString("history", "") ?: ""
        if (jsonStr.isBlank()) return
        try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<ExportResult>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(ExportResult(
                    fileName = obj.getString("fileName"),
                    filePath = obj.getString("filePath"),
                    charCount = obj.getInt("charCount"),
                    tokenCount = obj.getInt("tokenCount"),
                    lineCount = obj.getInt("lineCount"),
                    fullText = obj.getString("fullText"),
                    godotVersion = obj.optString("godotVersion", "4.x"),
                    timestamp = obj.getLong("timestamp")
                ))
            }
            historyList.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun clearHistory() {
        historyList.value = emptyList()
        if (::prefs.isInitialized) {
            prefs.edit().remove("history").apply()
        }
    }

    fun viewHistoryItem(item: ExportResult) {
        result.value = item
        currentScreen.value = "result"
    }
}
