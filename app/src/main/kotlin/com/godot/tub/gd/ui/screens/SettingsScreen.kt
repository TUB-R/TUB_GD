
package com.godot.tub.gd.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.godot.tub.gd.AppSettings
import com.godot.tub.gd.utils.SourceMergeUtils
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
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    
    var showPathDialog by remember { mutableStateOf(false) }
    var showAddRuleDialog by remember { mutableStateOf(false) }
    var showSummaryDialog by remember { mutableStateOf(false) }
    var showPromptDialog by remember { mutableStateOf(false) }
    var showDefaultRulesDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var ruleToDelete by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    
    val scrollState = rememberScrollState()

    var tempPath by remember { mutableStateOf(settings.outputPath) }
    LaunchedEffect(settings.outputPath) { tempPath = settings.outputPath }

    val infiniteTransition = rememberInfiniteTransition(label = "arrow")
    val bounceOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "bounce"
    )

        val promptModeColor = when (settings.promptMode) {
        "GENERAL" -> Color(0xFF4CAF50) // 绿
        "ADVANCED" -> Color(0xFFFFC107) // 黄
        "TUB_STYLE" -> Color(0xFFF44336) // 红
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    }
    
    val promptModeText = when (settings.promptMode) {
        "NONE" -> "不注入（纯净模式）"
        "GENERAL" -> "通用开发"
        "ADVANCED" -> "进阶开发"
        "TUB_STYLE" -> "TUB 同款"
        else -> "未选择"
    }

    // === 弹窗区 ===


    if (showPathDialog) {
        AlertDialog(
            onDismissRequest = { showPathDialog = false },
            title = { Text("修改导出路径") },
            text = { OutlinedTextField(value = tempPath, onValueChange = { tempPath = it }, modifier = Modifier.fillMaxWidth(), singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateSettings { it.copy(outputPath = tempPath) }
                    showPathDialog = false
                    Toast.makeText(context, "路径已保存", Toast.LENGTH_SHORT).show()
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { tempPath = settings.outputPath; showPathDialog = false }) { Text("取消") } }
        )
    }

    if (showDeleteConfirmDialog && ruleToDelete != null) {
        val (rule, isHard) = ruleToDelete!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("确认删除") },
            text = { Text("是否删除${if (isHard) "重排除" else "软排除"}规则：\n$rule ？") },
            confirmButton = {
                TextButton(onClick = {
                    if (isHard) onUpdateSettings { it.copy(hardExcludeRules = it.hardExcludeRules - rule) }
                    else onUpdateSettings { it.copy(softExcludeRules = it.softExcludeRules - rule) }
                    showDeleteConfirmDialog = false
                    ruleToDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false; ruleToDelete = null }) { Text("取消") }
            }
        )
    }

    if (showDefaultRulesDialog) {
        AlertDialog(
            onDismissRequest = { showDefaultRulesDialog = false },
            title = { Text("系统默认规则 (只读)") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("为了确保扫描速度与 AI 纯净度，系统内置了以下不可修改的规则：", fontSize = 13.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text("底层重排除 (连路径都不显示)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(SourceMergeUtils.DEFAULT_HARD_DIRS.joinToString(", ") { "$it/" }, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("默认软排除 (保留路径，省略内容)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(SourceMergeUtils.DEFAULT_SOFT_EXTS.joinToString(", "), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            },
            confirmButton = { TextButton(onClick = { showDefaultRulesDialog = false }) { Text("我知道了") } }
        )
    }



    if (showAddRuleDialog) {
        var extInput by remember { mutableStateOf("") }
        var isHardMode by remember { mutableStateOf(false) }
        
        AlertDialog(
            onDismissRequest = { showAddRuleDialog = false },
            title = { Text("添加自定义过滤规则") },
            text = {
                Column {
                    Text("语法提示：\n• 文件夹请以 / 结尾，如 场景/ 或 .godot/\n• 支持中文及常规符号，不能包含空格或特殊字符", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = extInput, 
                        onValueChange = { extInput = it.trim() }, 
                        placeholder = { Text("例如: build/ 或 .log") }, 
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("选择过滤类型：", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { isHardMode = false }) {
                        RadioButton(selected = !isHardMode, onClick = { isHardMode = false })
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text("软排除 (推荐)", fontWeight = FontWeight.Bold)
                            Text("保留路径，但省略文件内容", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { isHardMode = true }) {
                        RadioButton(selected = isHardMode, onClick = { isHardMode = true })
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text("重排除", fontWeight = FontWeight.Bold)
                            Text("彻底隐藏，连路径也不显示", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val rule = extInput.trim()
                    if (rule.isNotEmpty()) {
                        val isDirectory = rule.endsWith("/")
                        val cleanRule = if (isDirectory) rule.dropLast(1) else rule
                        
                        // 简化非法字符校验：直接用字符串包含，避开正则转义地狱
                        val illegalChars = "\\:*?\"<>|"
                        
                        if (cleanRule.isEmpty()) {
                            Toast.makeText(context, "规则不能仅为斜杠", Toast.LENGTH_SHORT).show()
                        } else if (cleanRule.startsWith("/")) {
                            Toast.makeText(context, "规则不能以斜杠开头", Toast.LENGTH_SHORT).show()
                        } else if (cleanRule.any { it in illegalChars }) {
                            Toast.makeText(context, "包含系统不支持的特殊字符", Toast.LENGTH_SHORT).show()
                        } else if (cleanRule.contains(" ")) {
                            Toast.makeText(context, "规则不能包含空格", Toast.LENGTH_SHORT).show()
                        } else {
                            val allHard = SourceMergeUtils.DEFAULT_HARD_DIRS + settings.hardExcludeRules
                            val allSoft = SourceMergeUtils.DEFAULT_SOFT_EXTS + settings.softExcludeRules
                            
                            val existsInHard = if (isDirectory) allHard.any { it.removeSuffix("/") == cleanRule } else allHard.contains(cleanRule)
                            val existsInSoft = if (isDirectory) allSoft.any { it.removeSuffix("/") == cleanRule } else allSoft.contains(cleanRule)

                            if (existsInHard || existsInSoft) {
                                Toast.makeText(context, "该规则已存在，无需重复添加", Toast.LENGTH_SHORT).show()
                            } else {
                                if (isHardMode) onUpdateSettings { it.copy(hardExcludeRules = it.hardExcludeRules + rule) }
                                else onUpdateSettings { it.copy(softExcludeRules = it.softExcludeRules + rule) }
                                showAddRuleDialog = false
                            }
                        }
                    } else {
                        showAddRuleDialog = false
                    }
                }) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { showAddRuleDialog = false }) { Text("取消") } }
        )
    }

    if (showPromptDialog) {
        val promptModes = listOf(
            PromptModeOption("NONE", "不注入", "最适合让 AI 直接阅读并分析项目结构，或你想完全自定义提示词时使用。", MaterialTheme.colorScheme.onSurface),
            PromptModeOption("GENERAL", "通用开发", "扩展性极高，仅注入基本语法标准，适合简单的问答和自由发挥。", Color(0xFF4CAF50)),
            PromptModeOption("ADVANCED", "进阶开发", "如同高级工程师全方位重构代码，并针对 3D 场景与物理深度优化。", Color(0xFFFFC107)),
            PromptModeOption("TUB_STYLE", "TUB 同款", "在进阶基础上，强制 AI 生成 Python 一键构建脚本，效率翻三倍。", Color(0xFFF44336))
        )
        AlertDialog(
            onDismissRequest = { showPromptDialog = false },
            title = { Text("选择 AI 提示词模式") },
            text = {
                Column {
                    promptModes.forEach { option ->
                        val isSelected = (settings.promptMode == option.id)
                        Row(
                            modifier = Modifier.fillMaxWidth().selectable(selected = isSelected, onClick = {
                                onUpdateSettings { it.copy(promptMode = option.id) }
                                showPromptDialog = false
                            }).padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected, 
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = option.color)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = option.title, 
                                    color = if (isSelected) option.color else MaterialTheme.colorScheme.onSurface, 
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = option.desc,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPromptDialog = false }) { Text("取消") } }
        )
    }

    // === 主 UI ===
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 80.dp)
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
                modifier = Modifier.fillMaxWidth().clickable { tempPath = settings.outputPath; showPathDialog = true },
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
                        Text(settings.outputPath, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                onSelected = { onUpdateSettings { s -> s.copy(format = when(it) { 0 -> "TXT"; 1 -> "MARKDOWN"; else -> "XML" }) } }
            )
            Spacer(modifier = Modifier.height(24.dp))

            SectionTitle("输出模式", Icons.AutoMirrored.Filled.List)
            SegmentedControl(
                options = listOf("仅目录", "项目配置", "完整导出"),
                selectedIndex = when(settings.exportMode) { "DIR_ONLY" -> 0; "PROJECT_CONFIG" -> 1; "FULL_EXPORT" -> 2; else -> 0 },
                onSelected = { 
                    val m = when(it) { 0 -> "DIR_ONLY"; 1 -> "PROJECT_CONFIG"; else -> "FULL_EXPORT" }
                    onUpdateSettings { s -> s.copy(exportMode = m) } 
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when(settings.exportMode) {
                    "DIR_ONLY" -> "仅生成完整的文件目录树，不包含任何代码内容。"
                    "PROJECT_CONFIG" -> "仅提取 project.godot 和 .cfg 等核心配置文件。"
                    else -> "生成完整目录树及所有纯文本代码内容。"
                },
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("全局占用显示", color = MaterialTheme.colorScheme.onBackground)
                    Text("关闭时，仅软排除和超限的文件显示大小", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 12.sp)
                }
                Switch(
                    checked = settings.showGlobalFileSize,
                    onCheckedChange = { onUpdateSettings { s -> s.copy(showGlobalFileSize = it) } }
                )
            }

            if (settings.exportMode == "FULL_EXPORT") {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).clickable { showPromptDialog = true }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AI 创作提示词模式", color = MaterialTheme.colorScheme.onBackground)
                        Text(text = promptModeText, color = promptModeColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha=0.3f))
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("文件大小上限", color = MaterialTheme.colorScheme.onBackground)
                        Text("仅对文件夹项目扫描生效，多文件合并不受限", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                    var sizeText by remember { mutableStateOf(settings.maxFileSizeKb.toString()) }
                    LaunchedEffect(settings.maxFileSizeKb) { 
                        sizeText = settings.maxFileSizeKb.toString() 
                    }
                    OutlinedTextField(
                        value = sizeText,
                        onValueChange = { 
                            sizeText = it // 仅更新文本，不实时保存
                        },
                        modifier = Modifier.width(100.dp).onFocusChanged { focusState ->
                            if (!focusState.isFocused) {
                                // 失去焦点时统一处理保存或重置
                                val newVal = sizeText.toIntOrNull()
                                if (newVal != null && newVal > 0) {
                                    onUpdateSettings { s -> s.copy(maxFileSizeKb = newVal) }
                                } else {
                                    // 如果清空或非法输入，重置为上次的设置值
                                    sizeText = settings.maxFileSizeKb.toString()
                                }
                            }
                        }, 
                        singleLine = true, 
                        suffix = { Text("KB", fontSize = 12.sp) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            SectionTitle("排除规则", Icons.Default.FilterAlt)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showDefaultRulesDialog = true }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("系统默认规则 (点击查看详情)", color = Color.Gray, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("软排除 (保留路径，省略内容)", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    if (settings.softExcludeRules.isEmpty()) {
                        Text("暂无自定义软排除", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    } else {
                        FlowRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            settings.softExcludeRules.forEach { ext ->
                                InputChip(
                                    selected = false, 
                                    onClick = { ruleToDelete = Pair(ext, false); showDeleteConfirmDialog = true },
                                    label = { Text(ext, fontSize = 12.sp) },
                                    colors = InputChipDefaults.inputChipColors(
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        labelColor = MaterialTheme.colorScheme.primary
                                    ),
                                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "删除", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("重排除 (彻底隐藏)", color = MaterialTheme.colorScheme.error, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    if (settings.hardExcludeRules.isEmpty()) {
                        Text("暂无自定义重排除", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    } else {
                        FlowRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            settings.hardExcludeRules.forEach { ext ->
                                InputChip(
                                    selected = false, 
                                    onClick = { ruleToDelete = Pair(ext, true); showDeleteConfirmDialog = true },
                                    label = { Text(ext, fontSize = 12.sp) },
                                    colors = InputChipDefaults.inputChipColors(
                                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                                        labelColor = MaterialTheme.colorScheme.error
                                    ),
                                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "删除", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedButton(
                        onClick = { showAddRuleDialog = true }, 
                        modifier = Modifier.fillMaxWidth(), 
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("添加新的排除规则")
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
                    Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                            Text(">/<", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Godot整合工具", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("v1.8.2", color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.5f), fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.1f))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("by TUB-R · 点击访问 GitHub 仓库", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        if (scrollState.canScrollForward) {
            Box(
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 32.dp, end = 24.dp).offset(y = bounceOffset.dp)
                    .shadow(8.dp, CircleShape).background(MaterialTheme.colorScheme.primary, CircleShape).size(48.dp)
                    .clickable { scope.launch { scrollState.animateScrollTo(scrollState.maxValue) } },
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "滑动到底部", tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
    }
}

private data class PromptModeOption(val id: String, val title: String, val desc: String, val color: Color)
