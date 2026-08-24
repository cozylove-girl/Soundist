package com.soundist.feature.notes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

class NotesViewModelTest {
    @Test fun editingTextAdvancesNoteRevision() {
        val repository = InMemoryNotesRepository()
        val viewModel = NotesViewModel(repository)
        val id = viewModel.createNote()

        viewModel.updateText(id, "真实内容")

        val note = repository.state.value.notes.single()
        assertEquals(2, note.revision)
        assertEquals("真实内容", note.text)
    }

    @Test fun trashAndRestorePreserveOriginalNotebook() {
        val repository = InMemoryNotesRepository()
        val viewModel = NotesViewModel(repository)
        val id = viewModel.createNote("nb6")
        viewModel.togglePin(id)

        viewModel.trash(id)
        assertTrue(repository.state.value.notes.single().trashedAt != null)
        assertFalse(repository.state.value.notes.single().pinned)
        assertEquals(NotesView.NOTEBOOK, repository.state.value.view)
        viewModel.restore(id)

        val restored = repository.state.value.notes.single()
        assertEquals("nb6", restored.notebookId)
        assertFalse(restored.trashedAt != null)
        assertEquals("nb6", repository.state.value.selectedNotebookId)
    }

    @Test fun internalLinkKeepsTargetIdentityAndDoesNotDuplicate() {
        val target = Note(id = "target", notebookId = "nb6", title = "线性代数")
        val source = Note(id = "source", notebookId = "nb4", title = "复习")
        val repository = InMemoryNotesRepository(NotesState(notes = listOf(target, source)))
        val viewModel = NotesViewModel(repository)

        viewModel.addInternalLink(source.id, target.id)
        viewModel.addInternalLink(source.id, target.id)

        val link = repository.state.value.notes.single { it.id == source.id }.internalLinks.single { it.type == BlockType.INTERNAL_LINK }
        assertEquals(target.id, link.linkedNoteId)
        assertEquals("线性代数", link.text)
    }

    @Test fun notesRootUsesOneTaggedLazyColumnForWholePageScrolling() {
        assertEquals("LazyColumn:$NOTES_PAGE_SCROLL_TEST_TAG", notesPageScrollSemantics)
    }

    @Test fun drawingBinaryRoundTripPreservesEveryStrokeAndPoint() {
        val file = File.createTempFile("soundist-drawing", ".sdi")
        try {
            val drawing = DrawingDocument("drawing-1", listOf(
                InkStroke(Color(0xFF91D3C5), 3f, listOf(Offset(1.5f, 2.5f), Offset(20f, 30f))),
                InkStroke(Color(0xFFC99662), 7f, listOf(Offset(9f, 8f))),
            ))
            writeDrawingDocument(file, drawing)

            val restored = readDrawingDocument(file)

            assertEquals(drawing.id, restored.id)
            assertEquals(drawing.strokes.map { it.color.toArgb() }, restored.strokes.map { it.color.toArgb() })
            assertEquals(drawing.strokes.map { it.width }, restored.strokes.map { it.width })
            assertEquals(drawing.strokes.map { it.points }, restored.strokes.map { it.points })
            assertNotEquals(drawing.id, file.absolutePath)
        } finally { file.delete() }
    }

    @Test fun noteContextBinaryRoundTripPreservesEveryField() {
        val file = File.createTempFile("soundist-context", ".snc")
        try {
            val context = NoteContext("focus-1", "todo", "todo-7", "写报告", listOf("小雨", "篝火"), "夜航电台")

            writeNoteContext(file, context)

            assertEquals(context, readNoteContext(file))
        } finally { file.delete() }
    }

    @Test fun customNotebookCanBeCreatedRenamedAndDeletedWithoutDeletingNotes() {
        val repository = InMemoryNotesRepository()
        val viewModel = NotesViewModel(repository)
        viewModel.createNotebook("灵感")
        val notebook = repository.state.value.notebooks.single { it.title == "灵感" }
        viewModel.renameNotebook(notebook.id, "灵感库")
        assertEquals("灵感库", repository.state.value.notebooks.single { it.id == notebook.id }.title)
        val noteId = viewModel.createNote(notebook.id)

        viewModel.deleteNotebook(notebook.id)

        assertTrue(repository.state.value.notebooks.none { it.id == notebook.id })
        assertEquals("nb4", repository.state.value.notes.single { it.id == noteId }.notebookId)
    }

