package org.jetbrains.research.testspark.tools.llm.generation.grazie

import com.intellij.openapi.project.Project
import org.jetbrains.research.testspark.core.data.ChatMessage
import org.jetbrains.research.testspark.core.error.LlmError
import org.jetbrains.research.testspark.core.error.TestSparkError
import org.jetbrains.research.testspark.core.error.TestSparkResult
import org.jetbrains.research.testspark.core.monitor.ErrorMonitor
import org.jetbrains.research.testspark.core.progress.CustomProgressIndicator
import org.jetbrains.research.testspark.core.test.TestsAssembler
import org.jetbrains.research.testspark.tools.llm.LlmSettingsArguments
import org.jetbrains.research.testspark.tools.llm.generation.IJRequestManager

class GrazieRequestManager(project: Project) : IJRequestManager(project) {
    override fun send(
        prompt: String,
        indicator: CustomProgressIndicator,
        testsAssembler: TestsAssembler,
        errorMonitor: ErrorMonitor,
    ): TestSparkResult<Unit, TestSparkError> {
        return try {
            val className = "org.jetbrains.research.grazie.Request"
            val request: GrazieRequest = Class.forName(className).getDeclaredConstructor().newInstance() as GrazieRequest

            val requestError = request.request(token, getMessages(), LlmSettingsArguments(project).getModel(), testsAssembler)

            if (requestError.isNotEmpty()) {
                with(requestError) {
                    when {
                        contains("invalid: 401") -> TestSparkResult.Failure(
                            error = LlmError.HttpUnauthorized()
                        )

                        contains("invalid: 413 Payload Too Large") -> TestSparkResult.Failure(
                            error = LlmError.PromptTooLong()
                        )

                        else -> TestSparkResult.Failure(
                            error = LlmError.GrazieHttpError(requestError)
                        )
                    }
                }
            } else {
                TestSparkResult.Success(data = Unit)
            }
        } catch (_: ClassNotFoundException) {
            TestSparkResult.Failure(error = LlmError.GrazieNotAvailable())
        }
    }

    private fun getMessages(): List<Pair<String, String>> {
        return chatHistory.map {
            val role = when (it.role) {
                ChatMessage.ChatRole.User -> "user"
                ChatMessage.ChatRole.Assistant -> "assistant"
            }
            (role to it.content)
        }
    }
}
