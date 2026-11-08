package com.tub.code.md.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.tub.code.md.SelectedFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object SourceMergeUtils {
    val DEFAULT_HARD_DIRS = setOf(".git", ".svn", ".idea", ".vscode", ".vs", ".gradle", "node_modules", "__pycache__", "build", "dist", ".godot", ".import")
    val DEFAULT_SOFT_EXTS = setOf(
        ".png", ".jpg", ".jpeg", ".gif", ".webp", ".ico", ".psd", ".tga", ".exr", ".hdr", ".dds", ".ktx", ".basis", 
        ".glb", ".fbx", ".blend", 
        ".mp3", ".wav", ".ogg", ".mp4", ".ogv", 
        ".zip", ".tar", ".gz", ".rar", ".7z", ".apk", ".exe", ".dll",
        ".res", ".scn", ".mesh", ".anim", ".material", ".font", ".fontdata", ".theme", ".translation",
        ".ttf", ".otf", ".woff", ".woff2",
        ".bin", ".dat", ".pck", ".pak",
        ".so", ".dylib", ".a", ".lib", ".o"
    )
    
    // TEXT_EXTS 白名单已移除：改用纯黑名单（DEFAULT_SOFT_EXTS / 用户自定义规则）

    data class FileNode(
        val name: String, val isDir: Boolean, val uri: Uri, val size: Long, 
        val documentFile: DocumentFile? = null, val children: MutableList<FileNode> = mutableListOf()
    )

    data class ScanResult(
        val content: String, 
        val godotVersion: String,
        val binaryFiles: List<String> = emptyList()
    )

    fun formatSize(size: Long): String {
        return if (size < 1024) "$size B"
        else if (size < 1024 * 1024) String.format(Locale.US, "%.1f KB", size / 1024.0)
        else String.format(Locale.US, "%.1f MB", size / (1024.0 * 1024.0))
    }

    fun scanDirectory(
        context: Context, treeUri: Uri, outputFormat: String, exportMode: String, promptMode: String, reasoningLevel: String,
        softExcludes: Set<String>, hardExcludes: Set<String>, maxFileSizeKb: Int, showGlobalFileSize: Boolean,
        customPromptContent: String = "", customPromptTitle: String = "", planMode: Boolean = false,
        forceIncludeBinary: Boolean = false, configPlatform: String = "GODOT"
    ): ScanResult {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return ScanResult("无法读取该文件夹", "Unknown")
        
        var detectedVersion = "Unknown"
        val projectFile = rootDoc.findFile("project.godot")
        if (projectFile != null && projectFile.isFile) {
            try {
                val text = context.contentResolver.openInputStream(projectFile.uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    val regexFeatures = Regex("""config/features\s*=\s*PackedStringArray\(\s*"([^"]+)"""")
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

        val binaryFiles = mutableListOf<String>()
        val mergedSoftExcludes = softExcludes + DEFAULT_SOFT_EXTS
        val content = buildOutput(context, rootNode.name, rootNode, outputFormat, exportMode, promptMode, reasoningLevel, mergedSoftExcludes, maxFileSizeKb, showGlobalFileSize, detectedVersion, customPromptContent, customPromptTitle, binaryFiles, planMode, forceIncludeBinary, configPlatform)
        return ScanResult(content, detectedVersion, binaryFiles)
    }

    fun processMultipleFiles(
        context: Context, files: List<SelectedFile>, outputFormat: String, 
        softExcludes: Set<String>, hardExcludes: Set<String>, maxFileSizeKb: Int, showGlobalFileSize: Boolean,
        forceIncludeBinary: Boolean = false
    ): ScanResult {
        val sb = StringBuilder()
        val binaryFiles = mutableListOf<String>()
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
                val mergedSoftExcludes = softExcludes + DEFAULT_SOFT_EXTS
                val isSoftFiltered = mergedSoftExcludes.contains(".$ext") || mergedSoftExcludes.contains(ext)
                
                if (!isHardFiltered) {
                    val sizeAttr = if (showGlobalFileSize) " size=\"${formatSize(size)}\"" else ""
                    val lang = getLang(ext)
                    val langAttr = if (lang.isNotEmpty()) " language=\"$lang\"" else ""
                    sb.append("    <file path=\"$pathStr\"$langAttr$sizeAttr>\n")
                    
                    if (isSoftFiltered) {
                        sb.append("      <![CDATA[\n[内容已省略：命中软排除规则]\n]]>\n")
                    } else if (!forceIncludeBinary && isBinaryFile(context, uri, size)) {
                        binaryFiles.add(pathStr)
                        sb.append("      <![CDATA[\n[二进制资源已省略]\n]]>\n")
                    } else {
                        val rawText = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                        val text = rawText.replace("]]>", "]]]]><![CDATA[>")
                        sb.append("      <![CDATA[\n$text\n]]>\n")
                    }
                    sb.append("    </file>\n")
                }
            }
            sb.append("  </files>\n</project>")
            return ScanResult(sb.toString(), detectedVersion, binaryFiles)
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
            val mergedSoftExcludes = softExcludes + DEFAULT_SOFT_EXTS
                val isSoftFiltered = mergedSoftExcludes.contains(".$ext") || mergedSoftExcludes.contains(ext)
            
            if (!isHardFiltered) {
                val sizeStr = if (showGlobalFileSize) " (${formatSize(size)})" else ""
                if (isMd) sb.append("\n---\n### [${index + 1}/${files.size}] $pathStr$sizeStr\n\n") 
                else sb.append("\n==================================================\n[${index + 1}/${files.size}] $pathStr$sizeStr\n==================================================\n\n")
                
                if (isSoftFiltered) {
                    sb.append("> **[内容已省略]** 命中软排除规则过滤。\n\n")
                } else if (!forceIncludeBinary && isBinaryFile(context, uri, size)) {
                    binaryFiles.add(pathStr)
                    sb.append("> **[内容已省略]** 二进制资源，文件大小: ${formatSize(size)}\n\n")
                } else {
                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    if (isMd) sb.append("```${getLang(ext)}\n").append(text).append("\n```\n\n")
                    else sb.append(text).append("\n\n")
                }
            }
        }
        return ScanResult(sb.toString(), detectedVersion, binaryFiles)
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
        godotVersion: String, customPromptContent: String = "", customPromptTitle: String = "",
        binaryFiles: MutableList<String> = mutableListOf(),
        planMode: Boolean = false,
        forceIncludeBinary: Boolean = false,
        configPlatform: String = "GODOT"
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
                        if (shouldExtractConfigFile(node.name, configPlatform)) {
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
                        if (shouldExtractConfigFile(node.name, configPlatform)) {
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
                        val mergedSoftExcludes = softExcludes + DEFAULT_SOFT_EXTS
                val isSoftFiltered = mergedSoftExcludes.contains(".$ext") || mergedSoftExcludes.contains(ext)
                        
                        val sizeAttr = if (showGlobalFileSize) " size=\"${formatSize(node.size)}\"" else ""
                        val lang = getLang(ext)
                        val langAttr = if (lang.isNotEmpty()) " language=\"$lang\"" else ""
                        sb.append("    <file path=\"$relPath\"$langAttr$sizeAttr>\n")
                        if (isSoftFiltered) {
                            sb.append("      <![CDATA[\n[内容已省略：命中软排除规则]\n]]>\n")
                        } else if (node.size > maxFileSizeKb * 1024L) {
                            sb.append("      <![CDATA[\n[内容已省略：文件超限]\n]]>\n")
                        } else if (!forceIncludeBinary && isBinaryFile(context, node.uri, node.size)) {
                            binaryFiles.add(relPath)
                            sb.append("      <![CDATA[\n[二进制资源已省略]\n]]>\n")
                        } else {
                            try {
                                val rawText = context.contentResolver.openInputStream(node.uri)?.bufferedReader()?.use { it.readText() } ?: ""
                                val text = rawText.replace("]]>", "]]]]><![CDATA[>")
                                sb.append("      <![CDATA[\n$text\n]]>\n")
                            } catch (e: Exception) {
                                sb.append("      <![CDATA[\n无法读取\n]]>\n")
                            }
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
                sb.append("  <prompt>\n    <![CDATA[\n").append(buildPrompt(promptMode, godotVersion, context, customPromptContent, customPromptTitle, reasoningLevel, planMode)).append("\n]]>\n  </prompt>\n")
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
                    val mergedSoftExcludes = softExcludes + DEFAULT_SOFT_EXTS
                val isSoftFiltered = mergedSoftExcludes.contains(".$ext") || mergedSoftExcludes.contains(ext)
                    
                    val sizeStr = if (showGlobalFileSize) " (${formatSize(node.size)})" else ""
                    if (isMd) sb.append("### [$fileIndex/$totalFiles] $projectName/$relPath$sizeStr\n\n")
                    else sb.append("[$fileIndex/$totalFiles] $projectName/$relPath$sizeStr\n\n")
                    
                    if (isSoftFiltered) {
                        sb.append("> **[内容已省略]** 此处存在软排除规则过滤。\n\n")
                    } else if (node.size > maxFileSizeKb * 1024L) {
                        sb.append("> **[内容超限已忽略]** 文件大小 ${formatSize(node.size)}，超过设定上限 ${maxFileSizeKb} KB。\n\n")
                    } else if (!forceIncludeBinary && isBinaryFile(context, node.uri, node.size)) {
                        binaryFiles.add(relPath)
                        sb.append("> **[内容已省略]** 此处存在二进制资源文件。\n\n")
                    } else {
                        try {
                            val text = context.contentResolver.openInputStream(node.uri)?.bufferedReader()?.use { it.readText() } ?: ""
                            if (isMd) sb.append("```${getLang(ext)}\n").append(text).append("\n```\n\n")
                            else sb.append(text).append("\n\n")
                        } catch (e: Exception) {
                            sb.append("> 无法读取内容\n\n")
                        }
                    }
                } else {
                    val newPath = if (path.isEmpty()) node.name else "$path/${node.name}"
                    node.children.sortedBy { it.name.lowercase() }.forEach { extractContent(it, newPath) }
                }
            }
            rootNode.children.sortedBy { it.name.lowercase() }.forEach { extractContent(it, "") }
        }

        if (exportMode == "FULL_EXPORT" && promptMode != "NONE") {
            val prompt = buildPrompt(promptMode, godotVersion, context, customPromptContent, customPromptTitle, reasoningLevel, planMode)
            if (isMd) sb.append(prompt)
            else sb.append(prompt.replace("##", "").replace("**", ""))
        }
        return sb.toString()
    }

    private fun shouldExtractConfigFile(fileName: String, configPlatform: String): Boolean {
        return when (configPlatform) {
            "APP" -> {
                val lower = fileName.lowercase()
                lower == "build.gradle" ||
                lower == "build.gradle.kts" ||
                lower == "settings.gradle" ||
                lower == "settings.gradle.kts" ||
                lower == "gradle.properties" ||
                lower == "gradle-wrapper.properties" ||
                lower == "androidmanifest.xml" ||
                lower == "proguard-rules.pro" ||
                lower == "local.properties"
            }
            else -> {
                val lower = fileName.lowercase()
                val ext = fileName.substringAfterLast(".").lowercase()
                lower == "project.godot" ||
                lower == "project.binary" ||
                lower == "export_presets.cfg" ||
                lower == "override.cfg" ||
                ext == "godot" ||
                ext == "cfg"
            }
        }
    }

    private fun isBinaryFile(context: Context, uri: Uri, size: Long): Boolean {
        if (size == 0L) return false
        return try {
            val stream = context.contentResolver.openInputStream(uri) ?: return false
            stream.use { input ->
                val buffer = ByteArray(1024)
                val read = input.read(buffer)
                if (read <= 0) return false

                // UTF-8 BOM: EF BB BF —— 确定是文本
                if (read >= 3 &&
                    (buffer[0].toInt() and 0xFF) == 0xEF &&
                    (buffer[1].toInt() and 0xFF) == 0xBB &&
                    (buffer[2].toInt() and 0xFF) == 0xBF
                ) {
                    return false
                }
                // UTF-16 BOM: FF FE (LE) / FE FF (BE) —— 确定是文本
                if (read >= 2) {
                    val b0 = buffer[0].toInt() and 0xFF
                    val b1 = buffer[1].toInt() and 0xFF
                    if ((b0 == 0xFF && b1 == 0xFE) || (b0 == 0xFE && b1 == 0xFF)) {
                        return false
                    }
                }
                // 无 BOM 时退回 0x00 检测
                for (i in 0 until read) {
                    if (buffer[i] == 0.toByte()) return true
                }
            }
            false
        } catch (_: Exception) {
            false
        }
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

    fun buildPrompt(mode: String, version: String, context: Context? = null, customPromptContent: String = "", customPromptTitle: String = "", reasoningLevel: String = "HIGH", planMode: Boolean = false): String {
        if (mode == "NONE") return ""

        if (mode == "CUSTOM") {
            val sb = StringBuilder()
            sb.append("---\n## 🎮 自定义提示词")
            if (customPromptTitle.isNotBlank()) sb.append("：").append(customPromptTitle)
            sb.append("\n\n")
            if (customPromptContent.isBlank()) {
                sb.append("> **[自定义提示词为空]** 请在设置中通过「添加提示词」录入内容。")
            } else {
                sb.append(customPromptContent)
            }
            
            if (planMode) {
                sb.append("\n---\n### ⚠️ 计划模式（已开启）\n\n")
                sb.append("**先给方案，等用户确认后再输出代码。未确认前禁止输出完整实现、diff 或脚本。**\n\n")
            }
            
            appendReasoningLevel(sb, reasoningLevel)
            return sb.toString()
        }

        val v = version.toFloatOrNull() ?: 4.0f
        val sb = StringBuilder()
        
        sb.append("---\n## 🎮 AI 创作提示（工具自动注入）\n\n")
        sb.append("### 身份与背景\n")
        sb.append("你是一个资深的 Godot $version 游戏开发者。当前上下文包含了一个完整的 Godot $version 游戏项目源码。\n\n")
        
        if (planMode) {
            sb.append("### ⚠️ 计划模式（已开启）\n\n")
            sb.append("**先给方案，等用户确认后再输出代码。未确认前禁止输出完整实现、diff 或脚本。**\n\n")
        }
        
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

        sb.append("### 开发指南（必读）\n")
        sb.append("1. **场景理解**：读 `.tscn`，理解节点树层级与父子关系。\n")
        sb.append("2. **物理与碰撞**：涉及 `PhysicsRayQueryParameters3D` 时检查 `collision_layer` / `collision_mask`。\n")
        sb.append("3. **资源加载**：确定会用到的用 `preload()`，动态加载的用 `load()`。\n")
        sb.append("4. **UI 与 3D**：CanvasLayer 下 UI 坐标独立于 3D 世界；屏幕中心用 `get_viewport().get_visible_rect().size / 2`。\n")
        sb.append("5. **解耦**：善用 `signal` 和分组，避免硬编码 `get_node(\"../../Player\")`。\n\n")

        if (mode == "ADVANCED" || mode == "TUB_GD") {
            sb.append("### 改动前必问（最容易忘的一层）\n")
            sb.append("写代码前先想清楚：**这个改动在哪里被读取？读取方有没有缓存？改完要不要重启 / 清缓存 / 卸载重装？**\n\n")
            sb.append("- 配置类文件（`project.godot` / `export_presets.cfg`）→ 清 `.godot/` + **完全关闭编辑器重开**；平台权限/签名/包名改动 → **卸载旧版再装**。\n")
            sb.append("- 脚本 / 场景（`.gd` / `.tscn`）→ 保存即生效，**不要误报\"要清缓存\"**。\n")
            sb.append("- 跨进程 / 跨网络时序（切场景后立刻通信、外部服务刚启动就连接）→ 加 0.3~0.5s 缓冲 + 接收端加 `is_inside_tree()` 之类守卫。\n")
            sb.append("- 多端要算出相同结果的逻辑（初始位置、随机种子、状态分配）→ 用确定性输入（如 `peer_id % N`），**不要用本地计数器自增**。\n")
            sb.append("- 第三方工具配置（隧道、代理、外部服务）→ 核对版本语法 + 重启服务生效。\n\n")
            sb.append("**代码往往是对的，环境里的旧状态才是真凶。**\n\n")
        }

        sb.append("### 代码输出规范（极重要）\n")
        sb.append("当你直接输出代码时，请务必使用 **Tab 缩进**，严禁使用 2 个或 4 个空格。若你需要给出完整的 `.gd` 文件代码，请直接输出包含 Tab 格式的完整代码块，不要使用 `replace()` 或 `diff` 语法，以免用户复制粘贴后出现缩进错乱。\n\n")

        if (mode == "TUB_GD") {
            sb.append("### TUB 自动化工作流（极高优先级）\n\n")
            sb.append("**核心目标：输出一个可直接在 Godot 项目根目录运行的纯 Python 脚本（默认文件名 `patch.py`），完成对 .gd 文件的可靠修改。宁可整文件重写，也不要用不可靠的局部替换。**\n\n")
            sb.append("#### 一、读写铁律\n")
            sb.append("1. **必须使用 `pathlib.Path`**，基于脚本自身定位项目根：\n")
            sb.append("   `ROOT = Path(__file__).resolve().parent`\n")
            sb.append("2. **【绝对禁止】直接使用 `p.write_text()` 覆写原文件！** Godot 有文件监视器，直接截断覆写会导致它读到半截代码，从而报\"与缓存不匹配\"的致命错误。\n")
            sb.append("3. **必须使用\"安全原子写入（Safe Save）\"策略**：先写临时文件，再原子重命名替换原文件。请直接复制下面这个安全写入函数：\n\n")
            sb.append("```python\n")
            sb.append("def safe_write(rel_path: str, content: str) -> None:\n")
            sb.append("    p = ROOT / rel_path\n")
            sb.append("    p.parent.mkdir(parents=True, exist_ok=True)\n")
            sb.append("    tmp = p.with_suffix(p.suffix + \".tmp\")\n")
            sb.append("    tmp.write_text(content, encoding=\"utf-8\", newline=\"\\n\")\n")
            sb.append("    tmp.replace(p)\n")
            sb.append("```\n\n")
            sb.append("4. **GDScript 缩进必须是 Tab（`\\t`）**，Python 里拼装 GDScript 时每一级缩进都用 `\\t`，绝不用空格。\n\n")
            sb.append("#### 二、修改策略\n")
            sb.append("**策略 A（首选）：整文件覆盖。** 目标文件 ≤ 400 行，或对函数边界没 100% 把握，就直接输出完整新文件用 `safe_write()` 覆写。最可靠。\n\n")
            sb.append("**策略 B（次选）：函数级替换。** 仅在文件 > 400 行且能准确说出函数名时使用。**禁止 `str.replace` / `re.sub` 匹配多行 GDScript**，用下面的 `replace_function()`：\n\n")
            sb.append("```python\n")
            sb.append("def replace_function(source: str, func_name: str, new_func_src: str) -> str:\n")
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
            sb.append("**注解陷阱**：`replace_function` 从 `func xxx(` 起匹配，**函数上方的注解行（`@rpc` / `@export` / `@onready` / `@tool`）会原样保留**。所以新函数体**只写 `func` 行往下、不带注解**，否则会出现重复注解报错；要改注解参数只能单独匹配注解行或整文件覆盖。\n\n")
                        sb.append("#### 二次修改流程（脚本执行失败 / 用户反馈不成功时）\n\n")
            sb.append("**第一次修改：直接给出完整可执行脚本，让用户执行。**\n\n")
            sb.append("如果用户反馈\"未成功\"或\"结果不对\"，**不要再盲目给第二个脚本**。请先输出「诊断预览」：\n\n")
            sb.append("1. **可能的失败原因**：匹配锚点没找到？文件结构已变？缩进不一致？\n")
            sb.append("2. **关键行的上下文**：把打算修改的位置前后 5-10 行贴出来，让用户核对\n")
            sb.append("3. **本次的修改方案**：改哪几处、用什么策略、预期效果\n")
            sb.append("4. 让用户确认或补充信息，**用户确认后**再给出新脚本\n\n")
            sb.append("**为什么不直接盲改第二次？**\n\n")
            sb.append("- 连续盲改会把文件改得更乱，出问题时更难回滚\n")
            sb.append("- 用户看到预览能补充关键信息（\"你说的这行不在文件里\"、\"应该是另一处\"）\n")
            sb.append("- 一个确认回合的成本，远低于第二次改错的成本\n\n")

sb.append("#### 三、禁用清单\n")
            sb.append("- ❌ `p.write_text()` 原地覆写\n")
            sb.append("- ❌ `content.replace(\"多行代码\", ...)` / `re.sub` 匹配多行 GDScript\n")
            sb.append("- ❌ `writelines` 不指定 `newline`（会写入 `\\r\\n`）\n")
            sb.append("- ❌ 修改 `.uid` / `.import` / `.godot` 内部文件\n")
            sb.append("- ❌ 修改 `.tscn` 里的 `unique_id`\n")
            sb.append("- ❌ 改完配置类文件忘记提醒清缓存 / 重启\n")
            sb.append("- ❌ 改完脚本 / 场景误报\"要清缓存\"\n\n")
            sb.append("#### 四、脚本模板\n\n")
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
            sb.append("    safe_write(rel_path, replace_function(src, func_name, new_func_src))\n\n\n")
            sb.append("if __name__ == \"__main__\":\n")
            sb.append("    # 策略 A（推荐）\n")
            sb.append("    safe_write(\"scripts/player.gd\", PLAYER_GD_SOURCE)\n")
            sb.append("    # 策略 B（仅大文件）\n")
            sb.append("    # patch_function(\"scripts/player.gd\", \"_physics_process\", NEW_PHYSICS_PROCESS)\n")
            sb.append("```\n\n")
            sb.append("#### 五、交付前自检\n\n")
            sb.append("**代码层面**\n")
            sb.append("- ☐ 用 `safe_write()` 而非直接覆写；缩进全 Tab；读写都指定 `encoding=\"utf-8\"` 和 `newline=\"\\n\"`\n")
            sb.append("- ☐ 说明用了「整文件覆盖」还是「函数级替换」及理由\n")
            sb.append("- ☐ `replace_function` 替换后，目标函数上方无残留重复注解\n\n")
            sb.append("**环境层面**\n")
            sb.append("- ☐ 改配置类文件 → 提醒清 `.godot/` + 重启；平台权限/签名 → 提醒卸载重装\n")
            sb.append("- ☐ 改脚本/场景 → 明确告知\"保存即生效\"，不误报清缓存\n")
            sb.append("- ☐ 涉及时序/多端一致性 → 用延迟缓冲 + 守卫 + 确定性输入\n\n")
        }
        if (mode == "TUB_APP") {
            val sb2 = StringBuilder()
            sb2.append("---\n## 📱 TUB_APP 安卓项目 Python 脚本模式\n\n")
            sb2.append("### 身份与背景\n")
            sb2.append("你是一个资深的 Android 应用开发者（Java + Kotlin）。当前上下文是一个 Android 项目的完整源码。\n\n")
            
            if (planMode) {
                sb2.append("### ⚠️ 计划模式（已开启）\n\n")
                sb2.append("**先给方案，等用户确认后再输出代码。未确认前禁止输出完整实现、diff 或脚本。**\n\n")
            }
            
            sb2.append("### Android 技术红线\n")
            sb2.append("- Java / Kotlin 使用 **4 个空格**缩进，**严禁 Tab**（和 GDScript 相反）。\n")
            sb2.append("- 读写文件统一 UTF-8，中文字符不要被破坏（出现 `?` / `�` / 乱码即视为失败）。\n")
            sb2.append("- 遵守 Android / Gradle 标准项目结构：`app/src/main/java`、`app/src/main/res`、`app/build.gradle` 等。\n")
            sb2.append("- 修改 `AndroidManifest.xml` / `build.gradle` 等配置类文件后，提醒用户 Sync / Clean / 重装 APK。\n\n")
            
            sb2.append("### 一、读写铁律\n")
            sb2.append("1. **必须用 `pathlib.Path`**，基于脚本自身定位项目根：`ROOT = Path(__file__).resolve().parent`\n")
            sb2.append("2. **必须用 UTF-8 编码读写**，`encoding=\"utf-8\"` 是硬性要求，否则中文字符会乱码。\n")
            sb2.append("3. **必须用安全原子写入**：先写 `.tmp` 再 `replace()`，避免 IDE 读到半截文件：\n\n")
            sb2.append("```python\n")
            sb2.append("def safe_write(rel_path: str, content: str) -> None:\n")
            sb2.append("    p = ROOT / rel_path\n")
            sb2.append("    p.parent.mkdir(parents=True, exist_ok=True)\n")
            sb2.append("    tmp = p.with_suffix(p.suffix + \".tmp\")\n")
            sb2.append("    tmp.write_text(content, encoding=\"utf-8\", newline=\"\\n\")\n")
            sb2.append("    tmp.replace(p)\n")
            sb2.append("```\n\n")
            sb2.append("4. **Java / Kotlin 缩进用 4 个空格**，Python 里拼装代码字符串时每一级缩进都是 4 个空格。\n\n")
            
            sb2.append("### 二、修改策略\n")
            sb2.append("**策略 A（首选）：整文件覆盖。** 目标文件 ≤ 400 行，或对方法边界没 100% 把握，直接输出完整新文件用 `safe_write()` 覆写。\n\n")
            sb2.append("**策略 B（次选）：函数级替换。** 仅在文件 > 400 行且能准确说出方法名时使用。**禁止 `str.replace` / `re.sub` 匹配多行 Java/Kotlin 代码**，用下面的 `replace_method()`：\n\n")
            sb2.append("```python\n")
            sb2.append("def replace_method(source: str, sig_prefix: str, new_method_src: str) -> str:\n")
            sb2.append("    lines = source.split(\"\\n\")\n")
            sb2.append("    start = None\n")
            sb2.append("    base_indent = 0\n")
            sb2.append("    for i, line in enumerate(lines):\n")
            sb2.append("        stripped = line.lstrip(\" \")\n")
            sb2.append("        if sig_prefix in stripped and (stripped.startswith(\"public \") or stripped.startswith(\"private \") or stripped.startswith(\"protected \") or stripped.startswith(\"override \") or stripped.startswith(\"internal \") or stripped.startswith(\"fun \")):\n")
            sb2.append("            start = i\n")
            sb2.append("            base_indent = len(line) - len(stripped)\n")
            sb2.append("            break\n")
            sb2.append("    if start is None:\n")
            sb2.append("        raise ValueError(f\"未找到方法: {sig_prefix}\")\n")
            sb2.append("    end = len(lines)\n")
            sb2.append("    for i in range(start + 1, len(lines)):\n")
            sb2.append("        line = lines[i]\n")
            sb2.append("        if not line.strip():\n")
            sb2.append("            continue\n")
            sb2.append("        cur = len(line) - len(line.lstrip(\" \"))\n")
            sb2.append("        if cur <= base_indent:\n")
            sb2.append("            end = i\n")
            sb2.append("            break\n")
            sb2.append("    new_lines = new_method_src.rstrip(\"\\n\").split(\"\\n\")\n")
            sb2.append("    return \"\\n\".join(lines[:start] + new_lines + lines[end:])\n")
            sb2.append("```\n\n")
            sb2.append("**注解陷阱**：`replace_method` 从方法签名那一行起匹配，**上方的注解行（`@Override` / `@JvmStatic` / `@Suppress` 等）会原样保留**。所以新方法体**只写签名行往下、不带注解**，否则会出现重复注解报错；要改注解参数只能单独匹配注解行或整文件覆盖。\n\n")
            
            sb2.append("#### 二次修改流程（脚本执行失败 / 用户反馈不成功时）\n\n")
            sb2.append("**第一次修改：直接给出完整可执行脚本，让用户执行。**\n\n")
            sb2.append("如果用户反馈\"未成功\"或\"结果不对\"，**不要再盲目给第二个脚本**。请先输出「诊断预览」：\n\n")
            sb2.append("1. **可能的失败原因**：匹配锚点没找到？文件结构已变？缩进不一致？\n")
            sb2.append("2. **关键行的上下文**：把打算修改的位置前后 5-10 行贴出来，让用户核对\n")
            sb2.append("3. **本次的修改方案**：改哪几处、用什么策略、预期效果\n")
            sb2.append("4. 让用户确认或补充信息，**用户确认后**再给出新脚本\n\n")
            sb2.append("**为什么不直接盲改第二次？**\n\n")
            sb2.append("- 连续盲改会把文件改得更乱，出问题时更难回滚\n")
            sb2.append("- 用户看到预览能补充关键信息（\"你说的这行不在文件里\"、\"应该是另一处\"）\n")
            sb2.append("- 一个确认回合的成本，远低于第二次改错的成本\n\n")
            
            sb2.append("#### 三、禁用清单\n")
            sb2.append("- ❌ `p.write_text()` 不带 `encoding=\"utf-8\"`（中文必乱码）\n")
            sb2.append("- ❌ `content.replace(\"多行代码\", ...)` / `re.sub` 匹配多行 Java/Kotlin 代码\n")
            sb2.append("- ❌ 缩进混入 Tab（Java/Kotlin 官方规范就是 4 空格）\n")
            sb2.append("- ❌ 修改 `build/` / `.gradle/` / `.idea/` 下任何文件\n")
            sb2.append("- ❌ 修改 `R.java` / 生成的 `BuildConfig.java`\n")
            sb2.append("- ❌ 改完 `AndroidManifest.xml` / `build.gradle` 忘记提醒用户 Sync / 重装\n\n")
            
            sb2.append("#### 四、脚本模板\n\n")
            sb2.append("```python\n")
            sb2.append("#!/usr/bin/env python3\n")
            sb2.append("# -*- coding: utf-8 -*-\n")
            sb2.append("from pathlib import Path\n\n")
            sb2.append("ROOT = Path(__file__).resolve().parent\n\n\n")
            sb2.append("def safe_write(rel_path: str, content: str) -> None:\n")
            sb2.append("    p = ROOT / rel_path\n")
            sb2.append("    p.parent.mkdir(parents=True, exist_ok=True)\n")
            sb2.append("    tmp = p.with_suffix(p.suffix + \".tmp\")\n")
            sb2.append("    tmp.write_text(content, encoding=\"utf-8\", newline=\"\\n\")\n")
            sb2.append("    tmp.replace(p)\n\n\n")
            sb2.append("def replace_method(source: str, sig_prefix: str, new_method_src: str) -> str:\n")
            sb2.append("    # 粘贴上面的实现\n")
            sb2.append("    ...\n\n\n")
            sb2.append("def patch_method(rel_path: str, sig_prefix: str, new_method_src: str) -> None:\n")
            sb2.append("    p = ROOT / rel_path\n")
            sb2.append("    src = p.read_text(encoding=\"utf-8\")\n")
            sb2.append("    safe_write(rel_path, replace_method(src, sig_prefix, new_method_src))\n\n\n")
            sb2.append("if __name__ == \"__main__\":\n")
            sb2.append("    # 策略 A（推荐）\n")
            sb2.append("    safe_write(\"app/src/main/java/com/example/MainActivity.java\", MAIN_ACTIVITY_JAVA)\n")
            sb2.append("    # 策略 B（仅大文件）\n")
            sb2.append("    # patch_method(\"app/src/main/java/com/example/MainActivity.java\", \"protected void onResume(\", NEW_ON_RESUME)\n")
            sb2.append("```\n\n")
            
            sb2.append("#### 五、交付前自检\n\n")
            sb2.append("**代码层面**\n")
            sb2.append("- ☐ 用 `safe_write()` 而非直接覆写；缩进全 4 空格；读写都指定 `encoding=\"utf-8\"`\n")
            sb2.append("- ☐ 说明用了「整文件覆盖」还是「函数级替换」及理由\n")
            sb2.append("- ☐ `replace_method` 替换后，目标方法上方无残留重复注解\n\n")
            sb2.append("**环境层面**\n")
            sb2.append("- ☐ 改 `AndroidManifest.xml` / `build.gradle` → 提醒 Sync / Clean / 重装\n")
            sb2.append("- ☐ 改 `res/` 下资源 → 提醒 Sync\n")
            sb2.append("- ☐ 改 `.java` / `.kt` → 保存即生效，不误报清缓存\n\n")
            
            sb2.append("### 用户需求\n")
            sb2.append("请仔细阅读上述项目结构及代码，等待用户的具体 App 开发需求。\n")
            
            appendReasoningLevel(sb2, reasoningLevel)
            return sb2.toString()
        }

        if (mode == "TUB_PY") {
            val sb3 = StringBuilder()
            sb3.append("---\n## 🐍 TUB_PY 通用 Python 脚本模式\n\n")
            sb3.append("### 身份与背景\n")
            sb3.append("你是一个资深的软件工程师。当前上下文是一个完整项目源码。\n\n")
            
            if (planMode) {
                sb3.append("### ⚠️ 计划模式（已开启）\n\n")
                sb3.append("**先给方案，等用户确认后再输出代码。未确认前禁止输出完整实现、diff 或脚本。**\n\n")
            }
            
            sb3.append("### 核心目标\n")
            sb3.append("输出一个可直接运行在项目根目录的纯 Python 脚本（默认 `patch.py`），可靠地修改项目文件。**宁可整文件重写，也不要用不可靠的局部替换。**\n\n")
            
            sb3.append("### 一、读写铁律\n")
            sb3.append("1. **必须用 `pathlib.Path`**，基于脚本自身定位项目根：`ROOT = Path(__file__).resolve().parent`\n")
            sb3.append("2. **必须用安全原子写入**：先写 `.tmp` 再 `replace()`，避免编辑器 / 引擎监视器读到半截文件：\n\n")
            sb3.append("```python\n")
            sb3.append("def safe_write(rel_path: str, content: str) -> None:\n")
            sb3.append("    p = ROOT / rel_path\n")
            sb3.append("    p.parent.mkdir(parents=True, exist_ok=True)\n")
            sb3.append("    tmp = p.with_suffix(p.suffix + \".tmp\")\n")
            sb3.append("    tmp.write_text(content, encoding=\"utf-8\", newline=\"\\n\")\n")
            sb3.append("    tmp.replace(p)\n")
            sb3.append("```\n\n")
            sb3.append("3. **编码统一 UTF-8，换行统一 `\\n`**，除非目标文件原本就是其他编码 / 换行。\n")
            sb3.append("4. **缩进必须匹配目标文件的原有风格**：Tab / 2 空格 / 4 空格——改前先看几行，不要硬编码。\n\n")
            
            sb3.append("### 二、修改策略\n")
            sb3.append("**策略 A（首选）：整文件覆盖。** 目标文件 ≤ 400 行，或对边界没 100% 把握，直接输出完整新文件用 `safe_write()` 覆写。最可靠。\n\n")
            sb3.append("**策略 B（次选）：函数 / 块级替换。** 用缩进扫描定位边界（`replace_function`），自动识别缩进字符。\n\n")
            sb3.append("**策略 C：行级锚点。** 对配置类文件（json / yaml / ini / cfg / xml），按唯一 key 定位替换，避免误伤。\n\n")
            sb3.append("**禁止**：`str.replace` / `re.sub` 匹配多行代码，缩进 / 引号一处不一致就失败。\n\n")
            
            sb3.append("### 三、通用 replace_function（自动识别缩进）\n\n")
            sb3.append("```python\n")
            sb3.append("def detect_indent(source: str) -> str:\n")
            sb3.append("    for line in source.split(\"\\n\"):\n")
            sb3.append("        if line.startswith(\"\\t\"): return \"\\t\"\n")
            sb3.append("        if line.startswith(\"    \"): return \"    \"\n")
            sb3.append("        if line.startswith(\"  \"): return \"  \"\n")
            sb3.append("    return \"    \"\n\n\n")
            sb3.append("def replace_function(source: str, func_name: str, new_func_src: str, indent_char: str = \"    \") -> str:\n")
            sb3.append("    lines = source.split(\"\\n\")\n")
            sb3.append("    start = None\n")
            sb3.append("    base_indent = 0\n")
            sb3.append("    for i, line in enumerate(lines):\n")
            sb3.append("        stripped = line.lstrip(indent_char)\n")
            sb3.append("        for prefix in [\"func \", \"def \", \"function \", \"class \"]:\n")
            sb3.append("            if stripped.startswith(prefix + func_name + \"(\") or stripped.startswith(prefix + func_name + \" \"):\n")
            sb3.append("                start = i\n")
            sb3.append("                base_indent = (len(line) - len(stripped)) // len(indent_char)\n")
            sb3.append("                break\n")
            sb3.append("        if start is not None: break\n")
            sb3.append("    if start is None:\n")
            sb3.append("        raise ValueError(f\"未找到: {func_name}\")\n")
            sb3.append("    end = len(lines)\n")
            sb3.append("    for i in range(start + 1, len(lines)):\n")
            sb3.append("        line = lines[i]\n")
            sb3.append("        if not line.strip(): continue\n")
            sb3.append("        cur = (len(line) - len(line.lstrip(indent_char))) // len(indent_char)\n")
            sb3.append("        if cur <= base_indent:\n")
            sb3.append("            end = i; break\n")
            sb3.append("    new_lines = new_func_src.rstrip(\"\\n\").split(\"\\n\")\n")
            sb3.append("    return \"\\n\".join(lines[:start] + new_lines + lines[end:])\n")
            sb3.append("```\n\n")
            sb3.append("**调用前先探测缩进**：`indent = detect_indent(src)`，再传 `indent_char=indent`。\n\n")
            
            sb3.append("#### 二次修改流程（脚本执行失败 / 用户反馈不成功时）\n\n")
            sb3.append("**第一次修改：直接给出完整可执行脚本，让用户执行。**\n\n")
            sb3.append("如果用户反馈\"未成功\"或\"结果不对\"，**不要再盲目给第二个脚本**。请先输出「诊断预览」：\n\n")
            sb3.append("1. **可能的失败原因**：匹配锚点没找到？文件结构已变？缩进不一致？\n")
            sb3.append("2. **关键行的上下文**：把打算修改的位置前后 5-10 行贴出来，让用户核对\n")
            sb3.append("3. **本次的修改方案**：改哪几处、用什么策略、预期效果\n")
            sb3.append("4. 让用户确认或补充信息，**用户确认后**再给出新脚本\n\n")
            sb3.append("**为什么不直接盲改第二次？**\n\n")
            sb3.append("- 连续盲改会把文件改得更乱，出问题时更难回滚\n")
            sb3.append("- 用户看到预览能补充关键信息（\"你说的这行不在文件里\"、\"应该是另一处\"）\n")
            sb3.append("- 一个确认回合的成本，远低于第二次改错的成本\n\n")
            
            sb3.append("### 四、禁用清单\n")
            sb3.append("- ❌ `p.write_text()` 直接覆写（会触发 IDE / 引擎的文件监视异常）\n")
            sb3.append("- ❌ `content.replace(\"多行代码\", ...)` / `re.sub` 匹配多行\n")
            sb3.append("- ❌ `writelines` 不指定 `newline`（会写入 `\\r\\n`）\n")
            sb3.append("- ❌ 硬编码缩进字符（不同文件不同风格，必须探测）\n")
            sb3.append("- ❌ 修改编译产物目录（build / dist / node_modules / .git / .gradle 等）\n\n")
            
            sb3.append("### 五、交付前自检\n")
            sb3.append("- ☐ 用 `safe_write()` 而非直接覆写\n")
            sb3.append("- ☐ 缩进已探测目标文件风格，未硬编码\n")
            sb3.append("- ☐ 读写都指定 `encoding=\"utf-8\"` 和 `newline=\"\\n\"`\n")
            sb3.append("- ☐ 说明用了「整文件覆盖」还是「函数级替换」及理由\n")
            sb3.append("- ☐ 没触碰编译产物目录\n\n")
            
            sb3.append("### 用户需求\n")
            sb3.append("请仔细阅读上述项目结构及代码，等待用户的具体修改需求。\n")
            
            appendReasoningLevel(sb3, reasoningLevel)
            return sb3.toString()
        }


        appendReasoningLevel(sb, reasoningLevel)

        sb.append("### 用户需求\n")
        sb.append("请仔细阅读上述项目结构及代码，等待用户的具体游戏开发需求。\n")
        
        return sb.toString()
    }

    private fun appendReasoningLevel(sb: StringBuilder, level: String) {
        when (level) {
            "NO" -> {
                // 不注入任何档位提示词
            }
            "LOW" -> {
                sb.append("### 🧠 思考强度：LOW\n")
                sb.append("严格遵守 LOW 思考强度：快速给出回答。跳过方案权衡、边界推演、性能分析，优先让用户立刻拿到可用的结果。允许短答案、允许省略解释。\n\n")
            }
            "HIGH" -> {
                sb.append("### 🧠 思考强度：HIGH\n")
                sb.append("严格遵守 HIGH 思考强度：正常深度思考。聚焦当前任务的主线逻辑与关键输入，必要处做校验和异常处理，其余细节正常推进，不主动扩展范围。\n\n")
            }
            "MAX" -> {
                sb.append("### 🧠 思考强度：MAX\n")
                sb.append("严格遵守 MAX 思考强度：深度思考。对方案做对抗性推演，主动寻找潜在失败路径，覆盖已知边界和风险后再交付，但不越界修改当前任务之外的内容。\n\n")
            }
        }
    }
}
