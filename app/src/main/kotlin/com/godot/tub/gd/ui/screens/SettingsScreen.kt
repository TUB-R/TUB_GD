package com.tub.code.md.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tub.code.md.AppSettings
import com.tub.code.md.MainViewModel
import com.tub.code.md.ImportResult
import com.tub.code.md.CustomPrompt
import com.tub.code.md.utils.SourceMergeUtils
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit,
    onExportConfig: (android.net.Uri, (String) -> Unit) -> Unit = { _, _ -> },
    onImportConfig: (android.net.Uri, (ImportResult) -> Unit) -> Unit = { _, _ -> },
    onAddCustomPrompt: (String, String) -> Unit = { _, _ -> },
    onRemoveCustomPrompt: (String) -> Unit = {},
    onSelectCustomPrompt: (String) -> Unit = {},
    onCancelImport: () -> Unit = {},
    onUpdateCustomPrompt: (String, String, String) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var showAddPromptDialog by remember { mutableStateOf(false) }
    var promptToDelete by remember { mutableStateOf<CustomPrompt?>(null) }
    var previewCustomPromptId by remember { mutableStateOf<String?>(null) }
    var editingPrompt by remember { mutableStateOf<CustomPrompt?>(null) }
    var showEditPromptDialog by remember { mutableStateOf(false) }
    var pendingImportResult by remember { mutableStateOf<ImportResult?>(null) }
    var showAndroidEnableDialog by remember { mutableStateOf(false) }
    var showAndroidDisableDialog by remember { mutableStateOf(false) }

    val exportConfigLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            onExportConfig(uri) { msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }
    val importConfigLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportConfig(uri) { result ->
                pendingImportResult = result
            }
        }
    }

    var showPathDialog by remember { mutableStateOf(false) }
    var showAddRuleDialog by remember { mutableStateOf(false) }
    var showSummaryDialog by remember { mutableStateOf(false) }
    var showPromptDialog by remember { mutableStateOf(false) }
    var showPromptPreviewDialog by remember { mutableStateOf(false) }
    var previewPromptMode by remember { mutableStateOf("GENERAL") }
    var showDefaultRulesDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var ruleToDelete by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    var showInvalidPromptPathDialog by remember { mutableStateOf(false) }
    var invalidPathValue by remember { mutableStateOf("") }
    
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
        "GENERAL" -> Color(0xFF4CAF50)
        "ADVANCED" -> Color(0xFFFFC107)
        "TUB_GD" -> Color(0xFFF44336)
        "TUB_APP" -> Color(0xFF9C27B0)
        "TUB_PY" -> Color(0xFF009688)
        "CUSTOM" -> Color(0xFF2196F3)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    }
    
    val promptModeText = when (settings.promptMode) {
        "NONE" -> "不注入（纯净模式）"
        "GENERAL" -> "通用开发"
        "ADVANCED" -> "进阶开发"
        "TUB_GD" -> "TUB_GD"
        "TUB_APP" -> "TUB_APP"
        "TUB_PY" -> "TUB_PY"
        "CUSTOM" -> "自定义提示词"
        else -> "未选择"
    }

    val previewModeTitle = when (previewPromptMode) {
        "NONE" -> "不注入"
        "GENERAL" -> "通用开发"
        "ADVANCED" -> "进阶开发"
        "TUB_GD" -> "TUB_GD"
        "TUB_APP" -> "TUB_APP"
        "TUB_PY" -> "TUB_PY"
        "CUSTOM" -> "自定义提示词"
        else -> previewPromptMode
    }

    if (pendingImportResult != null) {
        val r = pendingImportResult!!
                val allGood = r.failedItems.isEmpty() && r.versionMatch
        AlertDialog(
            onDismissRequest = { /* 强制二选一 */ },
            icon = {
                Icon(
                    if (allGood) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = when {
                        allGood -> Color(0xFF4CAF50)
                        !r.versionMatch -> Color(0xFFFF9800)
                        else -> MaterialTheme.colorScheme.error
                    }
                )
            },
            title = { Text("导入成功 [${r.successCount}/${r.totalCount}]") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    // 版本状态
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (r.versionMatch) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (r.versionMatch) Color(0xFF4CAF50) else Color(0xFFFF9800),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (r.versionMatch) "版本匹配：v${r.currentVersion}"
                            else "版本不匹配：配置 v${r.configVersion} · 当前 v${r.currentVersion}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 17.sp,
                            color = if (r.versionMatch) Color(0xFF4CAF50) else Color(0xFFE65100)
                        )
                    }

                    // 未知属性警告
                    if (r.warningItems.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        r.warningItems.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFFF9800).copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFFF9800),
                                    modifier = Modifier.size(15.dp).padding(top = 1.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    item,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    color = Color(0xFFE65100)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    if (r.failedItems.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        r.failedItems.forEach { item ->
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    Icons.Default.Cancel,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(15.dp).padding(top = 2.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "失败 $item",
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    // 完全失败提示
                    if (r.successItems.isEmpty() && r.failedItems.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "没有任何内容成功解析，建议撤销并重新选择配置文件。",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    if (!allGood) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "如不满意，可撤销回到导入前的状态。",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingImportResult = null
                }) { Text("保留") }
            },
            dismissButton = {
                TextButton(onClick = {
                    onCancelImport()
                    pendingImportResult = null
                    Toast.makeText(context, "已回滚到导入前状态", Toast.LENGTH_SHORT).show()
                }) { Text("撤销", color = MaterialTheme.colorScheme.error) }
            }
        )
    }

    if (showInvalidPromptPathDialog) {
        AlertDialog(
            onDismissRequest = { showInvalidPromptPathDialog = false },
            icon = { Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("无效的文件路径") },
            text = {
                Column {
                    Text("仅支持 .txt 或 .md 格式的提示词文件。", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "你输入的路径：\n$invalidPathValue",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("输入框已自动清空，请重新填写。", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }
            },
            confirmButton = {
                TextButton(onClick = { showInvalidPromptPathDialog = false }) { Text("知道了") }
            }
        )
    }

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

    if (showAddPromptDialog) {
        var newTitle by remember { mutableStateOf("") }
        var newContent by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddPromptDialog = false },
            title = { Text("添加提示词") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("标题") },
                        placeholder = { Text("例如：我的 Godot 开发规范") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newContent,
                        onValueChange = { newContent = it },
                        label = { Text("提示词内容") },
                        placeholder = { Text("在这里粘贴或输入提示词内容...") },
                        minLines = 8,
                        maxLines = 15,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "添加后会出现在提示词列表末尾，带「自定义」标签，可随时删除。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newContent.isBlank()) {
                            Toast.makeText(context, "提示词内容不能为空", Toast.LENGTH_SHORT).show()
                        } else {
                            onAddCustomPrompt(newTitle, newContent)
                            Toast.makeText(context, "已添加提示词", Toast.LENGTH_SHORT).show()
                            showAddPromptDialog = false
                        }
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showAddPromptDialog = false }) { Text("取消") }
            }
        )
    }

    if (promptToDelete != null) {
        val target = promptToDelete!!
        AlertDialog(
            onDismissRequest = { promptToDelete = null },
            title = { Text("删除提示词") },
            text = { Text("确定删除「${target.title}」吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveCustomPrompt(target.id)
                    promptToDelete = null
                    Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { promptToDelete = null }) { Text("取消") }
            }
        )
    }

    if (showEditPromptDialog && editingPrompt != null) {
        val editing = editingPrompt!!
        var editTitle by remember(editing.id) { mutableStateOf(editing.title) }
        var editContent by remember(editing.id) { mutableStateOf(editing.content) }
        AlertDialog(
            onDismissRequest = { showEditPromptDialog = false; editingPrompt = null },
            title = { Text("编辑提示词") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("标题") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editContent,
                        onValueChange = { editContent = it },
                        label = { Text("提示词内容") },
                        minLines = 8,
                        maxLines = 15,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            onRemoveCustomPrompt(editing.id)
                            showEditPromptDialog = false
                            editingPrompt = null
                            Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("删除此提示词", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editContent.isBlank()) {
                        Toast.makeText(context, "提示词内容不能为空", Toast.LENGTH_SHORT).show()
                    } else {
                        onUpdateCustomPrompt(editing.id, editTitle, editContent)
                        showEditPromptDialog = false
                        editingPrompt = null
                        Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("保存", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showEditPromptDialog = false
                    editingPrompt = null
                }) { Text("取消") }
            }
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
                    Text("语法提示：\n• 文件夹请以 / 结尾，如 场景/ 或 .godot/\n• 支持中文及常规符号，不能包含空格或特殊字符\n• 支持换行批量输入，每行一条规则", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = extInput, 
                        onValueChange = { extInput = it }, 
                        placeholder = { Text("例如:\nbuild/\n.log\n场景/") }, 
                        singleLine = false,
                        minLines = 3,
                        maxLines = 8,
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
                    val lines = extInput.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    if (lines.isEmpty()) {
                        showAddRuleDialog = false
                        return@TextButton
                    }

                    val illegalChars = "\\:*?\"<>|"
                    val existingHard = SourceMergeUtils.DEFAULT_HARD_DIRS + settings.hardExcludeRules
                    val existingSoft = SourceMergeUtils.DEFAULT_SOFT_EXTS + settings.softExcludeRules

                    val validRules = mutableListOf<String>()
                    val skippedCount = intArrayOf(0)

                    lines.forEach { rule ->
                        val isDirectory = rule.endsWith("/")
                        val cleanRule = if (isDirectory) rule.dropLast(1) else rule

                        when {
                            cleanRule.isEmpty() -> skippedCount[0]++
                            cleanRule.startsWith("/") -> skippedCount[0]++
                            cleanRule.any { it in illegalChars } -> skippedCount[0]++
                            cleanRule.contains(" ") -> skippedCount[0]++
                            else -> {
                                val existsInHard = if (isDirectory) existingHard.any { it.removeSuffix("/") == cleanRule } else existingHard.contains(cleanRule)
                                val existsInSoft = if (isDirectory) existingSoft.any { it.removeSuffix("/") == cleanRule } else existingSoft.contains(cleanRule)

                                if (existsInHard || existsInSoft || validRules.contains(rule)) {
                                    skippedCount[0]++
                                } else {
                                    validRules.add(rule)
                                }
                            }
                        }
                    }

                    if (validRules.isEmpty()) {
                        Toast.makeText(context, "没有可添加的有效规则", Toast.LENGTH_SHORT).show()
                    } else {
                        if (isHardMode) {
                            onUpdateSettings { it.copy(hardExcludeRules = it.hardExcludeRules + validRules) }
                        } else {
                            onUpdateSettings { it.copy(softExcludeRules = it.softExcludeRules + validRules) }
                        }
                        if (skippedCount[0] > 0) {
                            Toast.makeText(context, "已添加 ${validRules.size} 条，跳过 ${skippedCount[0]} 条无效/重复规则", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "已添加 ${validRules.size} 条规则", Toast.LENGTH_SHORT).show()
                        }
                        showAddRuleDialog = false
                    }
                }) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { showAddRuleDialog = false }) { Text("取消") } }
        )
    }

    if (showPromptDialog) {
        val noneMode = listOf(
            PromptModeOption("NONE", "不注入", "完全不注入提示词，仅输出目录结构与代码内容。", MaterialTheme.colorScheme.onSurface)
        )
        val godotModes = listOf(
            PromptModeOption("GENERAL", "通用开发", "基础开发规范：技术红线 + 开发指南 + 代码输出规范。", Color(0xFF4CAF50)),
            PromptModeOption("ADVANCED", "进阶开发", "通用基础 + 改动前必问（缓存 / 重启 / 时序 / 多端一致性）。", Color(0xFFFFC107)),
            PromptModeOption("TUB_GD", "TUB_GD", "Godot 专用：输出 Python 脚本，Tab 缩进，注解陷阱提醒。", Color(0xFFF44336))
        )
        val otherModes = listOf(
            PromptModeOption("TUB_APP", "TUB_APP", "Android 专用：输出 Python 脚本，4 空格缩进，Java / Kotlin 语法。", Color(0xFF9C27B0)),
            PromptModeOption("TUB_PY", "TUB_PY", "任意项目通用：输出 Python 脚本，自动识别缩进，安全原子写入。", Color(0xFF009688))
        )
        AlertDialog(
            onDismissRequest = { showPromptDialog = false },
            title = { Text("选择 AI 提示词模式") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    noneMode.forEach { option ->
                        val isSelected = (settings.promptMode == option.id)
                        Row(
                            modifier = Modifier.fillMaxWidth().selectable(selected = isSelected, onClick = {
                                onUpdateSettings { it.copy(promptMode = option.id) }
                                showPromptDialog = false
                            }).padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected, 
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = option.color)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = option.title, 
                                        color = if (isSelected) option.color else MaterialTheme.colorScheme.onSurface, 
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 15.sp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clickable {
                                                previewPromptMode = option.id
                                                showPromptPreviewDialog = true
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Visibility,
                                            contentDescription = "预览提示词",
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
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
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Godot",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF44336),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                    godotModes.forEach { option ->
                        val isSelected = (settings.promptMode == option.id)
                        Row(
                            modifier = Modifier.fillMaxWidth().selectable(selected = isSelected, onClick = {
                                onUpdateSettings { it.copy(promptMode = option.id) }
                                showPromptDialog = false
                            }).padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected, 
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = option.color)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = option.title, 
                                        color = if (isSelected) option.color else MaterialTheme.colorScheme.onSurface, 
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 15.sp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clickable {
                                                previewPromptMode = option.id
                                                showPromptPreviewDialog = true
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Visibility,
                                            contentDescription = "预览提示词",
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
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
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "其他",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                    otherModes.forEach { option ->
                        val isSelected = (settings.promptMode == option.id)
                        Row(
                            modifier = Modifier.fillMaxWidth().selectable(selected = isSelected, onClick = {
                                onUpdateSettings { it.copy(promptMode = option.id) }
                                showPromptDialog = false
                            }).padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected, 
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = option.color)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = option.title, 
                                        color = if (isSelected) option.color else MaterialTheme.colorScheme.onSurface, 
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 15.sp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clickable {
                                                previewPromptMode = option.id
                                                showPromptPreviewDialog = true
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Visibility,
                                            contentDescription = "预览提示词",
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
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
                    
                    if (settings.customPrompts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "我的提示词",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                        settings.customPrompts.forEach { cp ->
                            val isSelected = (settings.promptMode == "CUSTOM" && settings.selectedCustomPromptId == cp.id)
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .selectable(
                                        selected = isSelected,
                                        onClick = {
                                            onSelectCustomPrompt(cp.id)
                                            showPromptDialog = false
                                        }
                                    )
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF2196F3))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            cp.title,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color(0xFF2196F3) else MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFF2196F3).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "自定义",
                                                fontSize = 10.sp,
                                                color = Color(0xFF2196F3),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        previewCustomPromptId = cp.id
                                        previewPromptMode = "CUSTOM"
                                        showPromptPreviewDialog = true
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Visibility,
                                        contentDescription = "预览",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        editingPrompt = cp
                                        showEditPromptDialog = true
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "编辑",
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            showPromptDialog = false
                            showAddPromptDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("添加提示词")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPromptDialog = false }) { Text("取消") } }
        )
    }

    if (showPromptPreviewDialog) {
        val previewText = remember(previewPromptMode, previewCustomPromptId, settings.customPrompts, settings.selectedCustomPromptId, settings.reasoningLevel, settings.planMode) {
            if (previewCustomPromptId != null) {
                val cp = settings.customPrompts.find { it.id == previewCustomPromptId }
                SourceMergeUtils.buildPrompt(
                    "CUSTOM", "4.x", context,
                    customPromptContent = cp?.content ?: "",
                    customPromptTitle = cp?.title ?: "",
                    reasoningLevel = settings.reasoningLevel,
                    planMode = settings.planMode
                )
            } else {
                val selectedPrompt = settings.customPrompts.find { it.id == settings.selectedCustomPromptId }
                SourceMergeUtils.buildPrompt(
                    previewPromptMode, "4.x", context,
                    customPromptContent = selectedPrompt?.content ?: "",
                    customPromptTitle = selectedPrompt?.title ?: "",
                    reasoningLevel = settings.reasoningLevel,
                    planMode = settings.planMode
                )
            }
        }
        AlertDialog(
            onDismissRequest = { showPromptPreviewDialog = false },
            title = {
                val displayTitle = if (previewCustomPromptId != null) {
                    val cp = settings.customPrompts.find { it.id == previewCustomPromptId }
                    "自定义提示词预览 · ${cp?.title ?: "未命名"}"
                } else {
                    "提示词预览 · $previewModeTitle"
                }
                Text(displayTitle)
            },
            text = {
                SelectionContainer {
                    Text(
                        previewText,
                        modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Prompt", previewText))
                    Toast.makeText(context, "提示词已复制", Toast.LENGTH_SHORT).show()
                }) { Text("复制") }
            },
            dismissButton = {
                TextButton(onClick = { showPromptPreviewDialog = false }) { Text("返回") }
            }
        )
    }

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
                        Text(
                            if (settings.outputPath == "APP_INTERNAL_STORAGE") "应用内部处理" else settings.outputPath, 
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
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
                    "PROJECT_CONFIG" -> "仅提取平台核心配置文件。"
                    else -> "生成完整目录树及所有纯文本代码内容。"
                },
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp)
            )

            if (settings.exportMode == "PROJECT_CONFIG") {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "配置平台",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
                SegmentedControl(
                    options = listOf("Godot", "App"),
                    selectedIndex = if (settings.configPlatform == "APP") 1 else 0,
                    onSelected = { onUpdateSettings { s -> s.copy(configPlatform = if (it == 1) "APP" else "GODOT") } },
                    icons = listOf(Icons.Default.Gamepad, Icons.Default.PhoneAndroid)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (settings.configPlatform == "APP") {
                        "提取 build.gradle / settings.gradle / AndroidManifest.xml / gradle.properties 等 Android 配置文件。"
                    } else {
                        "提取 project.godot / *.cfg 等 Godot 配置文件。"
                    },
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            
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

                if (settings.promptMode != "NONE") {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("计划模式", color = MaterialTheme.colorScheme.onBackground)
                            Text("开启后，AI 先给方案、等用户确认后才生成代码", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 12.sp)
                        }
                        Switch(
                            checked = settings.planMode,
                            onCheckedChange = { onUpdateSettings { s -> s.copy(planMode = it) } }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    SectionTitle("思考强度", Icons.Default.Psychology)
                    LiquidReasoningSlider(
                        selectedIndex = when(settings.reasoningLevel) { "NO" -> 0; "LOW" -> 1; "HIGH" -> 2; "MAX" -> 3; else -> 2 },
                        onSelected = { onUpdateSettings { s -> s.copy(reasoningLevel = when(it) { 0 -> "NO"; 1 -> "LOW"; 2 -> "HIGH"; else -> "MAX" }) } }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when(settings.reasoningLevel) {
                            "NO" -> "不注入思考强度提示词，由 AI 自行决定思考深度。"
                            "LOW" -> "快速给出回答，跳过方案权衡和边界推演，优先拿到可用结果。"
                            "HIGH" -> "正常深度思考，聚焦主线逻辑与关键输入，必要处做校验和异常处理。"
                            else -> "深度思考，对抗性推演，主动寻找潜在失败路径，覆盖已知边界和风险。"
                        },
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                if (settings.promptMode == "CUSTOM") {
                    Spacer(modifier = Modifier.height(8.dp))
                    val selectedPrompt = settings.customPrompts.find { it.id == settings.selectedCustomPromptId }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                tint = Color(0xFF2196F3),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "当前提示词",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Text(
                                    selectedPrompt?.title ?: "未选择",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedPrompt != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
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
                            sizeText = it
                        },
                        modifier = Modifier.width(100.dp).onFocusChanged { focusState ->
                            if (!focusState.isFocused) {
                                val newVal = sizeText.toIntOrNull()
                                if (newVal != null && newVal > 0) {
                                    onUpdateSettings { s -> s.copy(maxFileSizeKb = newVal) }
                                } else {
                                    sizeText = settings.maxFileSizeKb.toString()
                                }
                            }
                        }, 
                        singleLine = true, 
                        suffix = { Text("KB", fontSize = 12.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("历史储存数量", color = MaterialTheme.colorScheme.onBackground)
                        Text("历史记录储存的数量修改超出储存会自动清理旧的", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                    var historyCountText by remember { mutableStateOf(settings.maxHistoryCount.toString()) }
                    LaunchedEffect(settings.maxHistoryCount) { 
                        historyCountText = settings.maxHistoryCount.toString() 
                    }
                    OutlinedTextField(
                        value = historyCountText,
                        onValueChange = { newVal ->
                            historyCountText = newVal.filter { it.isDigit() }
                        },
                        modifier = Modifier.width(100.dp).onFocusChanged { focusState ->
                            if (!focusState.isFocused) {
                                val newVal = historyCountText.toIntOrNull()
                                if (newVal != null && newVal > 0) {
                                    onUpdateSettings { s -> s.copy(maxHistoryCount = newVal) }
                                } else {
                                    historyCountText = settings.maxHistoryCount.toString()
                                }
                            }
                        }, 
                        singleLine = true, 
                        suffix = { Text("条", fontSize = 12.sp) }
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

            Spacer(modifier = Modifier.height(24.dp))

            SectionTitle("配置管理", Icons.Default.Settings)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "将当前所有设置与历史记录导出为 JSON 配置文件，可用于备份或在其他设备上恢复。",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = {
                                val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(java.util.Date())
                                exportConfigLauncher.launch("TUB_GD_Config_$ts.json")
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("导出配置")
                        }
                        OutlinedButton(
                            onClick = { importConfigLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("导入配置")
                        }
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
                        Text("TUB_Code_md", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("v3.6.3", color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.5f), fontSize = 12.sp)
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

@Composable
fun LiquidReasoningSlider(selectedIndex: Int, onSelected: (Int) -> Unit) {
    val speedMillis = when (selectedIndex) {
        0 -> 6000
        1 -> 5000
        2 -> 4000
        else -> 2000
    }

    val gradientLength = 1200f

    val infiniteTransition = rememberInfiniteTransition(label = "liquid")
    val shift by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = gradientLength,
        animationSpec = infiniteRepeatable(
            animation = tween(speedMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "shift"
    )

    val targetFraction = selectedIndex / 3f
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)
    )

    val activeColors = when (selectedIndex) {
        0 -> listOf(Color(0xFF9E9E9E), Color(0xFFBDBDBD), Color(0xFF9E9E9E), Color(0xFFBDBDBD), Color(0xFF9E9E9E))
        1 -> listOf(Color(0xFF00E5FF), Color(0xFF00B0FF), Color(0xFF2979FF), Color(0xFF00B0FF), Color(0xFF00E5FF))
        2 -> listOf(Color(0xFF2979FF), Color(0xFF651FFF), Color(0xFF7C4DFF), Color(0xFF651FFF), Color(0xFF2979FF))
        else -> listOf(Color(0xFF00E5FF), Color(0xFF7C4DFF), Color(0xFFFF4081), Color(0xFF7C4DFF), Color(0xFF00E5FF))
    }

    val brush = Brush.linearGradient(
        colors = activeColors,
        start = Offset(shift, 0f),
        end = Offset(shift + gradientLength, 0f),
        tileMode = TileMode.Repeated
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(18.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp)
                        )
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedFraction)
                        .fillMaxHeight()
                        .background(
                            brush = brush,
                            shape = RoundedCornerShape(10.dp)
                        )
                )
            }

            Slider(
                value = selectedIndex.toFloat(),
                onValueChange = { 
                    val newIndex = it.roundToInt().coerceIn(0, 3)
                    if (newIndex != selectedIndex) onSelected(newIndex)
                },
                valueRange = 0f..3f,
                steps = 2,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 10.dp, end = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "NO",
                fontSize = 12.sp,
                fontWeight = if (selectedIndex == 0) FontWeight.Bold else FontWeight.Normal,
                color = if (selectedIndex == 0) Color(0xFF9E9E9E) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                text = "LOW",
                fontSize = 12.sp,
                fontWeight = if (selectedIndex == 1) FontWeight.Bold else FontWeight.Normal,
                color = if (selectedIndex == 1) Color(0xFF00E5FF) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                text = "HIGH",
                fontSize = 12.sp,
                fontWeight = if (selectedIndex == 2) FontWeight.Bold else FontWeight.Normal,
                color = if (selectedIndex == 2) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                text = "MAX",
                fontSize = 12.sp,
                fontWeight = if (selectedIndex == 3) FontWeight.Bold else FontWeight.Normal,
                color = if (selectedIndex == 3) Color(0xFFFF4081) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

private data class PromptModeOption(val id: String, val title: String, val desc: String, val color: Color)
