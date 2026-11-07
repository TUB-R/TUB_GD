package com.godot.tub.gd

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.godot.tub.gd.utils.SourceMergeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SelectedFile(val uri: Uri, val name: String)

data class AppSettings(
    val themeMode: Int = 0,
    val outputPath: String = "/storage/emulated/0/Documents/",
    val format: String = "MARKDOWN",
    val exportMode: String = "DIR_ONLY",
    val promptMode: String = "GENERAL",
    val reasoningLevel: String = "HIGH",
    val customPromptPath: String = "",
    val softExcludeRules: Set<String> = emptySet(),
    val hardExcludeRules: Set<String> = emptySet(),
    val maxFileSizeKb: Int = 100,
    val maxHistoryCount: Int = 10,
    val showGlobalFileSize: Boolean = true
)

data class ExportResult(
    val fileName: String, val filePath: String, val charCount: Int,
    val tokenCount: Int, val lineCount: Int, val fullText: String,
    val godotVersion: String = "4.x",
    val timestamp: Long = System.currentTimeMillis()
)

class MainViewModel : ViewModel() {
    private lateinit var prefs: SharedPreferences
    private lateinit var historyFile: File

    val settings = MutableStateFlow(AppSettings())
    val currentScreen = MutableStateFlow("home")
    var previousScreen = "home"
    val result = MutableStateFlow<ExportResult?>(null)
    val historyList = MutableStateFlow<List<ExportResult>>(emptyList())
    val pendingMultiFiles = MutableStateFlow<List<SelectedFile>>(emptyList())
    val isLoading = MutableStateFlow(false)

    val errorMessage = MutableSharedFlow<String>()
    val showPermissionError = MutableSharedFlow<Unit>()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences("TUB_GD_SETTINGS", Context.MODE_PRIVATE)
        historyFile = File(context.filesDir, "history.json")

        val softStr = prefs.getString("softExcludeRules", "") ?: ""
        val softRules = if (softStr.isBlank()) emptySet() else softStr.split(",").toSet()

        val hardStr = prefs.getString("hardExcludeRules", "") ?: ""
        val hardRules = if (hardStr.isBlank()) emptySet() else hardStr.split(",").toSet()

        val savedPath = prefs.getString("outputPath", null)
        val finalPath = if (savedPath.isNullOrBlank()) "/storage/emulated/0/Documents/" else savedPath

        var savedMode = prefs.getString("exportMode", "DIR_ONLY") ?: "DIR_ONLY"
        if (savedMode == "PROJECT_SUMMARY") {
            savedMode = "DIR_ONLY"
            prefs.edit().putString("exportMode", "DIR_ONLY").apply()
        }

