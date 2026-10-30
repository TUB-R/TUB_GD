
package com.godot.tub.gd

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.unit.dp
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
import com.godot.tub.gd.ui.screens.MultiFileScreen
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
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE), 100)
            }
        }

        val viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        viewModel.init(applicationContext)
        
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

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    val screen by viewModel.currentScreen.collectAsState()
    val result by viewModel.result.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val historyList by viewModel.historyList.collectAsState()
    val context = LocalContext.current

    BackHandler(enabled = screen != "home") {
        if (screen == "settings") {
            viewModel.currentScreen.value = viewModel.previousScreen
        } else {
            viewModel.currentScreen.value = "home"
        }
    }

    when (screen) {
        "home" -> HomeScreen(
            outputPath = settings.outputPath,
            format = settings.format,
            historyList = historyList,
            onFolderSelected = { uri -> viewModel.generateFromTreeUri(context, uri) },
            onNavigateToMultiFile = { viewModel.currentScreen.value = "multi_files" },
            onHistoryItemClick = { item -> viewModel.viewHistoryItem(item) },
            onClearHistory = { viewModel.clearHistory() },
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
