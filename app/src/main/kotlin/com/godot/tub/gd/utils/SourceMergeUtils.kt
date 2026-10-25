
package com.godot.tub.gd.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import java.text.SimpleDateFormat
import java.util.*

object SourceMergeUtils {
    val IGNORE_DIRS = setOf(".git", ".svn", ".idea", ".gradle", "node_modules", "__pycache__", "build", "dist", ".godot", ".import")
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

    fun scanDirectory(
        context: Context, treeUri: Uri, outputFormat: String, exportMode: String, promptMode: String, customExcludeExts: Set<String>, maxFileSizeKb: Int
    ): ScanResult {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return ScanResult("无法读取该文件夹", "Unknown")
        
        var detectedVersion = "Unknown"
        val projectFile = rootDoc.findFile("project.godot")
        
        if (projectFile != null && projectFile.isFile) {
            try {
                val text = context.contentResolver.openInputStream(projectFile.uri)?.bufferedReader()?.use { it.readText() } ?: ""
                val regexFeatures = Regex("""config/features\s*=\s*PackedStringArray\("([^"]+)"""")
                val matchFeatures = regexFeatures.find(text)
                if (matchFeatures != null) {
                    detectedVersion = matchFeatures.groupValues[1]
                } else {
                    val regexConfig = Regex("""config_version\s*=\s*(\d+)""")
                    val matchConfig = regexConfig.find(text)
                    if (matchConfig != null && matchConfig.groupValues[1] == "5") {
                        detectedVersion = "4.x"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val rootNode = FileNode(rootDoc.name ?: "Project", true, rootDoc.uri, 0, rootDoc)
        fun parseDir(doc: DocumentFile, node: FileNode) {
            doc.listFiles().forEach { child ->
                if (child.isDirectory) {
                    if (!IGNORE_DIRS.contains(child.name)) {
                        val childNode = FileNode(child.name ?: "unknown", true, child.uri, 0, child)
                        node.children.add(childNode)
                        parseDir(child, childNode)
                    }
                } else {
                    node.children.add(FileNode(child.name ?: "unknown", false, child.uri, child.length(), child))
                }
            }
        }
        parseDir(rootDoc, rootNode)

        val content = buildOutput(context, rootNode.name, rootNode, outputFormat, exportMode, promptMode, customExcludeExts, maxFileSizeKb, detectedVersion)
        return ScanResult(content, detectedVersion)
    }

    fun processMultipleFiles(context: Context, uris: List<Uri>, outputFormat: String, promptMode: String, customExcludeExts: Set<String>, maxFileSizeKb: Int): ScanResult {
        val sb = StringBuilder()
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val detectedVersion = "Unknown" 

        if (outputFormat == "XML") {
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<project name=\"MultiFiles\" generated=\"$date\">\n")
            sb.append("  <files>\n")
            uris.forEach { uri ->
                var name = "unknown_file"
                var size = 0L
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx != -1) name = cursor.getString(nameIdx)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
                    }
                }
                val ext = name.substringAfterLast(".").lowercase()
                val isCustomFiltered = customExcludeExts.contains(".$ext")
                
                sb.append("    <file path=\"$name\">\n")
                if (isCustomFiltered || size > maxFileSizeKb * 1024L) {
                    sb.append("      <![CDATA[\n[内容已省略]\n]]>\n")
                } else if (isTextFile(context, name, size, uri)) {
                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    sb.append("      <![CDATA[\n$text\n]]>\n")
                } else {
                    sb.append("      <![CDATA[\n[二进制资源]\n]]>\n")
                }
                sb.append("    </file>\n")
            }
            sb.append("  </files>\n")
            if (promptMode != "NONE") {
                sb.append("  <prompt>\n    <![CDATA[\n").append(buildPrompt(promptMode, detectedVersion)).append("\n]]>\n  </prompt>\n")
            }
            sb.append("</project>")
            return ScanResult(sb.toString(), detectedVersion)
        }

        val isMd = (outputFormat == "MARKDOWN")
        if (isMd) sb.append("# 多文件合并\n\n> 文件数: ${uris.size} | 生成时间: $date\n\n")
        else sb.append("多文件合并\n文件数: ${uris.size} | 生成时间: $date\n\n")
        
        uris.forEachIndexed { index, uri ->
            var name = "unknown_file"
            var size = 0L
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1) name = cursor.getString(nameIdx)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
                }
            }
            val ext = name.substringAfterLast(".").lowercase()
            val isCustomFiltered = customExcludeExts.contains(".$ext")
            
