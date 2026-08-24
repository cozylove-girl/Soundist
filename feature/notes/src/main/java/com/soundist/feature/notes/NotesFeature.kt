package com.soundist.feature.notes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soundist.core.designsystem.SoundistColors
import com.soundist.core.designsystem.SoundSlider
import com.soundist.core.designsystem.activity
import com.soundist.core.designsystem.archive
import com.soundist.core.designsystem.archiveRestore
import com.soundist.core.designsystem.arrowLeft
import com.soundist.core.designsystem.bookOpen
import com.soundist.core.designsystem.bookmark
import com.soundist.core.designsystem.briefcase
import com.soundist.core.designsystem.check
import com.soundist.core.designsystem.checkSquare
import com.soundist.core.designsystem.chevronDown
import com.soundist.core.designsystem.chevronRight
import com.soundist.core.designsystem.circleAlert
import com.soundist.core.designsystem.circlePause
import com.soundist.core.designsystem.eraser
import com.soundist.core.designsystem.fileText
import com.soundist.core.designsystem.flag
import com.soundist.core.designsystem.folderInput
import com.soundist.core.designsystem.graduationCap
import com.soundist.core.designsystem.heading2
import com.soundist.core.designsystem.heart
import com.soundist.core.designsystem.home
import com.soundist.core.designsystem.image
import com.soundist.core.designsystem.listChecks
import com.soundist.core.designsystem.mic
import com.soundist.core.designsystem.moreHorizontal
import com.soundist.core.designsystem.music2
import com.soundist.core.designsystem.paperclip
import com.soundist.core.designsystem.pencilLine
import com.soundist.core.designsystem.pin
import com.soundist.core.designsystem.plane
import com.soundist.core.designsystem.playCircle
import com.soundist.core.designsystem.plus
import com.soundist.core.designsystem.search
import com.soundist.core.designsystem.star
import com.soundist.core.designsystem.tag
import com.soundist.core.designsystem.trash2
import com.soundist.core.designsystem.undo2
import com.soundist.core.designsystem.user
import com.soundist.core.designsystem.x
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.draw.shadow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.time.Clock
import java.text.Collator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

const val CURRENT_NOTE_SCHEMA_VERSION = 2
const val NOTES_PAGE_SCROLL_TEST_TAG = "notes-page-scroll"
internal val notesPageScrollSemantics: String get() = "LazyColumn:$NOTES_PAGE_SCROLL_TEST_TAG"

enum class BlockType { PARAGRAPH, CHECKLIST, QUOTE, HEADING, INTERNAL_LINK }

data class NoteBlock(
    val id: String = UUID.randomUUID().toString(),
    val type: BlockType = BlockType.PARAGRAPH,
    val text: String = "",
    val checked: Boolean = false,
    val linkedNoteId: String? = null,
    val version: Int = 1,
)

enum class AttachmentType(val label: String) { IMAGE("图片"), FILE("文件"), RECORDING("录音"), DRAWING("手写") }

data class NoteAttachment(
    val id: String = UUID.randomUUID().toString(),
    val type: AttachmentType,
    val name: String,
    val privatePath: String,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val durationMillis: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

data class Notebook(
    val id: String,
    val title: String,
    val accent: Long,
    val system: Boolean = false,
    val iconKey: String = "bookOpen",
)

data class NoteContext(
    val focusSessionId: String? = null,
    val targetKind: String? = null,
    val targetId: String? = null,
    val targetName: String? = null,
    val soundNames: List<String> = emptyList(),
    val radioName: String? = null,
)

data class Note(
    val id: String = UUID.randomUUID().toString(),
    val notebookId: String,
    val title: String = "",
    val text: String = "",
    val checklist: List<NoteBlock> = emptyList(),
    val internalLinks: List<NoteBlock> = emptyList(),
    val tags: Set<String> = emptySet(),
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val trashedAt: Long? = null,
    val originalNotebookId: String? = null,
    val attachments: List<NoteAttachment> = emptyList(),
    val context: NoteContext? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val revision: Long = 1,
    val schemaVersion: Int = CURRENT_NOTE_SCHEMA_VERSION,
) {
    /** Flat storage model: body is a single string (one PARAGRAPH core block), checklist + internal links are extra blocks. */
    val allBlocks: List<NoteBlock>
        get() = listOf(NoteBlock(id = "body-$id", type = BlockType.PARAGRAPH, text = text, version = revision.toInt().coerceAtLeast(1))) + checklist + internalLinks
}

enum class NotesView { LIBRARY, NOTEBOOK, EDITOR }
enum class PendingNoteAction { NONE, RECORDING, DRAWING, ATTACHMENT }
enum class NoteFilter { ALL, PINNED, IMAGES, AUDIO, CHECKLISTS, LINKED, ARCHIVED }
enum class NoteSort { UPDATED, CREATED, TITLE }
enum class SaveStatus { SAVED, SAVING, ERROR }

data class NotesState(
    val notebooks: List<Notebook> = defaultNotebooks(),
    val notes: List<Note> = emptyList(),
    val view: NotesView = NotesView.LIBRARY,
    val selectedNotebookId: String = ALL_NOTES_ID,
    val selectedNoteId: String? = null,
    val query: String = "",
    val filter: NoteFilter = NoteFilter.ALL,
    val sort: NoteSort = NoteSort.UPDATED,
    val saveStatus: SaveStatus = SaveStatus.SAVED,
    val assetError: String? = null,
    val pendingAction: PendingNoteAction = PendingNoteAction.NONE,
    val selectedTagFilter: String? = null,
)

data class NoteNotice(
    val id: Long,
    val message: String,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
)

interface NotesRepository : AutoCloseable {
    val state: StateFlow<NotesState>
    fun update(transform: (NotesState) -> NotesState)
    fun createNotebook(notebook: Notebook) = Unit
    fun renameNotebook(id: String, title: String) = Unit
    fun deleteNotebook(id: String) = Unit
    override fun close() = Unit
}

class InMemoryNotesRepository(initial: NotesState = NotesState()) : NotesRepository {
    private val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<NotesState> = mutableState.asStateFlow()
    override fun update(transform: (NotesState) -> NotesState) = mutableState.update(transform)
    override fun createNotebook(notebook: Notebook) { mutableState.update { it.copy(notebooks = it.notebooks + notebook) } }
    override fun renameNotebook(id: String, title: String) { mutableState.update { it.copy(notebooks = it.notebooks.map { notebook -> if (notebook.id == id) notebook.copy(title = title) else notebook }) } }
    override fun deleteNotebook(id: String) { mutableState.update { state -> state.copy(notebooks = state.notebooks.filterNot { it.id == id }, notes = state.notes.map { note -> if (note.notebookId == id) note.copy(notebookId = "nb4") else note }) } }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CoreNotesRepository(
    private val core: com.soundist.core.model.NotesRepository,
) : NotesRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(NotesState())
    override val state: StateFlow<NotesState> = mutableState.asStateFlow()

    init {
        scope.launch {
            combine(
                core.observeNotebooks(),
                core.observeNotes(includeDeleted = true).flatMapLatest { notes ->
                    if (notes.isEmpty()) kotlinx.coroutines.flow.flowOf(Triple(notes, emptyMap(), emptyMap()))
                    else combineNoteChildren(core, notes)
                },
            ) { notebooks, payload ->
                val (notes, blocks, attachments) = payload
                val previous = mutableState.value
                val previousById = previous.notes.associateBy(Note::id)
                previous.copy(
                    notebooks = defaultNotebooks() + notebooks.filterNot { item -> item.id in defaultNotebooks().map(Notebook::id) }.map { item ->
                        Notebook(item.id, item.title, item.accent, iconKey = item.iconKey)
                    },
                    notes = notes.map { item -> item.toFeatureNote(blocks[item.id].orEmpty(), attachments[item.id].orEmpty()).copy(context = previousById[item.id]?.context) },
                )
            }.collect { mutableState.value = it }
        }
    }

    override fun update(transform: (NotesState) -> NotesState) {
        val before = mutableState.value
        val after = transform(before)
        val contentChanged = before.notes.map { it.copy(context = null) } != after.notes.map { it.copy(context = null) }
        mutableState.value = if (contentChanged) after.copy(saveStatus = SaveStatus.SAVING) else after
        if (!contentChanged) return
        scope.launch {
            runCatching {
                val beforeById = before.notes.associateBy(Note::id)
                after.notes.forEach { note ->
                    val old = beforeById[note.id]
                    if (old != note) {
                        core.saveNote(note.toCoreNote())
                        core.saveBlocks(note.id, note.allBlocks.mapIndexed { index, block -> block.toCoreBlock(note.id, index.toDouble()) })
                        note.attachments.forEach { core.saveAttachment(it.toCoreAttachment(note.id)) }
                        old?.attachments?.filterNot { attachment -> note.attachments.any { it.id == attachment.id } }?.forEach { core.deleteAttachment(it.id) }
                    }
                }
                before.notes.filterNot { old -> after.notes.any { it.id == old.id } }.forEach { core.deleteForever(it.id) }
            }.onSuccess {
                mutableState.update { current -> if (current.notes == after.notes) current.copy(saveStatus = SaveStatus.SAVED) else current }
            }.onFailure {
                mutableState.update { current -> if (current.notes == after.notes) current.copy(saveStatus = SaveStatus.ERROR) else current }
            }
        }
    }

    override fun close() { scope.cancel() }
    override fun createNotebook(notebook: Notebook) { scope.launch { core.saveNotebook(com.soundist.core.model.Notebook(notebook.id, notebook.title, System.currentTimeMillis(), accent = notebook.accent, iconKey = notebook.iconKey)) } }
    override fun renameNotebook(id: String, title: String) {
        val notebook = mutableState.value.notebooks.firstOrNull { it.id == id && !it.system } ?: return
        mutableState.update { it.copy(notebooks = it.notebooks.map { row -> if (row.id == id) row.copy(title = title) else row }) }
        scope.launch { core.saveNotebook(com.soundist.core.model.Notebook(notebook.id, title, System.currentTimeMillis(), accent = notebook.accent, iconKey = notebook.iconKey)) }
    }
    override fun deleteNotebook(id: String) {
        if (mutableState.value.notebooks.firstOrNull { it.id == id }?.system != false) return
        val moved = mutableState.value.notes.filter { it.notebookId == id }.map { it.copy(notebookId = "nb4", updatedAt = System.currentTimeMillis(), revision = it.revision + 1) }
        mutableState.update { state -> state.copy(notebooks = state.notebooks.filterNot { it.id == id }, notes = state.notes.map { note -> moved.firstOrNull { it.id == note.id } ?: note }, selectedNotebookId = ALL_NOTES_ID, view = NotesView.LIBRARY) }
        scope.launch {
            moved.forEach { core.saveNote(it.toCoreNote()) }
            core.deleteNotebook(id)
        }
    }

    private fun com.soundist.core.model.Note.toFeatureNote(blocks: List<com.soundist.core.model.NoteBlock>, attachments: List<com.soundist.core.model.Attachment>) = Note(
        id = id, notebookId = notebookId ?: "nb4", title = title,
        text = blocks.filter { it.kind != com.soundist.core.model.NoteBlockKind.CHECKLIST && it.kind != com.soundist.core.model.NoteBlockKind.INTERNAL_LINK }
            .joinToString("\n") { it.text }.ifEmpty { body },
        checklist = blocks.filter { it.kind == com.soundist.core.model.NoteBlockKind.CHECKLIST }
            .map { NoteBlock(it.id, BlockType.CHECKLIST, it.text, it.checked, it.linkedNoteId, it.revision.toInt().coerceAtLeast(1)) },
        internalLinks = blocks.filter { it.kind == com.soundist.core.model.NoteBlockKind.INTERNAL_LINK }
            .map { NoteBlock(it.id, BlockType.INTERNAL_LINK, it.text, it.checked, it.linkedNoteId, it.revision.toInt().coerceAtLeast(1)) },
        tags = tags, pinned = pinned, archived = archived, trashedAt = deletedAt, originalNotebookId = originalNotebookId,
        attachments = attachments.map { NoteAttachment(it.id, AttachmentType.valueOf(if (it.kind.name == "AUDIO") "RECORDING" else it.kind.name), it.displayName, it.localUri, it.mimeType, it.sizeBytes, it.durationSeconds?.times(1000), it.updatedAt) },
        createdAt = createdAt, updatedAt = updatedAt,
    )

    private fun Note.toCoreNote() = com.soundist.core.model.Note(id, notebookId, title, text, archived, trashedAt, updatedAt, createdAt, pinned, tags, originalNotebookId)
    private fun NoteBlock.toCoreBlock(noteId: String, position: Double) = com.soundist.core.model.NoteBlock(id, noteId, com.soundist.core.model.NoteBlockKind.valueOf(type.name), text, checked, linkedNoteId = linkedNoteId, position = position, revision = version.toLong())
    private fun NoteAttachment.toCoreAttachment(noteId: String) = com.soundist.core.model.Attachment(id, noteId, mimeType ?: "application/octet-stream", privatePath, null, createdAt, com.soundist.core.model.AttachmentKind.valueOf(if (type == AttachmentType.RECORDING) "AUDIO" else type.name), name, sizeBytes ?: 0, durationMillis?.div(1000))
}

private fun combineNoteChildren(
    core: com.soundist.core.model.NotesRepository,
    notes: List<com.soundist.core.model.Note>,
): Flow<Triple<List<com.soundist.core.model.Note>, Map<String, List<com.soundist.core.model.NoteBlock>>, Map<String, List<com.soundist.core.model.Attachment>>>> {
    val blockFlows = notes.map { core.observeBlocks(it.id) }
    val attachmentFlows = notes.map { core.observeAttachments(it.id) }
    return combine(combine(blockFlows) { it.toList() }, combine(attachmentFlows) { it.toList() }) { blockRows, attachmentRows ->
        Triple(notes, notes.mapIndexed { index, note -> note.id to blockRows[index] }.toMap(), notes.mapIndexed { index, note -> note.id to attachmentRows[index] }.toMap())
    }
}

interface NoteAssetStore {
    /** Imports a picker result into app-private storage. URIs are never retained as the source of truth. */
    suspend fun import(context: Context, uri: Uri, type: AttachmentType, displayName: String): NoteAttachment
    suspend fun saveDrawing(context: Context, noteId: String, drawing: DrawingDocument): NoteAttachment
    /** Replaces the content of an existing drawing attachment in place, keeping its id and path. */
    suspend fun overwriteDrawing(context: Context, existing: NoteAttachment, drawing: DrawingDocument): NoteAttachment
    suspend fun delete(attachment: NoteAttachment)
    /** App.tsx mediaStore purgeDeletedMedia：宽限期后清除删除标记并回收残留文件。 */
    suspend fun garbageCollect(context: Context) = Unit
}

data class AttachmentSelection(
    val uri: Uri,
    val displayName: String,
    val type: AttachmentType,
)

data class AttachmentPickerRequest(
    val noteId: String,
    val type: AttachmentType,
    val mimeTypes: List<String>,
    val allowMultiple: Boolean = true,
    val maximumItems: Int = 5,
    val maximumBytesPerItem: Long = 8L * 1024 * 1024,
)

fun interface NoteAttachmentPicker {
    fun launch(request: AttachmentPickerRequest, onResult: (Result<List<AttachmentSelection>>) -> Unit)
}

class AppPrivateNoteAssetStore : NoteAssetStore {
    override suspend fun import(context: Context, uri: Uri, type: AttachmentType, displayName: String): NoteAttachment {
        val directory = File(context.filesDir, "note-attachments").apply { mkdirs() }
        val target = File(directory, "${UUID.randomUUID()}-${displayName.safeFileName()}")
        context.contentResolver.openInputStream(uri).use { source ->
            copyAttachmentToPrivateFile(requireNotNull(source) { "无法读取附件" }, target)
        }
        clearAttachmentTombstone(target)
        return NoteAttachment(
            type = type,
            name = displayName,
            privatePath = target.absolutePath,
            mimeType = context.contentResolver.getType(uri),
            sizeBytes = target.length(),
        )
    }

    override suspend fun saveDrawing(context: Context, noteId: String, drawing: DrawingDocument): NoteAttachment {
        require(drawing.strokes.isNotEmpty()) { "手写内容为空" }
        val directory = File(context.filesDir, "note-drawings").apply { mkdirs() }
        val file = File(directory, "${noteId}-${drawing.id}.sdi")
        writeDrawingDocument(file, drawing)
        clearAttachmentTombstone(file)
        return NoteAttachment(
            type = AttachmentType.DRAWING,
            name = "手写-${SimpleDateFormat("MM-dd HH:mm", Locale.ROOT).format(Date())}.sdi",
            privatePath = file.absolutePath,
            mimeType = DRAWING_MIME_TYPE,
            sizeBytes = file.length(),
        )
    }

    override suspend fun overwriteDrawing(context: Context, existing: NoteAttachment, drawing: DrawingDocument): NoteAttachment {
        require(drawing.strokes.isNotEmpty()) { "手写内容为空" }
        val file = File(existing.privatePath)
        writeDrawingDocument(file, drawing)
        clearAttachmentTombstone(file)
        return existing.copy(sizeBytes = file.length(), mimeType = DRAWING_MIME_TYPE)
    }

    override suspend fun delete(attachment: NoteAttachment) {
        val file = File(attachment.privatePath)
        if (file.exists() && file.isFile) file.delete()
        recordAttachmentTombstone(file)
    }

    override suspend fun garbageCollect(context: Context) = withContext(Dispatchers.IO) {
        // App.tsx mediaStore purgeDeletedMedia({ deletedBefore: Date.now() - 5600, clearTombstones: true })
        listOf("note-attachments", "note-recordings", "note-drawings").forEach { dirName ->
            val directory = File(context.filesDir, dirName)
            if (!directory.isDirectory) return@forEach
            directory.listFiles { file -> file.isFile && file.name.endsWith(ATTACHMENT_TOMBSTONE_SUFFIX) }
                ?.forEach { tombstone ->
                    val deletedAt = tombstone.readText().toLongOrNull() ?: 0L
                    if (System.currentTimeMillis() - deletedAt >= ATTACHMENT_TOMBSTONE_GRACE_MILLIS) {
                        val target = File(directory, tombstone.name.removePrefix(".").removeSuffix(ATTACHMENT_TOMBSTONE_SUFFIX))
                        if (target.isFile) target.delete()
                        tombstone.delete()
                    }
                }
        }
    }
}

private const val ATTACHMENT_TOMBSTONE_SUFFIX = ".tomb"
private const val ATTACHMENT_TOMBSTONE_GRACE_MILLIS = 5_600L

/** 与前端 mediaStore 的 tombstone 等价：删除附件时记录删除标记，宽限期后由 GC 清除。 */
private fun recordAttachmentTombstone(file: File) {
    val tombstone = File(file.parentFile ?: return, ".${file.name}$ATTACHMENT_TOMBSTONE_SUFFIX")
    runCatching {
        tombstone.parentFile?.mkdirs()
        tombstone.writeText(System.currentTimeMillis().toString())
    }
}

/** 保存/导入新附件时清除同名 tombstone（前端 saveMediaBlob 会删除 tombstone）。 */
private fun clearAttachmentTombstone(file: File) {
    val tombstone = File(file.parentFile ?: return, ".${file.name}$ATTACHMENT_TOMBSTONE_SUFFIX")
    if (tombstone.isFile) runCatching { tombstone.delete() }
}

internal fun copyAttachmentToPrivateFile(
    source: InputStream,
    target: File,
    maximumBytes: Long = MAX_ATTACHMENT_BYTES,
): Long = try {
    target.outputStream().buffered().use { sink ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = source.read(buffer)
            if (read < 0) break
            total += read
            require(total <= maximumBytes) { "附件不能超过 8 MB" }
            sink.write(buffer, 0, read)
        }
        total
    }
} catch (failure: Throwable) {
    target.delete()
    throw failure
}

