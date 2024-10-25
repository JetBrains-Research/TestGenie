package org.jetbrains.research.testspark.core.generation.llm.prompt

import org.jetbrains.research.testspark.core.data.ClassType
import org.jetbrains.research.testspark.core.generation.llm.prompt.configuration.ClassRepresentation
import org.jetbrains.research.testspark.core.generation.llm.prompt.configuration.MethodRepresentation
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertContains

class PromptBuilderTest {
    @Test
    fun insertLanguage() {
        val (keyword, value) = PromptKeyword.LANGUAGE to "Java"

        val prompt = PromptBuilder("Language: ${keyword.variable}")
            .insertLanguage(value)
            .build()

        assertEquals("Language: Java", prompt)
    }

    @Test
    fun insertName() {
        val (keyword, value) = PromptKeyword.NAME to "MyClass"

        val prompt = PromptBuilder("Name: ${keyword.variable}")
            .insertName(value)
            .build()

        assertEquals("Name: MyClass", prompt)
    }

    @Test
    fun insertTestingPlatform() {
        val (keyword, value) = PromptKeyword.TESTING_PLATFORM to "JUnit4"

        val prompt = PromptBuilder("Testing platform: ${keyword.variable}")
            .insertTestingPlatform(value)
            .build()

        assertEquals("Testing platform: JUnit4", prompt)
    }

    @Test
    fun insertMockingFramework() {
        val (keyword, value) = PromptKeyword.MOCKING_FRAMEWORK to "Mockito"

        val prompt = PromptBuilder("Mocking framework: ${keyword.variable}")
            .insertMockingFramework(value)
            .build()

        assertEquals("Mocking framework: Mockito", prompt)
    }

    @Test
    fun insertCodeUnderTest() {
        val keyword = PromptKeyword.CODE
        val code = """
            class MyClass() {
              fun f() { println("Hello world!") }
            }
        """.trimIndent()

        val prompt = PromptBuilder("Code:\n${keyword.variable}")
            .insertCodeUnderTest(code, emptyList())
            .build()

        assertContains(prompt, code, message = "Code under test should be inserted into prompt template")
    }

    @Test
    fun lastInsertionPrevails() {
        val keyword = PromptKeyword.LANGUAGE

        val prompt = PromptBuilder("Language: ${keyword.variable}")
            .insertLanguage("Java")
            .insertLanguage("Kotlin")
            .build()

        assertEquals("Language: Kotlin", prompt)
    }

    @Test
    fun throwsOnMissingMandatoryKeyword() {
        val keywords: List<PromptKeyword> = PromptKeyword.entries

        for (keyword in keywords) {
            val promptTemplate = "My variable is: ${keyword.variable}"

            if (keyword.mandatory) {
                val exception = assertThrows<IllegalStateException> { PromptBuilder(promptTemplate).build() }
                assertEquals("The prompt must contain ${keyword.name} keyword", exception.message)
            } else {
                assertDoesNotThrow { PromptBuilder(promptTemplate).build() }
            }
        }
    }

    @Test
    fun testPopulateMultipleVariableEntries() {
        val keyword = PromptKeyword.LANGUAGE
        val template = """
            Language1: '${keyword.variable}'
            Language2: \\${keyword.variable}\\
            Language3: `${keyword.variable}`
        """.trimIndent()

        val prompt = PromptBuilder(template)
            .insertLanguage("Java")
            .build()

        assertEquals(
            """
            Language1: 'Java'
            Language2: \\Java\\
            Language3: `Java`
            """.trimIndent(),
            prompt,
        )
    }

