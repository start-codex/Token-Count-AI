package com.startcodex.tokencountai

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup
import com.intellij.util.messages.MessageBusConnection

class TokenCountAIWidget(project: Project) :
    EditorBasedStatusBarPopup(project, false),
    CaretListener, SelectionListener, DocumentListener, FileEditorManagerListener {

    private var tokenCount: Int = 0
    private var selectionActive: Boolean = false
    private var connection: MessageBusConnection? = null
    private var currentEditor: Editor? = null

    init {
        connection = project.messageBus.connect(this)
        val editorFactory = EditorFactory.getInstance()

        // Listeners existentes
        editorFactory.eventMulticaster.addCaretListener(this, this)
        editorFactory.eventMulticaster.addSelectionListener(this, this)
        editorFactory.eventMulticaster.addDocumentListener(this, this)

        // Nuevo listener para cambios de archivo
        connection?.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this)

        // Actualizar inmediatamente con el editor actual
        updateCurrentEditor()
    }

    override fun ID(): String = "TokenCountAIWidget"

    override fun getWidgetState(file: VirtualFile?): WidgetState {
        val text = "$tokenCount tokens"
        val tooltip = if (selectionActive) {
            "$tokenCount tokens (selection)"
        } else {
            "$tokenCount tokens (full file)"
        }
        return WidgetState(text, tooltip, true)
    }

    override fun createPopup(context: DataContext): ListPopup? = null

    override fun createInstance(project: Project): EditorBasedStatusBarPopup =
        TokenCountAIWidget(project)

    // FileEditorManagerListener methods
    override fun selectionChanged(event: FileEditorManagerEvent) {
        updateCurrentEditor()
    }

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        updateCurrentEditor()
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        updateCurrentEditor()
    }

    /**
     * Actualiza la referencia al editor actual y recalcula tokens
     */
    private fun updateCurrentEditor() {
        ApplicationManager.getApplication().invokeLater {
            val fileEditorManager = FileEditorManager.getInstance(project)
            val selectedEditor = fileEditorManager.selectedTextEditor

            if (selectedEditor != currentEditor) {
                currentEditor = selectedEditor
                selectedEditor?.let { editor ->
                    updateTokenCount(editor)
                } ?: run {
                    tokenCount = 0
                    selectionActive = false
                    update()
                }
            }
        }
    }

    override fun caretPositionChanged(event: CaretEvent) {
        // Solo actualizar si es el editor actual
        if (event.editor == currentEditor) {
            updateTokenCount(event.editor)
        }
    }

    override fun selectionChanged(e: SelectionEvent) {
        // Solo actualizar si es el editor actual
        if (e.editor == currentEditor) {
            updateTokenCount(e.editor)
        }
    }

    override fun documentChanged(event: DocumentEvent) {
        // Buscar el editor activo que corresponde a este documento
        val editors = EditorFactory.getInstance().getEditors(event.document)
        val activeEditor = editors.firstOrNull {
            it.project == project && it == currentEditor
        } ?: return

        updateTokenCount(activeEditor)
    }

    /**
     * Actualiza el conteo de tokens basado en la selección actual o el documento completo
     * @param editor El editor activo del cual obtener el texto
     */
    private fun updateTokenCount(editor: Editor) {
        try {
            val selectionModel = editor.selectionModel

            // Determinar si hay selección activa y obtener el texto correspondiente
            val text = if (selectionModel.hasSelection()) {
                selectionActive = true
                selectionModel.selectedText ?: ""
            } else {
                selectionActive = false
                editor.document.text
            }

            // Calcular tokens
            tokenCount = TokenCount.calculateTokens(text)

            // Actualizar el widget en el hilo de la UI
            ApplicationManager.getApplication().invokeLater {
                update()
            }

        } catch (e: Exception) {
            // En caso de error, resetear el contador
            tokenCount = 0
            selectionActive = false

            ApplicationManager.getApplication().invokeLater {
                update()
            }
        }
    }

    override fun dispose() {
        connection?.disconnect()
        currentEditor = null
        super.dispose()
    }
}