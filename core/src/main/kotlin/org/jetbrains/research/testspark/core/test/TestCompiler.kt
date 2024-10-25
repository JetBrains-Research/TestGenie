package org.jetbrains.research.testspark.core.test

import org.jetbrains.research.testspark.core.test.data.TestCaseGeneratedByLLM
import org.jetbrains.research.testspark.core.utils.DataFilesUtil

data class TestCasesCompilationResult(
    val allTestCasesCompilable: Boolean,
    val compilableTestCases: MutableSet<TestCaseGeneratedByLLM>,
)

data class ExecutionResult(
    val exitCode: Int,
    val executionMessage: String,
) {
    fun isSuccessful(): Boolean = exitCode == 0
}

abstract class TestCompiler(libPaths: List<String>, junitLibPaths: List<String>) {
    val separator = DataFilesUtil.classpathSeparator
    val dependencyLibPath = libPaths.joinToString(separator.toString())
    val junitPath = junitLibPaths.joinToString(separator.toString())
    val commonPath = "$junitPath${separator}$dependencyLibPath$separator"

    /**
     * Compiles a list of test cases and returns the compilation result.
     *
     * @param generatedTestCasesPaths A list of file paths where the generated test cases are located.
     * @param buildPath All the directories where the compiled code of the project under test is saved. This path is used as a classpath to run each test case.
     * @param testCases A mutable list of `TestCaseGeneratedByLLM` objects representing the test cases to be compiled.
     * @param workingDir The path of the directory that contains package directories of the code to compile
     * @return A `TestCasesCompilationResult` object containing the overall compilation success status and a set of compilable test cases.
     */
    fun compileTestCases(
        generatedTestCasesPaths: List<String>,
        buildPath: String,
        testCases: MutableList<TestCaseGeneratedByLLM>,
        workingDir: String,
    ): TestCasesCompilationResult {
        var allTestCasesCompilable = true
        val compilableTestCases: MutableSet<TestCaseGeneratedByLLM> = mutableSetOf()

        for (index in generatedTestCasesPaths.indices) {
            val compilable = compileCode(generatedTestCasesPaths[index], buildPath, workingDir).isSuccessful()
            allTestCasesCompilable = allTestCasesCompilable && compilable
            if (compilable) {
                compilableTestCases.add(testCases[index])
            }
        }

        return TestCasesCompilationResult(allTestCasesCompilable, compilableTestCases)
    }

    /**
     * Compiles the code at the specified path using the provided project build path.
     *
     * @param path The path of the code file to compile.
     * @param projectBuildPath All the directories where the compiled code of the project under test is saved. This path is used as a classpath to run each test case.
     * @param workingDir The path of the directory that contains package directories of the code to compile
     * @return A pair containing a boolean value indicating whether the compilation was successful (true) or not (false),
     *         and a string message describing any error encountered during compilation.
     */
    abstract fun compileCode(path: String, projectBuildPath: String, workingDir: String): ExecutionResult

    /**
     * Generates the path for the command by concatenating the necessary paths.
     *
     * @param buildPath The path of the build file.
     * @return The generated path as a string.
     */
    abstract fun getClassPaths(buildPath: String): String
}