fun readDrawingDocument(file: File): DrawingDocument = DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
    require(input.readInt() == DRAWING_FILE_MAGIC) { "不是 Soundist 手写文件" }
    val version = input.readInt()
    require(version in 1..DRAWING_FILE_VERSION) { "不支持的手写文件版本" }
    val id = input.readUTF()
    val strokeCount = input.readInt().also { require(it in 0..20_000) { "手写笔画数量异常" } }
    DrawingDocument(id, List(strokeCount) {
        val color = Color(input.readInt())
        val width = input.readFloat()
        val pointCount = input.readInt().also { require(it in 0..1_000_000) { "手写点数量异常" } }
        val points = List(pointCount) { Offset(input.readFloat(), input.readFloat()) }
        val erase = if (version >= 2) input.readBoolean() else false
        InkStroke(color, width, points, erase)
    })
}

fun writeDrawingDocument(file: File, drawing: DrawingDocument) {
    require(drawing.strokes.isNotEmpty()) { "手写内容为空" }
    val temporary = File(file.parentFile ?: file.absoluteFile.parentFile, ".${file.name}.${UUID.randomUUID()}.tmp")
    try {
        DataOutputStream(BufferedOutputStream(temporary.outputStream())).use { output ->
            output.writeInt(DRAWING_FILE_MAGIC)
            output.writeInt(DRAWING_FILE_VERSION)
            output.writeUTF(drawing.id)
            output.writeInt(drawing.strokes.size)
            drawing.strokes.forEach { stroke ->
                output.writeInt(stroke.color.toArgb())
                output.writeFloat(stroke.width)
                output.writeInt(stroke.points.size)
                stroke.points.forEach { point -> output.writeFloat(point.x); output.writeFloat(point.y) }
                output.writeBoolean(stroke.erase)
            }
        }
        RandomAccessFile(temporary, "rw").use { it.fd.sync() }
        if (!temporary.renameTo(file)) {
            temporary.copyTo(file, overwrite = true)
        }
    } finally {
        temporary.delete()
    }
}

@Deprecated("Use NoteAssetStore", ReplaceWith("NoteAssetStore"))
typealias PrivateAttachmentStore = NoteAssetStore
@Deprecated("Use AppPrivateNoteAssetStore", ReplaceWith("AppPrivateNoteAssetStore"))
typealias AppPrivateAttachmentStore = AppPrivateNoteAssetStore

enum class RecorderStatus { IDLE, RECORDING, PAUSED, SAVING, ERROR }

data class RecorderState(
    val status: RecorderStatus = RecorderStatus.IDLE,
    val elapsedMillis: Long = 0,
    val errorMessage: String? = null,
) {
    val recording: Boolean get() = status == RecorderStatus.RECORDING || status == RecorderStatus.PAUSED
    val paused: Boolean get() = status == RecorderStatus.PAUSED
}

interface NoteRecorder : AutoCloseable {
    val state: StateFlow<RecorderState>
    suspend fun start(noteId: String)
    suspend fun pause()
    suspend fun resume()
    suspend fun stop(): NoteAttachment?
    suspend fun cancel()
    fun reportError(message: String)
    fun clearError()
    override fun close() = Unit
}

object DisabledNoteRecorder : NoteRecorder {
    private val mutableState = MutableStateFlow(RecorderState())
    override val state: StateFlow<RecorderState> = mutableState.asStateFlow()
    override suspend fun start(noteId: String) { mutableState.value = RecorderState(RecorderStatus.ERROR, errorMessage = "无法启动录音，请检查录音服务") }
    override suspend fun pause() = Unit
    override suspend fun resume() = Unit
    override suspend fun stop(): NoteAttachment? = null
    override suspend fun cancel() = Unit
    override fun reportError(message: String) { mutableState.value = RecorderState(RecorderStatus.ERROR, errorMessage = message) }
    override fun clearError() { mutableState.value = RecorderState() }
}

/** Production recorder writes directly to app-private storage; the Activity owns runtime permission UX. */
class AndroidNoteRecorder(private val context: Context) : NoteRecorder {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableState = MutableStateFlow(RecorderState())
    override val state: StateFlow<RecorderState> = mutableState.asStateFlow()
    private var recorder: MediaRecorder? = null
    private var output: File? = null
    private var startedAt = 0L
    private var elapsedBeforeResume = 0L
    private var ticker: Job? = null

    override suspend fun start(noteId: String) {
        if (mutableState.value.recording) return
        clearError()
        runCatching {
            val directory = File(context.filesDir, "note-recordings").apply { mkdirs() }
            val file = File(directory, "${noteId}-${UUID.randomUUID()}.m4a")
            val instance = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
            recorder = instance
            output = file
            instance.setAudioSource(MediaRecorder.AudioSource.MIC)
            instance.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            instance.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            instance.setAudioEncodingBitRate(128_000)
            instance.setAudioSamplingRate(44_100)
            instance.setOutputFile(file.absolutePath)
            instance.prepare()
            instance.start()
            startedAt = System.currentTimeMillis(); elapsedBeforeResume = 0L
            mutableState.value = RecorderState(RecorderStatus.RECORDING)
            startTicker()
        }.onFailure { failure ->
            runCatching { recorder?.release() }; recorder = null; discardIncompleteRecording(output); output = null
            mutableState.value = RecorderState(RecorderStatus.ERROR, errorMessage = failure.recordingMessage())
        }
    }

    override suspend fun pause() {
        if (mutableState.value.status != RecorderStatus.RECORDING) return
        if (Build.VERSION.SDK_INT < 24) { mutableState.value = mutableState.value.copy(status = RecorderStatus.ERROR, errorMessage = "当前系统不支持暂停录音"); return }
        runCatching { recorder?.pause() }.onSuccess {
            elapsedBeforeResume = currentElapsed(); ticker?.cancel(); ticker = null
            mutableState.value = RecorderState(RecorderStatus.PAUSED, elapsedBeforeResume)
        }.onFailure { failRecording(it.recordingMessage(), currentElapsed()) }
    }

    override suspend fun resume() {
        if (mutableState.value.status != RecorderStatus.PAUSED) return
        runCatching { recorder?.resume() }.onSuccess {
            startedAt = System.currentTimeMillis()
            mutableState.value = RecorderState(RecorderStatus.RECORDING, elapsedBeforeResume)
            startTicker()
        }.onFailure { failRecording(it.recordingMessage(), elapsedBeforeResume) }
    }

    override suspend fun stop(): NoteAttachment? {
        val instance = recorder ?: return null
        val pendingOutput = output
        val duration = if (mutableState.value.paused) elapsedBeforeResume else currentElapsed()
        ticker?.cancel(); ticker = null; mutableState.value = RecorderState(RecorderStatus.SAVING, duration)
        return runCatching {
            instance.stop(); instance.release(); recorder = null
            val file = pendingOutput.also { output = null }
            requireNotNull(file?.takeIf(File::exists)) { "录音文件不存在" }
            require(file.length() > 0L) { "录音文件为空" }
            mutableState.value = RecorderState()
            NoteAttachment(type = AttachmentType.RECORDING, name = "录音-${SimpleDateFormat("MM-dd HH:mm", Locale.ROOT).format(Date())}.m4a", privatePath = file.absolutePath, mimeType = "audio/mp4", sizeBytes = file.length(), durationMillis = duration)
        }.getOrElse { failure ->
            runCatching { instance.release() }; recorder = null; discardIncompleteRecording(pendingOutput); output = null
            mutableState.value = RecorderState(RecorderStatus.ERROR, duration, failure.recordingMessage())
            null
        }
    }

    override suspend fun cancel() {
        val instance = recorder
        if (instance != null) { runCatching { instance.stop() }; runCatching { instance.release() } }
        ticker?.cancel(); ticker = null; recorder = null; discardIncompleteRecording(output); output = null; mutableState.value = RecorderState()
    }

    override fun reportError(message: String) {
        if (mutableState.value.recording) failRecording(message, currentElapsed())
        else mutableState.value = RecorderState(RecorderStatus.ERROR, errorMessage = message)
    }
    override fun clearError() { if (mutableState.value.status == RecorderStatus.ERROR) mutableState.value = RecorderState() }
    override fun close() {
        ticker?.cancel(); ticker = null
        runCatching { recorder?.stop() }; runCatching { recorder?.release() }; recorder = null
        discardIncompleteRecording(output); output = null
        scope.cancel()
        mutableState.value = RecorderState()
    }

    private fun currentElapsed() = elapsedBeforeResume + (System.currentTimeMillis() - startedAt).coerceAtLeast(0)
    private fun failRecording(message: String, elapsedMillis: Long) {
        ticker?.cancel(); ticker = null
        runCatching { recorder?.release() }; recorder = null
        discardIncompleteRecording(output); output = null
        mutableState.value = RecorderState(RecorderStatus.ERROR, elapsedMillis, message)
    }
    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (true) { delay(250); mutableState.value = RecorderState(RecorderStatus.RECORDING, currentElapsed()) }
        }
    }
}

internal fun discardIncompleteRecording(file: File?) {
    if (file?.isFile == true) file.delete()
}

data class NoteContextSnapshot(val context: NoteContext)
fun interface NoteContextProvider { suspend fun current(): NoteContextSnapshot? }

data class InkStroke(val color: Color, val width: Float, val points: List<Offset>, val erase: Boolean = false)
data class DrawingDocument(val id: String = UUID.randomUUID().toString(), val strokes: List<InkStroke>)

private const val DRAWING_FILE_MAGIC = 0x53444931
private const val DRAWING_FILE_VERSION = 2
private const val DRAWING_MIME_TYPE = "application/vnd.soundist.ink"
private const val NOTE_CONTEXT_FILE_MAGIC = 0x534E4331
private const val NOTE_CONTEXT_FILE_VERSION = 1

interface NoteContextStore {
    suspend fun read(context: Context, noteId: String): NoteContext?
    suspend fun write(context: Context, noteId: String, noteContext: NoteContext?)
    suspend fun delete(context: Context, noteId: String)
}

object DisabledNoteContextStore : NoteContextStore {
    override suspend fun read(context: Context, noteId: String): NoteContext? = null
    override suspend fun write(context: Context, noteId: String, noteContext: NoteContext?) = Unit
    override suspend fun delete(context: Context, noteId: String) = Unit
}

class AppPrivateNoteContextStore : NoteContextStore {
    override suspend fun read(context: Context, noteId: String): NoteContext? = withContext(Dispatchers.IO) {
        val file = context.noteContextFile(noteId)
        if (file.isFile) runCatching { readNoteContext(file) }.getOrNull() else null
    }
    override suspend fun write(context: Context, noteId: String, noteContext: NoteContext?): Unit = withContext(Dispatchers.IO) {
        val file = context.noteContextFile(noteId)
        if (noteContext == null) file.delete() else writeNoteContext(file, noteContext)
        Unit
    }
    override suspend fun delete(context: Context, noteId: String) = withContext(Dispatchers.IO) { context.noteContextFile(noteId).delete(); Unit }
}

private fun Context.noteContextFile(noteId: String): File = File(File(filesDir, "note-contexts").apply { mkdirs() }, "${noteId.safeFileName()}.snc")