        settings.value = AppSettings(
            themeMode = prefs.getInt("themeMode", 0),
            outputPath = finalPath,
            format = prefs.getString("format", "MARKDOWN") ?: "MARKDOWN",
            exportMode = savedMode,
            promptMode = prefs.getString("promptMode", "GENERAL") ?: "GENERAL",
            reasoningLevel = prefs.getString("reasoningLevel", "HIGH") ?: "HIGH",
            customPromptPath = prefs.getString("customPromptPath", "") ?: "",
            softExcludeRules = softRules,
            hardExcludeRules = hardRules,
            maxFileSizeKb = prefs.getInt("maxFileSizeKb", 100),
            maxHistoryCount = prefs.getInt("maxHistoryCount", 10),
            showGlobalFileSize = prefs.getBoolean("showGlobalFileSize", true)
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
                putString("reasoningLevel", newSettings.reasoningLevel)
                putString("customPromptPath", newSettings.customPromptPath)
                putString("softExcludeRules", newSettings.softExcludeRules.joinToString(","))
                putString("hardExcludeRules", newSettings.hardExcludeRules.joinToString(","))
                putInt("maxFileSizeKb", newSettings.maxFileSizeKb)
                putInt("maxHistoryCount", newSettings.maxHistoryCount)
                putBoolean("showGlobalFileSize", newSettings.showGlobalFileSize)
                apply()
            }
        }
    }

    fun generateFromTreeUri(context: Context, uri: Uri, ignorePrompt: Boolean = false) {
        isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cfg = settings.value
                val effectivePromptMode = if (ignorePrompt) "NONE" else cfg.promptMode
                val scanResult = SourceMergeUtils.scanDirectory(
                    context, uri, cfg.format, cfg.exportMode, effectivePromptMode, cfg.reasoningLevel,
                    cfg.softExcludeRules, cfg.hardExcludeRules, cfg.maxFileSizeKb,
                    cfg.showGlobalFileSize, cfg.customPromptPath
                )
                val rootDoc = DocumentFile.fromTreeUri(context, uri)
                val folderName = rootDoc?.name ?: "Project"
                saveResult(context, folderName, scanResult.content, scanResult.godotVersion)
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage.emit("扫描失败: ${e.message ?: "请检查是否授予了文件访问权限"}")
            } finally {
                isLoading.value = false
            }
        }
    }

    fun addPendingMultiFile(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            var name = "unknown_file"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1) name = cursor.getString(nameIdx)
                }
            }
            val file = SelectedFile(uri, name)
            val list = pendingMultiFiles.value.toMutableList()
            if (list.none { it.uri == uri }) {
                list.add(file)
                pendingMultiFiles.value = list
            }
        }
    }

    fun removePendingMultiFile(file: SelectedFile) {
        val list = pendingMultiFiles.value.toMutableList()
        list.remove(file)
        pendingMultiFiles.value = list
    }

    fun clearPendingMultiFiles() {
        pendingMultiFiles.value = emptyList()
    }

    fun startMultiFileMerge(context: Context) {
        val files = pendingMultiFiles.value
        if (files.isEmpty()) return
        generateFromMultipleFiles(context, files)
    }

    fun generateFromMultipleFiles(context: Context, files: List<SelectedFile>) {
        isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cfg = settings.value
                val scanResult = SourceMergeUtils.processMultipleFiles(
                    context, files, cfg.format, cfg.softExcludeRules, cfg.hardExcludeRules, cfg.maxFileSizeKb, cfg.showGlobalFileSize
                )
                saveResult(context, "MultiFiles", scanResult.content, scanResult.godotVersion)
                clearPendingMultiFiles()
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage.emit("合并失败: ${e.message}")
            } finally {
                isLoading.value = false
            }
        }
    }

    private fun saveResult(context: Context, baseName: String, text: String, godotVersion: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cfg = settings.value
                val pathStr = if (cfg.outputPath == "APP_INTERNAL_STORAGE") {
                    context.getExternalFilesDir(null)?.absolutePath ?: context.filesDir.absolutePath
                } else {
                    cfg.outputPath
                }
                
                val outDir = File(pathStr)
                if (!outDir.exists()) {
                    val created = outDir.mkdirs()
                    if (!created && !outDir.exists()) {
                        throw Exception("无法创建输出目录")
                    }
                }
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
            } catch (e: Exception) {
                e.printStackTrace()
                showPermissionError.emit(Unit)
            }
        }
    }

    private fun addHistory(item: ExportResult) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val list = historyList.value.toMutableList()
                list.add(0, item)
                val maxHistory = settings.value.maxHistoryCount.coerceAtLeast(1)
                while (list.size > maxHistory) {
                    list.removeAt(list.size - 1)
                }
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
                historyFile.writeText(jsonArray.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadHistory() {
        if (!historyFile.exists()) return
        try {
            val jsonStr = historyFile.readText()
            if (jsonStr.isBlank()) return
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

    fun clearHistory(onResult: (String) -> Unit) {
        val currentHistory = historyList.value
        var deletedInternalCount = 0
        
        currentHistory.forEach { item ->
            val isInternal = item.filePath.contains("Android/data") || item.filePath == "APP_INTERNAL_STORAGE"
            
            if (isInternal) {
                try {
                    val file = File(item.filePath, item.fileName)
                    if (file.exists()) {
                        file.delete()
                    }
                    deletedInternalCount++
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        historyList.value = emptyList()
        if (historyFile.exists()) historyFile.delete()
        
        if (deletedInternalCount > 0) {
            onResult("已清空历史，并同步清理了 $deletedInternalCount 个内部原文件")
        } else {
            onResult("历史记录已清空")
        }
    }

    fun viewHistoryItem(item: ExportResult) {
        result.value = item
        currentScreen.value = "result"
    }
}
