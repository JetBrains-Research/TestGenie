package org.jetbrains.research.testspark.core.test.strategies

import org.jetbrains.research.testspark.core.data.JUnitVersion
import org.jetbrains.research.testspark.core.test.TestBodyPrinter
import org.jetbrains.research.testspark.core.test.OperationResult
import org.jetbrains.research.testspark.core.test.ErrorDetails
import org.jetbrains.research.testspark.core.test.data.TestCaseGeneratedByLLM
import org.jetbrains.research.testspark.core.test.data.TestLine
import org.jetbrains.research.testspark.core.test.data.TestLineType
import org.jetbrains.research.testspark.core.test.data.TestSuiteGeneratedByLLM

typealias TestCaseParseResult = OperationResult<TestCaseGeneratedByLLM>

class JUnitTestSuiteParserStrategy {
    companion object {
        fun parseJUnitTestSuite(
            rawText: String,
            junitVersion: JUnitVersion,
            importPattern: Regex,
            packageName: String,
            testNamePattern: String,
            printTestBodyStrategy: TestBodyPrinter,
        ): TestSuiteGeneratedByLLM? {
            if (rawText.isBlank()) {
                return null
            }

            try {
                val rawCode = if (rawText.contains("```")) rawText.split("```")[1] else rawText

                // save imports
                val imports = importPattern.findAll(rawCode)
                    .map { it.groupValues[0] }
                    .toMutableSet()

                // save RunWith
                val runWith: String = junitVersion.runWithAnnotationMeta.extract(rawCode) ?: ""

                val testSet: MutableList<String> = rawCode.split("@Test").toMutableList()

                // save annotations and pre-set methods
                val otherInfo: String = run {
                    val otherInfoList = testSet.removeAt(0).split("{").toMutableList()
                    otherInfoList.removeFirst()
                    val otherInfo = otherInfoList.joinToString("{").trimEnd() + "\n\n"
                    otherInfo.ifBlank { "" }
                }

                // Save the main test cases
                val testCases: MutableList<TestCaseGeneratedByLLM> = mutableListOf()
                val testCaseParser = JUnitTestCaseParser()

                testSet.forEach ca@{
                    val rawTest = "@Test$it"

                    val isLastTestCaseInTestSuite = (testCases.size == testSet.size - 1)
                    val result: TestCaseParseResult =
                        testCaseParser.parse(rawTest, isLastTestCaseInTestSuite, testNamePattern, printTestBodyStrategy)

                    if (result.error != null) {
                        println("WARNING: ${result.error}")
                        return@ca
                    }

                    val currentTest = result.content!!

                    // TODO: make logging work
                    // log.info("New test case: $currentTest")

                    testCases.add(currentTest)
                }

                val testSuite = TestSuiteGeneratedByLLM(
                    imports = imports,
                    packageName = packageName,
                    runWith = runWith,
                    otherInfo = otherInfo,
                    testCases = testCases,
                )

                return testSuite
            } catch (e: Exception) {
                return null
            }
        }
    }

    private class JUnitTestCaseParser {
        fun parse(
            rawTest: String,
            isLastTestCaseInTestSuite: Boolean,
            testNamePattern: String,
            printTestBodyStrategy: TestBodyPrinter,
        ): TestCaseParseResult {
            var expectedException = ""
            var throwsException = ""
            val testLines: MutableList<TestLine> = mutableListOf()

            // Get expected Exception
            if (rawTest.startsWith("@Test(expected =")) {
                expectedException = rawTest.split(")")[0].trim()
            }

            // Get unexpected exceptions
            /* Each test case should follow fun <testcase name> {...}
                Tests do not return anything so it is safe to consider that void always appears before test case name
             */
            if (!rawTest.contains(testNamePattern)) {
                return TestCaseParseResult(
                    error = ErrorDetails("The raw Test does not contain $testNamePattern:\n $rawTest")
                )
            }

            /**
             * The test definition is split into two parts:
             * [void|fun] <testcase name>() [throws <exception>] { ... }
             * The first part is the test definition prologue,
             * the second part is the test definition epilogue.
             *
             * Therefore, as an epilogue, we have everything after [void|fun].
             *
             * `limit = 2` is used to avoid additional splitting in case if the test
             * case name starts with the same word as the test definition prologue.
             */
            val testCaseEpilogue = rawTest.split(testNamePattern, limit = 2)[1]

            /**
             * Optional [throws <exception>] part is extracted from the test definition epilogue.
             */
            val interestingPartOfSignature = testCaseEpilogue
                .split("{")[0]
                .split("()")[1]
                .trim()

            if (interestingPartOfSignature.contains("throws")) {
                throwsException = interestingPartOfSignature.split("throws")[1].trim()
            }

            // Get test name
            val testName: String = testCaseEpilogue
                .split("()")[0]
                .trim()

            // Get test body and remove opening bracket
            var testBody = rawTest.split("{").toMutableList().apply { removeFirst() }
                .joinToString("{").trim()

            // remove closing bracket
            val tempList = testBody.split("}").toMutableList()
            tempList.removeLast()

            if (isLastTestCaseInTestSuite) {
                // it is the last test, thus we should remove another closing bracket
                if (tempList.isNotEmpty()) {
                    tempList.removeLast()
                } else {
                    println("WARNING: the final test does not have the enclosing bracket:\n $testBody")
                }
            }

            testBody = tempList.joinToString("}")

            // Save each line
            val rawLines = testBody.split("\n").toMutableList()
            rawLines.forEach { rawLine ->
                val line = rawLine.trim()

                val type: TestLineType = when {
                    line.startsWith("//") -> TestLineType.COMMENT
                    line.isBlank() -> TestLineType.BREAK
                    line.lowercase().startsWith("assert") -> TestLineType.ASSERTION
                    else -> TestLineType.CODE
                }

                testLines.add(TestLine(type, line))
            }

            val currentTest = TestCaseGeneratedByLLM(
                name = testName,
                expectedException = expectedException,
                throwsException = throwsException,
                lines = testLines,
                printTestBodyStrategy = printTestBodyStrategy,
            )

            return TestCaseParseResult(content = currentTest)
        }
    }
}
