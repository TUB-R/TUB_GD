
package com.godot.tub.gd.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.godot.tub.gd.ExportResult
import java.io.File

@Composable
fun ResultScreen(result: ExportResult, onBack: () -> Unit) {
    val context = LocalContext.current
    
    val onCopy = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Source Code", result.fullText))
        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }
    
    val onShareFile = {
        try {
            val file = File(result.filePath, result.fileName)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            
            val mimeType = when {
                result.fileName.endsWith(".md") -> "text/markdown"
                result.fileName.endsWith(".txt") -> "text/plain"
                result.fileName.endsWith(".xml") -> "text/xml"
                else -> "application/octet-stream"
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "分享文件"))
        } catch (e: Exception) {
            Toast.makeText(context, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onBackground) }
            Text("导出结果", color = MaterialTheme.colorScheme.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
        }

        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
            Box(modifier = Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF4B3B78), Color(0xFF222233)))).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(48.dp).background(Color.White.copy(alpha=0.9f), RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF9D7EE5))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(result.fileName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(result.filePath, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.background(Color.White.copy(alpha=0.2f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text("Godot ${result.godotVersion} 适配", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // 替换 Description 为 Edit
            StatCard("字符", "${result.charCount}", Icons.Default.Edit, Modifier.weight(1f))
            // 替换 Build 为 Settings
            StatCard("Token", "~${result.tokenCount}", Icons.Default.Settings, Modifier.weight(1f))
            // 替换 Code 为 List
            StatCard("行数", "${result.lineCount}", Icons.Default.List, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onCopy, 
                modifier = Modifier.weight(1f).height(50.dp), 
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                // 替换 ContentCopy 为 Create
                Icon(Icons.Default.Create, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("复制内容", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onShareFile, 
                modifier = Modifier.weight(1f).height(50.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("分享文件", color = MaterialTheme.colorScheme.onBackground)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Text("内容预览", color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))

        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(result.fullText, color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
fun StatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}
