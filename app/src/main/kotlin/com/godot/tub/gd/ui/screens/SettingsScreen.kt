
package com.godot.tub.gd.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.godot.tub.gd.AppSettings
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPathDialog by remember { mutableStateOf(false) }
    var showAddExtDialog by remember { mutableStateOf(false) }
    var showSummaryDialog by remember { mutableStateOf(false) } // 新增项目概要弹窗状态
    
    val scrollState = rememberScrollState()

    var tempPath by remember { mutableStateOf(settings.outputPath) }
    LaunchedEffect(settings.outputPath) { tempPath = settings.outputPath }

    // Q弹动画
    val infiniteTransition = rememberInfiniteTransition(label = "arrow")
    val bounceOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    if (showPathDialog) {
        AlertDialog(
            onDismissRequest = { showPathDialog = false },
            title = { Text("修改导出路径") },
            text = { 
                OutlinedTextField(
                    value = tempPath, 
                    onValueChange = { tempPath = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                ) 
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateSettings { it.copy(outputPath = tempPath) }
                    showPathDialog = false
                    Toast.makeText(context, "路径已保存", Toast.LENGTH_SHORT).show()
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { 
                tempPath = settings.outputPath
                showPathDialog = false 
            }) { Text("取消") } }
        )
    }

    if (showAddExtDialog) {
        var extInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddExtDialog = false },
            title = { Text("添加自定义过滤后缀") },
            text = {
                Column {
                    Text("输入后缀名（如 log, csv），若被过滤，目录保留但内容会省略。", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = extInput, onValueChange = { extInput = it.trim() }, placeholder = { Text("例如: log") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (extInput.isNotBlank()) {
                        val formattedExt = if (extInput.startsWith(".")) extInput else ".$extInput"
                        onUpdateSettings { it.copy(customExcludeExts = it.customExcludeExts + formattedExt.lowercase(Locale.ROOT)) }
                    }
                    showAddExtDialog = false
                }) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { showAddExtDialog = false }) { Text("取消") } }
        )
    }

    if (showSummaryDialog) {
        AlertDialog(
            onDismissRequest = { showSummaryDialog = false },
            title = { Text("项目概要") },
            text = { Text("暂时没有设计 awa") },
            confirmButton = {
                TextButton(onClick = { showSummaryDialog = false }) { Text("知道了") }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 80.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onBackground) }
                Text("设置", color = MaterialTheme.colorScheme.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            }

            SectionTitle("外观", Icons.Default.Palette)
            SegmentedControl(
                options = listOf("跟随系统", "浅色", "深色"),
                selectedIndex = settings.themeMode,
                onSelected = { onUpdateSettings { s -> s.copy(themeMode = it) } },
                icons = listOf(Icons.Default.Settings, Icons.Default.WbSunny, Icons.Default.NightsStay)
            )
            Spacer(modifier = Modifier.height(24.dp))

            SectionTitle("导出路径", Icons.Default.Folder)
            Card(
                modifier = Modifier.fillMaxWidth().clickable { 
                    tempPath = settings.outputPath
                    showPathDialog = true 
                },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary.copy(alpha=0.2f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("输出文件夹", color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            text = settings.outputPath, 
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), 
                            fontSize = 12.sp, 
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { 
                        onUpdateSettings { it.copy(outputPath = "/storage/emulated/0/Documents/") }
                        tempPath = "/storage/emulated/0/Documents/"
                        Toast.makeText(context, "已恢复默认路径", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "恢复默认", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            SectionTitle("输出格式", Icons.Default.Description)
            SegmentedControl(
                options = listOf("TXT 纯文本", "MARKDOWN", "XML"),
                selectedIndex = when(settings.format) { "TXT" -> 0; "MARKDOWN" -> 1; "XML" -> 2; else -> 1 },
                onSelected = { 
                    val f = when(it) { 0 -> "TXT"; 1 -> "MARKDOWN"; else -> "XML" }
                    onUpdateSettings { s -> s.copy(format = f) } 
                }
            )
            Spacer(modifier = Modifier.height(24.dp))

            SectionTitle("输出模式", Icons.AutoMirrored.Filled.List)
            SegmentedControl(
                options = listOf("项目概要", "仅目录", "完整导出"),
                selectedIndex = when(settings.exportMode) { "PROJECT_SUMMARY" -> 0; "DIR_ONLY" -> 1; "FULL_EXPORT" -> 2; else -> 1 },
                onSelected = { 
                    val m = when(it) { 0 -> "PROJECT_SUMMARY"; 1 -> "DIR_ONLY"; else -> "FULL_EXPORT" }
                    if (m == "PROJECT_SUMMARY") {
                        showSummaryDialog = true // 核心修复：点击时触发弹窗
                    }
                    onUpdateSettings { s -> s.copy(exportMode = m) } 
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when(settings.exportMode) {
                    "PROJECT_SUMMARY" -> "仅生成项目体量统计，不包含目录与代码内容。"
                    "DIR_ONLY" -> "仅生成完整文件目录树，不包含代码内容。"
                    else -> "生成完整目录树及所有纯文本代码内容。"
                },
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp)
            )

            if (settings.exportMode == "FULL_EXPORT") {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("注入 AI 创作提示词", color = MaterialTheme.colorScheme.onBackground)
                        Text("追加 Godot 专属开发约束", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                    Switch(
                        checked = settings.injectPrompt,
                        onCheckedChange = { onUpdateSettings { s -> s.copy(injectPrompt = it) } }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("文件大小上限", color = MaterialTheme.colorScheme.onBackground)
                        Text("超过此大小文件内容将被忽略", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                    OutlinedTextField(
                        value = settings.maxFileSizeKb.toString(),
                        onValueChange = { 
                            val newVal = it.toIntOrNull()
                            if (newVal != null) onUpdateSettings { s -> s.copy(maxFileSizeKb = newVal) }
                        },
                        modifier = Modifier.width(100.dp),
                        singleLine = true,
                        suffix = { Text("KB", fontSize = 12.sp) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            SectionTitle("默认智能规则", Icons.Default.AutoAwesome)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha=0.5f), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("保留全量路径，仅提取文本", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("自动忽略 .git, build 等无意义目录；SVG/代码文件正常提取，图片/二进制文件在目录中保留路径，内容转为占位符。", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            SectionTitle("自定义过滤后缀", Icons.Default.FilterAlt)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (settings.customExcludeExts.isEmpty()) {
                        Text("暂无自定义过滤后缀", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 12.sp)
                    } else {
                        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            settings.customExcludeExts.forEach { ext ->
                                InputChip(
                                    selected = false,
                                    onClick = { onUpdateSettings { it.copy(customExcludeExts = it.customExcludeExts - ext) } },
                                    label = { Text(ext, fontSize = 12.sp) },
                                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "删除", modifier = Modifier.size(14.dp)) }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(onClick = { showAddExtDialog = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("添加后缀")
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                SectionTitle("关于", Icons.Default.Info)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().clickable { 
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/TUB-R/TUB_GD"))
                        context.startActivity(intent)
                    }
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(), 
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.primary, CircleShape), 
                            contentAlignment = Alignment.Center
                        ) {
                            Text(">/<", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Godot整合工具", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("v1.0.0", color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.5f), fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.1f))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "智能源码整合工具，帮助开发者将项目源码整合为精简文本，最大化利用 AI 上下文窗口",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "by TUB-R · 点击访问 GitHub 仓库",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        // 右下角 Q弹悬浮箭头
        if (scrollState.canScrollForward) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 32.dp, end = 24.dp)
                    .offset(y = bounceOffset.dp)
                    .shadow(8.dp, CircleShape)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .size(48.dp)
                    .clickable { 
                        scope.launch { 
                            scrollState.animateScrollTo(scrollState.maxValue) 
                        } 
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "滑动到底部",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
