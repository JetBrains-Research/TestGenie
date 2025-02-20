package org.jetbrains.research.testspark.core.error

sealed class LlmError(cause: Throwable? = null) : TestSparkError(cause) {
    class PromptTooLong() : LlmError()
    class EmptyLlmResponse() : LlmError()
    class TestSuiteParsingError() : LlmError()
    class GrazieNotAvailable() : LlmError()
    class FeedbackCycleCancelled() : LlmError()
    class NoCompilableTestCasesGenerated() : LlmError()
    class FailedToSaveTestFiles() : LlmError()
    class HttpInternalError() : LlmError()
    class HttpUnauthorized() : LlmError()
    class HttpStatusError(cause: Throwable? = null): LlmError()
    class HttpError(val httpCode: Int): LlmError()
    class GrazieHttpError(val error: String) : LlmError()
}