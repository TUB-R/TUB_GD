package com.godot.tub.gd.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.godot.tub.gd.SelectedFile
import com.godot.tub.gd.AppSettings

@Composable
fun MultiFileScreen(
    pendingFiles: List<SelectedFile>,
    settings: AppSettings,
    onAddFile: (Uri) -> Unit,
    onRemoveFile: (SelectedFile) -> Unit,
    onClearAll: () -> Unit,
    onStartMerge: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { onAddFile(it) }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
        // 顶部栏
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onBackground) }
            Text("多文件合并", color = MaterialTheme.colorScheme.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp).weight(1f))
            if (pendingFiles.isNotEmpty()) {
                TextButton(onClick = onClearAll) {
                    Text("清空全部", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        
        // 极简提示文字
        Text(
            "多文件合并与文件夹扫描为两个独立工具。开始编写项目请使用主界面的“文件夹扫描”。",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 48.dp, bottom = 12.dp, end = 16.dp)
        )

        // 列表区
        if (pendingFiles.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("列表为空，请点击下方添加文件", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(pendingFiles) { file ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(file.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                            IconButton(onClick = { onRemoveFile(file) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "移除", tint = Color.Gray, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        // 底部信息与操作区
        Spacer(modifier = Modifier.height(16.dp))
        
        // 当前生效的设置 + 路径显示
        Column(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(settings.outputPath, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(modifier = Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha=0.2f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text(settings.format, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 动态显示生效设置
                val displayText = if (settings.showGlobalFileSize) {
                    "生效设置: 全局占用显示 · 排除规则已应用"
                } else {
                    "生效设置: 排除规则已应用"
                }
                Text(
                    displayText,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
                IconButton(onClick = onNavigateToSettings, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Settings, contentDescription = "设置", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 按钮
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { launcher.launch(arrayOf("*/*")) },
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("继续添加")
            }
            Button(
                onClick = onStartMerge,
                enabled = pendingFiles.isNotEmpty(),
                modifier = Modifier.weight(1f).height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("开始合并", fontWeight = FontWeight.Bold)
            }
        }
    }
}
