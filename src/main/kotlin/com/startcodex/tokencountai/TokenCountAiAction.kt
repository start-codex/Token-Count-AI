package com.startcodex.tokencountai

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.Messages

class TokenCountAiAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor: Editor = e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR) ?: return
        val document = editor.document
        val selectedText = editor.selectionModel.selectedText
        val textToCount = selectedText ?: document.text

        val tokens = TokenCount.calculateTokens(textToCount)

        Messages.showInfoMessage(
            "Token in ${if (selectedText != null) "selection" else "file"}: $tokens",
            "Token Count AI"
        )
    }
}