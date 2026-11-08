package com.tub.code.md

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tub.code.md.utils.SourceMergeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SelectedFile(val uri: Uri, val name: String)

data class CustomPrompt(
    val id: String,
    val title: String,
    val content: String
)

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
    val showGlobalFileSize: Boolean = true,
    val planMode: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val customPrompts: List<CustomPrompt> = emptyList(),
    val selectedCustomPromptId: String = "",
    val androidAppMode: Boolean = false,
    val preferredMode: String = "GENERAL",
    val configPlatform: String = "GODOT"
)


data class ExportResult(
    val fileName: String, val filePath: String, val charCount: Int,
    val tokenCount: Int, val lineCount: Int, val fullText: String,
    val godotVersion: String = "4.x",
    val timestamp: Long = System.currentTimeMillis()
)

data class PendingBinaryConfirm(
    val sourceType: String,
    val folderName: String,
    val content: String,
    val godotVersion: String,
    val binaryFiles: List<String>,
    val sourceUri: android.net.Uri? = null,
    val sourceFiles: List<SelectedFile>? = null,
    val promptMode: String = "NONE"
)

data class ImportResult(
    val fullySuccess: Boolean,
    val versionMatch: Boolean,
    val configVersion: String,
    val currentVersion: String,
    val successItems: List<String>,
    val failedItems: List<String>,
    val warningItems: List<String> = emptyList(),
    val successCount: Int = 0,
    val totalCount: Int = 0
)