    @Test fun pickerContractEnforcesFiveItemsAndEightMegabytes() {
        val request = AttachmentPickerRequest("note", AttachmentType.IMAGE, listOf("image/*"))
        assertEquals(5, request.maximumItems)
        assertEquals(8L * 1024 * 1024, request.maximumBytesPerItem)
        assertTrue(request.allowMultiple)
    }

    @Test fun pausedRecorderRemainsAnActiveRecordingSession() {
        val paused = RecorderState(RecorderStatus.PAUSED, elapsedMillis = 1_500)
        val failed = RecorderState(RecorderStatus.ERROR, errorMessage = "麦克风不可用")

        assertTrue(paused.recording)
        assertTrue(paused.paused)
        assertFalse(failed.recording)
        assertEquals("麦克风不可用", failed.errorMessage)
    }

    @Test fun rejectedRecordingPermissionUsesRecorderErrorState() {
        DisabledNoteRecorder.clearError()
        val viewModel = NotesViewModel(
            InMemoryNotesRepository(),
            AppPrivateNoteAssetStore(),
            DisabledNoteRecorder,
            null,
            java.time.Clock.systemUTC(),
        )

        viewModel.reportRecordingError("需要麦克风权限才能创建录音笔记")

        assertEquals(RecorderStatus.ERROR, viewModel.recorderState.value.status)
        assertEquals("需要麦克风权限才能创建录音笔记", viewModel.recorderState.value.errorMessage)
        DisabledNoteRecorder.clearError()
    }

    @Test fun oversizedAttachmentIsRejectedAndPartialFileIsDeleted() {
        val target = File.createTempFile("soundist-oversized", ".bin").also { it.delete() }
        val input = ByteArrayInputStream(ByteArray(17) { 1 })

        assertThrows(IllegalArgumentException::class.java) {
            copyAttachmentToPrivateFile(input, target, maximumBytes = 16)
        }

        assertFalse(target.exists())
    }

    @Test fun attachmentAtMaximumSizeIsPersistedExactly() {
        val target = File.createTempFile("soundist-maximum", ".bin").also { it.delete() }
        try {
            val source = ByteArray(16) { it.toByte() }

            val copied = copyAttachmentToPrivateFile(ByteArrayInputStream(source), target, maximumBytes = 16)

            assertEquals(16L, copied)
            assertTrue(source.contentEquals(target.readBytes()))
        } finally { target.delete() }
    }

    @Test fun failedRecordingCleanupDeletesIncompleteFile() {
        val file = File.createTempFile("soundist-incomplete", ".m4a")

        discardIncompleteRecording(file)

        assertFalse(file.exists())
    }

    @Test fun trashHidesNoteFromLiveNotebookAndRestoreReturnsIt() {
        val repository = InMemoryNotesRepository()
        val viewModel = NotesViewModel(repository)
        val id = viewModel.createNote("nb6")

        viewModel.trash(id)
        assertTrue(repository.state.value.notes.filter { it.trashedAt == null }.none { it.id == id })
        assertTrue(repository.state.value.notes.filter { it.trashedAt != null }.any { it.id == id })

        viewModel.restore(id)
        assertEquals("nb6", repository.state.value.notes.single { it.id == id }.notebookId)
    }

    @Test fun openingSearchResultReturnsToItsOwnNotebook() {
        val note = Note(id = "result", notebookId = "nb6", title = "线性代数")
        val repository = InMemoryNotesRepository(NotesState(notes = listOf(note), selectedNotebookId = "nb1"))
        val viewModel = NotesViewModel(repository)

        viewModel.openNote(note.id)
        viewModel.backFromEditor()

        assertEquals("nb6", repository.state.value.selectedNotebookId)
        assertEquals(NotesView.NOTEBOOK, repository.state.value.view)
    }
}
