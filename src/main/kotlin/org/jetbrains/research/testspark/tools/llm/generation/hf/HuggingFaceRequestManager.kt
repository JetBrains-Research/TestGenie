package org.jetbrains.research.testspark.tools.llm.generation.hf

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.HttpRequests.HttpStatusException
import org.jetbrains.research.testspark.bundles.llm.LLMDefaultsBundle
import org.jetbrains.research.testspark.core.data.ChatUserMessage
import org.jetbrains.research.testspark.core.error.LlmError
import org.jetbrains.research.testspark.core.error.TestSparkError
import org.jetbrains.research.testspark.core.error.TestSparkResult
import org.jetbrains.research.testspark.core.monitor.ErrorMonitor
import org.jetbrains.research.testspark.core.progress.CustomProgressIndicator
import org.jetbrains.research.testspark.core.test.TestsAssembler
import org.jetbrains.research.testspark.tools.llm.LlmSettingsArguments
import org.jetbrains.research.testspark.tools.llm.error.LLMErrorManager
import org.jetbrains.research.testspark.tools.llm.generation.IJRequestManager
import java.net.HttpURLConnection

/**
 * A class to manage requests sent to large language models hosted on HuggingFace
 */
class HuggingFaceRequestManager(project: Project) : IJRequestManager(project) {
    private val url = "https://api-inference.huggingface.co/models/meta-llama/"

    // TODO: The user should be able to change these numbers in the plugin's settings
    private val topProbability = 0.9
    private val temperature = 0.9

    private val llmErrorManager = LLMErrorManager()

    override fun send(
        prompt: String,
        indicator: CustomProgressIndicator,
        testsAssembler: TestsAssembler,
        errorMonitor: ErrorMonitor,
    ): TestSparkResult<Unit, TestSparkError> {
        val httpRequest = HttpRequests.post(
            url + LlmSettingsArguments(project).getModel(),
            "application/json",
        ).tuner {
            it.setRequestProperty("Authorization", "Bearer $token")
        }

        // Add system prompt
        if (chatHistory.size == 1) {
            chatHistory[0] = ChatUserMessage(
                createInstructionPrompt(
                    chatHistory[0].content,
                ),
            )
        }

        val llmRequestBody = HuggingFaceRequestBody(chatHistory, Parameters(topProbability, temperature)).toMap()
        return try {
            httpRequest.connect {
                it.write(GsonBuilder().disableHtmlEscaping().create().toJson(llmRequestBody))
                when (val responseCode = (it.connection as HttpURLConnection).responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val text = it.reader.readLine()
                        val generatedTestCases = extractLLMGeneratedCode(
                            JsonParser.parseString(text).asJsonArray[0]
                                .asJsonObject["generated_text"].asString.trim(),
                        )
                        testsAssembler.consume(generatedTestCases)
                        TestSparkResult.Success(data = Unit)
                    }

                    HttpURLConnection.HTTP_INTERNAL_ERROR -> TestSparkResult.Failure(
                        error = LlmError.HttpInternalError()
                    )

                    HttpURLConnection.HTTP_BAD_REQUEST -> TestSparkResult.Failure(
                        error = LlmError.HttpError(httpCode = responseCode)
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