fun readNoteContext(file: File): NoteContext = DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
    require(input.readInt() == NOTE_CONTEXT_FILE_MAGIC) { "不是 Soundist 笔记上下文文件" }
    require(input.readInt() == NOTE_CONTEXT_FILE_VERSION) { "不支持的笔记上下文版本" }
    fun optional(): String? = input.readBoolean().let { present -> if (present) input.readUTF() else null }
    val focusSessionId = optional()
    val targetKind = optional()
    val targetId = optional()
    val targetName = optional()
    val soundCount = input.readInt().also { require(it in 0..1_000) { "声音上下文数量异常" } }
    val sounds = List(soundCount) { input.readUTF() }
    NoteContext(focusSessionId, targetKind, targetId, targetName, sounds, optional())
}

fun writeNoteContext(file: File, noteContext: NoteContext) {
    val temporary = File(file.parentFile ?: file.absoluteFile.parentFile, ".${file.name}.${UUID.randomUUID()}.tmp")
    file.parentFile?.mkdirs()
    fun DataOutputStream.optional(value: String?) { writeBoolean(value != null); if (value != null) writeUTF(value) }
    try {
        DataOutputStream(BufferedOutputStream(temporary.outputStream())).use { output ->
            output.writeInt(NOTE_CONTEXT_FILE_MAGIC)
            output.writeInt(NOTE_CONTEXT_FILE_VERSION)
            output.optional(noteContext.focusSessionId)
            output.optional(noteContext.targetKind)
            output.optional(noteContext.targetId)
            output.optional(noteContext.targetName)
            output.writeInt(noteContext.soundNames.size)
            noteContext.soundNames.forEach(output::writeUTF)
            output.optional(noteContext.radioName)
        }
        RandomAccessFile(temporary, "rw").use { it.fd.sync() }
        if (!temporary.renameTo(file)) temporary.copyTo(file, overwrite = true)
    } finally { temporary.delete() }
}

class NotesViewModel(
    private val repository: NotesRepository,
    val assetStore: NoteAssetStore,
    val recorder: NoteRecorder,
    val contextProvider: NoteContextProvider?,
    private val clock: Clock,
    private val noteContextStore: NoteContextStore = DisabledNoteContextStore,
) : ViewModel() {
    constructor(repository: NotesRepository = InMemoryNotesRepository()) : this(
        repository, AppPrivateNoteAssetStore(), DisabledNoteRecorder, null, Clock.systemDefaultZone(), DisabledNoteContextStore,
    )

    val state: StateFlow<NotesState> = repository.state
    val recorderState: StateFlow<RecorderState> = recorder.state
    private val mutableNotice = MutableStateFlow<NoteNotice?>(null)
    val notice: StateFlow<NoteNotice?> = mutableNotice.asStateFlow()
    private val hydratedContextIds = mutableSetOf<String>()

    fun setQuery(value: String) = repository.update { it.copy(query = value) }
    fun setFilter(value: NoteFilter) = repository.update { it.copy(filter = value) }
    fun setSort(value: NoteSort) = repository.update { it.copy(sort = value) }
    /** App.tsx setSelectedTagFilter：标签筛选（点击同标签取消）。 */
    fun setTagFilter(tag: String?) = repository.update { it.copy(selectedTagFilter = tag) }

    /** App.tsx showNotice：无动作 2800ms 自动消失，带动作 5200ms。 */
    fun showNotice(message: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        mutableNotice.value = NoteNotice(System.currentTimeMillis(), message, actionLabel, action)
    }
    fun dismissNotice() { mutableNotice.value = null }
    fun createNotebook(title: String, accent: Long = 0xFF7F8C87, iconKey: String = "bookOpen") {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return
        repository.createNotebook(Notebook("notebook-${UUID.randomUUID()}", trimmed, accent, iconKey = iconKey))
    }
    fun renameNotebook(id: String, title: String) { title.trim().takeIf(String::isNotBlank)?.let { repository.renameNotebook(id, it) } }
    fun deleteNotebook(id: String) = repository.deleteNotebook(id)
    fun openLibrary() = repository.update { it.copy(view = NotesView.LIBRARY, query = "", filter = NoteFilter.ALL) }
    fun openNotebook(id: String) = repository.update { it.copy(view = NotesView.NOTEBOOK, selectedNotebookId = id, query = "", filter = NoteFilter.ALL) }
    fun openNote(id: String) = repository.update { current ->
        val note = current.notes.firstOrNull { it.id == id }
        current.copy(view = NotesView.EDITOR, selectedNoteId = id, selectedNotebookId = note?.notebookId ?: current.selectedNotebookId)
    }
    fun backFromEditor() = repository.update { it.copy(view = NotesView.NOTEBOOK) }

    fun createNote(
        notebookId: String = "nb4",
        initialType: BlockType = BlockType.PARAGRAPH,
        pendingAction: PendingNoteAction = PendingNoteAction.NONE,
    ): String {
        val targetNotebook = if (notebookId == ALL_NOTES_ID) "nb4" else notebookId
        val checklist = if (initialType == BlockType.CHECKLIST) listOf(NoteBlock(type = BlockType.CHECKLIST)) else emptyList()
        val note = Note(notebookId = targetNotebook, checklist = checklist, createdAt = now(), updatedAt = now())
        repository.update { it.copy(notes = listOf(note) + it.notes, view = NotesView.EDITOR, selectedNoteId = note.id, selectedNotebookId = targetNotebook, pendingAction = pendingAction) }
        return note.id
    }
    fun consumePendingAction() = repository.update { it.copy(pendingAction = PendingNoteAction.NONE) }

    /** App.tsx `saveFocusSession` 复盘笔记：静默前置，不进入编辑器。 */
    fun addNote(title: String, text: String, notebookId: String = "nb4", tags: Set<String> = emptySet(), context: NoteContext? = null) {
        val targetNotebook = if (notebookId == ALL_NOTES_ID) "nb4" else notebookId
        val note = Note(notebookId = targetNotebook, title = title, text = text, tags = tags, context = context, createdAt = now(), updatedAt = now())
        repository.update { it.copy(notes = listOf(note) + it.notes) }
    }

    fun updateTitle(id: String, value: String) = changeNote(id) { it.copy(title = value) }
    fun updateText(noteId: String, value: String) = changeNote(noteId) { it.copy(text = value) }
    fun toggleChecklist(noteId: String, blockId: String) = changeNote(noteId) { note ->
        note.copy(checklist = note.checklist.map { block -> if (block.id == blockId) block.copy(checked = !block.checked, version = block.version + 1) else block })
    }
    fun updateChecklistText(noteId: String, blockId: String, value: String) = changeNote(noteId) { note ->
        note.copy(checklist = note.checklist.map { block -> if (block.id == blockId) block.copy(text = value, version = block.version + 1) else block })
    }
    fun addChecklist(noteId: String) = changeNote(noteId) { note -> note.copy(checklist = note.checklist + NoteBlock(type = BlockType.CHECKLIST)) }
    fun removeChecklist(noteId: String, blockId: String) = changeNote(noteId) { note -> note.copy(checklist = note.checklist.filterNot { it.id == blockId }) }
    fun appendHeading(noteId: String) = changeNote(noteId) { note ->
        val next = when {
            note.text.endsWith("\n# ") || note.text == "# " -> note.text.dropLast(2) + "## "
            note.text.endsWith("\n## ") || note.text == "## " -> note.text.dropLast(3) + "### "
            note.text.endsWith("\n### ") || note.text == "### " -> note.text.dropLast(4) + "# "
            note.text.isEmpty() -> "# "
            else -> note.text + "\n# "
        }
        note.copy(text = next)
    }
    fun addInternalLink(noteId: String, linkedNoteId: String) {
        val linked = state.value.notes.firstOrNull { it.id == linkedNoteId && it.trashedAt == null } ?: return
        changeNote(noteId) { note ->
            if (note.id == linked.id || note.internalLinks.any { it.type == BlockType.INTERNAL_LINK && it.linkedNoteId == linked.id }) note
            else note.copy(internalLinks = note.internalLinks + NoteBlock(type = BlockType.INTERNAL_LINK, text = linked.title.ifBlank { "无标题笔记" }, linkedNoteId = linked.id))
        }
    }
    fun removeInternalLink(noteId: String, blockId: String) = changeNote(noteId) { note -> note.copy(internalLinks = note.internalLinks.filterNot { it.id == blockId }) }
    fun togglePin(id: String) = changeNote(id) { it.copy(pinned = !it.pinned) }
    fun toggleArchive(id: String) {
        val wasArchived = state.value.notes.firstOrNull { it.id == id }?.archived == true
        changeNote(id) { it.copy(archived = !it.archived) }
        showNotice(if (wasArchived) "已取消归档" else "笔记已归档")
    }
    fun move(id: String, notebookId: String) {
        val name = state.value.notebooks.firstOrNull { it.id == notebookId }?.title ?: "笔记本"
        changeNote(id) { it.copy(notebookId = notebookId, archived = false) }
        showNotice("已移到「$name」")
    }
    fun trash(id: String) {
        repository.update { current ->
            current.copy(
                notes = current.notes.map { note -> if (note.id != id) note else note.copy(originalNotebookId = note.notebookId, notebookId = TRASH_ID, pinned = false, archived = false, trashedAt = now(), updatedAt = now(), revision = note.revision + 1) },
                selectedNoteId = null,
                view = NotesView.NOTEBOOK,
            )
        }
        showNotice("笔记已移到回收站", "撤销") { undoTrash(id) }
    }
    fun undoTrash(id: String) {
        repository.update { current ->
            val note = current.notes.firstOrNull { it.id == id }
            val destination = note?.originalNotebookId ?: "nb4"
            current.copy(
                notes = current.notes.map { item -> if (item.id != id) item else item.copy(notebookId = destination, originalNotebookId = null, trashedAt = null, updatedAt = now(), revision = item.revision + 1) },
            )
        }
    }
    fun restore(id: String) {
        repository.update { current ->
            val note = current.notes.firstOrNull { it.id == id }
            val destination = note?.originalNotebookId ?: "nb4"
            current.copy(
                notes = current.notes.map { item -> if (item.id != id) item else item.copy(notebookId = destination, originalNotebookId = null, trashedAt = null, updatedAt = now(), revision = item.revision + 1) },
                selectedNotebookId = destination,
                selectedNoteId = null,
                view = NotesView.NOTEBOOK,
            )
        }
        showNotice("笔记已恢复")
    }
    fun deleteForever(context: Context, id: String) {
        val attachments = state.value.notes.firstOrNull { it.id == id }?.attachments.orEmpty()
        repository.update { current -> current.copy(notes = current.notes.filterNot { it.id == id }, selectedNoteId = null, view = NotesView.NOTEBOOK) }
        viewModelScope.launch { attachments.forEach { runCatching { assetStore.delete(it) } }; runCatching { noteContextStore.delete(context, id) } }
        showNotice("笔记已永久删除")
    }
    fun addTag(id: String, tag: String) { if (tag.isNotBlank()) changeNote(id) { it.copy(tags = it.tags + tag.trim()) } }
    fun removeTag(id: String, tag: String) = changeNote(id) { it.copy(tags = it.tags - tag) }
    fun attach(id: String, attachment: NoteAttachment) = changeNote(id) { it.copy(attachments = it.attachments + attachment) }
    fun removeAttachment(id: String, attachmentId: String) {
        val attachment = state.value.notes.firstOrNull { it.id == id }?.attachments?.firstOrNull { it.id == attachmentId }
        changeNote(id) { it.copy(attachments = it.attachments.filterNot { item -> item.id == attachmentId }) }
        if (attachment != null) viewModelScope.launch { runCatching { assetStore.delete(attachment) } }
    }
    fun renameAttachment(id: String, attachmentId: String, requestedBaseName: String): Boolean {
        val existing = state.value.notes.firstOrNull { it.id == id }?.attachments?.firstOrNull { it.id == attachmentId } ?: return false
        val ext = existing.name.substringAfterLast('.', "")
        val hasExt = '.' in existing.name && ext.isNotEmpty()
        val base = requestedBaseName.filterNot(Char::isISOControl).trim().take(120)
            .let { if (hasExt) it.removeSuffix(".$ext") else it }
        if (base.isBlank()) {
            repository.update { it.copy(assetError = "附件名称不能为空") }
            return false
        }
        val finalName = if (hasExt) "$base.$ext" else base
        changeNote(id) { note ->
            note.copy(attachments = note.attachments.map { attachment ->
                if (attachment.id == attachmentId) attachment.copy(name = finalName) else attachment
            })
        }
        return true
    }
    fun importAttachments(context: Context, noteId: String, selections: List<AttachmentSelection>) = viewModelScope.launch {
        repository.update { it.copy(assetError = if (selections.size > 5) "每次最多导入 5 个附件" else null) }
        val accepted = selections.take(5)
        val imported = mutableListOf<NoteAttachment>()
        for (selection in accepted) {
            runCatching { assetStore.import(context, selection.uri, selection.type, selection.displayName) }
                .onSuccess { attachment ->
                    if ((attachment.sizeBytes ?: 0L) > MAX_ATTACHMENT_BYTES) {
                        runCatching { assetStore.delete(attachment) }
                        repository.update { it.copy(assetError = "「${selection.displayName}」超过 8 MB，未导入") }
                    }
                    else imported += attachment
                }
                .onFailure { failure -> repository.update { it.copy(assetError = "「${selection.displayName}」导入失败：${failure.localizedMessage ?: "无法读取文件"}") } }
        }
        if (imported.isNotEmpty()) changeNote(noteId) { it.copy(attachments = it.attachments + imported) }
    }
    fun reportAttachmentPickerError(message: String) = repository.update { it.copy(assetError = message) }
    fun clearAssetError() = repository.update { it.copy(assetError = null) }
    fun garbageCollect(androidContext: Context) = viewModelScope.launch { runCatching { assetStore.garbageCollect(androidContext) } }
    fun connect(androidContext: Context, id: String, noteContext: NoteContext?) = viewModelScope.launch {
        runCatching { noteContextStore.write(androidContext, id, noteContext) }
            .onSuccess { changeNote(id) { it.copy(context = noteContext) } }
            .onFailure { failure -> repository.update { it.copy(assetError = "关联保存失败：${failure.localizedMessage ?: "无法写入私有存储"}") } }
    }
    fun connectCurrent(androidContext: Context, id: String) = viewModelScope.launch {
        val currentContext = contextProvider?.current()?.context ?: return@launch
        runCatching { noteContextStore.write(androidContext, id, currentContext) }
            .onSuccess { changeNote(id) { it.copy(context = currentContext) } }
            .onFailure { failure -> repository.update { it.copy(assetError = "关联保存失败：${failure.localizedMessage ?: "无法写入私有存储"}") } }
    }
    fun hydrateContexts(androidContext: Context, notes: List<Note>) {
        val pending = notes.map(Note::id).filter(hydratedContextIds::add)
        if (pending.isEmpty()) return
        viewModelScope.launch {
            pending.forEach { noteId -> noteContextStore.read(androidContext, noteId)?.let { stored -> repository.update { state -> state.copy(notes = state.notes.map { note -> if (note.id == noteId) note.copy(context = stored) else note }) } } }
        }
    }
    fun toggleRecording(id: String) = viewModelScope.launch { if (recorder.state.value.recording) recorder.stop()?.let { attach(id, it) } else recorder.start(id) }
    fun pauseRecording() = viewModelScope.launch { recorder.pause() }
    fun resumeRecording() = viewModelScope.launch { recorder.resume() }
    fun cancelRecording() = viewModelScope.launch { recorder.cancel() }
    fun reportRecordingError(message: String) = recorder.reportError(message)
    fun clearRecordingError() = recorder.clearError()
    fun saveDrawing(context: Context, id: String, drawing: DrawingDocument) = viewModelScope.launch {
        runCatching { assetStore.saveDrawing(context, id, drawing) }
            .onSuccess { attach(id, it) }
            .onFailure { failure -> repository.update { it.copy(assetError = "手写保存失败：${failure.localizedMessage ?: "无法写入私有存储"}") } }
    }
    fun saveDrawingEdit(context: Context, id: String, attachmentId: String, drawing: DrawingDocument) = viewModelScope.launch {
        val existing = state.value.notes.firstOrNull { it.id == id }?.attachments?.firstOrNull { it.id == attachmentId }
        if (existing == null) { repository.update { it.copy(assetError = "手写附件不存在") }; return@launch }
        runCatching { assetStore.overwriteDrawing(context, existing, drawing) }
            .onSuccess { updated -> changeNote(id) { note -> note.copy(attachments = note.attachments.map { if (it.id == attachmentId) updated else it }) } }
            .onFailure { failure -> repository.update { it.copy(assetError = "手写保存失败：${failure.localizedMessage ?: "无法写入私有存储"}") } }
    }

    private fun changeNote(id: String, transform: (Note) -> Note) = repository.update { state ->
        state.copy(notes = state.notes.map { note ->
            if (note.id != id) note else transform(note).copy(updatedAt = now(), revision = note.revision + 1, schemaVersion = CURRENT_NOTE_SCHEMA_VERSION)
        }, saveStatus = SaveStatus.SAVED)
    }
    private fun now(): Long = clock.millis()
    override fun onCleared() {
        recorder.close()
        repository.close()
    }
}

