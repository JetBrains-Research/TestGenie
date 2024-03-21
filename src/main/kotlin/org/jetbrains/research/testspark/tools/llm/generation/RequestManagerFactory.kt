package org.jetbrains.research.testspark.tools.llm.generation

import com.intellij.openapi.project.Project
import org.jetbrains.research.testspark.core.generation.llm.network.RequestManager
import org.jetbrains.research.testspark.services.SettingsApplicationService
import org.jetbrains.research.testspark.settings.SettingsApplicationState
import org.jetbrains.research.testspark.tools.llm.SettingsArguments
import org.jetbrains.research.testspark.tools.llm.generation.grazie.GrazieRequestManager
import org.jetbrains.research.testspark.tools.llm.generation.openai.OpenAIRequestManager

interface RequestManagerFactory {
    fun getRequestManager(project: Project): RequestManager
}

class StandardRequestManagerFactory : RequestManagerFactory {
    private val settingsState: SettingsApplicationState
        get() = SettingsApplicationService.getInstance().state!!

    override fun getRequestManager(project: Project): RequestManager {
        return when (val platform = SettingsArguments.currentLLMPlatformName()) {
            settingsState.openAIName -> OpenAIRequestManager(project)
            settingsState.grazieName -> GrazieRequestManager(project)
            else -> throw IllegalStateException("Unknown selected platform: $platform")
        }
    }
}
