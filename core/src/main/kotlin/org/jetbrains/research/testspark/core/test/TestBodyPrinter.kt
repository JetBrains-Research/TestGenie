package org.jetbrains.research.testspark.core.test

import org.jetbrains.research.testspark.core.test.data.TestLine

interface TestBodyPrinter {
    fun printTestBody(
        testInitiatedText: String,
        lines: MutableList<TestLine>,
        throwsException: String,
        name: String,
    ): String
}