    @Test
    fun testInsertMultipleVariables() {
        val template = """
            language: ${PromptKeyword.LANGUAGE.variable}
            name: ${PromptKeyword.NAME.variable}
            testing platform: ${PromptKeyword.TESTING_PLATFORM.variable}
            mocking framework: ${PromptKeyword.MOCKING_FRAMEWORK.variable}
        """.trimIndent()

        val prompt = PromptBuilder(template)
            .insertLanguage("Java")
            .insertName("org.pkg.MyClass")
            .insertTestingPlatform("JUnit4")
            .insertMockingFramework("Mockito")
            .build()

        val expected = """
            language: Java
            name: org.pkg.MyClass
            testing platform: JUnit4
            mocking framework: Mockito
        """.trimIndent()

        assertEquals(expected, prompt)
    }

    @Test
    fun testThrowsOnNonExistentKeywordInsertion() {
        val template = "Language: ${PromptKeyword.LANGUAGE.variable}"
        val exception = assertThrows<IllegalArgumentException> {
            PromptBuilder(template).insertName("Name")
        }
        assertEquals("Prompt template does not contain mandatory ${PromptKeyword.NAME.name}", exception.message)
    }

    @Test
    fun testInsertMethodsSignatures() {
        val keyword = PromptKeyword.METHODS

        val method1 = MethodRepresentation(
            signature = "method1():Boolean",
            name = "method1",
            text = "fun method1(): Boolean { return true }",
            containingClassQualifiedName = "MyClass",
        )
        val method2 = MethodRepresentation(
            signature = "method2():Boolean",
            name = "method2",
            text = "fun method2(): Boolean { return false }",
            containingClassQualifiedName = "MyClass",
        )
        val myClass = ClassRepresentation(
            qualifiedName = "MyClass",
            fullText = """
                    class MyClass {
                        fun method1(): Boolean { return true }
                        fun method2(): Boolean { return false }
                    }
            """.trimIndent(),
            allMethods = listOf(method1, method2),
            constructorSignatures = emptyList(),
            classType = ClassType.CLASS,
        )

        val interestingClasses = listOf(myClass)

        val expectedMethodsText = """
            Methods:
            Here are some information about other methods and classes used by the class under test. Only use them for creating objects, not your own ideas.
            === methods in MyClass:
             - method1():Boolean
             - method2():Boolean
        """.trimIndent()

        val builder = PromptBuilder("Methods:\n${keyword.variable}")
        builder.insertMethodsSignatures(interestingClasses)

        val prompt = builder.build()

        assertEquals(
            expectedMethodsText + "\n",
            prompt,
            "Methods' signatures should be inserted into prompt template correctly",
        )
    }

    @Test
    fun testInsertPolymorphismRelations() {
        val myInterface = ClassRepresentation(
            qualifiedName = "MyInterface",
            fullText = """
            class MyInterface {
            }
            """.trimIndent(),
            allMethods = emptyList(),
            constructorSignatures = emptyList(),
            classType = ClassType.INTERFACE,
        )
        val mySubClass = ClassRepresentation(
            qualifiedName = "MySubClass",
            fullText = """
            class MySubClass : MyInterface {
            }
            """.trimIndent(),
            allMethods = emptyList(),
            constructorSignatures = emptyList(),
            classType = ClassType.CLASS,
        )
        val polymorphicRelations = mapOf(myInterface to listOf(mySubClass))

        val prompt = PromptBuilder(PromptKeyword.POLYMORPHISM.variable)
            .insertPolymorphismRelations(polymorphicRelations)
            .build()

        println("'$prompt'")

        val expected = """
            Use the following polymorphic relationships of classes present in the project. Use them for instantiation when necessary. Do not mock classes if an instantiation of a sub-class is applicable.

            MySubClass is a sub-class of MyInterface.
        """.trimIndent()

        assertEquals(expected + "\n", prompt)
    }

    @Test
    fun testInsertTestSample() {
        val testSamplesCode = """
        @Test
        fun testMethod() {
            assertEquals(4, 2+2)
        }
        """.trimIndent()

        val prompt = PromptBuilder(PromptKeyword.TEST_SAMPLE.variable)
            .insertTestSample(testSamplesCode)
            .build()

        val expected = "Use this test samples:\n" + testSamplesCode + "\n"

        assertEquals(expected, prompt)
    }
}
