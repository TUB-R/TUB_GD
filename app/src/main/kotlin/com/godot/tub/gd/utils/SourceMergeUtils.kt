
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

    fun scanDirectory(
        context: Context, treeUri: Uri, outputFormat: String, exportMode: String, injectPrompt: Boolean, customExcludeExts: Set<String>, maxFileSizeKb: Int
    ): String {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return "无法读取该文件夹"
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

        return buildOutput(context, rootNode.name, rootNode, outputFormat, exportMode, injectPrompt, customExcludeExts, maxFileSizeKb)
    }

    fun processMultipleFiles(context: Context, uris: List<Uri>, outputFormat: String, injectPrompt: Boolean, customExcludeExts: Set<String>, maxFileSizeKb: Int): String {
        val sb = StringBuilder()
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        
        if (outputFormat == "XML") {
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<project name=\"MultiFiles\" generated=\"$date\">\n")
            sb.append("  <note>此文档由用户手动选取的多个独立文件合并而成，可能不属于同一目录结构。</note>\n")
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
            if (injectPrompt) {
                sb.append("  <prompt>\n    <![CDATA[\n").append(buildPrompt()).append("\n]]>\n  </prompt>\n")
            }
            sb.append("</project>")
            return sb.toString()
        }

        // TXT 和 Markdown 的分支
        val isMd = (outputFormat == "MARKDOWN")
        
        if (isMd) {
            sb.append("# 多文件合并\n\n> 文件数: ${uris.size} | 生成时间: $date\n> *注：此为独立选取的文件合并，可能不属于同一目录树。*\n\n")
        } else {
            sb.append("多文件合并\n文件数: ${uris.size} | 生成时间: $date\n注：此为独立选取的文件合并。\n\n")
        }
        
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
        
        if (injectPrompt) {
            if (isMd) sb.append(buildPrompt())
            else sb.append(buildPrompt().replace("##", "").replace("**", "")) // TXT 剥离 Markdown 语法
        }
        return sb.toString()
    }

    private fun buildOutput(
        context: Context, projectName: String, rootNode: FileNode, 
        outputFormat: String, exportMode: String, injectPrompt: Boolean, customExcludeExts: Set<String>, maxFileSizeKb: Int
    ): String {
        val sb = StringBuilder()
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        
        // ================= XML 模式 =================
        if (outputFormat == "XML") {
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<project name=\"$projectName\" generated=\"$date\">\n")
            if (exportMode == "PROJECT_SUMMARY") {
                sb.append("  <summary>暂时没有设计awa</summary>\n</project>")
                return sb.toString()
            }
            
            if (exportMode != "PROJECT_SUMMARY") {
                sb.append("  <structure>\n")
                sb.append(buildTree(rootNode).lines().joinToString("\n") { "    $it" })
                sb.append("\n  </structure>\n")
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
            
            if (exportMode == "FULL_EXPORT" && injectPrompt) {
                sb.append("  <prompt>\n    <![CDATA[\n").append(buildPrompt()).append("\n]]>\n  </prompt>\n")
            }
            
            sb.append("</project>")
            return sb.toString()
        }

        // ================= TXT / Markdown 模式 =================
        val isMd = (outputFormat == "MARKDOWN")
        
        if (isMd) {
            sb.append("# 项目: $projectName\n\n")
            sb.append("> 生成时间: $date\n\n")
            if (exportMode == "DIR_ONLY") sb.append("*本文档仅包含项目的目录结构。*\n\n") else sb.append("*本文档包含目录结构与文件内容。*\n\n")
        } else {
            // TXT 纯净版头部
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

        if (exportMode == "FULL_EXPORT" && injectPrompt) {
            if (isMd) sb.append(buildPrompt())
            else sb.append(buildPrompt().replace("##", "").replace("**", "")) // TXT 剥离 Markdown 语法
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
        if (prefix.isEmpty()) {
            sb.append("└── 📁 ${node.name}\n")
        }
        
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
    
    private fun buildPrompt(): String = "\n\n---\n## AI 创作提示（工具自动注入）\n\n### 身份\n你是资深 Godot 游戏开发大师，熟悉当前版本 API、节点树架构、信号系统、渲染管线与移动端优化。\n\n### 技术红线\n- **不要手写 `.import`**：每个资源旁的 `.import` 文件由编辑器自动生成。\n- **跨文件引用**：用 `res://` 路径，不要用绝对路径。\n- **`.import/` 目录是缓存**：所有导入产物由编辑器重建，不要手动编辑。\n"
}
