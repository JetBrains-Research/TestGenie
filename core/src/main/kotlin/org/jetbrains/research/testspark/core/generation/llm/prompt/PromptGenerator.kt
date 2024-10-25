package org.jetbrains.research.testspark.core.generation.llm.prompt

import org.jetbrains.research.testspark.core.generation.llm.prompt.configuration.ClassRepresentation
import org.jetbrains.research.testspark.core.generation.llm.prompt.configuration.MethodRepresentation
import org.jetbrains.research.testspark.core.generation.llm.prompt.configuration.PromptGenerationContext
import org.jetbrains.research.testspark.core.generation.llm.prompt.configuration.PromptTemplates

class PromptGenerator(
    private val context: PromptGenerationContext,
    private val promptTemplates: PromptTemplates,
) {
    /**
     * Generates a prompt for a given class under test. It accepts the list of interesting classes and inserts their method signatures.
     *
     * @param interestingClasses The list of ClassRepresentation objects.
     * @return The generated prompt.
     * @throws IllegalStateException If any of the required keywords are missing in the prompt template.
     */
    fun generatePromptForClass(interestingClasses: List<ClassRepresentation>, testSamplesCode: String): String {
        val prompt = PromptBuilder(promptTemplates.classPrompt)
            .insertLanguage(context.promptConfiguration.desiredLanguage)
            .insertName(context.cut!!.qualifiedName)
            .insertTestingPlatform(context.promptConfiguration.desiredTestingPlatform)
            .insertMockingFramework(context.promptConfiguration.desiredMockingFramework)
            .insertCodeUnderTest(context.cut.fullText, context.classesToTest)
            .insertMethodsSignatures(interestingClasses)
            .insertPolymorphismRelations(context.polymorphismRelations)
            .insertTestSample(testSamplesCode)
            .build()

        println("Prompt: $prompt")
        return prompt
    }

    /**
     * Generates a prompt for a given method representation and a list of interesting classes.
     *
     * @param method The representation of a method to be tested.
     * @param interestingClassesFromMethod A list of interesting classes related to the method.
     * @return The generated prompt.
     * @throws IllegalStateException If any of the required keywords are missing in the prompt template.
     */
    fun generatePromptForMethod(
        method: MethodRepresentation,
        interestingClassesFromMethod: List<ClassRepresentation>,
        testSamplesCode: String,
        packageName: String,
    ): String {
        val methodQualifiedName = context.cut?.let { "${it.qualifiedName}.${method.name}" } ?: "$packageName.${method.name}"

        val prompt = PromptBuilder(promptTemplates.methodPrompt)
            .insertLanguage(context.promptConfiguration.desiredLanguage)
            .insertName(methodQualifiedName)
            .insertTestingPlatform(context.promptConfiguration.desiredTestingPlatform)
            .insertMockingFramework(context.promptConfiguration.desiredMockingFramework)
            .insertCodeUnderTest(method.text, context.classesToTest)
            .insertMethodsSignatures(interestingClassesFromMethod)
            .insertPolymorphismRelations(context.polymorphismRelations)
            .insertTestSample(testSamplesCode)
            .build()

        return prompt
    }

    /**
     * Generates a prompt for a given line under test using a surrounding method/function as a context.
     *
     * @param lineUnderTest The source code of the line to be tested.
     * @param method The representation of the method that contains the line.
     * @param interestingClassesFromMethod A list of interesting classes related to the method.
     * @return The generated prompt.
     * @throws IllegalStateException If any of the required keywords are missing in the prompt template.
     */
    fun generatePromptForLine(
        lineUnderTest: String,
        method: MethodRepresentation,
        interestingClassesFromMethod: List<ClassRepresentation>,
        testSamplesCode: String,
        packageName: String,
    ): String {
        val codeUnderTest = if (context.cut != null) {
            // `method` is a method within a class
            buildCutDeclaration(context.cut, method)
        } else {
            // `method` is a top-level function
            method.text
        }

        val methodQualifiedName = context.cut?.let { "${it.qualifiedName}.${method.name}" } ?: "$packageName.${method.name}"
        val lineReference = "`${lineUnderTest.trim()}` within `$methodQualifiedName`"

        val prompt = PromptBuilder(promptTemplates.linePrompt)
            .insertLanguage(context.promptConfiguration.desiredLanguage)
            .insertName(lineReference)
            .insertTestingPlatform(context.promptConfiguration.desiredTestingPlatform)
            .insertMockingFramework(context.promptConfiguration.desiredMockingFramework)
            .insertCodeUnderTest(codeUnderTest, context.classesToTest)
            .insertMethodsSignatures(interestingClassesFromMethod)
            .insertPolymorphismRelations(context.polymorphismRelations)
            .insertTestSample(testSamplesCode)
            .build()

        return prompt
    }

    /**
     * Generates a prompt for a given line under test using CUT as a context.
     *
     * **Contract: `context.cut` is not `null`.**
     *
     * @param lineUnderTest The source code of the line to be tested.
     * @param interestingClasses The list of `ClassRepresentation` objects related to the line under test.
     * @param testSamplesCode The code snippet that serves as test samples.
     * @return The generated prompt as `String`.
     * @throws IllegalStateException If any of the required keywords are missing in the prompt template.
     */
    fun generatePromptForLine(
        lineUnderTest: String,
        interestingClasses: List<ClassRepresentation>,
        testSamplesCode: String,
    ): String {
        val lineReference = "`${lineUnderTest.trim()}` within `${context.cut!!.qualifiedName}`"

        val prompt = PromptBuilder(promptTemplates.linePrompt)
            .insertLanguage(context.promptConfiguration.desiredLanguage)
            .insertName(lineReference)
            .insertTestingPlatform(context.promptConfiguration.desiredTestingPlatform)
            .insertMockingFramework(context.promptConfiguration.desiredMockingFramework)
            .insertCodeUnderTest(context.cut.fullText, context.classesToTest)
            .insertMethodsSignatures(interestingClasses)
            .insertPolymorphismRelations(context.polymorphismRelations)
            .insertTestSample(testSamplesCode)
            .build()

        return prompt
    }
}

