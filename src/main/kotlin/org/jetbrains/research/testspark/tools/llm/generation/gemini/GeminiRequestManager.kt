package org.jetbrains.research.testspark.tools.llm.generation.gemini

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import org.jetbrains.research.testspark.core.monitor.ErrorMonitor
import org.jetbrains.research.testspark.core.progress.CustomProgressIndicator
import org.jetbrains.research.testspark.core.test.TestsAssembler
import org.jetbrains.research.testspark.tools.ToolUtils
import org.jetbrains.research.testspark.tools.llm.generation.TestSparkRequestManager
import java.net.URLConnection

class GeminiRequestManager(project: Project) : TestSparkRequestManager(project) {

    private val gson = GsonBuilder().create()

    override val url: String
        get() {
            val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models/"
            return "$baseUrl$llmModel:generateContent?key=$token"
        }

    override fun tuneRequest(connection: URLConnection) = Unit

    override fun assembleRequestBodyJson(): String {
        val messages = chatHistory.map { GeminiChatMessage(it.content) }
        val geminiRequest = GeminiRequest(listOf(GeminiRequestBody(messages)))
        return gson.toJson(geminiRequest)
    }

    override fun assembleResponse(
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
