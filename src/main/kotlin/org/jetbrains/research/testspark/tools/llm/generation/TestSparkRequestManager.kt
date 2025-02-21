package org.jetbrains.research.testspark.tools.llm.generation

import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.HttpRequests.HttpStatusException
import org.jetbrains.research.testspark.core.error.LlmError
import org.jetbrains.research.testspark.core.error.TestSparkError
import org.jetbrains.research.testspark.core.error.TestSparkResult
import org.jetbrains.research.testspark.core.generation.llm.network.RequestManager
import org.jetbrains.research.testspark.core.monitor.ErrorMonitor
import org.jetbrains.research.testspark.core.progress.CustomProgressIndicator
import org.jetbrains.research.testspark.core.test.TestsAssembler
import org.jetbrains.research.testspark.tools.llm.LlmSettingsArguments
import java.net.HttpURLConnection
import java.net.URLConnection

abstract class TestSparkRequestManager(project: Project) : RequestManager(
    token = LlmSettingsArguments(project).getToken(),
    llmModel = LlmSettingsArguments(project).getModel(),
) {
    protected abstract val url: String

    protected abstract fun assembleRequestBodyJson(): String

    /** Set headers, tokens, etc. */
    protected abstract fun tuneRequest(connection: URLConnection)

    protected abstract fun assembleResponse(
        httpRequest: HttpRequests.Request,
        testsAssembler: TestsAssembler,
        indicator: CustomProgressIndicator,
        errorMonitor: ErrorMonitor,
    )

    protected open fun mapHttpCodeToError(httpCode: Int): TestSparkError = when(httpCode) {
        HttpURLConnection.HTTP_INTERNAL_ERROR -> LlmError.HttpInternalError()
        HttpURLConnection.HTTP_BAD_REQUEST -> LlmError.PromptTooLong()
        HttpURLConnection.HTTP_UNAUTHORIZED -> LlmError.HttpUnauthorized()
        else -> LlmError.HttpError(httpCode)
    }

    override fun send(
        prompt: String,
        indicator: CustomProgressIndicator,
        testsAssembler: TestsAssembler,
        errorMonitor: ErrorMonitor
    ): TestSparkResult<Unit, TestSparkError> = try {
        HttpRequests
            .post(url, "application/json")
            .tuner { tuneRequest(it) }
            .connect { request ->
                request.write(assembleRequestBodyJson())
                val connection = request.connection as HttpURLConnection
                when (val responseCode = connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> TestSparkResult.Success(
                        data = assembleResponse(request, testsAssembler, indicator, errorMonitor)
                    )
                    else -> TestSparkResult.Failure(mapHttpCodeToError(responseCode))
                }
            }
    } catch (e: HttpStatusException) {
        TestSparkResult.Failure(LlmError.HttpStatusError(e))
    }
}
