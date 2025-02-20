package org.jetbrains.research.testspark.tools.llm.generation.gemini

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.HttpRequests.HttpStatusException
import org.jetbrains.research.testspark.core.error.LlmError
import org.jetbrains.research.testspark.core.error.TestSparkError
import org.jetbrains.research.testspark.core.error.TestSparkResult
import org.jetbrains.research.testspark.core.monitor.ErrorMonitor
import org.jetbrains.research.testspark.core.progress.CustomProgressIndicator
import org.jetbrains.research.testspark.core.test.TestsAssembler
import org.jetbrains.research.testspark.tools.ToolUtils
import org.jetbrains.research.testspark.tools.llm.LlmSettingsArguments
import org.jetbrains.research.testspark.tools.llm.error.LLMErrorManager
import org.jetbrains.research.testspark.tools.llm.generation.IJRequestManager
import java.net.HttpURLConnection

class GeminiRequestManager(project: Project) : IJRequestManager(project) {
    private val url = "https://generativelanguage.googleapis.com/v1beta/models/"
    private val gson = GsonBuilder().create()

    private val llmErrorManager = LLMErrorManager()

    override fun send(
        prompt: String,
        indicator: CustomProgressIndicator,
        testsAssembler: TestsAssembler,
        errorMonitor: ErrorMonitor,
    ): TestSparkResult<Unit, TestSparkError> {
        val model = LlmSettingsArguments(project).getModel()
        val apiURL = "$url$model:generateContent?key=$token"
        val httpRequest = HttpRequests.post(apiURL, "application/json")

        val messages = chatHistory.map {
            GeminiChatMessage(it.content)
        }

        val geminiRequest = GeminiRequest(listOf(GeminiRequestBody(messages)))

        return try {
            httpRequest.connect { request ->
                request.write(gson.toJson(geminiRequest))

                val connection = request.connection as HttpURLConnection

                when (val responseCode = connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> TestSparkResult.Success(
                        data = assembleGeminiResponse(request, testsAssembler, indicator, errorMonitor)
                    )

                    HttpURLConnection.HTTP_INTERNAL_ERROR -> TestSparkResult.Failure(
                        error = LlmError.HttpInternalError()
                    )

                    HttpURLConnection.HTTP_BAD_REQUEST -> TestSparkResult.Failure(
                        error = LlmError.PromptTooLong()
                    )

                    HttpURLConnection.HTTP_UNAUTHORIZED -> TestSparkResult.Failure(
                        error = LlmError.HttpUnauthorized()
                    )

                    else -> TestSparkResult.Failure(
                        error = LlmError.HttpError(httpCode = responseCode)
                    )
                }
            }
        } catch (e: HttpStatusException) {
            TestSparkResult.Failure(LlmError.HttpStatusError(e))
        }
    }

    private fun assembleGeminiResponse(
        httpRequest: HttpRequests.Request,
        testsAssembler: TestsAssembler,
        indicator: CustomProgressIndicator,
        errorMonitor: ErrorMonitor,
    ) {
        while (true) {
            if (ToolUtils.isProcessCanceled(errorMonitor, indicator)) return

            val text = httpRequest.reader.readText()
            val result =
                gson.fromJson(
                    JsonParser.parseString(text)
                        .asJsonObject["candidates"]
                        .asJsonArray[0].asJsonObject,
                    GeminiReply::class.java,
                )

            testsAssembler.consume(result.content.parts[0].text)

            if (result.finishReason == "STOP") break
        }

        log.debug { testsAssembler.getContent() }
    }
}
