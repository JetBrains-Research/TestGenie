package org.jetbrains.research.testspark.tools.llm.generation.openai

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import org.jetbrains.research.testspark.core.data.ChatMessage
import org.jetbrains.research.testspark.core.monitor.ErrorMonitor
import org.jetbrains.research.testspark.core.progress.CustomProgressIndicator
import org.jetbrains.research.testspark.core.test.TestsAssembler
import org.jetbrains.research.testspark.tools.ToolUtils
import org.jetbrains.research.testspark.tools.llm.generation.TestSparkRequestManager
import java.net.URLConnection

class OpenAIRequestManager(project: Project) : TestSparkRequestManager(project) {

    override val url = "https://api.openai.com/v1/chat/completions"

    override fun tuneRequest(connection: URLConnection) {
        connection.setRequestProperty("Authorization", "Bearer $token")
    }

    override fun assembleRequestBodyJson(): String {
        val messages = chatHistory.map {
            val role = when (it.role) {
                ChatMessage.ChatRole.User -> "user"
                ChatMessage.ChatRole.Assistant -> "assistant"
            }
            OpenAIChatMessage(role, it.content)
        }
        val llmRequestBody = OpenAIRequestBody(llmModel, messages)
        return GsonBuilder().create().toJson(llmRequestBody)
    }

    override fun assembleResponse(
        httpRequest: HttpRequests.Request,
        testsAssembler: TestsAssembler,
        indicator: CustomProgressIndicator,
        errorMonitor: ErrorMonitor,
    ) {
        while (true) {
            if (ToolUtils.isProcessCanceled(errorMonitor, indicator)) return

            var text = httpRequest.reader.readLine()
            if (text.isEmpty()) continue
            text = text.removePrefix("data: ")

            val choices =
                Gson().fromJson(
                    JsonParser.parseString(text)
                        .asJsonObject["choices"]
                        .asJsonArray[0].asJsonObject,
                    OpenAIChoice::class.java,
                )
            if (choices.finishedReason == "stop") break

            testsAssembler.consume(choices.delta.content)
        }
        log.debug { testsAssembler.getContent() }
    }
}
