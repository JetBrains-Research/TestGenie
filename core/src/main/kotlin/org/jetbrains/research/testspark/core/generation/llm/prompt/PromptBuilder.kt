package org.jetbrains.research.testspark.core.generation.llm.prompt

import org.jetbrains.research.testspark.core.data.ClassType
import org.jetbrains.research.testspark.core.generation.llm.prompt.configuration.ClassRepresentation
import java.util.EnumMap

/**
 * Builds prompts by populating a template with keyword values
 * and validates that all mandatory keywords are provided.
 *
 * @property promptTemplate The template string for the prompt.
 */
class PromptBuilder(private val promptTemplate: String) {
    private val insertedKeywordValues: EnumMap<PromptKeyword, String> = EnumMap(PromptKeyword::class.java)

    // collect all the keywords present in the prompt template
    private val templateKeywords: List<PromptKeyword> = buildList {
        for (keyword in PromptKeyword.entries) {
            if (promptTemplate.contains(keyword.variable)) {
                add(keyword)
            }
        }
    }

    /**
     * Builds the prompt by populating the template with the inserted values
     * and validating that all mandatory keywords were provided.
     *
     * @return The built prompt.
     * @throws IllegalStateException if a mandatory keyword is not present in the template.
     */
    fun build(): String {
        var populatedPrompt = promptTemplate

        // populate the template with the inserted values
        for ((keyword, value) in insertedKeywordValues.entries) {
            populatedPrompt = populatedPrompt.replace(keyword.variable, value, ignoreCase = false)
        }

        // validate that all mandatory keywords were provided
        for (keyword in templateKeywords) {
            if (!insertedKeywordValues.contains(keyword) && keyword.mandatory) {
                throw IllegalStateException("The prompt must contain ${keyword.name} keyword")
            }
        }

        return populatedPrompt
    }

    /**
     * Inserts a keyword and its corresponding value into the prompt template.
     * If the keyword is marked as mandatory and not present in the template, an IllegalArgumentException is thrown.
     *
     * @param keyword The keyword to be inserted.
     * @param value The value corresponding to the keyword.
     * @throws IllegalArgumentException if a mandatory keyword is not present in the template.
     */
    private fun insert(keyword: PromptKeyword, value: String) {
        if (!templateKeywords.contains(keyword) && keyword.mandatory) {
            throw IllegalArgumentException("Prompt template does not contain mandatory ${keyword.name}")
        }
        insertedKeywordValues[keyword] = value
    }

    fun insertLanguage(language: String) = apply {
        insert(PromptKeyword.LANGUAGE, language)
    }

    fun insertName(classDisplayName: String) = apply {
        insert(PromptKeyword.NAME, classDisplayName)
    }

    fun insertTestingPlatform(testingPlatformName: String) = apply {
        insert(PromptKeyword.TESTING_PLATFORM, testingPlatformName)
    }

    fun insertMockingFramework(mockingFrameworkName: String) = apply {
        insert(PromptKeyword.MOCKING_FRAMEWORK, mockingFrameworkName)
    }

    /**
     * Inserts the code under test and its related superclass code into the prompt template.
     *
     * @param codeFullText The full text of the code under test.
     * @param classesToTest The list of ClassRepresentation objects representing the classes involved in the code under test.
     * @return The modified prompt builder.
     */
    fun insertCodeUnderTest(codeFullText: String, classesToTest: List<ClassRepresentation>) = apply {
        val fullText = StringBuilder("```\n${codeFullText}\n```\n")

        for (i in 2..classesToTest.size) {
            val subClass = classesToTest[i - 2]
            val superClass = classesToTest[i - 1]

            fullText.append("${subClass.qualifiedName} extends ${superClass.qualifiedName}. ")
                .append("The source code of ${superClass.qualifiedName} is:\n```\n${superClass.fullText}\n")
                .append("```\n")
        }

        insert(PromptKeyword.CODE, fullText.toString())
    }

    fun insertMethodsSignatures(interestingClasses: List<ClassRepresentation>) = apply {
        val fullText = StringBuilder()

        if (interestingClasses.isNotEmpty()) {
            fullText.append("Here are some information about other methods and classes used by the class under test. Only use them for creating objects, not your own ideas.\n")
        }

        for (interestingClass in interestingClasses) {
            if (interestingClass.qualifiedName.startsWith("java") ||
                interestingClass.qualifiedName.startsWith("kotlin")
            ) {
                continue
            }

            fullText.append("=== methods in ${interestingClass.qualifiedName}:\n")

            for (method in interestingClass.allMethods) {
                // TODO: checks for java methods should be done by a caller to make
                //       this class as abstract and language agnostic as possible.
                if (method.containingClassQualifiedName.startsWith("java") ||
                    method.containingClassQualifiedName.startsWith("kotlin")
                ) {
                    continue
                }

                fullText.append(" - ${method.signature}\n")
            }
        }

        insert(PromptKeyword.METHODS, fullText.toString())
    }

    fun insertPolymorphismRelations(
        polymorphismRelations: Map<ClassRepresentation, List<ClassRepresentation>>,
    ) = apply {
        val fullText = StringBuilder()

        if (polymorphismRelations.isNotEmpty()) {
            fullText.append("Use the following polymorphic relationships of classes present in the project. Use them for instantiation when necessary. Do not mock classes if an instantiation of a sub-class is applicable.\n\n")
        }

        for (entry in polymorphismRelations) {
            for (currentSubClass in entry.value) {
                val subClassTypeName = when (currentSubClass.classType) {
                    ClassType.INTERFACE -> "an interface implementing"
                    ClassType.ABSTRACT_CLASS -> "an abstract sub-class of"
                    ClassType.CLASS -> "a sub-class of"
                    ClassType.DATA_CLASS -> "a sub data class of"
                    ClassType.INLINE_VALUE_CLASS -> "a sub inline value class class of"
                    ClassType.OBJECT -> "a sub object of"
                }
                fullText.append("${currentSubClass.qualifiedName} is $subClassTypeName ${entry.key.qualifiedName}.\n")
            }
        }

        insert(PromptKeyword.POLYMORPHISM, fullText.toString())
    }

    fun insertTestSample(testSamplesCode: String) = apply {
        var fullText = testSamplesCode
        if (fullText.isNotBlank()) {
            fullText = "Use this test samples:\n$fullText\n"
        }

        insert(PromptKeyword.TEST_SAMPLE, fullText)
    }
}
