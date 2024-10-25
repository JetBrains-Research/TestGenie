package org.jetbrains.research.testspark.core.generation.llm.prompt.configuration

import org.jetbrains.research.testspark.core.data.ClassType

/**
 * Represents the context for generating prompts for generating unit tests.
 *
 * @property cut The ClassRepresentation of the class being tested.
 * @property classesToTest The list of ClassRepresentation objects representing the classes to be tested.
 * @property polymorphismRelations A map where the key represents a ClassRepresentation object and the value is a list of its detected subclasses.
 */
data class PromptGenerationContext(
    /**
     * The cut is null when we want to generate tests for top-level function
     */
    val cut: ClassRepresentation?,
    val classesToTest: List<ClassRepresentation>,
    val polymorphismRelations: Map<ClassRepresentation, List<ClassRepresentation>>,
    val promptConfiguration: PromptConfiguration,
)

/**
 * Represents the configuration for a prompt.
 *
 * @property desiredLanguage The programming language in which the generated tests should be written.
 * @property desiredTestingPlatform The desired testing platform.
 * @property desiredMockingFramework The desired mocking framework.
 */
data class PromptConfiguration(
    val desiredLanguage: String,
    val desiredTestingPlatform: String,
    val desiredMockingFramework: String,
)

/**
 * The class stores the data collected from PsiClass that is required for further generation of a prompt.
 * It is introduced to manipulate psi-related components without having psi as a dependency.
 *
 * @property qualifiedName - qualified name of the class.
 * @property fullText - the source code of the class.
 * @property allMethods - list of methods in the class and all its superclasses, it is an analogy of PsiClass.allMethods property.
 */
data class ClassRepresentation(
    val qualifiedName: String,
    val fullText: String,
    val constructorSignatures: List<String>,
    val allMethods: List<MethodRepresentation>,
    val classType: ClassType,
)

/**
 * Represents a method in a class.
 * It is introduced to manipulate psi-related components without having psi as a dependency.
 *
 * @property signature The signature of the method.
 * @property name The name of the method.
 * @property text The source code of the method.
 * @property containingClassQualifiedName The qualified name of the class containing the method.
 */
data class MethodRepresentation(
    val signature: String,
    val name: String,
    val text: String,
    val containingClassQualifiedName: String,
)

/**
 * Represents the prompt templates used for generating prompts for different scenarios.
 *
 * @property classPrompt The prompt template for generating prompts for a class.
 * @property methodPrompt The prompt template for generating prompts for a method.
 * @property linePrompt The prompt template for generating prompts for a specific line number in the code.
 */
data class PromptTemplates(
    val classPrompt: String,
    val methodPrompt: String,
    val linePrompt: String,
)
