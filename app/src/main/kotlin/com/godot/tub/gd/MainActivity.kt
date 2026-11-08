package com.tub.code.md

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tub.code.md.ui.screens.HomeScreen
import com.tub.code.md.ui.screens.ResultScreen
import com.tub.code.md.ui.screens.MultiFileScreen
import com.tub.code.md.ui.screens.SettingsScreen
import com.tub.code.md.ui.screens.OnboardingScreen
import com.tub.code.md.ui.screens.PermissionFixScreen
import com.tub.code.md.ui.theme.AppTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        viewModel.init(applicationContext)
        
        // 仅已完成引导 + 使用外部存储的用户才自动申请权限
        if (viewModel.settings.value.onboardingCompleted &&
            viewModel.settings.value.outputPath != "APP_INTERNAL_STORAGE"
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                }
            } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE), 100)
                }
            }
        }
        
        setContent {
            val context = androidx.compose.ui.platform.LocalContext.current
            androidx.compose.runtime.LaunchedEffect(Unit) {
                viewModel.errorMessage.collect { msg ->
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                }
            }
            val settings by viewModel.settings.collectAsState()
            val isSystemDark = isSystemInDarkTheme()
            val darkTheme = when(settings.themeMode) {
                1 -> false
                2 -> true
                else -> isSystemDark
            }

            var showPermissionDialog by remember { mutableStateOf(false) }
            
            LaunchedEffect(Unit) {
                viewModel.showPermissionError.collect {
                    showPermissionDialog = true
                }
            }

            if (showPermissionDialog) {
                AlertDialog(
                    onDismissRequest = { showPermissionDialog = false },
                    title = { Text("创建文件失败") },
                    text = { Text("写入文件时发生错误，可能是没有授予存储权限，或系统限制了写入操作。") },
                    confirmButton = {
                        TextButton(onClick = { showPermissionDialog = false }) { Text("确定") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showPermissionDialog = false
                            viewModel.previousScreen = viewModel.currentScreen.value
                            viewModel.currentScreen.value = "permission_fix"
                        }) { Text("查看修复") }
                    }
                )
            }

            var showVersionWarning by remember { mutableStateOf(false) }
            val result by viewModel.result.collectAsState()
            
            LaunchedEffect(result) {
                val currentPromptMode = viewModel.settings.value.promptMode
                val isGodotMode = currentPromptMode == "GENERAL" || 
                    currentPromptMode == "ADVANCED" || 
                    currentPromptMode == "TUB_GD"
                if (result != null && result?.godotVersion == "Unknown" && isGodotMode) {
                    showVersionWarning = true
                } else {
                    showVersionWarning = false
                }
            }

            if (showVersionWarning) {
                AlertDialog(
                    onDismissRequest = { showVersionWarning = false },
                    icon = { Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFFFC107)) },
                    title = { Text("未检测到 Godot 版本") },
                    text = {
                        Column {
                            Text(
                                "在当前项目根目录下未找到 project.godot，或无法从 config/features 中解析出版本号。",
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "本次生成将默认采用 Godot 4.x 通用规范，不会应用 4.4+ / 4.6+ / 4.7+ 的针对性微调。",
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "若需获取精准的版本微调，请确保项目为 Godot 项目，或手动在 project.godot 中添加版本标识。",
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showVersionWarning = false }) {
                            Text("我知道了")
                        }
                    }
                )
            }

            AppTheme(darkTheme = darkTheme) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    if (!settings.onboardingCompleted) {
                        OnboardingScreen(
                            settings = settings,
                            onImportConfig = { uri, cb -> viewModel.importConfig(context, uri, cb) },
                            onCancelImport = { viewModel.cancelImport() },
                            onComplete = { path, preferredMode -> viewModel.completeOnboarding(path, preferredMode) }
                        )
                    } else {
                        AppNavigation(viewModel)
                        
                        val isLoading by viewModel.isLoading.collectAsState()
                        if (isLoading) {
                            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("正在扫描与合并，请稍候...", color = MaterialTheme.colorScheme.onBackground)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    val screen by viewModel.currentScreen.collectAsState()
    val result by viewModel.result.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val historyList by viewModel.historyList.collectAsState()
    val context = LocalContext.current
    val pendingBinary by viewModel.pendingBinaryConfirm.collectAsState()

        if (pendingBinary != null) {
        val pb = pendingBinary!!
        AlertDialog(
            onDismissRequest = { viewModel.cancelBinaryConfirm() },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("发现疑似二进制文件") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "扫描时发现以下文件内容疑似二进制（检测到 0x00 字节）：",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 160.dp)) {
                        items(pb.binaryFiles) { f ->
                            Text(
                                f,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "如果按文本强行提取，可能出现大量乱码、浪费 Token，并可能误导 AI 对项目结构的理解。",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    val extsPreview = pb.binaryFiles.mapNotNull { path ->
                        val name = path.substringAfterLast("/")
                        val dotIdx = name.lastIndexOf('.')
                        if (dotIdx > 0 && dotIdx < name.length - 1) {
                            name.substring(dotIdx).lowercase()
                        } else null
                    }.distinct()
                    if (extsPreview.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "「一键添加」会把这些后缀加入软排除（保留路径、省略内容）并自动重新扫描：",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (ext in extsPreview) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        ext,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                val extsInBtn = pb.binaryFiles.mapNotNull { path ->
                    val name = path.substringAfterLast("/")
                    val dotIdx = name.lastIndexOf('.')
                    if (dotIdx > 0 && dotIdx < name.length - 1) {
                        name.substring(dotIdx).lowercase()
                    } else null
                }.distinct()
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { viewModel.cancelBinaryConfirm() }) {
                        Text(
                            "取消",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    if (extsInBtn.isNotEmpty()) {
                        TextButton(onClick = { viewModel.confirmBinaryAddExclude(context) }) {
                            Text("一键添加", fontWeight = FontWeight.Bold)
                        }
                    }
                    TextButton(onClick = { viewModel.confirmBinaryContinue(context) }) {
                        Text(
                            "仍然继续",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100)
                        )
                    }
                }
            }
        )
    }

    BackHandler(enabled = screen != "home") {
        if (screen == "settings") {
            viewModel.currentScreen.value = viewModel.previousScreen
        } else if (screen == "permission_fix") {
            viewModel.currentScreen.value = viewModel.previousScreen
        } else {
            viewModel.currentScreen.value = "home"
        }
    }

    when (screen) {
        "home" -> HomeScreen(
            outputPath = settings.outputPath,
            format = settings.format,
            exportMode = settings.exportMode,
            promptMode = settings.promptMode,
            customPromptPath = settings.customPromptPath,
            customPrompts = settings.customPrompts,
            selectedCustomPromptId = settings.selectedCustomPromptId,
            maxHistoryCount = settings.maxHistoryCount,
            historyList = historyList,
            onFolderSelected = { uri, ignorePrompt -> viewModel.generateFromTreeUri(context, uri, ignorePrompt) },
            onNavigateToMultiFile = { viewModel.currentScreen.value = "multi_files" },
            onHistoryItemClick = { item -> viewModel.viewHistoryItem(item) },
            onClearHistory = { callback -> viewModel.clearHistory(callback) },
            onNavigateToSettings = { 
                viewModel.previousScreen = "home"
                viewModel.currentScreen.value = "settings" 
            }
        )
        "multi_files" -> MultiFileScreen(
            pendingFiles = viewModel.pendingMultiFiles.collectAsState().value,
            settings = settings,
            onAddFile = { viewModel.addPendingMultiFile(context, it) },
            onRemoveFile = { viewModel.removePendingMultiFile(it) },
            onClearAll = { viewModel.clearPendingMultiFiles() },
            onStartMerge = { viewModel.startMultiFileMerge(context) },
            onNavigateToSettings = { 
                viewModel.previousScreen = "multi_files"
                viewModel.currentScreen.value = "settings" 
            },
            onBack = { viewModel.currentScreen.value = "home" }
        )
        "settings" -> SettingsScreen(
            settings = settings,
            onUpdateSettings = { viewModel.updateSettings(it) },
            onBack = { viewModel.currentScreen.value = "home" },
            onExportConfig = { uri, callback -> viewModel.exportConfig(context, uri, callback) },
            onImportConfig = { uri, callback -> viewModel.importConfig(context, uri, callback) },
            onAddCustomPrompt = { title, content -> viewModel.addCustomPrompt(title, content) },
            onRemoveCustomPrompt = { id -> viewModel.removeCustomPrompt(id) },
            onSelectCustomPrompt = { id -> viewModel.selectCustomPrompt(id) },
            onUpdateCustomPrompt = { id, title, content -> viewModel.updateCustomPrompt(id, title, content) },
            onCancelImport = { viewModel.cancelImport() }
        )
        "permission_fix" -> PermissionFixScreen(
            onBack = { viewModel.currentScreen.value = viewModel.previousScreen },
            onApplyInternal = {
                viewModel.updateSettings { it.copy(outputPath = "APP_INTERNAL_STORAGE") }
                viewModel.currentScreen.value = viewModel.previousScreen
                android.widget.Toast.makeText(context, "已切换为应用内部存储", android.widget.Toast.LENGTH_SHORT).show()
            }
        )
        "result" -> {
            if (result != null) {
                ResultScreen(
                    result = result!!,
                    onBack = { viewModel.currentScreen.value = "home" }
                )
            }
        }
    }
}