class NotesViewModelFactory(
    private val repository: NotesRepository,
    private val assetStore: NoteAssetStore,
    private val recorder: NoteRecorder,
    private val contextProvider: NoteContextProvider? = null,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val noteContextStore: NoteContextStore = AppPrivateNoteContextStore(),
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = NotesViewModel(repository, assetStore, recorder, contextProvider, clock, noteContextStore) as T
}

@Composable
fun NotesRoute(
    vm: NotesViewModel = viewModel(),
    onAttachmentRequest: ((noteId: String, type: AttachmentType) -> Unit)? = null,
    attachmentPicker: NoteAttachmentPicker? = null,
    onRecordPermissionRequest: ((onGranted: () -> Unit) -> Unit)? = null,
    onEditorStateChange: (Boolean) -> Unit = {},
    confirmPermanentDeletes: Boolean = true,
) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()
    val notice by vm.notice.collectAsState()
    var notebookManagerOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.notes.map(Note::id)) { vm.hydrateContexts(context, state.notes) }
    LaunchedEffect(state.view) { onEditorStateChange(state.view == NotesView.EDITOR) }
    LaunchedEffect(Unit) { vm.garbageCollect(context) }
    LaunchedEffect(notice?.id) {
        if (notice != null) {
            delay(if (notice?.actionLabel != null) 5_200L else 2_800L)
            vm.dismissNotice()
        }
    }
    val requestAttachment: (String, AttachmentType) -> Unit = { noteId, type ->
        if (attachmentPicker != null) {
            attachmentPicker.launch(type.pickerRequest(noteId)) { result ->
                result.onSuccess { vm.importAttachments(context, noteId, it) }.onFailure { vm.reportAttachmentPickerError(it.localizedMessage ?: "附件选择失败") }
            }
        } else if (onAttachmentRequest != null) onAttachmentRequest(noteId, type)
        else vm.reportAttachmentPickerError("当前环境未接入附件选择器")
    }
    Box(Modifier.fillMaxSize()) {
        when (state.view) {
            NotesView.LIBRARY -> NotesLibrary(state, vm, openNotebookManager = { notebookManagerOpen = true })
            NotesView.NOTEBOOK -> NotesNotebook(state, vm)
            NotesView.EDITOR -> state.notes.firstOrNull { it.id == state.selectedNoteId }?.let { note ->
                NoteEditor(note, state, vm, requestAttachment, confirmPermanentDeletes) {
                    val start = { vm.toggleRecording(note.id); Unit }
                    if (onRecordPermissionRequest != null) onRecordPermissionRequest(start) else start()
                }
            } ?: NotesLibrary(state, vm)
        }
        if (notebookManagerOpen) NotebookManagerSheet(state, vm) { notebookManagerOpen = false }
        notice?.let { current -> NoteNoticeOverlay(current) { vm.dismissNotice() } }
    }
}

@Composable private fun Page(itemSpacing: Dp = 0.dp, content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(CanvasColor).testTag(NOTES_PAGE_SCROLL_TEST_TAG),
        // ProductivityRoute already owns the App.tsx `px-4` workspace gutter.
        contentPadding = PaddingValues(top = 16.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(itemSpacing),
        content = content,
    )
}

