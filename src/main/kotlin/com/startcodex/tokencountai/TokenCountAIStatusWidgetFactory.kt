package com.startcodex.tokencountai

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

class TokenCountAIStatusWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = "TokenCountAIWidget"
    override fun getDisplayName(): String = "Token Count AI"
    override fun isAvailable(project: Project): Boolean = true;
    override fun createWidget(project: Project): StatusBarWidget = TokenCountAIWidget(project)
    override fun disposeWidget(widget: StatusBarWidget) {}
    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}