            if (isMd) sb.append("### [${index + 1}/${uris.size}] $name\n\n") 
            else sb.append("--- [${index + 1}/${uris.size}] $name ---\n\n")
            
            if (isCustomFiltered || size > maxFileSizeKb * 1024L) {
                sb.append("> **[内容已省略]** 文件过大或已过滤\n\n")
            } else if (isTextFile(context, name, size, uri)) {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                if (isMd) sb.append("```${getLang(ext)}\n").append(text).append("\n```\n\n")
                else sb.append(text).append("\n\n")
            } else {
                sb.append("> **[内容已省略]** 二进制资源\n\n")
            }
        }
        
        if (promptMode != "NONE") {
            val prompt = buildPrompt(promptMode, detectedVersion)
            if (isMd) sb.append(prompt)
            else sb.append(prompt.replace("##", "").replace("**", ""))
        }
        return ScanResult(sb.toString(), detectedVersion)
    }

    private fun buildOutput(
        context: Context, projectName: String, rootNode: FileNode, 
        outputFormat: String, exportMode: String, promptMode: String, customExcludeExts: Set<String>, maxFileSizeKb: Int,
        godotVersion: String
    ): String {
        val sb = StringBuilder()
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        
        if (outputFormat == "XML") {
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<project name=\"$projectName\" godot_version=\"$godotVersion\" generated=\"$date\">\n")
            if (exportMode == "PROJECT_SUMMARY") {
                sb.append("  <summary>暂时没有设计awa</summary>\n</project>")
                return sb.toString()
            }
            
            if (exportMode != "PROJECT_SUMMARY") {
                sb.append("  <structure>\n    <![CDATA[\n")
                sb.append(buildTree(rootNode).lines().joinToString("\n") { "    $it" })
                sb.append("\n    ]]>\n  </structure>\n")
            }
            
            if (exportMode == "FULL_EXPORT") {
                sb.append("  <files>\n")
                fun extractXml(node: FileNode, path: String) {
                    if (!node.isDir) {
                        val relPath = if (path.isEmpty()) node.name else "$path/${node.name}"
                        val ext = node.name.substringAfterLast(".").lowercase()
                        val isCustomFiltered = customExcludeExts.contains(".$ext")
                        sb.append("    <file path=\"$relPath\">\n")
                        if (isCustomFiltered) {
                            sb.append("      <![CDATA[\n[内容已省略：命中自定义过滤规则]\n]]>\n")
                        } else if (node.size > maxFileSizeKb * 1024L) {
                            sb.append("      <![CDATA[\n[内容已省略：文件大小 ${node.size / 1024} KB 超过上限 ${maxFileSizeKb} KB]\n]]>\n")
                        } else if (isTextFile(context, node)) {
                            try {
                                val text = context.contentResolver.openInputStream(node.uri)?.bufferedReader()?.use { it.readText() } ?: ""
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

        if (exportMode != "PROJECT_SUMMARY") {
            if (isMd) sb.append("## 目录结构\n\n```\n")
            else sb.append("--- 目录结构 ---\n\n")
            sb.append(buildTree(rootNode))
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
                    val isCustomFiltered = customExcludeExts.contains(".$ext")
                    
                    if (isMd) sb.append("### [$fileIndex/$totalFiles] $projectName/$relPath\n\n")
                    else sb.append("[$fileIndex/$totalFiles] $projectName/$relPath\n\n")
                    
                    if (isCustomFiltered) {
                        sb.append("> **[内容已省略]** 此处存在自定义规则过滤。\n\n")
                    } else if (node.size > maxFileSizeKb * 1024L) {
                        sb.append("> **[内容超限已忽略]** 文件大小 ${node.size / 1024} KB，超过设定上限 ${maxFileSizeKb} KB。\n\n")
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

    private fun buildTree(node: FileNode, prefix: String = ""): String {
        val sb = StringBuilder()
        if (prefix.isEmpty()) sb.append("└── 📁 ${node.name}\n")
        val children = node.children.sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
        children.forEachIndexed { i, child ->
            val isLast = i == children.size - 1
            val conn = if (isLast) "└── " else "├── "
            val icon = if (child.isDir) "📁" else "📄"
            val actualPrefix = if (prefix.isEmpty()) "    " else prefix
            sb.append("$actualPrefix$conn$icon ${child.name}\n")
            if (child.isDir) {
                val nextPrefix = actualPrefix + if (isLast) "    " else "│   "
                sb.append(buildTree(child, nextPrefix))
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

    private fun getLang(ext: String) = when (ext.lowercase()) {
        "kt" -> "kotlin"; "py" -> "python"; "gd" -> "gdscript"; "xml" -> "xml"; "json" -> "json"; "svg" -> "xml"; else -> ""
    }

    // ==========================================
    // 提示词：区分已知版本与未知版本
    // ==========================================
    private fun buildPrompt(mode: String, version: String): String {
        if (mode == "NONE") return ""
        
        val sb = StringBuilder()
        sb.append("\n\n---\n## 🎮 AI 创作提示（工具自动注入）\n\n")
        sb.append("### 身份与背景\n")
        
        if (version == "Unknown" || version == "4.x") {
            // 未知版本的处理
            sb.append("你是一个资深的 Godot 游戏开发者。当前上下文包含了一个完整的 Godot 游戏项目源码。\n")
            sb.append("**注意：系统未能从配置中精确识别出当前的 Godot 具体版本，请你在阅读代码前，先检查 `project.godot` 文件中的 `config/features` 或 `config_version` 字段，自行判断当前项目的具体版本，并根据该版本调整你的代码风格。**\n\n")
            sb.append("### 通用 Godot 4.x 安全红线\n")
            sb.append("- 跨文件引用资源请使用 `res://` 路径，避免使用绝对路径。\n")
            sb.append("- 严禁使用 Godot 3 的 API（如 `KinematicBody`、`instance()` 等）。\n")
            sb.append("- 如果在代码中发现 `.uid` 或 `unique_id`，请注意它们是由较高版本编辑器自动生成的，请勿主动修改或手写。\n")
        } else {
            // 已知版本的处理
            val v = version.toFloatOrNull() ?: 4.0f
            sb.append("你是一个资深的 Godot $version 游戏开发者。当前上下文包含了一个完整的 Godot $version 游戏项目源码。请围绕该游戏进行代码编写、重构或功能添加。\n\n")
            
            sb.append("### Godot $version 技术红线\n")
            sb.append("- 跨文件引用资源请使用 `res://` 路径，避免使用绝对路径。\n")
            sb.append("- 请严格使用 GDScript 和 Godot 4.x 的 API。\n")

            if (v >= 4.4f) {
                sb.append("- **.uid 文件**：由编辑器自动生成，请勿手写或修改。\n")
            }
            if (v >= 4.6f) {
                sb.append("- **unique_id**：`.tscn` 中的节点唯一标识由编辑器维护，请勿手写。\n")
                sb.append("- **物理引擎**：默认使用 Jolt Physics。\n")
            }
            if (v >= 4.7f) {
                sb.append("- **is_class 参数**：参数类型为 StringName，请使用 &\"ClassName\" 语法。\n")
                sb.append("- **RichTextLabel.add_image**：参数变更为 width_unit / height_unit。\n")
                sb.append("- **PackedArray 赋值**：直接设置不触发 setter，请使用 .set(i, x)。\n")
                sb.append("- **类型化返回值**：重写方法时必须显式写出 return 语句。\n")
            }
        }
        
        if (mode == "TUB_STYLE") {
            sb.append("\n### TUB 自动化工作流\n")
            sb.append("为了批量修改项目文件，请编写一个可以直接在项目根目录运行的 **纯 Python 脚本**。\n")
            sb.append("该脚本的功能是：\n")
            sb.append("1. **创建**所需的新文件。\n")
            sb.append("2. **覆盖修改**现有的文件。\n")
            sb.append("3. **删除**不需要的旧文件。\n")
            sb.append("请直接将修改逻辑写入 Python 脚本中，供用户一键执行。\n")
        }
        
        sb.append("\n请仔细阅读上述项目结构及代码，等待用户的具体游戏开发需求。\n")
        
        return sb.toString()
    }
}