class MainViewModel : ViewModel() {
    companion object {
        val ANDROID_HARD_EXCLUDE_RULES = setOf(
            ".kotlin/", "drawable-v24/", "layout/", "mipmap-anydpi-v26/"
        )
    }

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
    val pendingBinaryConfirm = MutableStateFlow<PendingBinaryConfirm?>(null)
    private var preImportSettings: AppSettings? = null
    private var preImportHistory: List<ExportResult>? = null
    private var isImportDeferred: Boolean = false

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
            showGlobalFileSize = prefs.getBoolean("showGlobalFileSize", true),
            planMode = prefs.getBoolean("planMode", false),
            onboardingCompleted = prefs.getBoolean("onboardingCompleted", false),
            customPrompts = deserializeCustomPrompts(prefs.getString("customPrompts", "") ?: ""),
            selectedCustomPromptId = prefs.getString("selectedCustomPromptId", "") ?: "",
            androidAppMode = prefs.getBoolean("androidAppMode", false),
            preferredMode = prefs.getString("preferredMode", "GENERAL") ?: "GENERAL",
            configPlatform = prefs.getString("configPlatform", "GODOT") ?: "GODOT"
        )
        loadHistory()
    }

            fun updateSettings(update: (AppSettings) -> AppSettings) {
        val newSettings = update(settings.value).let { s ->
            if (s.androidAppMode && s.hardExcludeRules.intersect(ANDROID_HARD_EXCLUDE_RULES).isEmpty()) {
                s.copy(androidAppMode = false)
            } else {
                s
            }
        }
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
                putBoolean("planMode", newSettings.planMode)
                putBoolean("onboardingCompleted", newSettings.onboardingCompleted)
                putString("customPrompts", serializeCustomPrompts(newSettings.customPrompts))
                putString("selectedCustomPromptId", newSettings.selectedCustomPromptId)
                putBoolean("androidAppMode", newSettings.androidAppMode)
                putString("preferredMode", newSettings.preferredMode)
                putString("configPlatform", newSettings.configPlatform)
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
                val selectedPrompt = cfg.customPrompts.find { it.id == cfg.selectedCustomPromptId }
                val scanResult = SourceMergeUtils.scanDirectory(
                    context, uri, cfg.format, cfg.exportMode, effectivePromptMode, cfg.reasoningLevel,
                    cfg.softExcludeRules, cfg.hardExcludeRules, cfg.maxFileSizeKb,
                    cfg.showGlobalFileSize,
                    customPromptContent = selectedPrompt?.content ?: "",
                    customPromptTitle = selectedPrompt?.title ?: "",
                    planMode = cfg.planMode,
                    configPlatform = cfg.configPlatform
                )
                val rootDoc = DocumentFile.fromTreeUri(context, uri)
                val folderName = rootDoc?.name ?: "Project"
                
                // 判断是否为 Godot 模式，如果不是，强行将版本号设为空，避免后续误报
                val isGodot = effectivePromptMode == "GENERAL" || 
                              effectivePromptMode == "ADVANCED" || 
                              effectivePromptMode == "TUB_GD"
                val finalVersion = if (isGodot) scanResult.godotVersion else ""

                if (scanResult.binaryFiles.isNotEmpty()) {
                    pendingBinaryConfirm.value = PendingBinaryConfirm(
                        sourceType = "tree",
                        folderName = folderName,
                        content = scanResult.content,
                        godotVersion = finalVersion,
                        binaryFiles = scanResult.binaryFiles,
                        sourceUri = uri,
                        promptMode = effectivePromptMode
                    )
                } else {
                    saveResult(context, folderName, scanResult.content, finalVersion)
                }
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
                if (scanResult.binaryFiles.isNotEmpty()) {
                    pendingBinaryConfirm.value = PendingBinaryConfirm(
                        sourceType = "multi",
                        folderName = "MultiFiles",
                        content = scanResult.content,
                        godotVersion = scanResult.godotVersion,
                        binaryFiles = scanResult.binaryFiles,
                        sourceFiles = files
                    )
                } else {
                    saveResult(context, "MultiFiles", scanResult.content, scanResult.godotVersion)
                    clearPendingMultiFiles()
                }
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
                // 写入 UTF-8 BOM，确保 Windows/部分安卓编辑器不乱码
                outFile.writeText("\uFEFF$text")

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
                saveHistoryToDisk(list)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveHistoryToDisk(list: List<ExportResult>) {
        try {
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

    private fun loadHistory() {
        if (!historyFile.exists()) return
        try {
            val jsonStr = historyFile.readText().removePrefix("\uFEFF")
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


    fun exportConfig(context: Context, uri: Uri, onComplete: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val s = settings.value
                val sJson = JSONObject().apply {
                    put("themeMode", s.themeMode)
                    put("outputPath", s.outputPath)
                    put("format", s.format)
                    put("exportMode", s.exportMode)
                    put("promptMode", s.promptMode)
                    put("reasoningLevel", s.reasoningLevel)
                    put("customPromptPath", s.customPromptPath)
                    put("softExcludeRules", JSONArray(s.softExcludeRules.toList()))
                    put("hardExcludeRules", JSONArray(s.hardExcludeRules.toList()))
                    put("maxFileSizeKb", s.maxFileSizeKb)
                    put("maxHistoryCount", s.maxHistoryCount)
                    put("showGlobalFileSize", s.showGlobalFileSize)
                    put("planMode", s.planMode)
                    put("onboardingCompleted", s.onboardingCompleted)
                    put("customPrompts", JSONArray().apply {
                        s.customPrompts.forEach { cp ->
                            put(JSONObject().apply {
                                put("id", cp.id)
                                put("title", cp.title)
                                put("content", cp.content)
                            })
                        }
                    })
                    put("selectedCustomPromptId", s.selectedCustomPromptId)
                    put("androidAppMode", s.androidAppMode)
                    put("preferredMode", s.preferredMode)
                    put("configPlatform", s.configPlatform)
                }
                val historyJson = JSONArray()
                historyList.value.forEach { item ->
                    historyJson.put(JSONObject().apply {
                        put("fileName", item.fileName)
                        put("filePath", item.filePath)
                        put("charCount", item.charCount)
                        put("tokenCount", item.tokenCount)
                        put("lineCount", item.lineCount)
                        put("fullText", item.fullText)
                        put("godotVersion", item.godotVersion)
                        put("timestamp", item.timestamp)
                    })
                }
                val root = JSONObject().apply {
                    put("app", "TUB_GD")
                    put("appVersion", currentAppVersion(context))
                    put("version", 1)
                    put("exportTime", System.currentTimeMillis())
                    put("settings", sJson)
                    put("history", historyJson)
                }
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    // 写入 UTF-8 BOM，防止部分编辑器误判为 GBK 导致乱码
                    val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
                    out.write(bom)
                    out.write(root.toString(2).toByteArray(Charsets.UTF_8))
                } ?: throw Exception("无法写入文件")
                viewModelScope.launch(Dispatchers.Main) { onComplete("配置已导出") }
            } catch (e: Exception) {
                e.printStackTrace()
                viewModelScope.launch(Dispatchers.Main) { onComplete("导出失败: ${e.message}") }
            }
        }
    }

    fun importConfig(
        context: Context,
        uri: Uri,
        onComplete: (ImportResult) -> Unit,
        deferApply: Boolean = false
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentVersion = currentAppVersion(context)
            try {
                preImportSettings = settings.value
                preImportHistory = historyList.value
                isImportDeferred = deferApply

                var text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: throw Exception("无法读取文件")
                text = text.removePrefix("\uFEFF")

                val root = try {
                    JSONObject(text)
                } catch (e: Exception) {
                    preImportSettings = null
                    preImportHistory = null
                    viewModelScope.launch(Dispatchers.Main) {
                        onComplete(ImportResult(
                            fullySuccess = false,
                            versionMatch = false,
                            configVersion = "无法解析",
                            currentVersion = currentVersion,
                            successItems = emptyList(),
                            failedItems = listOf("配置文件无法解析，可能是版本不兼容或文件损坏")
                        ))
                    }
                    return@launch
                }

                if (root.optString("app") != "TUB_GD") {
                    preImportSettings = null
                    preImportHistory = null
                    viewModelScope.launch(Dispatchers.Main) {
                        onComplete(ImportResult(
                            fullySuccess = false,
                            versionMatch = false,
                            configVersion = root.optString("appVersion", "未知"),
                            currentVersion = currentVersion,
                            successItems = emptyList(),
                            failedItems = listOf("这不是有效的 TUB_GD 配置文件")
                        ))
                    }
                    return@launch
                }

                val configVersion = root.optString("appVersion", "未知")
                val versionMatch = configVersion != "未知" && configVersion == currentVersion

                val successItems = mutableListOf<String>()
                val failedItems = mutableListOf<String>()
                val warningItems = mutableListOf<String>()
                var settingsSuccessCount = 0
                var settingsTotalCount = 0
                var customPromptsSuccessCount = 0
                var customPromptsTotalCount = 0
                var historySuccessCount = 0
                var historyTotalCount = 0

                // 检测根级未知属性
                val knownRootKeys = setOf("app", "appVersion", "version", "exportTime", "settings", "history")
                val unknownRoot = root.keys().asSequence().filter { it !in knownRootKeys }.toList()
                if (unknownRoot.isNotEmpty()) {
                    warningItems.add("发现 ${unknownRoot.size} 个未知根属性（${unknownRoot.joinToString(", ")}），可能是新版本配置，当前版本不受影响")
                }

                val sJson = root.optJSONObject("settings")
                if (sJson != null) {
                    settingsTotalCount = 19
                    // 检测 settings 级未知属性
                    val knownSettingsKeys = setOf(
                        "themeMode", "outputPath", "format", "exportMode", "promptMode",
                        "reasoningLevel", "customPromptPath", "softExcludeRules", "hardExcludeRules",
                        "maxFileSizeKb", "maxHistoryCount", "showGlobalFileSize", "planMode",
                        "onboardingCompleted", "customPrompts", "selectedCustomPromptId",
                        "androidAppMode", "preferredMode", "configPlatform"
                    )
                    val unknownSettings = sJson.keys().asSequence().filter { it !in knownSettingsKeys }.toList()
                    if (unknownSettings.isNotEmpty()) {
                        warningItems.add("设置中发现 ${unknownSettings.size} 个未知属性（${unknownSettings.joinToString(", ")}），可能是新版本配置，当前版本不受影响")
                    }
                    try {
                        // 字段类型校验
                        val expectedTypes = mapOf(
                            "themeMode" to "数字",
                            "outputPath" to "字符串",
                            "format" to "字符串",
                            "exportMode" to "字符串",
                            "promptMode" to "字符串",
                            "reasoningLevel" to "字符串",
                            "customPromptPath" to "字符串",
                            "softExcludeRules" to "数组",
                            "hardExcludeRules" to "数组",
                            "maxFileSizeKb" to "数字",
                            "maxHistoryCount" to "数字",
                            "showGlobalFileSize" to "布尔",
                            "planMode" to "布尔",
                            "onboardingCompleted" to "布尔",
                            "customPrompts" to "数组",
                            "selectedCustomPromptId" to "字符串",
                            "androidAppMode" to "布尔",
                            "preferredMode" to "字符串",
                            "configPlatform" to "字符串"
                        )
                        val typeErrors = mutableListOf<String>()
                        for ((key, typeName) in expectedTypes) {
                            if (!sJson.has(key)) continue
                            val v = sJson.opt(key)
                            val ok = when (key) {
                                "themeMode", "maxFileSizeKb", "maxHistoryCount" -> v is Number
                                "showGlobalFileSize", "planMode", "onboardingCompleted", "androidAppMode" -> v is Boolean
                                "softExcludeRules", "hardExcludeRules", "customPrompts" -> v is JSONArray
                                else -> v is String
                            }
                            if (ok) settingsSuccessCount++
                            else typeErrors.add("$key（期望$typeName）")
                        }
                        if (typeErrors.isNotEmpty()) {
                            failedItems.add("${typeErrors.size} 个字段类型错误：${typeErrors.joinToString(", ")}")
                        }
                        
                        fun parseSet(key: String, fallback: Set<String>): Set<String> {
                            val arr = sJson.optJSONArray(key) ?: return fallback
                            val set = mutableSetOf<String>()
                            for (i in 0 until arr.length()) set.add(arr.getString(i))
                            return set
                        }
                        val promptsArr = sJson.optJSONArray("customPrompts")
                        customPromptsTotalCount = promptsArr?.length() ?: 0
                        val importedPrompts = mutableListOf<CustomPrompt>()
                        if (promptsArr != null) {
                            for (pi in 0 until promptsArr.length()) {
                                try {
                                    val po = promptsArr.getJSONObject(pi)
                                    importedPrompts.add(CustomPrompt(
                                        id = po.optString("id", "").ifBlank { "cp_imported_$pi" },
                                        title = po.optString("title", "未命名提示词"),
                                        content = po.optString("content", "")
                                    ))
                                    customPromptsSuccessCount++
                                } catch (_: Exception) {}
                            }
                        }
                        val importedSelectedId = sJson.optString("selectedCustomPromptId", "")
                        val cur = settings.value
                        val newSettings = cur.copy(
                            themeMode = sJson.optInt("themeMode", cur.themeMode),
                            outputPath = sJson.optString("outputPath", cur.outputPath),
                            format = sJson.optString("format", cur.format),
                            exportMode = sJson.optString("exportMode", cur.exportMode),
                            promptMode = when (sJson.optString("promptMode", cur.promptMode)) {
                                "TUB_STYLE" -> "TUB_GD"
                                "APP_TUB" -> "TUB_APP"
                                else -> sJson.optString("promptMode", cur.promptMode)
                            },
                            reasoningLevel = sJson.optString("reasoningLevel", cur.reasoningLevel),
                            customPromptPath = sJson.optString("customPromptPath", cur.customPromptPath),
                            softExcludeRules = parseSet("softExcludeRules", cur.softExcludeRules),
                            hardExcludeRules = parseSet("hardExcludeRules", cur.hardExcludeRules),
                            maxFileSizeKb = sJson.optInt("maxFileSizeKb", cur.maxFileSizeKb),
                            maxHistoryCount = sJson.optInt("maxHistoryCount", cur.maxHistoryCount),
                            showGlobalFileSize = sJson.optBoolean("showGlobalFileSize", cur.showGlobalFileSize),
                            planMode = sJson.optBoolean("planMode", cur.planMode),
                            customPrompts = importedPrompts,
                            selectedCustomPromptId = importedSelectedId,
                            androidAppMode = sJson.optBoolean("androidAppMode", cur.androidAppMode),
                            preferredMode = sJson.optString("preferredMode", cur.preferredMode),
                            configPlatform = sJson.optString("configPlatform", cur.configPlatform)
                        )
                        if (deferApply) {
                            // 暂存模式：只改内存，不写 SharedPreferences
                            settings.value = newSettings.let { s ->
                                if (s.androidAppMode && s.hardExcludeRules.intersect(ANDROID_HARD_EXCLUDE_RULES).isEmpty()) {
                                    s.copy(androidAppMode = false)
                                } else {
                                    s
                                }
                            }
                        } else {
                            updateSettings { newSettings }
                        }
                        successItems.add("设置")

                        val keyFields = listOf(
                            "themeMode", "outputPath", "format", "exportMode",
                            "promptMode", "reasoningLevel", "softExcludeRules", "hardExcludeRules"
                        )
                        val missing = keyFields.filter { !sJson.has(it) }
                        if (missing.isNotEmpty()) {
                            failedItems.add("部分设置项缺失（${missing.joinToString(", ")}），已使用默认值")
                        }
                    } catch (e: Exception) {
                        failedItems.add("设置解析失败: ${e.message}")
                    }
                } else {
                    failedItems.add("配置中缺少「设置」数据")
                }

                val historyJson = root.optJSONArray("history")
                if (historyJson != null && historyJson.length() > 0) {
                    historyTotalCount = historyJson.length()
                    try {
                        val list = mutableListOf<ExportResult>()
                        var failedCount = 0
                        for (i in 0 until historyJson.length()) {
                            try {
                                val obj = historyJson.getJSONObject(i)
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
                            } catch (e: Exception) {
                                failedCount++
                            }
                        }
                        historyList.value = list
                        if (!deferApply) saveHistoryToDisk(list)
                        historySuccessCount = list.size
                        if (list.isNotEmpty()) successItems.add("历史记录 (${list.size} 条)")
                        if (failedCount > 0) failedItems.add("${failedCount} 条历史记录解析失败")
                    } catch (e: Exception) {
                        failedItems.add("历史记录解析失败: ${e.message}")
                    }
                }

                val result = ImportResult(
                    fullySuccess = failedItems.isEmpty(),
                    versionMatch = versionMatch,
                    configVersion = configVersion,
                    currentVersion = currentVersion,
                    successItems = successItems,
                    failedItems = failedItems,
                    warningItems = warningItems,
                    successCount = settingsSuccessCount + customPromptsSuccessCount + historySuccessCount,
                    totalCount = settingsTotalCount + customPromptsTotalCount + historyTotalCount
                )
                viewModelScope.launch(Dispatchers.Main) { onComplete(result) }
            } catch (e: Exception) {
                e.printStackTrace()
                preImportSettings = null
                preImportHistory = null
                val errMsg = e.message ?: "未知错误"
                viewModelScope.launch(Dispatchers.Main) {
                    onComplete(ImportResult(
                        fullySuccess = false,
                        versionMatch = false,
                        configVersion = "未知",
                        currentVersion = currentVersion,
                        successItems = emptyList(),
                        failedItems = listOf("导入失败: $errMsg")
                    ))
                }
            }
        }
    }

    fun cancelImport() {
        val s = preImportSettings ?: return
        val h = preImportHistory ?: emptyList()
        if (isImportDeferred) {
            // 暂存模式：只回滚内存，不碰 SharedPreferences
            settings.value = s
            historyList.value = h
        } else {
            updateSettings { s }
            historyList.value = h
            saveHistoryToDisk(h)
        }
        isImportDeferred = false
        preImportSettings = null
        preImportHistory = null
    }

    private fun currentAppVersion(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "未知"
        } catch (e: Exception) {
            "未知"
        }
    }



    private fun extractExtensionsFromBinaryFiles(files: List<String>): Set<String> {
        return files.mapNotNull { path ->
            val name = path.substringAfterLast("/")
            val dotIdx = name.lastIndexOf('.')
            if (dotIdx > 0 && dotIdx < name.length - 1) {
                name.substring(dotIdx).lowercase()
            } else null
        }.toSet()
    }

    fun confirmBinaryAddExclude(context: Context) {
        val pending = pendingBinaryConfirm.value ?: return
        val exts = extractExtensionsFromBinaryFiles(pending.binaryFiles)
        if (exts.isEmpty()) {
            confirmBinaryContinue(context)
            return
        }
        updateSettings { s ->
            s.copy(softExcludeRules = s.softExcludeRules + exts)
        }
        pendingBinaryConfirm.value = null
        isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cfg = settings.value
                val selectedPrompt = cfg.customPrompts.find { it.id == cfg.selectedCustomPromptId }
                val scanResult = when (pending.sourceType) {
                    "tree" -> {
                        val uri = pending.sourceUri ?: return@launch
                        SourceMergeUtils.scanDirectory(
                            context, uri, cfg.format, cfg.exportMode, pending.promptMode, cfg.reasoningLevel,
                            cfg.softExcludeRules, cfg.hardExcludeRules, cfg.maxFileSizeKb,
                            cfg.showGlobalFileSize,
                            customPromptContent = selectedPrompt?.content ?: "",
                            customPromptTitle = selectedPrompt?.title ?: "",
                            planMode = cfg.planMode,
                            configPlatform = cfg.configPlatform
                        )
                    }
                    else -> {
                        val files = pending.sourceFiles ?: return@launch
                        SourceMergeUtils.processMultipleFiles(
                            context, files, cfg.format, cfg.softExcludeRules, cfg.hardExcludeRules,
                            cfg.maxFileSizeKb, cfg.showGlobalFileSize
                        )
                    }
                }
                if (scanResult.binaryFiles.isNotEmpty()) {
                    pendingBinaryConfirm.value = PendingBinaryConfirm(
                        sourceType = pending.sourceType,
                        folderName = pending.folderName,
                        content = scanResult.content,
                        godotVersion = scanResult.godotVersion,
                        binaryFiles = scanResult.binaryFiles,
                        sourceUri = pending.sourceUri,
                        sourceFiles = pending.sourceFiles,
                        promptMode = pending.promptMode
                    )
                } else {
                    saveResult(context, pending.folderName, scanResult.content, scanResult.godotVersion)
                    if (pending.sourceType == "multi") {
                        clearPendingMultiFiles()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showPermissionError.emit(Unit)
            } finally {
                isLoading.value = false
            }
        }
    }

    fun confirmBinaryContinue(context: Context) {
        val pending = pendingBinaryConfirm.value ?: return
        pendingBinaryConfirm.value = null
        isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cfg = settings.value
                val selectedPrompt = cfg.customPrompts.find { it.id == cfg.selectedCustomPromptId }
                val scanResult = when (pending.sourceType) {
                    "tree" -> {
                        val uri = pending.sourceUri ?: return@launch
                        SourceMergeUtils.scanDirectory(
                            context, uri, cfg.format, cfg.exportMode, pending.promptMode, cfg.reasoningLevel,
                            cfg.softExcludeRules, cfg.hardExcludeRules, cfg.maxFileSizeKb,
                            cfg.showGlobalFileSize,
                            customPromptContent = selectedPrompt?.content ?: "",
                            customPromptTitle = selectedPrompt?.title ?: "",
                            planMode = cfg.planMode,
                            forceIncludeBinary = true
                        )
                    }
                    else -> {
                        val files = pending.sourceFiles ?: return@launch
                        SourceMergeUtils.processMultipleFiles(
                            context, files, cfg.format, cfg.softExcludeRules, cfg.hardExcludeRules,
                            cfg.maxFileSizeKb, cfg.showGlobalFileSize, forceIncludeBinary = true
                        )
                    }
                }
                saveResult(context, pending.folderName, scanResult.content, scanResult.godotVersion)
                if (pending.sourceType == "multi") {
                    clearPendingMultiFiles()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showPermissionError.emit(Unit)
            } finally {
                isLoading.value = false
            }
        }
    }

    fun cancelBinaryConfirm() {
        pendingBinaryConfirm.value = null
    }

    fun viewHistoryItem(item: ExportResult) {
        result.value = item
        currentScreen.value = "result"
    }

        fun completeOnboarding(path: String, preferredMode: String) {
        updateSettings { s ->
            val base = s.copy(
                outputPath = path,
                preferredMode = preferredMode,
                onboardingCompleted = true,
                // 无论选哪个模式，都强制保留 4 条 Android 资源目录规则
                hardExcludeRules = s.hardExcludeRules + ANDROID_HARD_EXCLUDE_RULES
            )
            // 用户已通过导入配置设置了自定义提示词 → 保留 CUSTOM 模式
            if (s.promptMode == "CUSTOM" && s.customPrompts.isNotEmpty()) {
                base
            } else {
                when (preferredMode) {
                    "GODOT" -> base.copy(
                        promptMode = "TUB_GD",
                        format = "MARKDOWN",
                        exportMode = "FULL_EXPORT"
                    )
                    "APP" -> base.copy(
                        promptMode = "TUB_APP",
                        format = "MARKDOWN",
                        exportMode = "FULL_EXPORT"
                    )
                    else -> base.copy(
                        promptMode = "TUB_PY",
                        format = "MARKDOWN",
                        exportMode = "FULL_EXPORT"
                    )
                }
            }
        }
        saveHistoryToDisk(historyList.value)
        isImportDeferred = false
        preImportSettings = null
        preImportHistory = null
    }

    private fun serializeCustomPrompts(prompts: List<CustomPrompt>): String {
        val arr = JSONArray()
        prompts.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("title", it.title)
                put("content", it.content)
            })
        }
        return arr.toString()
    }

    private fun deserializeCustomPrompts(json: String): List<CustomPrompt> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<CustomPrompt>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(CustomPrompt(
                    id = o.optString("id", ""),
                    title = o.optString("title", ""),
                    content = o.optString("content", "")
                ))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addCustomPrompt(title: String, content: String) {
        val id = "cp_" + System.currentTimeMillis() + "_" + (100..999).random()
        val newPrompt = CustomPrompt(
            id = id,
            title = title.ifBlank { "未命名提示词" },
            content = content
        )
        updateSettings {
            it.copy(
                customPrompts = it.customPrompts + newPrompt,
                selectedCustomPromptId = id,
                promptMode = "CUSTOM"
            )
        }
    }

    fun removeCustomPrompt(id: String) {
        updateSettings {
            val newList = it.customPrompts.filter { p -> p.id != id }
            val wasSelected = it.selectedCustomPromptId == id
            it.copy(
                customPrompts = newList,
                selectedCustomPromptId = if (wasSelected) "" else it.selectedCustomPromptId,
                promptMode = if (wasSelected && it.promptMode == "CUSTOM") "GENERAL" else it.promptMode
            )
        }
    }

    fun selectCustomPrompt(id: String) {
        updateSettings { it.copy(selectedCustomPromptId = id, promptMode = "CUSTOM") }
    }

    fun updateCustomPrompt(id: String, title: String, content: String) {
        updateSettings {
            val newList = it.customPrompts.map { p ->
                if (p.id == id) p.copy(
                    title = title.ifBlank { "未命名提示词" },
                    content = content
                ) else p
            }
            it.copy(customPrompts = newList)
        }
    }
}