@Composable private fun NotesLibrary(state: NotesState, vm: NotesViewModel, openNotebookManager: () -> Unit = {}) {
    val live = state.notes.filter { it.trashedAt == null && !it.archived }
    val matches = live.filtered(state.query, NoteFilter.ALL).sorted(NoteSort.UPDATED)
    Page(itemSpacing = 20.dp) {
        item {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("笔记", color = Primary, fontSize = 18.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
                    Text("记录想法、整理生活，也可连接声场与专注", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                RoundAction(plus, "新建笔记") { vm.createNote() }
            }
        }
        item { QuickCreate(vm) }
        item { SearchField(state.query, vm::setQuery, "搜索所有笔记") }
        if (state.query.isNotBlank()) {
            item {
                Column {
                    Eyebrow("搜索结果 · ${matches.size}")
                    Spacer(Modifier.height(8.dp))
                    if (matches.isEmpty()) EmptyNotes("没有匹配的笔记", showIcon = false, verticalPadding = 56.dp)
                    else NoteRows(matches, vm::openNote)
                }
            }
        } else {
            item {
                Column {
                    Row(Modifier.fillMaxWidth().height(44.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Eyebrow("最近记录")
                        TextButton(onClick = { vm.openNotebook(ALL_NOTES_ID) }, modifier = Modifier.height(44.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("查看全部", color = AmbientLight, style = MaterialTheme.typography.labelSmall) }
                    }
                    NoteRows(live.sortedByDescending { it.pinned }.take(4), vm::openNote, emptyMessage = "还没有笔记")
                }
            }
            item { TagFilterSection(live, state.selectedTagFilter, vm::openNote, vm::setTagFilter) }
            item {
                Column {
                    NotebookHeading(openNotebookManager)
                    HorizontalDivider(color = BorderColor)
                    state.notebooks.forEachIndexed { index, notebook ->
                        if (index > 0) HorizontalDivider(Modifier.padding(start = 44.dp), color = BorderColor.copy(alpha = .7f))
                        val count = when (notebook.id) {
                            ALL_NOTES_ID -> live.size
                            ARCHIVE_ID -> state.notes.count { it.archived && it.trashedAt == null }
                            TRASH_ID -> state.notes.count { it.trashedAt != null }
                            else -> state.notes.count { it.notebookId == notebook.id && it.trashedAt == null }
                        }
                        NotebookRow(notebook, count) { vm.openNotebook(notebook.id) }
                    }
                    HorizontalDivider(color = BorderColor)
                }
            }
        }
    }
}

/** App.tsx 6851-6862「标签与筛选」折叠区块：跨笔记本标签统计 + 标签筛选笔记列表。 */
@Composable private fun TagFilterSection(live: List<Note>, selectedTagFilter: String?, open: (String) -> Unit, setTagFilter: (String?) -> Unit) {
    val tagCounts = live.tagCounts()
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column {
        HorizontalDivider(color = BorderColor)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { expanded = !expanded }.padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(32.dp).background(Ambient.copy(alpha = .08f), MaterialTheme.shapes.medium).border(1.dp, Ambient.copy(alpha = .25f), MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) { Icon(tag, null, Modifier.size(16.dp), tint = AmbientLight) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("标签与筛选", color = Primary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text("跨笔记本查找与整理", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            Text("${tagCounts.size} 个标签", fontSize = 11.sp, color = Muted)
            Icon(chevronDown, null, Modifier.size(16.dp).graphicsLayer { rotationZ = if (expanded) 180f else 0f }, tint = Color(0xFF5F6D69))
        }
        HorizontalDivider(color = BorderColor)
        if (expanded) {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                if (tagCounts.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        tagCounts.forEach { (tagName, count) ->
                            val selected = tagName == selectedTagFilter
                            Row(
                                Modifier.heightIn(min = 32.dp).background(if (selected) Ambient.copy(alpha = .14f) else SurfaceHigh, RoundedCornerShape(6.dp)).clickable { setTagFilter(if (selected) null else tagName) }.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("#$tagName", fontSize = 11.sp, color = if (selected) AmbientLight else Secondary)
                                Text(" $count", fontSize = 11.sp, color = Muted)
                            }
                        }
                    }
                } else {
                    Text("给笔记添加标签后，会在这里跨笔记本归类。", Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                if (selectedTagFilter != null) {
                    val tagNotes = live.filter { it.tags.contains(selectedTagFilter) }.sorted(NoteSort.UPDATED)
                    Column(Modifier.padding(top = 12.dp)) {
                        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text("标签 #$selectedTagFilter · ${tagNotes.size} 篇", fontSize = 11.sp, color = Secondary)
                            TextButton({ setTagFilter(null) }, Modifier.heightIn(min = 36.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("清除", fontSize = 11.sp, color = AmbientLight) }
                        }
                        NoteRows(tagNotes, open)
                    }
                }
            }
            HorizontalDivider(color = BorderColor)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun NotebookHeading(openNotebookManager: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(44.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("笔记本", style = MaterialTheme.typography.labelSmall, color = Muted, letterSpacing = 2.sp)
        Box(
            Modifier.size(44.dp).clickable(onClick = openNotebookManager),
            contentAlignment = Alignment.Center,
        ) { Icon(plus, "新建或管理笔记本", Modifier.size(16.dp), tint = AmbientLight) }
    }
}

@Composable private fun NoteRows(notes: List<Note>, open: (String) -> Unit, emptyMessage: String? = null) {
    if (notes.isEmpty()) {
        if (emptyMessage != null) EmptyNotes(emptyMessage, showIcon = false, verticalPadding = 56.dp)
        return
    }
    Column {
        HorizontalDivider(color = BorderColor)
        notes.forEach { NoteRow(it, open) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun NotebookManagerSheet(state: NotesState, vm: NotesViewModel, close: () -> Unit) {
    var panel by rememberSaveable { mutableStateOf("manage") }
    var newName by rememberSaveable { mutableStateOf("") }
    var newAccent by rememberSaveable { mutableStateOf(0xFF7FAE87) }
    var newIcon by rememberSaveable { mutableStateOf("bookOpen") }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingName by rememberSaveable { mutableStateOf("") }
    var deleteCandidate by remember { mutableStateOf<Notebook?>(null) }
    ModalBottomSheet(onDismissRequest = close, containerColor = SurfaceColor, contentColor = Primary, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), dragHandle = { BottomSheetDefaults.DragHandle(color = SoundistColors.Divider) }) {
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 700.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Column { Text(if (panel == "create") "新建笔记本" else "管理笔记本", color = Primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text(if (panel == "create") "笔记本决定笔记放在哪里，标签用于跨笔记本查找。" else "删除笔记本不会删除笔记，会移到「自我」。", style = MaterialTheme.typography.labelSmall, color = Muted) }; IconButton(close, Modifier.size(44.dp)) { Icon(x, "关闭笔记本管理", tint = Secondary) } } }
            if (panel == "create") {
                item { NotebookNameField(newName, { newName = it }) { if (newName.isNotBlank()) { vm.createNotebook(newName, newAccent, newIcon); newName = ""; panel = "manage" } } }
                item { NotebookChoicePreview(newName, newAccent, newIcon) }
                item { NotebookAccentPicker(newAccent) { newAccent = it } }
                item { NotebookIconPicker(newIcon) { newIcon = it } }
                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { SheetButton("取消", false, Modifier.weight(1f)) { panel = "manage" }; SheetButton("创建笔记本", true, Modifier.weight(1f)) { if (newName.isNotBlank()) { vm.createNotebook(newName, newAccent, newIcon); newName = ""; panel = "manage" } } } }
            } else {
                item { SheetButton("+ 新建笔记本", false, Modifier.fillMaxWidth()) { panel = "create" } }
            }
            if (panel == "manage") items(state.notebooks.filterNot { it.system }, key = { "manage-${it.id}" }) { notebook ->
                Column(Modifier.fillMaxWidth().border(1.dp, BorderColor, RoundedCornerShape(8.dp)).background(SurfaceLow, RoundedCornerShape(8.dp)).padding(12.dp)) {
                    if (editingId == notebook.id) {
                        NotebookNameField(editingName, { editingName = it }) { if (editingName.isNotBlank()) { vm.renameNotebook(notebook.id, editingName); editingId = null } }
                        Row(Modifier.fillMaxWidth(), Arrangement.End) {
                            TextButton({ editingId = null }) { Text("取消", color = Muted) }
                            TextButton({ if (editingName.isNotBlank()) vm.renameNotebook(notebook.id, editingName); editingId = null }) { Text("保存", color = AmbientLight) }
                        }
                    } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(32.dp).background(Color(notebook.accent).copy(alpha = .082f), MaterialTheme.shapes.medium).border(1.dp, Color(notebook.accent).copy(alpha = .145f), MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) { Icon(notebookIcon(notebook), null, Modifier.size(16.dp), tint = Color(notebook.accent)) }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(notebook.title, color = Primary, style = MaterialTheme.typography.bodyMedium); Text("${state.notes.count { it.notebookId == notebook.id && it.trashedAt == null }} 篇笔记", style = MaterialTheme.typography.labelSmall, color = Muted) }
                        IconButton({ editingId = notebook.id; editingName = notebook.title }, Modifier.size(44.dp)) { Icon(pencilLine, "重命名${notebook.title}", Modifier.size(18.dp), tint = Secondary) }
                        IconButton({ deleteCandidate = notebook }, Modifier.size(44.dp)) { Icon(trash2, "删除${notebook.title}", Modifier.size(18.dp), tint = Danger) }
                    }
                }
            }
            if (panel == "manage" && state.notebooks.none { !it.system }) item { EmptyNotes("还没有自定义笔记本") }
            deleteCandidate?.let { notebook ->
                item { Column(Modifier.fillMaxWidth().border(1.dp, Danger.copy(alpha = .3f), RoundedCornerShape(8.dp)).background(Danger.copy(alpha = .08f), RoundedCornerShape(8.dp)).padding(12.dp)) { Text("删除「${notebook.title}」？", color = Primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold); Text("其中的笔记会移动到「自我」，不会被删除。", Modifier.padding(top = 4.dp), color = Secondary, fontSize = 12.sp); Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { SheetButton("取消", false, Modifier.weight(1f)) { deleteCandidate = null }; SheetButton("删除", false, Modifier.weight(1f), textColor = Danger, border = Danger.copy(alpha = .4f)) { vm.deleteNotebook(notebook.id); deleteCandidate = null } } } }
            }
        }
    }
}

@Composable private fun NotebookChoicePreview(name: String, accent: Long, iconKey: String) {
    val color = Color(accent)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).background(SurfaceLow, RoundedCornerShape(8.dp)).border(1.dp, BorderColor, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).background(color.copy(alpha = .12f), RoundedCornerShape(8.dp)).border(1.dp, color.copy(alpha = .38f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(iconForKey(iconKey), null, Modifier.size(18.dp), tint = color) }
        Column(Modifier.padding(start = 12.dp)) {
            Text(name.ifBlank { "新笔记本" }, color = Primary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text("图标与颜色预览", color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable private fun NotebookNameField(value: String, onValueChange: (String) -> Unit, onDone: () -> Unit) {
    BasicTextField(value, onValueChange, Modifier.fillMaxWidth().heightIn(min = 44.dp).border(1.dp, BorderColor, RoundedCornerShape(8.dp)).background(CanvasColor, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp), singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { onDone() }), cursorBrush = SolidColor(Ambient), textStyle = MaterialTheme.typography.bodyMedium.copy(color = Primary), decorationBox = { field -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { if (value.isEmpty()) Text("例如：旅行、阅读、项目资料", color = Muted, fontSize = 14.sp); field() } })
}

@Composable private fun NotebookAccentPicker(selected: Long, select: (Long) -> Unit) {
    Column {
        Text("图标颜色", color = Secondary, fontSize = 11.sp)
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            notebookAccentOptions.forEach { option ->
                Box(
                    Modifier.size(36.dp).background(Color(option.color), CircleShape).border(if (selected == option.color) 2.dp else 0.dp, Primary, CircleShape).clickable { select(option.color) },
                    contentAlignment = Alignment.Center,
                ) { if (selected == option.color) Icon(check, "已选择${option.label}", Modifier.size(15.dp), tint = CanvasColor) }
            }
        }
    }
}

/** Android 增强：新建笔记本时可选择图标（前端无此功能，图标固定为 BookOpen）。 */
@Composable private fun NotebookIconPicker(selected: String, select: (String) -> Unit) {
    Column {
        Text("图标", color = Secondary, fontSize = 11.sp)
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            notebookIconOptions.forEach { option ->
                Box(
                    Modifier.size(36.dp).background(if (selected == option.key) Ambient.copy(alpha = .14f) else SurfaceLow, RoundedCornerShape(8.dp)).border(if (selected == option.key) 1.dp else 0.dp, if (selected == option.key) AmbientLight else BorderColor, RoundedCornerShape(8.dp)).clickable { select(option.key) },
                    contentAlignment = Alignment.Center,
                ) { Icon(iconForKey(option.key), option.label, Modifier.size(16.dp), tint = if (selected == option.key) AmbientLight else Secondary) }
            }
        }
    }
}

@Composable private fun SheetButton(label: String, primary: Boolean, modifier: Modifier = Modifier, textColor: Color = if (primary) CanvasColor else Secondary, border: Color = BorderColor, onClick: () -> Unit) {
    Box(modifier.heightIn(min = 44.dp).background(if (primary) Ambient else Color.Transparent, RoundedCornerShape(8.dp)).border(if (primary) 0.dp else 1.dp, if (primary) Color.Transparent else border, RoundedCornerShape(8.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Text(label, color = textColor, fontSize = 12.sp, fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal) }
}

@Composable private fun QuickCreate(vm: NotesViewModel) {
    Row(
        Modifier.fillMaxWidth().border(1.dp, BorderColor, MaterialTheme.shapes.medium).background(SurfaceColor, MaterialTheme.shapes.medium).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(
            QuickCreateItem(fileText, "文字", BlockType.PARAGRAPH),
            QuickCreateItem(listChecks, "清单", BlockType.CHECKLIST),
            QuickCreateItem(mic, "录音", action = PendingNoteAction.RECORDING),
            QuickCreateItem(pencilLine, "手写", action = PendingNoteAction.DRAWING),
            QuickCreateItem(image, "图片", action = PendingNoteAction.ATTACHMENT),
        ).forEach { item ->
            Column(
                Modifier.weight(1f).heightIn(min = 48.dp).clickable { vm.createNote(initialType = item.type, pendingAction = item.action) }.padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) { Icon(item.icon, item.label, Modifier.size(14.dp), tint = Secondary); Text(item.label, fontSize = 10.sp, lineHeight = 14.sp, color = Secondary) }
        }
    }
}

private data class QuickCreateItem(
    val icon: ImageVector,
    val label: String,
    val type: BlockType = BlockType.PARAGRAPH,
    val action: PendingNoteAction = PendingNoteAction.NONE,
)

@Composable private fun NotesNotebook(state: NotesState, vm: NotesViewModel) {
    val notebook = state.notebooks.firstOrNull { it.id == state.selectedNotebookId } ?: state.notebooks.first()
    val notebookNotes = if (state.selectedNotebookId == ALL_NOTES_ID && state.filter == NoteFilter.ARCHIVED) {
        state.notes.forNotebook(ARCHIVE_ID)
    } else state.notes.forNotebook(state.selectedNotebookId)
    val notes = notebookNotes.filtered(state.query, state.filter).sorted(state.sort)
    val headerCount = if (state.selectedNotebookId == ALL_NOTES_ID) state.notes.count { it.trashedAt == null && !it.archived } else state.notes.count { it.notebookId == state.selectedNotebookId }
    Page {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(vm::openLibrary, Modifier.size(44.dp)) { Icon(arrowLeft, "返回笔记本", Modifier.size(20.dp), tint = Secondary) }
                Column(Modifier.weight(1f)) { Text(notebook.title, color = Primary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("$headerCount 篇笔记", style = MaterialTheme.typography.bodySmall, color = Muted) }
                if (notebook.id != TRASH_ID && notebook.id != ARCHIVE_ID) RoundAction(plus, "新建笔记") { vm.createNote(notebook.id) }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
        item { SearchField(state.query, vm::setQuery, if (notebook.id == ALL_NOTES_ID) "搜索所有笔记" else "搜索这个笔记本") }
        item { Spacer(Modifier.height(12.dp)) }
        if (notebook.id == ALL_NOTES_ID) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NoteFilter.entries.forEach { filter -> FilterButton(filter.label, state.filter == filter) { vm.setFilter(filter) } }
                    }
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("${state.filter.label} · ${notes.size} 篇", fontSize = 10.sp, color = Muted)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("排序", fontSize = 10.sp, color = Muted)
                                NotesDropdown(
                                    value = state.sort,
                                    options = NoteSort.entries.map { it to it.shortLabel },
                                    onSelect = vm::setSort,
                                    modifier = Modifier.width(92.dp),
                                    minHeight = 36.dp,
                                    valueColor = Secondary,
                                    fontSize = 11.sp,
                                    menuWidth = 104.dp,
                                    maxMenuHeight = 120.dp,
                                    valueTextAlign = TextAlign.End,
                                )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
        if (notes.isEmpty()) item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyNotes(if (state.query.isNotBlank()) "没有匹配的笔记" else if (state.filter != NoteFilter.ALL) "没有符合条件的笔记" else "这个笔记本还是空的")
                if (state.query.isBlank() && state.filter == NoteFilter.ALL && notebook.id != TRASH_ID && notebook.id != ARCHIVE_ID) {
                    OutlinedButton(
                        onClick = { vm.createNote(notebook.id) },
                        modifier = Modifier.heightIn(min = 44.dp).padding(top = 16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Ambient.copy(alpha = .25f)),
                    ) { Text("新建第一篇笔记", color = AmbientLight, fontSize = 12.sp) }
                }
            }
            }
        }
        else item { NoteRows(notes, vm::openNote) }
    }
}

@Composable private fun NoteEditor(note: Note, state: NotesState, vm: NotesViewModel, onAttachmentRequest: ((String, AttachmentType) -> Unit)?, confirmPermanentDeletes: Boolean, requestRecordingStart: () -> Unit) {
    val context = LocalContext.current
    val recording by vm.recorderState.collectAsState()
    var more by remember { mutableStateOf(false) }
    var tools by remember { mutableStateOf<EditorTool?>(null) }
    var tagInput by remember { mutableStateOf("") }
    var confirmPermanentDelete by remember { mutableStateOf(false) }
    var editingDrawing by remember { mutableStateOf<NoteAttachment?>(null) }
    LaunchedEffect(note.id, state.pendingAction) {
        when (state.pendingAction) {
            PendingNoteAction.RECORDING -> requestRecordingStart()
            PendingNoteAction.DRAWING -> tools = EditorTool.DRAWING
            PendingNoteAction.ATTACHMENT -> tools = EditorTool.ATTACH
            PendingNoteAction.NONE -> Unit
        }
        if (state.pendingAction != PendingNoteAction.NONE) vm.consumePendingAction()
    }
    val editingDocument by produceState<DrawingDocument?>(null, editingDrawing?.privatePath) {
        val target = editingDrawing
        value = if (target == null) null else withContext(Dispatchers.IO) { runCatching { readDrawingDocument(File(target.privatePath)) }.getOrNull() }
    }
    Page {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(vm::backFromEditor, Modifier.size(44.dp)) { Icon(arrowLeft, "返回笔记列表", Modifier.size(20.dp), tint = Secondary) }
                Column(Modifier.weight(1f)) {
                    Text(state.notebooks.firstOrNull { it.id == note.notebookId }?.title ?: "所有笔记", style = MaterialTheme.typography.labelSmall, color = Muted)
                    Text(
                        when (state.saveStatus) {
                            SaveStatus.SAVING -> "保存中…"
                            SaveStatus.ERROR -> "保存失败，内容仍保留在本机"
                            SaveStatus.SAVED -> if (relativeTime(note.updatedAt) == "刚刚") "已保存到本机" else "更新于 ${relativeTime(note.updatedAt)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.saveStatus == SaveStatus.ERROR) Danger else Secondary,
                    )
                }
                IconButton({ vm.togglePin(note.id) }, Modifier.size(44.dp)) { Icon(pin, if (note.pinned) "取消置顶" else "置顶笔记", Modifier.size(16.dp), tint = if (note.pinned) Radio else Muted) }
                IconButton({ more = !more }, Modifier.size(44.dp)) { Icon(moreHorizontal, "笔记更多操作", Modifier.size(16.dp), tint = Secondary) }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
        if (more) {
            item { MorePanel(note, state.notebooks, vm, showContext = { tools = EditorTool.CONTEXT; more = false }, close = { more = false }, confirmPermanentDelete = { if (confirmPermanentDeletes) confirmPermanentDelete = true else vm.deleteForever(context, note.id) }) }
            item { Spacer(Modifier.height(16.dp)) }
        }
        item {
            BasicTextField(
                value = note.title,
                onValueChange = { vm.updateTitle(note.id, it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, color = Primary),
                cursorBrush = SolidColor(Ambient),
                decorationBox = { field -> Box { if (note.title.isEmpty()) Text("无标题笔记", fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5F6D69)); field() } },
            )
        }
        item { Spacer(Modifier.height(12.dp)) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                note.tags.forEach { item -> TagChip(item) { vm.removeTag(note.id, item) } }
                Row(Modifier.heightIn(min = 32.dp).border(1.dp, BorderColor, MaterialTheme.shapes.small).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(tag, null, Modifier.size(12.dp), tint = Muted)
                    BasicTagField(tagInput, { tagInput = it }) { vm.addTag(note.id, tagInput); tagInput = "" }
                }
            }
        }
        if (note.context != null) { item { Spacer(Modifier.height(12.dp)) }; item { ContextSummary(note.context) { tools = EditorTool.CONTEXT } } }
        item { Spacer(Modifier.height(12.dp)) }
        item { EditorToolbar(tools, recording.recording, { tools = it }, { vm.appendHeading(note.id) }, { vm.addChecklist(note.id) }, { if (recording.recording) vm.toggleRecording(note.id) else requestRecordingStart() }) }
        if (recording.recording || recording.status == RecorderStatus.SAVING) { item { Spacer(Modifier.height(8.dp)) }; item { RecordingPanel(recording) } }
        if (recording.status == RecorderStatus.ERROR) { item { Spacer(Modifier.height(8.dp)) }; item { ErrorBanner(recording.errorMessage ?: "录音失败", vm::clearRecordingError) } }
        state.assetError?.let { message -> item { Spacer(Modifier.height(8.dp)) }; item { ErrorBanner(message, vm::clearAssetError) } }
        if (tools == EditorTool.ATTACH) item {
            Column {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().border(1.dp, BorderColor, MaterialTheme.shapes.medium).background(SurfaceColor, MaterialTheme.shapes.medium).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AttachmentButton(image, "图片", Modifier.weight(1f)) { onAttachmentRequest?.invoke(note.id, AttachmentType.IMAGE) }
                    AttachmentButton(folderInput, "文件", Modifier.weight(1f)) { onAttachmentRequest?.invoke(note.id, AttachmentType.FILE) }
                }
            }
        }
        if (tools == EditorTool.DRAWING) item { Column { Spacer(Modifier.height(8.dp)); InkCanvas(onCancel = { tools = null }, onSave = { vm.saveDrawing(context, note.id, it); tools = null }) } }
        if (tools == EditorTool.CONTEXT) item { Column { Spacer(Modifier.height(8.dp)); ContextPanel(note, vm) { tools = null } } }
        editingDrawing?.let { target ->
            item { Column { Spacer(Modifier.height(8.dp)); InkCanvas(initial = editingDocument, onCancel = { editingDrawing = null }, onSave = { vm.saveDrawingEdit(context, note.id, target.id, it); editingDrawing = null }) } }
        }
        item { HorizontalDivider(Modifier.padding(top = 12.dp), color = BorderColor.copy(alpha = .7f)) }
        item { Spacer(Modifier.height(16.dp)) }
        item {
            BasicTextField(
                value = note.text,
                onValueChange = { vm.updateText(note.id, it) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 28.sp, color = Primary),
                cursorBrush = SolidColor(Ambient),
                visualTransformation = remember { HeadingVisualTransformation },
                decorationBox = { field -> Box { if (note.text.isEmpty()) Text("开始写下内容…", fontSize = 16.sp, lineHeight = 28.sp, color = Muted); field() } },
            )
        }
        if (note.checklist.isNotEmpty()) item { ChecklistSection(note, vm) }
        if (note.attachments.isNotEmpty()) item { AttachmentSection(note, vm, onEditDrawing = { editingDrawing = it }) }
    }
    if (confirmPermanentDelete) AlertDialog(
        onDismissRequest = { confirmPermanentDelete = false },
        title = { Text("永久删除这篇笔记？", color = Primary) },
        text = { Text("附件、录音、手写与关联元数据也会一并删除，且无法恢复。", color = Secondary) },
        confirmButton = { TextButton({ vm.deleteForever(context, note.id); confirmPermanentDelete = false }) { Text("永久删除", color = Danger) } },
        dismissButton = { TextButton({ confirmPermanentDelete = false }) { Text("取消", color = Secondary) } },
        containerColor = SurfaceColor,
    )
}

private object HeadingVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val styled = AnnotatedString.Builder(text)
        var start = 0
        text.text.split('\n').forEach { line ->
            val level = when {
                line.startsWith("### ") -> 3
                line.startsWith("## ") -> 2
                line.startsWith("# ") -> 1
                else -> 0
            }
            if (level > 0) {
                val end = start + line.length
                styled.addStyle(
                    SpanStyle(
                        fontSize = when (level) { 1 -> 24.sp; 2 -> 20.sp; else -> 18.sp },
                        fontWeight = if (level == 1) FontWeight.Bold else FontWeight.SemiBold,
                        color = Primary,
                    ),
                    start,
                    end,
                )
                styled.addStyle(SpanStyle(color = Muted), start, start + level)
            }
            start += line.length + 1
        }
        return TransformedText(styled.toAnnotatedString(), OffsetMapping.Identity)
    }
}

/** App.tsx 录音态提示条 (6625)：正在录音 mm:ss · 再次点击停止。前端无暂停/取消按钮。 */
@Composable private fun RecordingPanel(state: RecorderState) {
    val pulseAlpha by rememberInfiniteTransition(label = "recording-pulse").animateFloat(
        initialValue = 1f,
        targetValue = .5f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "recording-dot",
    )
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).background(Danger.copy(alpha = .08f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).background(Danger, CircleShape).graphicsLayer { alpha = pulseAlpha })
        if (state.status == RecorderStatus.SAVING) {
            Text("正在保存录音…", fontSize = 12.sp, color = Danger)
            Spacer(Modifier.weight(1f))
        } else {
            Text("正在录音 ${formatDuration(state.elapsedMillis)}", fontSize = 12.sp, color = Danger)
            Spacer(Modifier.weight(1f))
            Text("再次点击停止", fontSize = 10.sp, color = Secondary)
        }
    }
}

