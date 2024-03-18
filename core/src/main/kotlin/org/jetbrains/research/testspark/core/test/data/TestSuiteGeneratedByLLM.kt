package org.jetbrains.research.testspark.core.test.data

/**
 * Represents a test suite generated by LLM.
 *
 * @property imports The set of import statements in the test suite.
 * @property packageString The package string of the test suite.
 * @property testCases The list of test cases in the test suite.
 */
data class TestSuiteGeneratedByLLM(
    var imports: Set<String> = emptySet(),
    var packageString: String = "",
    var runWith: String = "",
    var otherInfo: String = "",
    var testCases: MutableList<TestCaseGeneratedByLLM> = mutableListOf(),
) {
    var testFileName: String = "GeneratedTest"
        private set

    /**
     * Checks if the testCases collection is empty.
     *
     * @return `true` if the testCases collection is empty, `false` otherwise.
     */
    fun isEmpty(): Boolean {
        return testCases.isEmpty()
    }

    /**
     * Sets the test cases for this object.
     *
     * @param testCases the list of test cases to be set
     */
    fun updateTestCases(testCases: MutableList<TestCaseGeneratedByLLM>) {
        this.testCases = testCases
    }

    /**
     * Reformat method for TestSuiteGeneratedByLLM class.
     *
     * This method iterates over each test case in the testCases list and invokes the reformat method on each test case.
     * After that, it removes any empty test cases from the list.
     *
     * @return The current instance of TestSuiteGeneratedByLLM.
     */
    fun reformat(): TestSuiteGeneratedByLLM {
        testCases.forEach {
            it.reformat()
        }

        // remove empty test cases
        testCases.removeIf { testCase -> testCase.isEmpty() }

        return this
    }

    /**
     * Sets the test file name.
     *
     * @param testFileName The file name of the test file to be set.
     */
    fun setTestFileName(testFileName: String) {
        this.testFileName = testFileName
    }
}