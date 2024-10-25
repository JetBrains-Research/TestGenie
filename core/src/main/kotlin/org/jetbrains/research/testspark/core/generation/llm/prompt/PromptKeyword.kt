package org.jetbrains.research.testspark.core.generation.llm.prompt

enum class PromptKeyword(val description: String, val mandatory: Boolean) {
    NAME("The name of the code under test (Class name, method name, line number)", true),
    CODE("The code under test (Class, method, or line)", true),
    LANGUAGE("Programming language of the project under test (only Java supported at this point)", true),
    TESTING_PLATFORM(
        "Testing platform used in the project (Only JUnit 4 is supported at this point)",
        true,
    ),
    MOCKING_FRAMEWORK(
        "Mock framework that can be used in generated test (Only Mockito is supported at this point)",
        false,
    ),
    METHODS("Signature of methods used in the code under tests", false),
    POLYMORPHISM("Polymorphism relations between classes involved in the code under test", false),
    TEST_SAMPLE("Test samples for LLM for test generation", false),
    ;

    fun getOffsets(prompt: String): Pair<Int, Int>? {
        val textToHighlight = variable
        if (!prompt.contains(textToHighlight)) {
            return null
        }

        val startOffset = prompt.indexOf(textToHighlight)
        val endOffset = startOffset + textToHighlight.length
        return Pair(startOffset, endOffset)
    }

    /**
     * Returns a keyword's text (i.e., its name) with a `$` attached at the start.
     *
     * Inside a prompt template every keyword is used as `$KEYWORD_NAME`.
     * Therefore, this property encapsulates the keyword's representation in a prompt.
     */
    val variable: String
        get() = "\$${this.name}"
}