@Composable private fun ErrorBanner(message: String, dismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Danger.copy(alpha = .08f), MaterialTheme.shapes.medium).border(1.dp, Danger.copy(alpha = .28f), MaterialTheme.shapes.medium).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(circleAlert, null, Modifier.size(18.dp), tint = Danger)
        Text(message, Modifier.weight(1f).padding(horizontal = 9.dp, vertical = 10.dp), style = MaterialTheme.typography.labelSmall, color = Danger)
        IconButton(dismiss, Modifier.size(44.dp)) { Icon(x, "关闭错误提示", Modifier.size(16.dp), tint = Danger) }
    }
}

/** App.tsx showNotice 提示条 (8473–8493)：left-4 right-4 bottom-[94px]、surface-high、ambient/20 描边、阴影。 */
@Composable private fun NoteNoticeOverlay(notice: NoteNotice, dismiss: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 94.dp)
                .heightIn(min = 48.dp)
                .shadow(24.dp, RoundedCornerShape(12.dp), ambientColor = Color.Black.copy(alpha = .28f), spotColor = Color.Black.copy(alpha = .28f))
                .background(SurfaceHigh, RoundedCornerShape(12.dp))
                .border(1.dp, Ambient.copy(alpha = .2f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(notice.message, Modifier.weight(1f), color = Primary, fontSize = 12.sp, lineHeight = 18.sp)
            if (notice.actionLabel != null && notice.action != null) {
                TextButton(
                    onClick = { notice.action?.invoke(); dismiss() },
                    modifier = Modifier.heightIn(min = 44.dp).border(1.dp, Ambient.copy(alpha = .25f), RoundedCornerShape(8.dp)),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) { Text(notice.actionLabel, color = AmbientLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            }
            Box(Modifier.size(44.dp).clickable { dismiss() }, contentAlignment = Alignment.Center) { Icon(x, "关闭提示", Modifier.size(16.dp), tint = Muted) }
        }
    }
}

private enum class EditorTool { ATTACH, RECORDING, DRAWING, CONTEXT }

@Composable private fun MorePanel(note: Note, notebooks: List<Notebook>, vm: NotesViewModel, showContext: () -> Unit, close: () -> Unit, confirmPermanentDelete: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().border(1.dp, BorderColor, MaterialTheme.shapes.medium).background(SurfaceColor, MaterialTheme.shapes.medium).padding(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (note.trashedAt != null) {
                SmallAction(archiveRestore, "恢复", AmbientLight, Modifier.weight(1f)) { vm.restore(note.id); close() }
                SmallAction(trash2, "永久删除", Danger, Modifier.weight(1f), confirmPermanentDelete)
            } else {
                SmallAction(archive, if (note.archived) "取消归档" else "归档", Secondary, Modifier.weight(1f)) { vm.toggleArchive(note.id); close() }
                SmallAction(activity, "关联", Secondary, Modifier.weight(1f), showContext)
                SmallAction(trash2, "删除", Danger, Modifier.weight(1f)) { vm.trash(note.id); close() }
            }
        }
        if (note.trashedAt == null) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = BorderColor)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallAction(fileText, "导出 PDF", Secondary, Modifier.weight(1f)) {
                    runCatching { shareNoteExport(context, exportNotePdf(context, note), "application/pdf", note.title) }
                        .onFailure { Toast.makeText(context, it.message ?: "导出失败", Toast.LENGTH_SHORT).show() }
                    close()
                }
                SmallAction(heading2, "导出 Markdown", Secondary, Modifier.weight(1f)) {
                    runCatching { shareNoteExport(context, exportNoteMarkdown(context, note), "text/markdown", note.title) }
                        .onFailure { Toast.makeText(context, it.message ?: "导出失败", Toast.LENGTH_SHORT).show() }
                    close()
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = BorderColor)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(folderInput, null, Modifier.size(14.dp), tint = Secondary)
                Text("移到", fontSize = 12.sp, color = Secondary)
                MoveNotebookSelect(note, notebooks, vm, close)
            }
        }
    }
}

/** App.tsx 6650「移到」：透明、右对齐的原生 select；弹层与「所有笔记」排序共用同一定位和行高。 */
@Composable private fun MoveNotebookSelect(note: Note, notebooks: List<Notebook>, vm: NotesViewModel, close: () -> Unit) {
    val movableNotebooks = notebooks.filterNot { it.system }
    NotesDropdown(
        value = note.notebookId,
        options = movableNotebooks.map { it.id to it.title },
        onSelect = { notebookId -> vm.move(note.id, notebookId); close() },
        modifier = Modifier.fillMaxWidth(),
        minHeight = 44.dp,
        valueColor = Primary,
        fontSize = 12.sp,
        menuWidth = 200.dp,
        maxMenuHeight = 220.dp,
        valueTextAlign = TextAlign.End,
    )
}

/**
 * 笔记页专用的紧凑下拉框。Material DropdownMenu 会加入默认 8dp 菜单内边距、48dp 行高与
 * 自动横向偏移，导致窄排序框和全宽「移到」框出现两套不同的尺寸/锚点；这里显式固定 Web select 的结构。
 */
@Composable private fun <T> NotesDropdown(
    value: T,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    modifier: Modifier,
    minHeight: Dp,
    valueColor: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    menuWidth: Dp,
    maxMenuHeight: Dp,
    valueTextAlign: TextAlign,
) {
    var expanded by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val positionProvider = remember(density) {
        NotesDropdownPositionProvider(
            edgePaddingPx = with(density) { 8.dp.roundToPx() },
            verticalGapPx = with(density) { 4.dp.roundToPx() },
        )
    }
    val selectedLabel = options.firstOrNull { it.first == value }?.second.orEmpty()

    Box(modifier.clickable(enabled = options.isNotEmpty()) { expanded = true }) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = minHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                selectedLabel,
                Modifier.weight(1f),
                color = valueColor,
                fontSize = fontSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = valueTextAlign,
            )
            Canvas(Modifier.size(16.dp)) {
                val y = size.height * .43f
                val half = 3.25.dp.toPx()
                val drop = 3.dp.toPx()
                drawLine(Muted, Offset(size.width / 2f - half, y), Offset(size.width / 2f, y + drop), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                drawLine(Muted, Offset(size.width / 2f, y + drop), Offset(size.width / 2f + half, y), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
            }
        }

        if (expanded) {
            Popup(
                popupPositionProvider = positionProvider,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier
                        .width(menuWidth)
                        .heightIn(max = maxMenuHeight)
                        .shadow(10.dp, RoundedCornerShape(8.dp), clip = false)
                        .background(SurfaceHigh, RoundedCornerShape(8.dp))
                        .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                        .verticalScroll(rememberScrollState()),
                ) {
                    options.forEach { (optionValue, label) ->
                        val selected = optionValue == value
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .background(if (selected) Ambient.copy(alpha = .10f) else Color.Transparent)
                                .clickable {
                                    expanded = false
                                    onSelect(optionValue)
                                }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                label,
                                Modifier.weight(1f),
                                color = if (selected) AmbientLight else Primary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (selected) Icon(check, null, Modifier.size(14.dp), tint = AmbientLight)
                        }
                    }
                }
            }
        }
    }
}

/** 弹层始终与触发区右边缘对齐；下方空间不足时整体翻到上方，且保留 8dp 屏幕边距。 */
private class NotesDropdownPositionProvider(
    private val edgePaddingPx: Int,
    private val verticalGapPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - edgePaddingPx - popupContentSize.width).coerceAtLeast(edgePaddingPx)
        val x = (anchorBounds.right - popupContentSize.width).coerceIn(edgePaddingPx, maxX)
        val below = anchorBounds.bottom + verticalGapPx
        val above = anchorBounds.top - verticalGapPx - popupContentSize.height
        val maxY = (windowSize.height - edgePaddingPx - popupContentSize.height).coerceAtLeast(edgePaddingPx)
        val y = if (below + popupContentSize.height <= windowSize.height - edgePaddingPx) below else above.coerceIn(edgePaddingPx, maxY)
        return IntOffset(x, y)
    }
}

@Composable private fun EditorToolbar(active: EditorTool?, isRecording: Boolean, select: (EditorTool?) -> Unit, heading: () -> Unit, checklist: () -> Unit, recording: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp).drawBehind { drawLine(BorderColor.copy(alpha = .7f), Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()); drawLine(BorderColor.copy(alpha = .7f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }.padding(vertical = 4.dp), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
        ToolbarIcon(heading2, "插入标题", heading)
        ToolbarIcon(listChecks, "添加清单", checklist)
        ToolbarIcon(paperclip, "添加附件") { select(if (active == EditorTool.ATTACH) null else EditorTool.ATTACH) }
        IconButton(recording, Modifier.size(40.dp)) { Icon(if (isRecording) squareFill else mic, if (isRecording) "停止录音" else "开始录音", Modifier.size(16.dp), tint = if (isRecording) Danger else Secondary) }
        ToolbarIcon(pencilLine, "添加手写") { select(EditorTool.DRAWING) }
    }
}

@Composable private fun ChecklistSection(note: Note, vm: NotesViewModel) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        HorizontalDivider(color = BorderColor)
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Eyebrow("清单"); TextButton({ vm.addChecklist(note.id) }) { Text("添加一项", color = AmbientLight, style = MaterialTheme.typography.labelSmall) } }
        note.checklist.forEach { block ->
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(40.dp).clickable { vm.toggleChecklist(note.id, block.id) }, contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(20.dp)
                            .border(1.dp, if (block.checked) Ambient else BorderStrong, RoundedCornerShape(6.dp))
                            .background(if (block.checked) Ambient else Color.Transparent, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (block.checked) Icon(check, null, Modifier.size(12.dp), tint = CanvasColor)
                    }
                }
                TextField(block.text, { vm.updateChecklistText(note.id, block.id, it) }, Modifier.weight(1f), placeholder = { Text("清单内容", color = Muted) }, textStyle = MaterialTheme.typography.bodyMedium.copy(color = if (block.checked) Muted else Primary, textDecoration = if (block.checked) TextDecoration.LineThrough else null), colors = editorTextFieldColors(focused = if (block.checked) Muted else Primary, unfocused = if (block.checked) Muted else Primary))
                IconButton({ vm.removeChecklist(note.id, block.id) }, Modifier.size(40.dp)) { Icon(x, "删除清单项", Modifier.size(14.dp), tint = Muted) }
            }
        }
    }
}

@Composable private fun AttachmentSection(note: Note, vm: NotesViewModel, onEditDrawing: (NoteAttachment) -> Unit) {
    val context = LocalContext.current
    var renaming by remember { mutableStateOf<NoteAttachment?>(null) }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        HorizontalDivider(color = BorderColor)
        Eyebrow("附件 · ${note.attachments.size}")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            note.attachments.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { attachment ->
                        Box(Modifier.weight(1f).heightIn(min = 96.dp).border(1.dp, BorderColor, MaterialTheme.shapes.medium).background(SurfaceColor, MaterialTheme.shapes.medium)) {
                            AttachmentPreview(attachment)
                            Row(Modifier.align(Alignment.TopEnd).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (attachment.type == AttachmentType.DRAWING) {
                                    Box(Modifier.size(28.dp).background(CanvasColor.copy(alpha = .75f), CircleShape).clickable { onEditDrawing(attachment) }, contentAlignment = Alignment.Center) { Icon(pencilLine, "编辑${attachment.name}", Modifier.size(14.dp), tint = Secondary) }
                                }
                                Box(Modifier.size(28.dp).background(CanvasColor.copy(alpha = .75f), CircleShape).clickable { renaming = attachment }, contentAlignment = Alignment.Center) { Icon(Icons.Outlined.TextFields, "重命名${attachment.name}", Modifier.size(14.dp), tint = Secondary) }
                                Box(Modifier.size(28.dp).background(CanvasColor.copy(alpha = .75f), CircleShape).clickable { shareAttachment(context, attachment) }, contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Share, "分享${attachment.name}", Modifier.size(14.dp), tint = Secondary) }
                                Box(Modifier.size(28.dp).background(CanvasColor.copy(alpha = .75f), CircleShape).clickable { vm.removeAttachment(note.id, attachment.id) }, contentAlignment = Alignment.Center) { Icon(x, "移除${attachment.name}", Modifier.size(14.dp), tint = Secondary) }
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
    renaming?.let { attachment ->
        val baseName = if ('.' in attachment.name) attachment.name.substringBeforeLast('.') else attachment.name
        var value by remember(attachment.id) { mutableStateOf(baseName) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("重命名附件", color = Primary) },
            text = {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.take(120) },
                    singleLine = true,
                    label = { Text("附件名称") },
                    supportingText = { Text("${value.length}/120") },
                )
            },
            confirmButton = { TextButton(onClick = { if (vm.renameAttachment(note.id, attachment.id, value)) renaming = null }) { Text("保存", color = AmbientLight) } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("取消", color = Secondary) } },
            containerColor = SurfaceHigh,
        )
    }
}

@Composable private fun AttachmentPreview(attachment: NoteAttachment) {
    when (attachment.type) {
        AttachmentType.IMAGE -> ImageAttachmentPreview(attachment)
        AttachmentType.RECORDING -> AudioAttachmentPreview(attachment)
        AttachmentType.DRAWING -> DrawingAttachmentPreview(attachment)
        AttachmentType.FILE -> GenericAttachmentPreview(attachment)
    }
}

@Composable private fun ImageAttachmentPreview(attachment: NoteAttachment) {
    var viewerOpen by remember { mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(null, attachment.privatePath) {
        value = withContext(Dispatchers.IO) { decodeBoundedBitmap(File(attachment.privatePath)) }
    }
    if (bitmap == null) GenericAttachmentPreview(attachment)
    else Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(bitmap!!.asImageBitmap(), attachment.name, Modifier.fillMaxWidth().height(112.dp).clickable { viewerOpen = true }, contentScale = ContentScale.Crop)
        AttachmentCaption(attachment)
    }
    if (viewerOpen) ImageZoomViewer(attachment, onDismiss = { viewerOpen = false })
}

@Composable private fun ImageZoomViewer(attachment: NoteAttachment, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val bitmap by produceState<Bitmap?>(null, attachment.privatePath) {
        value = withContext(Dispatchers.IO) { decodeBoundedBitmap(File(attachment.privatePath), 2048) }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)), contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Image(
                    bitmap!!.asImageBitmap(),
                    attachment.name,
                    Modifier.fillMaxSize()
                        .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                offset = if (scale > 1f) offset + pan else Offset.Zero
                            }
                        },
                    contentScale = ContentScale.Fit,
                )
            }
            IconButton(
                onDismiss,
                Modifier.align(Alignment.TopEnd).padding(12.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) { Icon(Icons.Outlined.Close, "关闭", tint = Color.White) }
        }
    }
}

