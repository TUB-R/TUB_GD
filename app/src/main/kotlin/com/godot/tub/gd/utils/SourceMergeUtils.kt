package com.godot.tub.gd.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.godot.tub.gd.SelectedFile
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
        context: Context, treeUri: Uri, outputFormat: String, exportMode: String, promptMode: String, 
        softExcludes: Set<String>, hardExcludes: Set<String>, maxFileSizeKb: Int, showGlobalFileSize: Boolean
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

        val content = buildOutput(context, rootNode.name, rootNode, outputFormat, exportMode, promptMode, softExcludes, maxFileSizeKb, showGlobalFileSize, detectedVersion)
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
        outputFormat: String, exportMode: String, promptMode: String, softExcludes: Set<String>, maxFileSizeKb: Int, showGlobalFileSize: Boolean,
        godotVersion: String
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
                sb.append("  <prompt>\n    <![CDATA[\n").append(buildPrompt(promptMode, godotVersion)).append("\n]]>\n  </prompt>\n")
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
            val prompt = buildPrompt(promptMode, godotVersion)
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
        // 常见编程语言
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
        
        // 脚本与命令行
        "sh", "bash" -> "bash"
        "zsh" -> "zsh"
        "bat", "cmd" -> "bat"
        "ps1" -> "powershell"
        
        // 标记语言与样式
        "html", "htm" -> "html"
        "css" -> "css"
        "scss" -> "scss"
        "less" -> "less"
        "xml", "svg" -> "xml"
        "md", "markdown" -> "markdown"
        "txt" -> "text"
        
        // 数据与配置
        "json" -> "json"
        "yaml", "yml" -> "yaml"
        "toml" -> "toml"
        "ini", "cfg", "properties", "godot", "tscn", "tres", "import", "uid" -> "ini"
        "sql" -> "sql"
        "gradle" -> "groovy"
        
        // 着色器与图形
        "glsl", "frag", "vert", "gdshader" -> "glsl"
        "shader", "hlsl" -> "hlsl"
        "obj", "mtl" -> "wavefront"
        
        else -> ""
    }

    private fun buildPrompt(mode: String, version: String): String {
        if (mode == "NONE") return ""
        
        val v = version.toFloatOrNull() ?: 4.0f
        val sb = StringBuilder()
        
        sb.append("---\n## 🎮 AI 创作提示（工具自动注入）\n\n")
        sb.append("### 身份与背景\n")
        sb.append("你是一个资深的 Godot $version 游戏开发者。当前上下文包含了一个完整的 Godot $version 游戏项目源码。\n\n")
        
        // 通用规则：所有模式都会携带
        sb.append("### Godot $version 技术红线\n")
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

        // 进阶模式：加入 3D 场景理解和开发规范
        if (mode == "ADVANCED" || mode == "TUB_STYLE") {
            sb.append("### 进阶开发指南（必读）\n")
            sb.append("1. **场景理解**：请仔细阅读上下文中的 `.tscn` 文件，理解节点树（Node Tree）结构。明确各个节点（Camera3D, CharacterBody3D, Marker3D, UI 等）的层级和父子关系。\n")
            sb.append("2. **物理与碰撞**：代码中涉及 `PhysicsRayQueryParameters3D` 时，请检查节点的 `collision_layer` 和 `collision_mask`，确保射线能正确命中目标。\n")
            sb.append("3. **资源预加载**：对于确定一定会用到的资源（如特效场景、常用音频），请优先使用 `preload()`；对于动态加载的，使用 `load()`。\n")
            sb.append("4. **UI 与 3D 空间**：所有 CanvasLayer 下的 UI 节点，其坐标独立于 3D 世界。处理屏幕中心准星时，请使用 `get_viewport().get_visible_rect().size / 2`。\n")
            sb.append("5. **面向对象设计**：善用 `signal`（信号）解耦节点，避免硬编码强引用（如 `get_node(\"../../Player\")`），推荐使用分组 `get_tree().get_nodes_in_group(\"player_team\")`。\n\n")
        }

        // TUB 同款：强制输出 Python 脚本，并给出严格的防翻车规范
        if (mode == "TUB_STYLE") {
            sb.append("### TUB 自动化工作流（极高优先级）\n")
            sb.append("为了批量修改项目文件，**你必须输出一个可以直接在项目根目录运行的纯 Python 脚本**（例如 `patch.py`）。\n")
            sb.append("该脚本的核心要求如下（请严格遵守，否则极易引发语法崩溃）：\n")
            sb.append("1. **路径处理**：使用 `pathlib.Path`，不要混用绝对路径，基于当前脚本所在目录（`root = Path(__file__).resolve().parent`）。\n")
            sb.append("2. **编码格式**：写入文件时，**必须**指定 `encoding=\"utf-8\"` 和 `newline=\"\\n\"`（防止 Windows 换行符污染 GDScript）。\n")
            sb.append("3. **修改策略**：\n")
            sb.append("   - 如果是**新增文件**，直接写入即可。\n")
            sb.append("   - 如果是**覆盖文件**，请直接将完整的修改后代码写入，**避免**让用户去跑什么 `replace` 匹配，这样极易导致代码残缺。\n")
            sb.append("   - 如果是**精准替换**，请使用 Python 的 raw string (`r\"\"\"...\"\"\"`) 包裹原代码和新代码，**绝对不要**让特殊符号（如 `\\n`, `\\t`, `\"`）在 Python 和 GDScript 之间发生转义冲突！\n")
            sb.append("4. **代码风格**：脚本里被修改或新增的 GDScript 代码，请严格遵守 Godot 4.x 规范，缩进请使用 Tab 或 4 个空格，并保持格式一致。\n")
            sb.append("5. **避免覆盖 `.uid` 或 `unique_id`**：保持这些标识符不变。\n\n")
        }

        sb.append("### 用户需求\n")
        sb.append("请仔细阅读上述项目结构及代码，等待用户的具体游戏开发需求。\n")
        
        return sb.toString()
    }
}
