
package com.godot.tub.gd

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import com.godot.tub.gd.ui.screens.HomeScreen
import com.godot.tub.gd.ui.screens.ResultScreen
import com.godot.tub.gd.ui.screens.SettingsScreen
import com.godot.tub.gd.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }

        val viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        viewModel.init(applicationContext)
        
        setContent {
            val settings by viewModel.settings.collectAsState()
            val isSystemDark = isSystemInDarkTheme()
            val darkTheme = when(settings.themeMode) {
                1 -> false
                2 -> true
                else -> isSystemDark
            }

            var showVersionWarning by remember { mutableStateOf(false) }
            val result by viewModel.result.collectAsState()
            
            // 检测到未知版本时触发弹窗
            LaunchedEffect(result) {
                if (result != null && result?.godotVersion == "Unknown") {
                    showVersionWarning = true
                }
            }

            AppTheme(darkTheme = darkTheme) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    AppNavigation(viewModel)
                    
                    val isLoading by viewModel.isLoading.collectAsState()
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    if (showVersionWarning) {
                        AlertDialog(
                            onDismissRequest = { showVersionWarning = false },
                            title = { Text("配置检测提示") },
                            text = { Text("未能准确识别出具体的 Godot 编辑器版本。建议先在 Godot 中打开项目并创建一个默认场景保存一次，以便生成完整的配置文件。\n\n提示词已自动调整为兼容模式，AI 将会通过阅读配置文件自行判断版本。") },
                            confirmButton = {
                                TextButton(onClick = { showVersionWarning = false }) { Text("知道了") }
                            }
                        )
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

    when (screen) {
        "home" -> HomeScreen(
            outputPath = settings.outputPath,
            format = settings.format,
            historyList = historyList,
            onFolderSelected = { uri -> viewModel.generateFromTreeUri(context, uri) },
            onFilesSelected = { uris -> viewModel.generateFromMultipleFiles(context, uris) },
            onHistoryItemClick = { item -> viewModel.viewHistoryItem(item) },
            onClearHistory = { viewModel.clearHistory() },
            onNavigateToSettings = { viewModel.currentScreen.value = "settings" }
        )
        "settings" -> SettingsScreen(
            settings = settings,
            onUpdateSettings = { viewModel.updateSettings(it) },
            onBack = { viewModel.currentScreen.value = "home" }
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
