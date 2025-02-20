package org.jetbrains.research.testspark.core.generation.llm

import org.jetbrains.research.testspark.core.generation.llm.network.RequestManager
import org.jetbrains.research.testspark.core.monitor.DefaultErrorMonitor
import org.jetbrains.research.testspark.core.monitor.ErrorMonitor
import org.jetbrains.research.testspark.core.progress.CustomProgressIndicator
import org.jetbrains.research.testspark.core.test.SupportedLanguage
import org.jetbrains.research.testspark.core.test.TestsAssembler
import org.jetbrains.research.testspark.core.test.data.TestSuiteGeneratedByLLM
import org.jetbrains.research.testspark.core.utils.javaPackagePattern
import org.jetbrains.research.testspark.core.utils.kotlinPackagePattern
import java.util.Locale

// TODO: find a better place for the below functions

/**
 * Retrieves the package declaration from the given test suite code for any language.
 *
 * @param testSuiteCode The generated code of the test suite.
 * @return The package name extracted from the test suite code, or an empty string if no package declaration was found.
 */
fun getPackageFromTestSuiteCode(testSuiteCode: String?, language: SupportedLanguage): String {
    testSuiteCode ?: return ""
    return when (language) {
        SupportedLanguage.Kotlin -> kotlinPackagePattern.find(testSuiteCode)?.groups?.get(1)?.value.orEmpty()
        SupportedLanguage.Java -> javaPackagePattern.find(testSuiteCode)?.groups?.get(1)?.value.orEmpty()
    }
}

/**
 * Retrieves the imports code from a given test suite code.
 *
 * @param testSuiteCode The test suite code from which to extract the imports code. If null, an empty string is returned.
 * @param classFQN The fully qualified name of the class to be excluded from the imports code. It will not be included in the result.
 * @return The imports code extracted from the test suite code. If no imports are found or the result is empty after filtering, an empty string is returned.
 */
fun getImportsCodeFromTestSuiteCode(testSuiteCode: String?, classFQN: String?): MutableSet<String> {
    testSuiteCode ?: return mutableSetOf()
    return testSuiteCode.replace("\r\n", "\n").split("\n").asSequence()
        .filter { it.contains("^import".toRegex()) }
        .filterNot { it.contains("evosuite".toRegex()) }
        .filterNot { it.contains("RunWith".toRegex()) }
        // classFQN will be null for the top level function
        .filterNot { classFQN != null && it.contains(classFQN.toRegex()) }
        .toMutableSet()
}

/**
 * Returns the generated class name for a given test case.
 *
 * @param testCaseName The test case name.
 * @return The generated class name as a string.
 */
fun getClassWithTestCaseName(testCaseName: String): String {
    val className = testCaseName.replaceFirstChar {
        if (it.isLowerCase()) {
            it.titlecase(Locale.getDefault())
        } else {
            it.toString()
        }
    }
    return "Generated$className"
}

/**
 * Sends a test modification request according to user's feedback.
 * After receiving the response, it tries to parse the generated test cases.
 *
 * @param testCase: The test that is requested to be modified
 * @param task: A string representing the requested task for test modification
 * @param indicator: A progress indicator object that represents the indication of the test generation progress.
 *
 * @return instance of TestSuiteGeneratedByLLM if the generated test cases are parsable, otherwise null.
 */
fun executeTestCaseModificationRequest(
    language: SupportedLanguage,
    testCase: String,
    task: String,
    indicator: CustomProgressIndicator,
    requestManager: RequestManager,
    testsAssembler: TestsAssembler,
    errorMonitor: ErrorMonitor = DefaultErrorMonitor(),
): TestSuiteGeneratedByLLM? {
    // Update Token information
    val prompt = buildString {
        append("For this test:\n ```\n ")
        append(testCase)
        append("\n```\nGenerate a SINGLE test method. Do not change class and method names.")
        append("\nPerform the following task:\n")
        append(task)
    }

    val packageName = getPackageFromTestSuiteCode(testCase, language)

    val response = requestManager.request(
        language,
        prompt,
        indicator,
        packageName,
        testsAssembler,
        isUserFeedback = true,
        errorMonitor,
    )

    return response.getDataOrNull()
}
