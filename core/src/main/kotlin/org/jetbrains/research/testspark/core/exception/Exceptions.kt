package org.jetbrains.research.testspark.core.exception

/**
 * Represents custom exceptions within TestSpark.
 *
 * This class serves as a base class for specific exceptions.
 *
 * @param message A descriptive message explaining the error that led to the exception.
 */
sealed class TestSparkException(message: String) : RuntimeException(message)

/**
 * Custom exception to indicate that the Kotlin compiler was not found.
 *
 * @param message A descriptive message explaining the error.
 */
class KotlinCompilerNotFoundException(message: String) : TestSparkException(message)

/**
 * Custom exception to indicate that the Java compiler was not found.
 *
 * @param message A descriptive message explaining the error.
 */
class JavaCompilerNotFoundException(message: String) : TestSparkException(message)

/**
 * Represents an exception thrown when a required Java SDK is missing in the system.
 *
 * @param message A descriptive message explaining the specific error that led to this exception.
 */
class JavaSDKMissingException(message: String) : TestSparkException(message)

/**
 * Represents an exception thrown when a class file could not be found in the same path after the code compilation.
 *
 * @param message A descriptive message explaining the error
 */
class ClassFileNotFoundException(message: String) : TestSparkException(message)