@Composable private fun DrawingAttachmentPreview(attachment: NoteAttachment) {
    var viewerOpen by remember { mutableStateOf(false) }
    val document by produceState<DrawingDocument?>(null, attachment.privatePath) {
        value = withContext(Dispatchers.IO) { runCatching { readDrawingDocument(File(attachment.privatePath)) }.getOrNull() }
    }
    val drawing = document
    if (drawing == null) GenericAttachmentPreview(attachment)
    else Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(112.dp).background(SurfaceLow).clickable { viewerOpen = true }) {
            Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                val allPoints = drawing.strokes.flatMap(InkStroke::points)
                val maxX = allPoints.maxOfOrNull { it.x }?.coerceAtLeast(1f) ?: 1f
                val maxY = allPoints.maxOfOrNull { it.y }?.coerceAtLeast(1f) ?: 1f
                val factor = minOf(size.width / maxX, size.height / maxY)
                scale(factor, pivot = Offset.Zero) {
                    drawing.strokes.forEach { stroke ->
                        val path = Path().apply {
                            stroke.points.firstOrNull()?.let { moveTo(it.x, it.y) }
                            stroke.points.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        if (stroke.erase) drawPath(path, stroke.color, style = Stroke(stroke.width, cap = StrokeCap.Round), blendMode = BlendMode.Clear)
                        else drawPath(path, stroke.color, style = Stroke(stroke.width, cap = StrokeCap.Round))
                    }
                }
            }
        }
        AttachmentCaption(attachment)
    }
    if (viewerOpen) DrawingZoomViewer(attachment, onDismiss = { viewerOpen = false })
}

@Composable private fun DrawingZoomViewer(attachment: NoteAttachment, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val document by produceState<DrawingDocument?>(null, attachment.privatePath) {
        value = withContext(Dispatchers.IO) { runCatching { readDrawingDocument(File(attachment.privatePath)) }.getOrNull() }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)), contentAlignment = Alignment.Center) {
            document?.let { drawing ->
                Box(
                    Modifier.fillMaxSize()
                        .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
                        .background(SurfaceLow)
                        .pointerInput(Unit) { detectTransformGestures { _, pan, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 5f); offset = if (scale > 1f) offset + pan else Offset.Zero } }
                ) {
                    Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                        val allPoints = drawing.strokes.flatMap(InkStroke::points)
                        val maxX = allPoints.maxOfOrNull { it.x }?.coerceAtLeast(1f) ?: 1f
                        val maxY = allPoints.maxOfOrNull { it.y }?.coerceAtLeast(1f) ?: 1f
                        val factor = minOf(size.width / maxX, size.height / maxY)
                        scale(factor, pivot = Offset.Zero) {
                            drawing.strokes.forEach { stroke ->
                                val path = Path().apply {
                                    stroke.points.firstOrNull()?.let { moveTo(it.x, it.y) }
                                    stroke.points.drop(1).forEach { lineTo(it.x, it.y) }
                                }
                                if (stroke.erase) drawPath(path, stroke.color, style = Stroke(stroke.width, cap = StrokeCap.Round), blendMode = BlendMode.Clear)
                                else drawPath(path, stroke.color, style = Stroke(stroke.width, cap = StrokeCap.Round))
                            }
                        }
                    }
                }
            }
            IconButton(
                onDismiss,
                Modifier.align(Alignment.TopEnd).padding(12.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) { Icon(Icons.Outlined.Close, "关闭", tint = Color.White) }
        }
    }
}

@Composable private fun AudioAttachmentPreview(attachment: NoteAttachment) {
    var playing by remember(attachment.privatePath) { mutableStateOf(false) }
    var playbackError by remember(attachment.privatePath) { mutableStateOf<String?>(null) }
    val player = remember(attachment.privatePath) {
        runCatching { MediaPlayer().apply { setDataSource(attachment.privatePath); prepare() } }
            .getOrNull()
    }
    LaunchedEffect(player) { if (player == null) playbackError = "无法播放此录音" }
    DisposableEffect(player) {
        player?.setOnCompletionListener { playing = false }
        onDispose { runCatching { player?.release() } }
    }
    Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = {
            if (player == null) playbackError = "无法播放此录音"
            else runCatching { if (player.isPlaying) player.pause() else player.start(); playing = player.isPlaying }
                .onFailure { playbackError = "无法播放此录音"; playing = false }
        }, Modifier.size(44.dp)) { Icon(if (playing) circlePause else playCircle, if (playing) "暂停${attachment.name}" else "播放${attachment.name}", tint = Radio) }
        Text(attachment.name, style = MaterialTheme.typography.labelSmall, color = Secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(playbackError ?: attachment.durationMillis?.let(::formatDuration).orEmpty(), style = MaterialTheme.typography.labelSmall, color = if (playbackError == null) Muted else Danger)
    }
}

@Composable private fun GenericAttachmentPreview(attachment: NoteAttachment) {
    Column(Modifier.fillMaxWidth().heightIn(min = 96.dp).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(attachment.type.icon, null, tint = Secondary)
        AttachmentCaption(attachment)
    }
}

@Composable private fun AttachmentCaption(attachment: NoteAttachment) {
    Text(attachment.name, Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp), style = MaterialTheme.typography.labelSmall, color = Secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    attachment.sizeBytes?.let { Text(formatBytes(it), style = MaterialTheme.typography.labelSmall, color = Muted) }
}

@Composable private fun InternalLinksSection(note: Note, links: List<NoteBlock>, notes: List<Note>, vm: NotesViewModel) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        HorizontalDivider(color = BorderColor)
        Eyebrow("内部链接 · ${links.size}")
        links.forEach { link ->
            val target = notes.firstOrNull { it.id == link.linkedNoteId }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = target != null) { target?.let { vm.openNote(it.id) } }, verticalAlignment = Alignment.CenterVertically) {
                Icon(activity, null, Modifier.size(18.dp), tint = AmbientLight)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(target?.title?.ifBlank { "无标题笔记" } ?: "链接的笔记已不存在", style = MaterialTheme.typography.bodySmall, color = if (target == null) Muted else Primary)
                    Text(target?.let { "打开内部笔记" } ?: "可移除此链接", style = MaterialTheme.typography.labelSmall, color = Muted)
                }
                IconButton({ vm.removeInternalLink(note.id, link.id) }, Modifier.size(44.dp)) { Icon(x, "移除内部链接", Modifier.size(16.dp), tint = Muted) }
            }
        }
    }
}

@Composable private fun ContextPanel(note: Note, vm: NotesViewModel, close: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().border(1.dp, BorderColor, MaterialTheme.shapes.medium).background(SurfaceColor, MaterialTheme.shapes.medium).padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text("连接 Soundist 上下文", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Primary); IconButton(close, Modifier.size(36.dp)) { Icon(x, "关闭关联", Modifier.size(16.dp), tint = Muted) } }
        Text("关联是可选增强；笔记不需要绑定专注或声场也能独立使用。", style = MaterialTheme.typography.bodySmall, color = Muted)
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { vm.connectCurrent(context, note.id) }, Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text("连接当前目标与声场", color = AmbientLight) }
        if (note.context != null) OutlinedButton(onClick = { vm.connect(context, note.id, null) }, Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text("移除关联", color = Secondary) }
    }
}

@Composable private fun InkCanvas(initial: DrawingDocument? = null, onCancel: () -> Unit, onSave: (DrawingDocument) -> Unit) {
    var strokes by remember(initial?.id) { mutableStateOf(initial?.strokes ?: emptyList()) }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var color by remember { mutableStateOf(AmbientLight) }
    var width by remember { mutableFloatStateOf(3f) }
    var eraserWidth by remember { mutableFloatStateOf(24f) }
    var erasing by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().border(1.dp, BorderColor, MaterialTheme.shapes.medium).background(SurfaceLow, MaterialTheme.shapes.medium).padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(AmbientLight, Primary, Radio, Color(0xFFD8849B)).forEach { ink ->
                Box(Modifier.size(36.dp).border(1.dp, if (color == ink && !erasing) Primary else BorderColor, RoundedCornerShape(8.dp)).clickable { color = ink; erasing = false }, contentAlignment = Alignment.Center) { Box(Modifier.size(16.dp).background(ink, CircleShape)) }
            }
            Spacer(Modifier.weight(1f)); IconButton({ if (strokes.isNotEmpty()) strokes = strokes.dropLast(1) }, Modifier.size(36.dp).border(1.dp, BorderColor, RoundedCornerShape(8.dp))) { Icon(undo2, "撤销上一笔", Modifier.size(16.dp), tint = Secondary) }
        }
        Box(
            Modifier.fillMaxWidth().height(400.dp).padding(top = 8.dp)
                .clip(MaterialTheme.shapes.medium).background(SurfaceLow)
                .border(1.dp, BorderColor, MaterialTheme.shapes.medium)
        ) {
            Canvas(
                Modifier.fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .pointerInput(width, eraserWidth, erasing) {
                        val strokeWidth = if (erasing) eraserWidth else width
                        detectDragGestures(
                            onDragStart = { current = listOf(it) },
                            onDrag = { change, _ ->
                                val p = Offset(change.position.x.coerceIn(0f, size.width.toFloat()), change.position.y.coerceIn(0f, size.height.toFloat()))
                                current = current + p
                            },
                            onDragEnd = {
                                if (current.size > 1) strokes = strokes + InkStroke(color, strokeWidth, current, erase = erasing)
                                current = emptyList()
                            },
                        )
                    }
            ) {
                (strokes + if (current.size > 1) listOf(InkStroke(color, if (erasing) eraserWidth else width, current, erase = erasing)) else emptyList()).forEach { stroke ->
                    val path = Path().apply { if (stroke.points.isNotEmpty()) { moveTo(stroke.points.first().x, stroke.points.first().y); stroke.points.drop(1).forEach { lineTo(it.x, it.y) } } }
                    if (stroke.erase) drawPath(path, stroke.color, style = Stroke(stroke.width, cap = StrokeCap.Round), blendMode = BlendMode.Clear)
                    else drawPath(path, stroke.color, style = Stroke(stroke.width, cap = StrokeCap.Round))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SoundSlider(
                value = if (erasing) eraserWidth else width,
                onValueChange = { if (erasing) eraserWidth = it else width = it },
                valueRange = if (erasing) 8f..48f else 1f..8f,
                modifier = Modifier.weight(1f),
            )
            IconButton({ erasing = !erasing }, Modifier.size(40.dp)) {
                Box(
                    Modifier.size(32.dp)
                        .border(1.dp, if (erasing) AmbientLight else BorderColor, RoundedCornerShape(8.dp))
                        .background(if (erasing) Ambient.copy(alpha = .1f) else Color.Transparent, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(eraser, "橡皮擦", Modifier.size(15.dp), tint = if (erasing) AmbientLight else Secondary) }
            }
            Text(
                if (erasing) "橡皮 ${eraserWidth.toInt()}px" else "画笔 ${width.toInt()}px",
                Modifier.width(68.dp),
                textAlign = TextAlign.Center,
                fontSize = 10.sp,
                color = if (erasing) AmbientLight else Muted,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onCancel, Modifier.weight(1f).heightIn(min = 44.dp)) { Text("取消", color = Secondary, fontSize = 12.sp) }
            Button({ onSave(DrawingDocument(id = initial?.id ?: UUID.randomUUID().toString(), strokes = strokes)) }, Modifier.weight(1f).heightIn(min = 44.dp), enabled = strokes.isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = Ambient, contentColor = CanvasColor)) { Text("保存手写", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable private fun NoteRow(note: Note, open: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { open(note.id) }.padding(vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { if (note.pinned) Icon(pin, null, Modifier.size(12.dp), tint = Radio); Text(note.title.ifBlank { "无标题笔记" }, color = Primary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Text(note.text.ifBlank { "开始写下内容…" }, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = Secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (note.tags.isNotEmpty() || note.attachments.isNotEmpty() || note.checklist.isNotEmpty() || note.context != null) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    note.tags.take(2).forEach { Text("#$it", fontSize = 10.sp, color = Muted, modifier = Modifier.background(SurfaceHigh, MaterialTheme.shapes.small).padding(horizontal = 6.dp, vertical = 2.dp)) }
                    if (note.attachments.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) { Icon(paperclip, null, Modifier.size(12.dp), tint = Muted); Spacer(Modifier.width(4.dp)); Text("${note.attachments.size}", fontSize = 10.sp, color = Muted) }
                    if (note.checklist.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) { Icon(checkSquare, null, Modifier.size(12.dp), tint = Muted); Spacer(Modifier.width(4.dp)); Text("${note.checklist.count { it.checked }}/${note.checklist.size}", fontSize = 10.sp, color = Muted) }
                    if (note.context != null) Row(verticalAlignment = Alignment.CenterVertically) { Icon(activity, null, Modifier.size(12.dp), tint = AmbientLight); Spacer(Modifier.width(4.dp)); Text("已连接", fontSize = 10.sp, color = AmbientLight) }
                }
            }
            Text(relativeTime(note.updatedAt), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal), color = Muted)
        }
        HorizontalDivider(Modifier.padding(top = 12.dp), color = BorderColor.copy(alpha = .7f))
    }
}

@Composable private fun NotebookRow(notebook: Notebook, count: Int, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        val accent = Color(notebook.accent)
        Box(Modifier.size(32.dp).background(accent.copy(alpha = .082f), MaterialTheme.shapes.medium).border(1.dp, accent.copy(alpha = .145f), MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) { Icon(notebookIcon(notebook), null, Modifier.size(16.dp), tint = accent) }
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(notebook.title, color = Primary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium); Text(if (count == 0) "暂无笔记" else "$count 篇", style = MaterialTheme.typography.bodySmall, color = Muted) }
        Icon(chevronRight, null, Modifier.size(16.dp), tint = Color(0xFF5F6D69))
    }
}

@Composable private fun SearchField(value: String, onChange: (String) -> Unit, hint: String) {
    Column(Modifier.fillMaxWidth()) {
    HorizontalDivider(color = BorderColor)
    Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(search, null, Modifier.size(16.dp), tint = Muted)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 8.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Primary),
            cursorBrush = SolidColor(Ambient),
            decorationBox = { field ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyMedium, color = Muted)
                    field()
                }
            },
        )
        if (value.isNotEmpty()) IconButton({ onChange("") }, Modifier.size(44.dp)) { Icon(x, "清除搜索", Modifier.size(16.dp), tint = Muted) }
    }
    HorizontalDivider(color = BorderColor)
    }
}

@Composable private fun EmptyNotes(text: String, showIcon: Boolean = true, verticalPadding: Dp = 64.dp) { Column(Modifier.fillMaxWidth().padding(vertical = verticalPadding), horizontalAlignment = Alignment.CenterHorizontally) { if (showIcon) Icon(fileText, null, Modifier.size(28.dp), tint = Color(0xFF5F6D69)); Text(text, Modifier.padding(top = if (showIcon) 12.dp else 0.dp), style = MaterialTheme.typography.bodyMedium, color = Secondary) } }
@Composable private fun Eyebrow(text: String) { Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Muted, letterSpacing = 2.sp) }
@Composable private fun RoundAction(icon: ImageVector, description: String, onClick: () -> Unit) { IconButton(onClick, Modifier.size(44.dp).background(Color(0xFF183C36), CircleShape).border(1.dp, Ambient.copy(alpha = .25f), CircleShape)) { Icon(icon, description, Modifier.size(16.dp), tint = AmbientLight) } }
@Composable private fun TagChip(tag: String, remove: () -> Unit) {
    Box(Modifier.heightIn(min = 32.dp).background(SurfaceHigh, RoundedCornerShape(6.dp)).clickable(onClick = remove).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
        Text("#$tag ×", fontSize = 11.sp, color = Secondary)
    }
}