/**
 * Builds a cut declaration with constructor declarations and  a method under test.
 *
 * Example when there exist non-default constructors:
 * ```
 * [Instruction]: Use the following constructor declarations to instantiate `org.example.CalcKotlin` and call the method under test `add`:
 *
 * Constructors of the class org.example.CalcKotlin:
 * 	=== (val value: Int)
 * 	=== constructor(c: Int, d: Int) : this(c+d)
 *
 * Method:
 * fun add(a: Int, b: Int): Int {
 *         return a + b
 *     }
 * ```
 *
 * Example when only a default constructor exists:
 * ```
 * [Instruction]: Use a default constructor with zero arguments to instantiate `Calc` and call the method under test `sum`:
 *
 * Constructors of the class Calc:
 * === Default constructor
 *
 * Method:
 * public int sum(int a, int b) {
 *         return a + b;
 *     }
 * ```
 *
 * @param cut The `ClassRepresentation` object representing the class to be instantiated.
 * @param method The `MethodRepresentation` object representing the method under test.
 * @return A formatted `String` representing the cut declaration, containing constructor declarations and method text.
 */
private fun buildCutDeclaration(cut: ClassRepresentation, method: MethodRepresentation): String {
    val instruction = buildString {
        val constructorToUse = if (cut.constructorSignatures.isEmpty()) {
            "a default constructor with zero arguments"
        } else {
            "the following constructor declarations"
        }
        append("Use $constructorToUse to instantiate `${cut.qualifiedName}` and call the method under test `${method.name}`")
    }

    val classType = cut.classType.representation

    val constructorDeclarations = buildString {
        appendLine("Constructors of the $classType ${cut.qualifiedName}:")
        if (cut.constructorSignatures.isEmpty()) {
            appendLine("=== Default constructor")
        }
        for (constructor in cut.constructorSignatures) {
            appendLine("\t=== $constructor")
        }
    }.trim()

    val cutDeclaration = buildString {
        appendLine("[Instruction]: $instruction:")
        appendLine()
        appendLine(constructorDeclarations)
        appendLine()
        appendLine("Method:")
        appendLine(method.text)
    }.trim()

    return cutDeclaration
}
