package com.godot.tub.gd.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.godot.tub.gd.SelectedFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object SourceMergeUtils {
    val DEFAULT_HARD_DIRS = setOf(".git", ".svn", ".idea", ".vscode", ".vs", ".gradle", "node_modules", "__pycache__", "build", "dist", ".godot", ".import")
    val DEFAULT_SOFT_EXTS = setOf(
        ".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".ico", ".psd", ".tga", ".exr", ".hdr", ".dds", ".ktx", ".basis", 
        ".gltf", ".glb", ".obj", ".fbx", ".dae", ".blend", 
        ".mp3", ".wav", ".ogg", ".mp4", ".ogv", 
        ".zip", ".tar", ".gz", ".rar", ".7z", ".apk", ".exe", ".dll"
    )
    
    val TEXT_EXTS = setOf(
        "kt", "java", "py", "gd", "c", "cpp", "h", "cs", "js", "ts", "html", "css", 
        "xml", "json", "yaml", "yml", "toml", "gradle", "properties", "md", "txt", 
        "tscn", "tres", "godot", "cfg", "sh", "bat", "gdshader", "glsl", "frag", "vert",
        "svg", "import", "uid"
    )

    data class FileNode(
        val name: String, val isDir: Boolean, val uri: Uri, val size: Long, 
        val documentFile: DocumentFile? = null, val children: MutableList<FileNode> = mutableListOf()
    )

    data class ScanResult(val content: String, val godotVersion: String)

    fun formatSize(size: Long): String {
        return if (size < 1024) "$size B"
        else if (size < 1024 * 1024) String.format(Locale.US, "%.1f KB", size / 1024.0)
        else String.format(Locale.US, "%.1f MB", size / (1024.0 * 1024.0))
    }

    fun scanDirectory(
        context: Context, treeUri: Uri, outputFormat: String, exportMode: String, promptMode: String, reasoningLevel: String,
        softExcludes: Set<String>, hardExcludes: Set<String>, maxFileSizeKb: Int, showGlobalFileSize: Boolean,
        customPromptPath: String = ""
    ): ScanResult {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return ScanResult("无法读取该文件夹", "Unknown")
        
        var detectedVersion = "Unknown"
        val projectFile = rootDoc.findFile("project.godot")
        if (projectFile != null && projectFile.isFile) {
            try {
                val text = context.contentResolver.openInputStream(projectFile.uri)?.bufferedReader()?.use { it.readText() } ?: ""
                val regexFeatures = Regex("""config/features\s*=\s*PackedStringArray\("([^"]+)"""")
                val match = regexFeatures.find(text)
                if (match != null) {
                    val rawVer = match.groupValues[1]
                    val parts = rawVer.split(".")
                    if (parts.size >= 2) {
                        detectedVersion = "${parts[0]}.${parts[1]}"
                    } else {
                        detectedVersion = rawVer
                    }
                } else if (text.contains("config_version=5")) {
                    detectedVersion = "4.x"
                }
            } catch (e: Exception) {}
        }

        val rootNode = FileNode(rootDoc.name ?: "Project", true, rootDoc.uri, 0, rootDoc)
        
        fun parseDir(doc: DocumentFile, node: FileNode) {
            doc.listFiles().forEach { child ->
                if (child.isDirectory) {
                    val dirName = child.name ?: ""
                    val isHardDir = DEFAULT_HARD_DIRS.contains(dirName) || hardExcludes.contains(dirName) || hardExcludes.contains("$dirName/")
                    if (!isHardDir) {
                        val childNode = FileNode(dirName, true, child.uri, 0, child)
                        node.children.add(childNode)
                        parseDir(child, childNode)
                    }
                } else {
                    val fileName = child.name ?: ""
                    val ext = fileName.substringAfterLast(".").lowercase()
                    val isHardFile = hardExcludes.contains(".$ext") || hardExcludes.contains(ext)
                    if (!isHardFile) {
                        node.children.add(FileNode(fileName, false, child.uri, child.length(), child))
                    }
                }
            }
        }
        parseDir(rootDoc, rootNode)

        val content = buildOutput(context, rootNode.name, rootNode, outputFormat, exportMode, promptMode, reasoningLevel, softExcludes, maxFileSizeKb, showGlobalFileSize, detectedVersion, customPromptPath)
        return ScanResult(content, detectedVersion)
    }

    fun processMultipleFiles(
        context: Context, files: List<SelectedFile>, outputFormat: String, 
        softExcludes: Set<String>, hardExcludes: Set<String>, maxFileSizeKb: Int, showGlobalFileSize: Boolean
    ): ScanResult {
        val sb = StringBuilder()
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val detectedVersion = ""

        if (outputFormat == "XML") {
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<project name=\"MultiFiles\" generated=\"$date\">\n")
            sb.append("  <files>\n")
            files.forEach { file ->
                val uri = file.uri
                val name = file.name
                val pathStr = getDisplayNameAndPath(context, uri).second
                var size = 0L
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
                    }
                }
                
                val ext = name.substringAfterLast(".").lowercase()
                val isHardFiltered = hardExcludes.contains(".$ext") || hardExcludes.contains(ext)
                val isSoftFiltered = softExcludes.contains(".$ext") || softExcludes.contains(ext)
                
                if (!isHardFiltered) {
                    val sizeAttr = if (showGlobalFileSize) " size=\"${formatSize(size)}\"" else ""
                    val lang = getLang(ext)
                    val langAttr = if (lang.isNotEmpty()) " language=\"$lang\"" else ""
                    sb.append("    <file path=\"$pathStr\"$langAttr$sizeAttr>\n")
                    
                    if (isSoftFiltered) {
                        sb.append("      <![CDATA[\n[内容已省略：命中软排除规则]\n]]>\n")
                    } else if (isTextFile(context, name, size, uri)) {
                        val rawText = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                        val text = rawText.replace("]]>", "]]]]><![CDATA[>")
                        sb.append("      <![CDATA[\n$text\n]]>\n")
                    } else {
                        sb.append("      <![CDATA[\n[二进制资源已省略]\n]]>\n")
                    }
                    sb.append("    </file>\n")
                }
            }
            sb.append("  </files>\n</project>")
            return ScanResult(sb.toString(), detectedVersion)
        }

        val isMd = (outputFormat == "MARKDOWN")
        if (isMd) sb.append("# 多文件合并\n\n> 文件数: ${files.size} | 生成时间: $date\n\n")
        else sb.append("多文件合并\n文件数: ${files.size} | 生成时间: $date\n\n")
        
        files.forEachIndexed { index, file ->
            val uri = file.uri
            val name = file.name
            val pathStr = getDisplayNameAndPath(context, uri).second
            var size = 0L
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
                }
            }
            
            val ext = name.substringAfterLast(".").lowercase()
            val isHardFiltered = hardExcludes.contains(".$ext") || hardExcludes.contains(ext)
            val isSoftFiltered = softExcludes.contains(".$ext") || softExcludes.contains(ext)
            
            if (!isHardFiltered) {
                val sizeStr = if (showGlobalFileSize) " (${formatSize(size)})" else ""
                if (isMd) sb.append("\n---\n### [${index + 1}/${files.size}] $pathStr$sizeStr\n\n") 
                else sb.append("\n==================================================\n[${index + 1}/${files.size}] $pathStr$sizeStr\n==================================================\n\n")
                
                if (isSoftFiltered) {
                    sb.append("> **[内容已省略]** 命中软排除规则过滤。\n\n")
                } else if (isTextFile(context, name, size, uri)) {
                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    if (isMd) sb.append("```${getLang(ext)}\n").append(text).append("\n```\n\n")
                    else sb.append(text).append("\n\n")
                } else {
                    sb.append("> **[内容已省略]** 二进制资源，文件大小: ${formatSize(size)}\n\n")
                }
            }
        }
        return ScanResult(sb.toString(), detectedVersion)
    }

    private fun getDisplayNameAndPath(context: Context, uri: Uri): Pair<String, String> {
        var name = "unknown_file"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx != -1) name = cursor.getString(nameIdx)
            }
        }
        
        var path = uri.path ?: return Pair(name, name)
        
        if (path.startsWith("/document/primary:")) {
            path = path.replace("/document/primary:", "/storage/emulated/0/")
        } else if (path.startsWith("/document/")) {
            path = path.substringAfter("/document/")
        } else if (path.startsWith("/tree/")) {
            path = path.substringAfter("/tree/").substringBefore("/document/")
        } else if (path.startsWith("document:")) {
             return Pair(name, name)
        }
        
        val decoded = try { java.net.URLDecoder.decode(path, "UTF-8") } catch (e: Exception) { path }
        if (decoded.startsWith("document:") || decoded.contains("%2F") || decoded.contains("primary:")) {
             return Pair(name, name)
        }
        return Pair(name, decoded)
    }

    private fun buildOutput(
        context: Context, projectName: String, rootNode: FileNode, 
        outputFormat: String, exportMode: String, promptMode: String, reasoningLevel: String, softExcludes: Set<String>, maxFileSizeKb: Int, showGlobalFileSize: Boolean,
        godotVersion: String, customPromptPath: String = ""
    ): String {
        val sb = StringBuilder()
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        
        if (exportMode == "PROJECT_CONFIG") {
            if (outputFormat == "XML") {
                sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<project name=\"$projectName\" godot_version=\"$godotVersion\" generated=\"$date\">\n")
                sb.append("  <files>\n")
                fun extractConfigs(node: FileNode, path: String) {
                    if (!node.isDir) {
                        val ext = node.name.substringAfterLast(".").lowercase()
                        if (ext == "godot" || ext == "cfg") {
                            val relPath = if (path.isEmpty()) node.name else "$path/${node.name}"
                            val sizeAttr = if (showGlobalFileSize) " size=\"${formatSize(node.size)}\"" else ""
                            val lang = getLang(ext)
                            val langAttr = if (lang.isNotEmpty()) " language=\"$lang\"" else ""
                            try {
                                val rawText = context.contentResolver.openInputStream(node.uri)?.bufferedReader()?.use { it.readText() } ?: ""
                                val text = rawText.replace("]]>", "]]]]><![CDATA[>")
                                sb.append("    <file path=\"$relPath\"$langAttr$sizeAttr>\n      <![CDATA[\n$text\n]]>\n    </file>\n")
                            } catch (e: Exception) {}
                        }
                    } else {
                        val newPath = if (path.isEmpty()) node.name else "$path/${node.name}"
                        node.children.sortedBy { it.name.lowercase() }.forEach { extractConfigs(it, newPath) }
                    }
                }
                rootNode.children.sortedBy { it.name.lowercase() }.forEach { extractConfigs(it, "") }
                sb.append("  </files>\n</project>")
            } else {
                sb.append("# 项目配置 (核心设置文件)\n\n> 生成时间: $date\n\n")
                fun extractConfigs(node: FileNode, path: String) {
                    if (!node.isDir) {
                        val ext = node.name.substringAfterLast(".").lowercase()
                        if (ext == "godot" || ext == "cfg") {
                            val relPath = if (path.isEmpty()) node.name else "$path/${node.name}"
                            val sizeStr = if (showGlobalFileSize) " (${formatSize(node.size)})" else ""
                            try {
                                val text = context.contentResolver.openInputStream(node.uri)?.bufferedReader()?.use { it.readText() } ?: ""
                                sb.append("### $relPath$sizeStr\n\n```ini\n$text\n```\n\n")
                            } catch (e: Exception) {}
                        }
                    } else {
                        val newPath = if (path.isEmpty()) node.name else "$path/${node.name}"
                        node.children.sortedBy { it.name.lowercase() }.forEach { extractConfigs(it, newPath) }
                    }
                }
                rootNode.children.sortedBy { it.name.lowercase() }.forEach { extractConfigs(it, "") }
            }
            return sb.toString()
        }

        if (outputFormat == "XML") {
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<project name=\"$projectName\" godot_version=\"$godotVersion\" generated=\"$date\">\n")
            
            sb.append("  <structure>\n    <![CDATA[\n")
            sb.append(buildTree(rootNode, showGlobalFileSize = showGlobalFileSize).lines().joinToString("\n") { "    $it" })
            sb.append("\n    ]]>\n  </structure>\n")
            
            if (exportMode == "FULL_EXPORT") {
                sb.append("  <files>\n")
                fun extractXml(node: FileNode, path: String) {
                    if (!node.isDir) {
                        val relPath = if (path.isEmpty()) node.name else "$path/${node.name}"
                        val ext = node.name.substringAfterLast(".").lowercase()
                        val isSoftFiltered = softExcludes.contains(".$ext") || softExcludes.contains(ext)
                        
                        val sizeAttr = if (showGlobalFileSize) " size=\"${formatSize(node.size)}\"" else ""
                        val lang = getLang(ext)
                        val langAttr = if (lang.isNotEmpty()) " language=\"$lang\"" else ""
                        sb.append("    <file path=\"$relPath\"$langAttr$sizeAttr>\n")
                        if (isSoftFiltered) {
                            sb.append("      <![CDATA[\n[内容已省略：命中软排除规则]\n]]>\n")
                        } else if (node.size > maxFileSizeKb * 1024L) {
                            sb.append("      <![CDATA[\n[内容已省略：文件超限]\n]]>\n")
                        } else if (isTextFile(context, node)) {
                            try {
                                val rawText = context.contentResolver.openInputStream(node.uri)?.bufferedReader()?.use { it.readText() } ?: ""
                                val text = rawText.replace("]]>", "]]]]><![CDATA[>")
                                sb.append("      <![CDATA[\n$text\n]]>\n")
                            } catch (e: Exception) {
                                sb.append("      <![CDATA[\n无法读取\n]]>\n")
                            }
                        } else {
                            sb.append("      <![CDATA[\n[二进制资源已省略]\n]]>\n")
                        }
                        sb.append("    </file>\n")
                    } else {
                        val newPath = if (path.isEmpty()) node.name else "$path/${node.name}"
                        node.children.sortedBy { it.name.lowercase() }.forEach { extractXml(it, newPath) }
                    }
                }
                rootNode.children.sortedBy { it.name.lowercase() }.forEach { extractXml(it, "") }
                sb.append("  </files>\n")
            }
            
            if (exportMode == "FULL_EXPORT" && promptMode != "NONE") {
                sb.append("  <prompt>\n    <![CDATA[\n").append(buildPrompt(promptMode, godotVersion, context, customPromptPath, reasoningLevel)).append("\n]]>\n  </prompt>\n")
            }
            sb.append("</project>")
            return sb.toString()
        }

        val isMd = (outputFormat == "MARKDOWN")
        if (isMd) {
            sb.append("# 项目: $projectName\n\n")
            sb.append("> 生成时间: $date\n\n")
            if (exportMode == "DIR_ONLY") sb.append("*本文档仅包含项目的目录结构。*\n\n") else sb.append("*本文档包含目录结构与文件内容。*\n\n")
        } else {
            sb.append("项目: $projectName\n")
            sb.append("生成时间: $date\n\n")
        }

        if (exportMode == "DIR_ONLY" || exportMode == "FULL_EXPORT") {
            if (isMd) sb.append("## 目录结构\n\n```\n")
            else sb.append("--- 目录结构 ---\n\n")
            sb.append(buildTree(rootNode, showGlobalFileSize = showGlobalFileSize))
            if (isMd) sb.append("\n```\n\n") else sb.append("\n\n")
        }

        if (exportMode == "FULL_EXPORT") {
            if (isMd) sb.append("## 文件内容\n\n")
            else sb.append("--- 文件内容 ---\n\n")
            
            var fileIndex = 0
            val totalFiles = countFiles(rootNode)

            fun extractContent(node: FileNode, path: String) {
                if (!node.isDir) {
                    fileIndex++
                    val relPath = if (path.isEmpty()) node.name else "$path/${node.name}"
                    val ext = node.name.substringAfterLast(".").lowercase()
                    val isSoftFiltered = softExcludes.contains(".$ext") || softExcludes.contains(ext)
                    
                    val sizeStr = if (showGlobalFileSize) " (${formatSize(node.size)})" else ""
                    if (isMd) sb.append("### [$fileIndex/$totalFiles] $projectName/$relPath$sizeStr\n\n")
                    else sb.append("[$fileIndex/$totalFiles] $projectName/$relPath$sizeStr\n\n")
                    
                    if (isSoftFiltered) {
                        sb.append("> **[内容已省略]** 此处存在软排除规则过滤。\n\n")
                    } else if (node.size > maxFileSizeKb * 1024L) {
                        sb.append("> **[内容超限已忽略]** 文件大小 ${formatSize(node.size)}，超过设定上限 ${maxFileSizeKb} KB。\n\n")
                    } else if (isTextFile(context, node)) {
                        try {
                            val text = context.contentResolver.openInputStream(node.uri)?.bufferedReader()?.use { it.readText() } ?: ""
                            if (isMd) sb.append("```${getLang(ext)}\n").append(text).append("\n```\n\n")
                            else sb.append(text).append("\n\n")
                        } catch (e: Exception) {
                            sb.append("> 无法读取内容\n\n")
                        }
                    } else {
                        sb.append("> **[内容已省略]** 此处存在二进制资源文件。\n\n")
                    }
                } else {
                    val newPath = if (path.isEmpty()) node.name else "$path/${node.name}"
                    node.children.sortedBy { it.name.lowercase() }.forEach { extractContent(it, newPath) }
                }
            }
            rootNode.children.sortedBy { it.name.lowercase() }.forEach { extractContent(it, "") }
        }

        if (exportMode == "FULL_EXPORT" && promptMode != "NONE") {
            val prompt = buildPrompt(promptMode, godotVersion, context, customPromptPath, reasoningLevel)
            if (isMd) sb.append(prompt)
            else sb.append(prompt.replace("##", "").replace("**", ""))
        }
        return sb.toString()
    }

    private fun isTextFile(context: Context, node: FileNode): Boolean {
        return isTextFile(context, node.name, node.size, node.uri)
    }

    private fun isTextFile(context: Context, fileName: String, size: Long, uri: Uri): Boolean {
        val ext = fileName.substringAfterLast(".").lowercase()
        val lowerName = fileName.lowercase()
        if (TEXT_EXTS.contains(ext)) return true
        if (lowerName == ".gitignore" || lowerName == ".editorconfig" || lowerName == ".gitattributes" || lowerName == ".nomedia") return true
        
        if (size in 1..102400) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(1024)
                    val read = input.read(buffer)
                    for (i in 0 until read) {
                        if (buffer[i] == 0.toByte()) return false
                    }
                    return true
                }
            } catch (_: Exception) {}
        }
        return false
    }

    private fun buildTree(node: FileNode, prefix: String = "", showGlobalFileSize: Boolean = false): String {
        val sb = StringBuilder()
        if (prefix.isEmpty()) sb.append("└── 📁 ${node.name}\n")
        val children = node.children.sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
        children.forEachIndexed { i, child ->
            val isLast = i == children.size - 1
            val conn = if (isLast) "└── " else "├── "
            val icon = if (child.isDir) "📁" else "📄"
            val actualPrefix = if (prefix.isEmpty()) "    " else prefix
            
            val sizeStr = if (showGlobalFileSize && !child.isDir) " (${formatSize(child.size)})" else ""
            sb.append("$actualPrefix$conn$icon ${child.name}$sizeStr\n")
            
            if (child.isDir) {
                val nextPrefix = actualPrefix + if (isLast) "    " else "│   "
                sb.append(buildTree(child, nextPrefix, showGlobalFileSize))
            }
        }
        return sb.toString()
    }

    private fun countFiles(node: FileNode): Int {
        var count = 0
        if (!node.isDir) count++
        node.children.forEach { count += countFiles(it) }
        return count
    }

    private fun getLang(ext: String): String = when (ext.lowercase()) {
        "kt", "kts" -> "kotlin"
        "java" -> "java"
        "py" -> "python"
        "gd" -> "gdscript"
        "c" -> "c"
        "cpp", "cc", "cxx", "h", "hpp" -> "cpp"
        "cs" -> "csharp"
        "js", "mjs" -> "javascript"
        "ts" -> "typescript"
        "jsx" -> "jsx"
        "tsx" -> "tsx"
        "go" -> "go"
        "rs" -> "rust"
        "swift" -> "swift"
        "php" -> "php"
        "rb" -> "ruby"
        "lua" -> "lua"
        "dart" -> "dart"
        "r" -> "r"
        "sh", "bash" -> "bash"
        "zsh" -> "zsh"
        "bat", "cmd" -> "bat"
        "ps1" -> "powershell"
        "html", "htm" -> "html"
        "css" -> "css"
        "scss" -> "scss"
        "less" -> "less"
        "xml", "svg" -> "xml"
        "md", "markdown" -> "markdown"
        "txt" -> "text"
        "json" -> "json"
        "yaml", "yml" -> "yaml"
        "toml" -> "toml"
        "ini", "cfg", "properties", "godot", "tscn", "tres", "import", "uid" -> "ini"
        "sql" -> "sql"
        "gradle" -> "groovy"
        "glsl", "frag", "vert", "gdshader" -> "glsl"
        "shader", "hlsl" -> "hlsl"
        "obj", "mtl" -> "wavefront"
        else -> ""
    }

    fun buildPrompt(mode: String, version: String, context: Context? = null, customPromptPath: String = "", reasoningLevel: String = "HIGH"): String {
        if (mode == "NONE") return ""

        if (mode == "CUSTOM") {
            val sb = StringBuilder()
            sb.append("---\n## 🎮 自定义提示词\n\n")
            if (context != null && customPromptPath.isNotBlank()) {
                try {
                    val file = File(customPromptPath)
                    val lowerName = file.name.lowercase()
                    when {
                        !file.exists() || !file.isFile -> {
                            sb.append("> **[自定义提示词加载失败]** 文件不存在：$customPromptPath")
                        }
                        !(lowerName.endsWith(".txt") || lowerName.endsWith(".md")) -> {
                            sb.append("> **[自定义提示词加载失败]** 仅支持 .txt 或 .md 格式：$customPromptPath")
                        }
                        else -> {
                            sb.append(file.readText())
                        }
                    }
                } catch (e: Exception) {
                    sb.append("> **[自定义提示词加载失败]** ${e.message}")
                }
            } else {
                sb.append("> **[未设置自定义提示词]** 请在设置中选择自定义提示词并填写文件路径。")
            }
            
            appendReasoningLevel(sb, reasoningLevel)
            return sb.toString()
        }

        val v = version.toFloatOrNull() ?: 4.0f
        val sb = StringBuilder()
        
        sb.append("---\n## 🎮 AI 创作提示（工具自动注入）\n\n")
        sb.append("### 身份与背景\n")
        sb.append("你是一个资深的 Godot $version 游戏开发者。当前上下文包含了一个完整的 Godot $version 游戏项目源码。\n\n")
        
        sb.append("### Godot $version 技术红线\n")
        sb.append("- **缩进**：GDScript 官方规范使用 Tab（`\\t`），严禁 2/4 空格混用；Tab/空格不一致是工具修改失败的头号原因。\n")
        sb.append("- 跨文件引用资源请使用 `res://` 路径，严禁使用绝对路径。\n")
        sb.append("- 请严格使用 GDScript 和 Godot 4.x 的 API。\n")
        sb.append("- 严禁使用 Godot 3 的旧版 API（如 `KinematicBody`、`instance()` 等）。\n")
        if (v >= 4.4f) {
            sb.append("- **.uid 文件**：由编辑器自动生成，请勿手写或修改。\n")
        }
        if (v >= 4.6f) {
            sb.append("- **unique_id**：`.tscn` 中的节点唯一标识由编辑器维护，请勿手写。\n")
            sb.append("- **物理引擎**：默认使用 Jolt Physics，请勿在代码里无端修改物理服务器设置。\n")
        }
        if (v >= 4.7f) {
            sb.append("- **is_class 参数**：参数类型为 StringName，请使用 &\"ClassName\" 语法。\n")
            sb.append("- **RichTextLabel.add_image**：参数变更为 width_unit / height_unit。\n")
            sb.append("- **PackedArray 赋值**：直接设置不触发 setter，请使用 .set(i, x)。\n")
            sb.append("- **类型化返回值**：重写方法时（如 `_process`）必须显式写出 return 语句。\n")
        }
        sb.append("\n")

        if (mode == "ADVANCED" || mode == "TUB_STYLE") {
            sb.append("### 进阶开发指南（必读）\n")
            sb.append("1. **场景理解**：请仔细阅读上下文中的 `.tscn` 文件，理解节点树（Node Tree）结构。明确各个节点（Camera3D, CharacterBody3D, Marker3D, UI 等）的层级和父子关系。\n")
            sb.append("2. **物理与碰撞**：代码中涉及 `PhysicsRayQueryParameters3D` 时，请检查节点的 `collision_layer` 和 `collision_mask`，确保射线能正确命中目标。\n")
            sb.append("3. **资源预加载**：对于确定一定会用到的资源（如特效场景、常用音频），请优先使用 `preload()`；对于动态加载的，使用 `load()`。\n")
            sb.append("4. **UI 与 3D 空间**：所有 CanvasLayer 下的 UI 节点，其坐标独立于 3D 世界。处理屏幕中心准星时，请使用 `get_viewport().get_visible_rect().size / 2`。\n")
            sb.append("5. **面向对象设计**：善用 `signal`（信号）解耦节点，避免硬编码强引用（如 `get_node(\"../../Player\")`），推荐使用分组 `get_tree().get_nodes_in_group(\"player_team\")`。\n\n")
        }

        if (mode == "GENERAL" || mode == "ADVANCED") {
            sb.append("### 代码输出规范（极重要）\n")
            sb.append("当你直接输出代码时，请务必使用 **Tab 缩进**，严禁使用 2 个或 4 个空格。若你需要给出完整的 `.gd` 文件代码，请直接输出包含 Tab 格式的完整代码块，不要使用 `replace()` 或 `diff` 语法，以免用户复制粘贴后出现缩进错乱。\n\n")
        }

        if (mode == "TUB_STYLE") {
            sb.append("### TUB 自动化工作流（极高优先级）\n\n")
            sb.append("**核心目标：输出一个可直接在 Godot 项目根目录运行的纯 Python 脚本（默认文件名 `patch.py`），完成对 .gd 文件的可靠修改。宁可整文件重写，也不要用不可靠的局部替换。**\n\n")
            
            sb.append("#### 一、读写铁律（违反任何一条都会导致项目损坏或引擎报错）\n\n")
            sb.append("1. **必须使用 `pathlib.Path`**，基于脚本自身定位项目根：\n")
            sb.append("   `ROOT = Path(__file__).resolve().parent`\n")
            sb.append("2. **【绝对禁止】直接使用 `p.write_text()` 覆写原文件！**\n")
            sb.append("   Godot 有文件监视器，直接截断覆写会导致它读到半截代码，从而报“与缓存不匹配”的致命错误。\n")
            sb.append("3. **必须使用“安全原子写入（Safe Save）”策略**：先写临时文件，再原子重命名替换原文件。这能保证 Godot 读到的永远是完整文件，从而正常弹出“文件已修改”提示，而不是崩溃。请直接复制下面这个安全写入函数：\n\n")
            sb.append("```python\n")
            sb.append("def safe_write(rel_path: str, content: str) -> None:\n")
            sb.append("    p = ROOT / rel_path\n")
            sb.append("    p.parent.mkdir(parents=True, exist_ok=True)\n")
            sb.append("    # 先写入临时文件\n")
            sb.append("    tmp = p.with_suffix(p.suffix + \".tmp\")\n")
            sb.append("    tmp.write_text(content, encoding=\"utf-8\", newline=\"\\n\")\n")
            sb.append("    # 再原子重命名替换，避免 Godot 读到一半\n")
            sb.append("    tmp.replace(p)\n")
            sb.append("```\n\n")
            sb.append("4. **GDScript 缩进必须是 Tab（`\\t`），严禁空格**。你在 Python 里拼装 GDScript 字符串时，每一级缩进都用 `\\t`，绝对不要用 2 个或 4 个空格。\n\n")
            
            sb.append("#### 二、修改策略（按可靠性从高到低）\n\n")
            sb.append("**策略 A（首选）：整文件覆盖**\n")
            sb.append("只要目标文件 ≤ 400 行，或你对函数边界没有 100% 把握，就直接输出完整的新文件内容，用 `safe_write()` 覆写。整文件覆盖最可靠，不存在匹配失败的问题。**本项目优先策略 A。**\n\n")
            sb.append("**策略 B（次选）：函数级替换**\n")
            sb.append("仅在满足全部条件时使用：\n")
            sb.append("- 单文件 > 400 行\n")
            sb.append("- 你能准确说出要修改的函数名\n\n")
            sb.append("**禁止用 `str.replace` 或 `re.sub` 匹配多行 GDScript 代码**。请使用下面的 `replace_function()`：它通过 Tab 缩进扫描定位函数边界，能正确处理嵌套的 if/for/match，不受代码内容影响。\n\n")
            
            sb.append("```python\n")
            sb.append("def replace_function(source: str, func_name: str, new_func_src: str) -> str:\n")
            sb.append("    # 通过缩进扫描替换 GDScript 函数。new_func_src 是含缩进的完整函数源码。\n")
            sb.append("    lines = source.split(\"\\n\")\n")
            sb.append("    start = None\n")
            sb.append("    base_indent = 0\n")
            sb.append("    for i, line in enumerate(lines):\n")
            sb.append("        stripped = line.lstrip(\"\\t\")\n")
            sb.append("        if stripped.startswith(f\"func {func_name}(\"):\n")
            sb.append("            start = i\n")
            sb.append("            base_indent = len(line) - len(stripped)\n")
            sb.append("            break\n")
            sb.append("    if start is None:\n")
            sb.append("        raise ValueError(f\"未找到函数: {func_name}\")\n")
            sb.append("    end = len(lines)\n")
            sb.append("    for i in range(start + 1, len(lines)):\n")
            sb.append("        line = lines[i]\n")
            sb.append("        if not line.strip():\n")
            sb.append("            continue\n")
            sb.append("        cur = len(line) - len(line.lstrip(\"\\t\"))\n")
            sb.append("        if cur <= base_indent:\n")
            sb.append("            end = i\n")
            sb.append("            break\n")
            sb.append("    new_lines = new_func_src.rstrip(\"\\n\").split(\"\\n\")\n")
            sb.append("    return \"\\n\".join(lines[:start] + new_lines + lines[end:])\n")
            sb.append("```\n\n")
            
            sb.append("#### 三、禁用清单（踩中任何一条都会导致修改失败或项目损坏）\n\n")
            sb.append("- ❌ 禁止 `p.write_text()` 原地覆写（会触发引擎缓存报错）\n")
            sb.append("- ❌ 禁止 `content.replace(\"多行代码\", \"...\")` 做多行替换（Tab / 空格 / 引号一处不一致就失败）\n")
            sb.append("- ❌ 禁止 `re.sub` 匹配多行 GDScript（缩进不能用正则可靠匹配）\n")
            sb.append("- ❌ 禁止用 `writelines` 且不指定 newline（会写入 `\\r\\n`）\n")
            sb.append("- ❌ 禁止修改 `.uid` / `.import` / `.godot` 内部文件\n")
            sb.append("- ❌ 禁止修改 `.tscn` 里的 `unique_id`（编辑器维护）\n\n")
            
            sb.append("#### 四、脚本模板（请直接复制使用）\n\n")
            sb.append("```python\n")
            sb.append("#!/usr/bin/env python3\n")
            sb.append("# -*- coding: utf-8 -*-\n")
            sb.append("from pathlib import Path\n\n")
            sb.append("ROOT = Path(__file__).resolve().parent\n\n\n")
            sb.append("def safe_write(rel_path: str, content: str) -> None:\n")
            sb.append("    p = ROOT / rel_path\n")
            sb.append("    p.parent.mkdir(parents=True, exist_ok=True)\n")
            sb.append("    tmp = p.with_suffix(p.suffix + \".tmp\")\n")
            sb.append("    tmp.write_text(content, encoding=\"utf-8\", newline=\"\\n\")\n")
            sb.append("    tmp.replace(p)\n\n\n")
            sb.append("def replace_function(source: str, func_name: str, new_func_src: str) -> str:\n")
            sb.append("    # 粘贴上面的实现\n")
            sb.append("    ...\n\n\n")
            sb.append("def patch_function(rel_path: str, func_name: str, new_func_src: str) -> None:\n")
            sb.append("    p = ROOT / rel_path\n")
            sb.append("    src = p.read_text(encoding=\"utf-8\")\n")
            sb.append("    new_src = replace_function(src, func_name, new_func_src)\n")
            sb.append("    safe_write(rel_path, new_src)\n\n\n")
            sb.append("if __name__ == \"__main__\":\n")
            sb.append("    # 策略 A：整文件覆盖（推荐）\n")
            sb.append("    safe_write(\"scripts/player.gd\", PLAYER_GD_SOURCE)\n\n")
            sb.append("    # 策略 B：函数级替换（仅大文件用）\n")
            sb.append("    # patch_function(\"scripts/player.gd\", \"_physics_process\", NEW_PHYSICS_PROCESS)\n")
            sb.append("```\n\n")
            
            sb.append("#### 五、生成脚本后的自检清单\n\n")
            sb.append("在输出末尾附上以下 Markdown 检查清单，逐项确认后再交付：\n\n")
            sb.append("- [ ] 使用了 `safe_write()` 安全覆写，而不是直接覆盖\n")
            sb.append("- [ ] 每级缩进都是 Tab，没有空格混入\n")
            sb.append("- [ ] 读/写文件都指定了 `encoding=\"utf-8\"` 和 `newline=\"\\n\"`\n")
            sb.append("- [ ] 明确说明用了「整文件覆盖」还是「函数级替换」，并给出理由\n")
            sb.append("- [ ] 没有触碰 `.uid` / `.import` / `.tscn` 的 `unique_id`\n\n")
        }

        appendReasoningLevel(sb, reasoningLevel)

        sb.append("### 用户需求\n")
        sb.append("请仔细阅读上述项目结构及代码，等待用户的具体游戏开发需求。\n")
        
        return sb.toString()
    }

    private fun appendReasoningLevel(sb: StringBuilder, level: String) {
        when (level) {
            "LOW" -> {
                sb.append("### 🧠 推理等级：LOW (极速原型)\n")
                sb.append("- **思考方向**：将重点完全放在“玩法实现”与“快速验证”上，不需要考虑复杂的工程化和极端情况。\n")
                sb.append("- **实现要求**：\n")
                sb.append("  1. 提供最直接、最短小精悍的代码，优先让功能立刻跑起来。\n")
                sb.append("  2. 忽略冗余的防御性检查，假定输入和状态都是符合预期的。\n")
                sb.append("  3. 不需要刻意优化性能，以“能跑”为最高准则。\n\n")
            }
            "HIGH" -> {
                sb.append("### 🧠 推理等级：HIGH (工程级标准)\n")
                sb.append("- **思考方向**：注重代码工程化与 Godot 引擎规范，保证代码的可读性、可维护性与健壮性。\n")
                sb.append("- **实现要求**：\n")
                sb.append("  1. **严格使用 Godot 4.x API**：杜绝使用任何已废弃的方法。\n")
                sb.append("  2. **生命周期与安全**：精确处理 `_ready`、`_process` 等节点生命周期；确保信号（Signal）连接安全、无内存泄漏。\n")
                sb.append("  3. **类型安全**：变量定义必须包含类型提示（Type Hint），函数参数与返回值也要明确类型。\n")
                sb.append("  4. **结构清晰**：代码需分段、有注释，变量命名符合驼峰/下划线规范。\n\n")
            }
            "MAX" -> {
                sb.append("### 🧠 推理等级：MAX (极致防御与优化)\n")
                sb.append("- **思考方向**：将代码视为要直接上线的商业级项目，启动“对抗性测试”与“性能压榨”思维。\n")
                sb.append("- **实现要求**：\n")
                sb.append("  1. **极端的边界防御**：必须在代码中处理空值（null）、数组越界、快速连点、状态冲突等所有可能引发崩溃的隐患。\n")
                sb.append("  2. **性能瓶颈检查**：确保 `_process` 或 `_physics_process` 中没有高开销的遍历或实例化操作；涉及大量计算时，必须给出优化方案。\n")
                sb.append("  3. **内部自我审查**：在输出代码前，必须在内部进行逻辑推演（左右互搏），确认没有逻辑死锁、死循环或内存泄漏。\n")
                sb.append("  4. **高内聚低耦合**：代码必须极其模块化，方便后续无限扩展。\n\n")
            }
        }
    }
}
