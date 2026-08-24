package com.soundist.feature.notes

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PDF_WIDTH = 595
private const val PDF_HEIGHT = 842
private const val PDF_MARGIN = 48f

internal fun exportNoteMarkdown(context: Context, note: Note): File {
    val file = exportFile(context, note, "md")
    val body = buildString {
        append("# ").append(note.title.ifBlank { "未命名笔记" }).append("\n\n")
        if (note.tags.isNotEmpty()) append(note.tags.joinToString(" ") { "#$it" }).append("\n\n")
        append(note.text.trim()).append("\n")
        if (note.checklist.isNotEmpty()) {
            append("\n## 清单\n\n")
            note.checklist.forEach { append(if (it.checked) "- [x] " else "- [ ] ").append(it.text).append('\n') }
        }
        note.context?.let { linked ->
            append("\n## 声音上下文\n\n")
            linked.targetName?.let { append("- 专注：").append(it).append('\n') }
            if (linked.soundNames.isNotEmpty()) append("- 环境声：").append(linked.soundNames.joinToString("、")).append('\n')
            linked.radioName?.let { append("- 电台：").append(it).append('\n') }
        }
        if (note.attachments.isNotEmpty()) {
            append("\n## 附件\n\n")
            note.attachments.forEach { append("- ").append(it.type.label).append("：").append(it.name).append('\n') }
        }
        append("\n---\n导出自 Soundist · ")
            .append(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()))
            .append('\n')
    }
    file.writeText(body, Charsets.UTF_8)
    return file
}

internal fun exportNotePdf(context: Context, note: Note): File {
    val file = exportFile(context, note, "pdf")
    val document = PdfDocument()
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF17201E.toInt(); textSize = 22f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF26312E.toInt(); textSize = 12f }
    val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF66726E.toInt(); textSize = 9f }
    val lines = buildList {
        if (note.tags.isNotEmpty()) add(note.tags.joinToString("  ") { "#$it" })
        addAll(note.text.lines())
        if (note.checklist.isNotEmpty()) {
            add("")
            add("清单")
            note.checklist.forEach { add("${if (it.checked) "☑" else "☐"} ${it.text}") }
        }
        note.context?.let { linked ->
            add("")
            add("声音上下文")
            linked.targetName?.let { add("专注：$it") }
            if (linked.soundNames.isNotEmpty()) add("环境声：${linked.soundNames.joinToString("、")}")
            linked.radioName?.let { add("电台：$it") }
        }
        if (note.attachments.isNotEmpty()) {
            add("")
            add("附件")
            note.attachments.forEach { add("${it.type.label}：${it.name}") }
        }
    }.flatMap { wrapPdfLine(it, bodyPaint, PDF_WIDTH - PDF_MARGIN * 2) }

    var pageNumber = 1
    var lineIndex = 0
    do {
        val page = document.startPage(PdfDocument.PageInfo.Builder(PDF_WIDTH, PDF_HEIGHT, pageNumber).create())
        val canvas = page.canvas
        var y = PDF_MARGIN
        if (pageNumber == 1) {
            canvas.drawText(note.title.ifBlank { "未命名笔记" }, PDF_MARGIN, y + 24f, titlePaint)
            y += 52f
        }
        while (lineIndex < lines.size && y <= PDF_HEIGHT - PDF_MARGIN - 24f) {
            canvas.drawText(lines[lineIndex], PDF_MARGIN, y + 14f, bodyPaint)
            y += 20f
            lineIndex++
        }
        canvas.drawText("Soundist · $pageNumber", PDF_MARGIN, PDF_HEIGHT - 24f, metaPaint)
        document.finishPage(page)
        pageNumber++
    } while (lineIndex < lines.size)

    FileOutputStream(file).use(document::writeTo)
    document.close()
    return file
}

internal fun shareNoteExport(context: Context, file: File, mimeType: String, title: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_TITLE, title.ifBlank { "Soundist 笔记" })
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, "导出笔记"))
}

internal fun shareAttachment(context: Context, attachment: NoteAttachment) {
    val file = when (attachment.type) {
        AttachmentType.DRAWING -> {
            val drawing = runCatching { readDrawingDocument(File(attachment.privatePath)) }.getOrNull()
            if (drawing == null) { shareNoteExport(context, File(attachment.privatePath), "application/octet-stream", attachment.name); return }
            rasterizeDrawingToPng(context, drawing, attachment.name)
        }
        else -> copyToSharedCache(context, attachment)
    }
    val mime = when (attachment.type) {
        AttachmentType.IMAGE -> "image/*"
        AttachmentType.RECORDING -> "audio/mp4"
        AttachmentType.DRAWING -> "image/png"
        else -> attachment.mimeType ?: "application/octet-stream"
    }
    shareNoteExport(context, file, mime, attachment.name)
}

private fun copyToSharedCache(context: Context, attachment: NoteAttachment): File {
    val source = File(attachment.privatePath)
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val safe = attachment.name.replace(Regex("[^\\p{L}\\p{N}._-]+"), "-").trim('-').ifBlank { "attachment" }
    val target = File(dir, safe)
    if (source.isFile && source.absolutePath != target.absolutePath) source.copyTo(target, overwrite = true)
    return target
}

private fun rasterizeDrawingToPng(context: Context, drawing: DrawingDocument, name: String): File {
    val allPoints = drawing.strokes.flatMap { it.points }
    val minX = allPoints.minOfOrNull { it.x } ?: 0f
    val minY = allPoints.minOfOrNull { it.y } ?: 0f
    val maxX = allPoints.maxOfOrNull { it.x } ?: minX + 1f
    val maxY = allPoints.maxOfOrNull { it.y } ?: minY + 1f
    val width = (maxX - minX).coerceAtLeast(1f).toInt().coerceIn(1, 2048)
    val height = (maxY - minY).coerceAtLeast(1f).toInt().coerceIn(1, 2048)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.translate(-minX, -minY)
    drawing.strokes.forEach { stroke ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stroke.color.toArgb()
            strokeWidth = stroke.width
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.STROKE
            if (stroke.erase) xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
        }
        val path = android.graphics.Path().apply {
            stroke.points.firstOrNull()?.let { moveTo(it.x, it.y) }
            stroke.points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        canvas.drawPath(path, paint)
    }
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val safe = name.substringBeforeLast('.').replace(Regex("[^\\p{L}\\p{N}._-]+"), "-").trim('-').ifBlank { "drawing" }
    val file = File(dir, "$safe.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
    return file
}

private fun exportFile(context: Context, note: Note, extension: String): File {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val safe = note.title.ifBlank { "soundist-note" }
        .replace(Regex("[^\\p{L}\\p{N}._-]+"), "-")
        .trim('-').take(48).ifBlank { "soundist-note" }
    return File(dir, "$safe.$extension")
}

private fun wrapPdfLine(text: String, paint: Paint, width: Float): List<String> {
    if (text.isEmpty()) return listOf("")
    val result = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        val count = paint.breakText(text, start, text.length, true, width, null).coerceAtLeast(1)
        result += text.substring(start, start + count)
        start += count
    }
    return result
}
