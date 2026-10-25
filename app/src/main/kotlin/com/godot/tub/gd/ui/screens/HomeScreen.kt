
package com.godot.tub.gd.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.godot.tub.gd.ExportResult
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(
    outputPath: String,
    format: String,
    historyList: List<ExportResult>,
    onFolderSelected: (Uri) -> Unit,
    onFilesSelected: (List<Uri>) -> Unit,
    onHistoryItemClick: (ExportResult) -> Unit,
    onClearHistory: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) } // 清空二次确认弹窗

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { onFolderSelected(it) }
    }
    val filesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onFilesSelected(uris)
    }

    // 清空二次确认弹窗
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("清空历史记录") },
            text = { Text("确定要清空所有历史记录吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = { 
                    onClearHistory()
                    showClearConfirmDialog = false
                    showHistoryDialog = false
                }) {
                    Text("确定清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) { Text("取消") }
            }
        )
    }

    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = { Text("历史记录") }, // 纯文字，无表情
            text = {
                if (historyList.isEmpty()) {
                    Text("暂无历史记录", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(historyList) { item ->
                            val date = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(item.timestamp))
                            Column(
                                modifier = Modifier.fillMaxWidth().clickable { 
                                    onHistoryItemClick(item)
                                    showHistoryDialog = false
                                }.padding(vertical = 12.dp)
                            ) {
                                Text(item.fileName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("$date · ${item.tokenCount} Tokens", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        }
                    }
                }
            },
            dismissButton = {
                if (historyList.isNotEmpty()) {
                    TextButton(onClick = { showClearConfirmDialog = true }) {
                        Text("清空", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistoryDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = { showHistoryDialog = true }) { 
                Icon(Icons.Default.History, contentDescription = "历史", tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)) 
            }
            IconButton(onClick = onNavigateToSettings) { 
                Icon(Icons.Default.Settings, contentDescription = "设置", tint = MaterialTheme.colorScheme.onBackground) 
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier.size(110.dp).background(Brush.linearGradient(listOf(Color(0xFF5C8DF6), Color(0xFF4A90E2))), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(">/<", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Godot整合工具", color = MaterialTheme.colorScheme.onBackground, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("智能源码整合，一键生成\nAI 友好的精简上下文", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)

        Spacer(modifier = Modifier.height(48.dp))

        ActionCard("选择项目文件夹", "扫描整个项目目录，智能过滤并整合", Icons.Default.Folder, Color(0xFF5C8DF6)) { 
            folderLauncher.launch(null) 
        }
        Spacer(modifier = Modifier.height(16.dp))
        ActionCard("选择单个文件", "快速整合单个或多个源码文件", Icons.Default.Description, Color(0xFF4A90E2)) { 
            filesLauncher.launch(arrayOf("*/*")) 
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("导出至", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 12.sp)
                    Text(outputPath, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
            Box(modifier = Modifier.padding(start = 8.dp).background(MaterialTheme.colorScheme.primary.copy(alpha=0.2f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(format, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
