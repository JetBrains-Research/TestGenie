package org.jetbrains.research.testspark.tools.llm.test

/**
 *
 * Represents a test case generated by LLM.
 *
 * @property name The name of the test case.
 **/
data class TestCaseGeneratedByLLM(
    var name: String = "",
    var expectedException: String = "",
    var throwsException: String = "",
    var lines: MutableList<TestLine> = mutableListOf(),
) {

    /**
     * Compares this object to the specified object for equality.
     *
     * @param other the object to compare to.
     * @return true if the objects are equal, false otherwise.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TestCaseGeneratedByLLM

        if (name != other.name) return false
        if (expectedException != other.expectedException) return false
        return lines == other.lines
    }

    /**
     * Checks if the lines list is empty.
     *
     * @return true if the lines list is empty, false otherwise.
     */
    fun isEmpty(): Boolean {
        return (lines.size == 0)
    }

    /**
     * Returns the hash code value for this object.
     *
     * The hash code is calculated based on the `name` field, the `expectedException`
     * field, and the `lines` field. The algorithm used for calculating the hash
     * code is consistent with the `equals` method, meaning that objects that are
     * considered equal according to the `equals` method will also have the same
     * hash code.
     *
     * @return the hash code value for this object.
     */
    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + expectedException.hashCode()
        result = 31 * result + arrayListOf(lines).hashCode()
        return result
    }

    /**
     * Returns a string representation of the method's test case.
     *
     * @return The string representation of the test case.
     */
    override fun toString(): String {
        return printTestBody(initiateTestString(true))
    }

    /**
     * Returns a string representation of the method's test case (excluding the expected exception).
     *
     * @return The string representation of the test case (excluding the expected exception).
     */
    fun toStringWithoutExpectedException(): String {
        return printTestBody(initiateTestString(false))
    }

    /**
     * Initiate the string of test case
     *
     * @param includeExpectedException a boolean value to indicate whether the expected exception should be included.
     * @return a string containing the upper part of the test case.
     */
    private fun initiateTestString(includeExpectedException: Boolean): String {
        var testText = ""

        // Add test annotation
        testText += "\t@Test"

        // add expectedException if it exists
        if (expectedException.isNotBlank() &&
            includeExpectedException
        ) {
            testText += "${expectedException.replace("@Test", "")})"
        }

        return testText
    }

    /**
     * return a full test case body
     *
     * @param testInitiatedText a string containing the upper part of the test case.
     * @return a string containing the body of test case
     */
    private fun printTestBody(testInitiatedText: String): String {
        var testFullText = testInitiatedText

        // start writing the test signature
        testFullText += "\n\tpublic void $name() "

        // add throws exception if exists
        if (throwsException.isNotBlank()) {
            testFullText += "throws $throwsException"
        }

        // start writing the test lines
        testFullText += "{\n"

        // write each line
        lines.forEach { line ->
            testFullText += when (line.type) {
                TestLineType.BREAK -> "\t\t\n"
                else -> "\t\t${line.text}\n"
            }
        }

        // close test case
        testFullText += "\t}"

        return testFullText
    }

    /**
     * Removes trailing lines of type TestLineType.BREAK from the given list of lines.
     * The lines are removed from the end of the list until a non-break line is encountered.
     *
     * @param lines The list of lines to be reformatted.
     */
    fun reformat() {
        for (index in lines.indices.reversed()) {
            if (lines[index].type == TestLineType.BREAK) {
                lines.removeAt(index)
            } else {
                break
            }
        }
    }
}
