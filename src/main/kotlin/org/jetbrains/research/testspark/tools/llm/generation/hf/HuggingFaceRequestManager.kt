package org.jetbrains.research.testspark.tools.llm.generation.hf

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import org.jetbrains.research.testspark.bundles.llm.LLMDefaultsBundle
import org.jetbrains.research.testspark.core.data.ChatUserMessage
import org.jetbrains.research.testspark.core.error.LlmError
import org.jetbrains.research.testspark.core.monitor.ErrorMonitor
import org.jetbrains.research.testspark.core.progress.CustomProgressIndicator
import org.jetbrains.research.testspark.core.test.TestsAssembler
import org.jetbrains.research.testspark.tools.llm.generation.TestSparkRequestManager
import java.net.HttpURLConnection
import java.net.URLConnection

/**
 * A class to manage requests sent to large language models hosted on HuggingFace
 */
class HuggingFaceRequestManager(project: Project) : TestSparkRequestManager(project) {

    // TODO: The user should be able to change these numbers in the plugin's settings
    private val topProbability = 0.9
    private val temperature = 0.9

    override val url: String
        get() {
            val baseUrl ="https://api-inference.huggingface.co/models/meta-llama/"
            return "$baseUrl$llmModel"
        }

    override fun assembleRequestBodyJson(): String {
        if (chatHistory.size == 1) {
            chatHistory[0] = ChatUserMessage(
                createInstructionPrompt(
                    chatHistory[0].content,
                ),
            )
        }
        val llmRequestBody =
            HuggingFaceRequestBody(chatHistory, Parameters(topProbability, temperature)).toMap()
        return GsonBuilder().disableHtmlEscaping().create().toJson(llmRequestBody)
    }

    override fun tuneRequest(connection: URLConnection) {
        connection.setRequestProperty("Authorization", "Bearer $token")
    }

    override fun assembleResponse(
        httpRequest: HttpRequests.Request,
        testsAssembler: TestsAssembler,
        indicator: CustomProgressIndicator,
        errorMonitor: ErrorMonitor
    ) {
        val text = httpRequest.reader.readLine()
        val generatedTestCases = extractLLMGeneratedCode(
            JsonParser.parseString(text).asJsonArray[0]
                .asJsonObject["generated_text"].asString.trim(),
        )
        testsAssembler.consume(generatedTestCases)
    }

    override fun mapHttpCodeToError(httpCode: Int) = when(httpCode) {
        HttpURLConnection.HTTP_INTERNAL_ERROR -> LlmError.HttpInternalError()
        HttpURLConnection.HTTP_BAD_REQUEST -> LlmError.HuggingFaceServerError()
        else -> LlmError.HttpError(httpCode)
    }

    /**
     * Creates the required prompt for Llama models. For more details see:
     * https://huggingface.co/blog/llama2#how-to-prompt-llama-2
     */
    private fun createInstructionPrompt(userMessage: String): String {
        // TODO: This is Llama-specific and should support other LLMs hosted on HF too.
        return "<s>[INST] <<SYS>> ${LLMDefaultsBundle.get("huggingFaceInitialSystemPrompt")} <</SYS>> $userMessage [/INST]"
    }

    /**
     * Extracts code blocks in LLMs' response.
     * Also, it handles the cases where the LLM-generated code does not end with ```
     */
    private fun extractLLMGeneratedCode(text: String): String {
        // TODO: This method should support other languages other than Java.
        val modifiedText = text.replace("```java", "```").replace("````", "```")
        val tripleTickBlockIndex = modifiedText.indexOf("```")
        val codePart = modifiedText.substring(tripleTickBlockIndex + 3)
        val lines = codePart.lines()
        val filteredLines = lines.filter { line -> line != "```" }
        val code = filteredLines.joinToString("\n")
        return "```\n$code\n```"
    }
}