@Composable private fun FilterButton(label: String, selected: Boolean, click: () -> Unit) { Text(label, Modifier.heightIn(min = 36.dp).background(if (selected) SurfaceHigh else Color.Transparent, MaterialTheme.shapes.small).clickable(onClick = click).padding(horizontal = 10.dp, vertical = 9.dp), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal), color = if (selected) Primary else Muted) }
@Composable private fun ToolbarIcon(icon: ImageVector, label: String, click: () -> Unit) { IconButton(click, Modifier.size(40.dp)) { Icon(icon, label, Modifier.size(16.dp), tint = Secondary) } }
@Composable private fun SmallAction(icon: ImageVector, label: String, color: Color, modifier: Modifier = Modifier, click: () -> Unit) { TextButton(click, modifier.heightIn(min = 44.dp)) { Icon(icon, null, Modifier.size(14.dp), tint = color); Text(label, Modifier.padding(start = 6.dp), color = color, fontSize = 12.sp) } }
@Composable private fun AttachmentButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, click: () -> Unit) { OutlinedButton(click, modifier.heightIn(min = 44.dp), border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)) { Icon(icon, null, Modifier.size(14.dp), tint = Secondary); Text(label, Modifier.padding(start = 6.dp), color = Secondary, fontSize = 12.sp) } }
@Composable private fun BasicTagField(value: String, change: (String) -> Unit, submit: () -> Unit) { BasicTextField(value, change, Modifier.width(64.dp), singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { submit() }), cursorBrush = SolidColor(Ambient), textStyle = MaterialTheme.typography.labelSmall.copy(color = Primary), decorationBox = { field -> Box { if (value.isEmpty()) Text("标签", color = Muted, style = MaterialTheme.typography.labelSmall); field() } }) }
@Composable private fun ContextSummary(context: NoteContext, click: () -> Unit) { Column(Modifier.fillMaxWidth().background(Ambient.copy(alpha = .06f), MaterialTheme.shapes.medium).border(1.dp, Ambient.copy(alpha = .2f), MaterialTheme.shapes.medium).clickable(onClick = click).padding(12.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(activity, null, Modifier.size(15.dp), tint = AmbientLight); Text(context.targetName ?: "已连接一次专注", Modifier.padding(start = 7.dp), style = MaterialTheme.typography.labelSmall, color = AmbientLight) }; Text((context.soundNames + listOfNotNull(context.radioName)).joinToString(" · ").ifBlank { "未记录环境声" }, Modifier.padding(top = 5.dp), style = MaterialTheme.typography.labelSmall, color = Muted) } }

@Composable private fun editorTextFieldColors(focused: Color = Primary, unfocused: Color = Primary) = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = Ambient, focusedTextColor = focused, unfocusedTextColor = unfocused)

/** App.tsx 6546-6548：live 笔记标签统计，标签名按 zh-CN 排序。 */
private fun List<Note>.tagCounts(): List<Pair<String, Int>> {
    val counts = flatMap { it.tags }.groupingBy { it }.eachCount()
    val collator = Collator.getInstance(Locale.CHINA)
    return counts.entries.map { it.key to it.value }.sortedWith(compareBy(collator) { it.first })
}

private fun List<Note>.forNotebook(id: String): List<Note> = when (id) {
    ALL_NOTES_ID -> filter { it.trashedAt == null && !it.archived }
    ARCHIVE_ID -> filter { it.trashedAt == null && it.archived }
    TRASH_ID -> filter { it.trashedAt != null }
    else -> filter { it.notebookId == id && it.trashedAt == null }
}

private fun List<Note>.filtered(query: String, filter: NoteFilter): List<Note> {
    val needle = query.trim().lowercase()
    return filter { note ->
        // App.tsx 6534/6536：title+text+tags 拼接整体 includes（可跨字段）
        val matchesQuery = needle.isBlank() || "${note.title} ${note.text} ${note.tags.joinToString(" ")}".lowercase().contains(needle)
        val matchesFilter = when (filter) {
            NoteFilter.ALL -> true
            NoteFilter.PINNED -> note.pinned
            NoteFilter.IMAGES -> note.attachments.any { it.type == AttachmentType.IMAGE || it.type == AttachmentType.DRAWING }
            NoteFilter.AUDIO -> note.attachments.any { it.type == AttachmentType.RECORDING }
            NoteFilter.CHECKLISTS -> note.checklist.isNotEmpty()
            NoteFilter.LINKED -> note.context != null
            NoteFilter.ARCHIVED -> note.archived
        }
        matchesQuery && matchesFilter
    }
}

/** App.tsx 6518 noteTimeRank：刚刚/今天 HH:MM/昨天/N天前 分级排序。 */
private fun noteTimeRank(value: String): Int {
    if (value == "刚刚") return 4_000_000
    val today = Regex("""^今天(?:\s+(\d{1,2}):(\d{2}))?""").find(value)
    if (today != null) {
        val h = today.groupValues.getOrNull(1)?.toIntOrNull() ?: 0
        val m = today.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
        return 3_000_000 + h * 60 + m
    }
    if (value.startsWith("昨天")) return 2_000_000
    val daysAgo = Regex("""^(\d+)天前""").find(value)
    if (daysAgo != null) return 1_000_000 - (daysAgo.groupValues.getOrNull(1)?.toIntOrNull() ?: 0)
    return 0
}

private fun List<Note>.sorted(sort: NoteSort): List<Note> = sortedWith(
    compareByDescending<Note> { it.pinned }.thenComparator { a, b ->
        when (sort) {
            NoteSort.UPDATED -> noteTimeRank(relativeTime(b.updatedAt)) - noteTimeRank(relativeTime(a.updatedAt))
            NoteSort.CREATED -> noteTimeRank(relativeTime(b.createdAt)) - noteTimeRank(relativeTime(a.createdAt))
            NoteSort.TITLE -> Collator.getInstance(Locale.CHINA).compare(a.title.ifBlank { "无标题笔记" }, b.title.ifBlank { "无标题笔记" })
        }
    },
)
private val NoteFilter.label get() = when (this) { NoteFilter.ALL -> "全部"; NoteFilter.PINNED -> "置顶"; NoteFilter.IMAGES -> "图片/手写"; NoteFilter.AUDIO -> "录音"; NoteFilter.CHECKLISTS -> "清单"; NoteFilter.LINKED -> "已关联"; NoteFilter.ARCHIVED -> "归档" }
private val NoteSort.shortLabel get() = when (this) { NoteSort.UPDATED -> "最近更新"; NoteSort.CREATED -> "创建时间"; NoteSort.TITLE -> "标题" }
private val AttachmentType.icon get() = when (this) { AttachmentType.IMAGE -> image; AttachmentType.FILE -> paperclip; AttachmentType.RECORDING -> mic; AttachmentType.DRAWING -> pencilLine }
private fun AttachmentType.pickerRequest(noteId: String) = AttachmentPickerRequest(
    noteId = noteId,
    type = this,
    mimeTypes = when (this) {
        AttachmentType.IMAGE -> listOf("image/*")
        AttachmentType.FILE -> listOf("*/*")
        AttachmentType.RECORDING -> listOf("audio/*")
        AttachmentType.DRAWING -> emptyList()
    },
)
internal fun decodeBoundedBitmap(file: File, maximumDimension: Int = 1024): Bitmap? {
    if (!file.isFile) return null
    require(maximumDimension > 0) { "图片解码尺寸必须大于 0" }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > maximumDimension) sample *= 2
    return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
}
private fun formatBytes(value: Long): String = when {
    value >= 1024 * 1024 -> "%.1f MB".format(Locale.ROOT, value / (1024.0 * 1024.0))
    value >= 1024 -> "%.1f KB".format(Locale.ROOT, value / 1024.0)
    else -> "$value B"
}
private data class NotebookIconOption(val key: String, val label: String)
private data class NotebookAccentOption(val color: Long, val label: String)

/** 新建笔记本的用途图标：保留原有十项，增加灵感、生活、情感、影像、旅行、财务、健康和饮食。 */
private val notebookIconOptions = listOf(
    NotebookIconOption("bookOpen", "阅读"), NotebookIconOption("music2", "音乐"),
    NotebookIconOption("bookmark", "收藏"), NotebookIconOption("user", "个人"),
    NotebookIconOption("briefcase", "工作"), NotebookIconOption("graduationCap", "学习"),
    NotebookIconOption("star", "梦想"), NotebookIconOption("flag", "目标"),
    NotebookIconOption("tag", "分类"), NotebookIconOption("archive", "资料"),
    NotebookIconOption("lightbulb", "灵感"), NotebookIconOption("home", "家庭生活"),
    NotebookIconOption("heart", "关系情感"), NotebookIconOption("camera", "回忆影像"),
    NotebookIconOption("plane", "旅行"), NotebookIconOption("wallet", "财务"),
    NotebookIconOption("fitness", "健康运动"), NotebookIconOption("restaurant", "饮食菜谱"),
)

/** 低饱和色板：原有五色加 Soundist 青绿、苔藓、暖黄、砖红与灰青。 */
private val notebookAccentOptions = listOf(
    NotebookAccentOption(0xFF7FAE87, "柔和绿"), NotebookAccentOption(0xFF738FA4, "雾霾蓝"),
    NotebookAccentOption(0xFFA292C1, "灰紫"), NotebookAccentOption(0xFFC99662, "暖棕"),
    NotebookAccentOption(0xFFD8849B, "灰粉"), NotebookAccentOption(0xFF55B6A3, "青绿色"),
    NotebookAccentOption(0xFF87986A, "苔藓绿"), NotebookAccentOption(0xFFB7A06A, "暖黄色"),
    NotebookAccentOption(0xFFB97872, "砖红色"), NotebookAccentOption(0xFF688F97, "灰青色"),
)

private fun iconForKey(key: String): ImageVector = when (key) {
    "bookOpen" -> bookOpen
    "music2" -> music2
    "bookmark" -> bookmark
    "user" -> user
    "briefcase" -> briefcase
    "graduationCap" -> graduationCap
    "star" -> star
    "flag" -> flag
    "tag" -> tag
    "archive" -> archive
    "lightbulb" -> Icons.Outlined.Lightbulb
    "home" -> home
    "heart" -> heart
    "camera" -> Icons.Outlined.PhotoCamera
    "plane" -> plane
    "wallet" -> Icons.Outlined.AccountBalanceWallet
    "fitness" -> Icons.Outlined.FitnessCenter
    "restaurant" -> Icons.Outlined.Restaurant
    "trash2" -> trash2
    else -> tag
}
/** 内置笔记本按 id 映射图标；自定义笔记本用 iconKey。 */
private fun notebookIcon(notebook: Notebook): ImageVector = when (notebook.id) {
    ALL_NOTES_ID -> bookOpen
    "nb2" -> music2
    "nb3" -> bookmark
    "nb4" -> user
    "nb5" -> briefcase
    "nb6" -> graduationCap
    "nb7" -> star
    "nb8" -> flag
    TRASH_ID -> trash2
    ARCHIVE_ID -> archive
    else -> iconForKey(notebook.iconKey)
}
/** App.tsx 笔记时间标签词汇：刚刚 / 今天 HH:MM / 昨天 / N天前（noteTimeRank 依赖此格式）。 */
private fun relativeTime(value: Long): String {
    val zone = java.time.ZoneId.systemDefault()
    val date = java.time.Instant.ofEpochMilli(value).atZone(zone)
    val today = java.time.LocalDate.now(zone)
    val day = date.toLocalDate()
    return when {
        System.currentTimeMillis() - value < 60_000 -> "刚刚"
        day == today -> "今天 ${String.format("%02d:%02d", date.hour, date.minute)}"
        day == today.minusDays(1) -> "昨天"
        else -> "${java.time.temporal.ChronoUnit.DAYS.between(day, today)}天前"
    }
}
private fun formatDuration(value: Long): String = "%d:%02d".format(value / 60_000, value / 1_000 % 60)
private fun String.safeFileName(): String = replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "attachment" }
private fun Throwable.recordingMessage(): String = when (this) {
    is SecurityException -> "没有麦克风权限，录音未开始"
    is IllegalStateException -> localizedMessage ?: "录音器状态异常"
    else -> localizedMessage ?: "录音失败，请检查麦克风是否被其他应用占用"
}

/** App.tsx 录音态 `Square fill="currentColor"`：实心方块（lucide square 默认是描边）。 */
private fun parsePath(d: String): List<PathNode> = PathParser().parsePathString(d).toNodes()

private val squareFill: ImageVector by lazy {
    ImageVector.Builder(
        name = "squareFill",
        defaultWidth = 24.0.dp, defaultHeight = 24.0.dp,
        viewportWidth = 24.0f, viewportHeight = 24.0f,
    ).apply {
        addPath(
            pathData = parsePath("M5.0 3.0H19.0A2.0 2.0 0 0 1 21.0 5.0V19.0A2.0 2.0 0 0 1 19.0 21.0H5.0A2.0 2.0 0 0 1 3.0 19.0V5.0A2.0 2.0 0 0 1 5.0 3.0Z"),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
    }.build()
}

private const val ALL_NOTES_ID = "nb1"
private const val TRASH_ID = "nb9"
private const val ARCHIVE_ID = "nb10"
private const val MAX_ATTACHMENT_BYTES = 8L * 1024 * 1024
private fun defaultNotebooks() = listOf(
    Notebook(ALL_NOTES_ID, "所有笔记", 0xFF55B6A3, true, iconKey = "bookOpen"), Notebook("nb2", "声场灵感库", 0xFF738FA4, iconKey = "music2"),
    Notebook("nb3", "我的记忆卡片", 0xFFC99662, iconKey = "bookmark"), Notebook("nb4", "自我", 0xFF7FAE87, iconKey = "user"),
    Notebook("nb5", "工作", 0xFF738FA4, iconKey = "briefcase"), Notebook("nb6", "学习", 0xFFA292C1, iconKey = "graduationCap"),
    Notebook("nb7", "梦想笔记本", 0xFFC99662, iconKey = "star"), Notebook("nb8", "人生大事记", 0xFFD8849B, iconKey = "flag"),
    Notebook(TRASH_ID, "回收本", 0xFF7F8C87, true, iconKey = "trash2"), Notebook(ARCHIVE_ID, "归档", 0xFF7F8C87, true, iconKey = "archive"),
)

private val CanvasColor = Color(0xFF080B0D)
private val SurfaceLow = Color(0xFF101719)
private val SurfaceColor = Color(0xFF161E21)
private val SurfaceHigh = Color(0xFF1E282B)
private val BorderColor = Color(0xFF314044)
private val BorderStrong = Color(0xFF43565A)
private val Primary = Color(0xFFE9ECE9)
private val Secondary = Color(0xFFA9B3AF)
private val Muted = Color(0xFF929D99)
private val Ambient = Color(0xFF55B6A3)
private val AmbientLight = Color(0xFF91D3C5)
private val Radio = Color(0xFFC99662)
private val Danger = Color(0xFFD57478)
