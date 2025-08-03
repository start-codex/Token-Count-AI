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
import java.util.concurrent.Future

class TokenCountAIWidget(project: Project) :
    EditorBasedStatusBarPopup(project, false),
    CaretListener, SelectionListener, DocumentListener, FileEditorManagerListener {

    private var tokenCount: Int = 0
    private var selectionActive: Boolean = false
    private var connection: MessageBusConnection? = null
    private var currentEditor: Editor? = null

    // Control de tareas asíncronas
    @Volatile private var calculationInProgress: Boolean = false
    private var currentCalculationTask: Future<*>? = null
    private val CALCULATION_DEBOUNCE_MS: Long = 300L // Reducido para mejor responsividad

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
        val text = if (calculationInProgress) {
            "Calculating..."
        } else {
            "$tokenCount tokens"
        }
        val tooltip = if (calculationInProgress) {
            "Calculating token count..."
        } else if (selectionActive) {
            "selection: $tokenCount tokens"
        } else {
            "$tokenCount tokens"
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
                // Cancelar cálculo anterior si hay cambio de editor
                cancelCurrentCalculation()

                currentEditor = selectedEditor
                selectedEditor?.let { editor ->
                    updateTokenCount(editor)
                } ?: run {
                    tokenCount = 0
                    selectionActive = false
                    calculationInProgress = false
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
     * Cancela el cálculo actual si existe
     */
    private fun cancelCurrentCalculation() {
        currentCalculationTask?.cancel(true)
        currentCalculationTask = null
        calculationInProgress = false
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

            // Cancelar cálculo anterior
            cancelCurrentCalculation()

            // Para textos pequeños, calcular inmediatamente
            if (text.length < 3_000) {
                tokenCount = TokenCount.calculateTokens(text)
                ApplicationManager.getApplication().invokeLater {
                    update()
                }
                return
            }

            // Para textos grandes, usar debouncing y cálculo asíncrono
            calculationInProgress = true

            // Actualizar UI inmediatamente para mostrar "Calculating..."
            ApplicationManager.getApplication().invokeLater {
                update()
            }

            // Ejecutar cálculo con debounce
            currentCalculationTask = ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    // Debounce: esperar un poco para evitar cálculos excesivos
                    Thread.sleep(CALCULATION_DEBOUNCE_MS)

                    // Verificar si la tarea fue cancelada durante el sleep
                    if (currentCalculationTask?.isCancelled == true) {
                        return@executeOnPooledThread
                    }

                    // Calcular tokens
                    val calculatedTokens = TokenCount.calculateTokens(text)

                    // Actualizar en el hilo de UI - CONDICIÓN CORREGIDA
                    ApplicationManager.getApplication().invokeLater {
                        // Solo actualizar si esta tarea sigue siendo la actual
                        if (calculationInProgress && currentCalculationTask?.isCancelled != true) {
                            tokenCount = calculatedTokens
                            calculationInProgress = false
                            update()
                        }
                    }
                } catch (e: InterruptedException) {
                    // Tarea interrumpida, no hacer nada
                } catch (e: Exception) {
                    // En caso de error, usar el último valor válido y resetear estado
                    ApplicationManager.getApplication().invokeLater {
                        calculationInProgress = false
                        update()
                    }
                }
            }

        } catch (e: Exception) {
            // En caso de error, resetear el contador
            tokenCount = 0
            selectionActive = false
            calculationInProgress = false

            ApplicationManager.getApplication().invokeLater {
                update()
            }
        }
    }

    override fun dispose() {
        cancelCurrentCalculation()
        connection?.disconnect()
        currentEditor = null
        super.dispose()
    }
}