package org.jetbrains.research.testspark.core.error

sealed interface TestSparkResult<out D, out E: TestSparkError> {
    data class Success<out D>(val data: D): TestSparkResult<D, Nothing>
    data class Failure<out E: TestSparkError>(val error: E): TestSparkResult<Nothing, E>

    fun getDataOrNull(): D? = if (this is Success) data else null

    fun <R> mapData(transform: (D) -> R): TestSparkResult<R, E> {
        return when (this) {
            is Success -> Success(transform(data))
            is Failure -> Failure(error)
        }
    }

    fun <R : TestSparkError> mapError(transform: (E) -> R): TestSparkResult<D, R> {
        return when (this) {
            is Success -> Success(data)
            is Failure -> Failure(transform(error))
        }
    }
}
