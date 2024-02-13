package org.jetbrains.research.testspark.tools.llm.generation.grazie

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import org.jetbrains.research.testspark.bundles.TestSparkBundle
import org.jetbrains.research.testspark.bundles.TestSparkDefaultsBundle
import org.jetbrains.research.testspark.tools.llm.SettingsArguments
import org.jetbrains.research.testspark.tools.llm.error.LLMErrorManager
import org.jetbrains.research.testspark.tools.llm.generation.RequestManager
import org.jetbrains.research.testspark.tools.llm.generation.TestsAssembler

class GrazieRequestManager : RequestManager() {
    override fun send(
        prompt: String,
        indicator: ProgressIndicator,
        project: Project,
        llmErrorManager: LLMErrorManager,
    ): Pair<SendResult, TestsAssembler> {
        var testsAssembler = TestsAssembler(project, indicator)
        var sendResult = SendResult.OK

        try {
            val className = "org.jetbrains.research.grazie.Request"
            val request: GrazieRequest = Class.forName(className).getDeclaredConstructor().newInstance() as GrazieRequest

            var model = ""
            for (llmPlatform in SettingsArguments.llmPlatforms()) {
                if (llmPlatform.name == TestSparkDefaultsBundle.defaultValue("grazie")) model = llmPlatform.model
            }

            val requestResult = request.request(token, getMessages(), model, TestsAssembler(project, indicator))
            val requestError = requestResult.first

            if (requestError.isNotEmpty()) {
                with(requestError) {
                    when {
                        contains("invalid: 401") -> {
                            llmErrorManager.errorProcess(
                                TestSparkBundle.message("wrongToken"),
                                project,
                            )
                            sendResult = SendResult.OTHER
                        }

                        contains("invalid: 413 Payload Too Large") -> {
                            llmErrorManager.warningProcess(
                                TestSparkBundle.message("tooLongPrompt"),
                                project,
                            )
                            sendResult = SendResult.TOOLONG
                        }

                        else -> {
                            llmErrorManager.errorProcess(requestError, project)
                            sendResult = SendResult.OTHER
                        }
                    }
                }
            } else {
                testsAssembler = requestResult.second
            }
        } catch (e: ClassNotFoundException) {
            llmErrorManager.errorProcess(TestSparkBundle.message("grazieError"), project)
        }

        return Pair(sendResult, testsAssembler)
    }

    private fun getMessages(): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        chatHistory.forEach {
            result.add(Pair(it.role, it.content))
        }
        return result
    }
}