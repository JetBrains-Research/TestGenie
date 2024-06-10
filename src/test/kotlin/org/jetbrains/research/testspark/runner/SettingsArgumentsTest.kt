package org.jetbrains.research.testspark.runner

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.research.testspark.services.EvoSuiteSettingsService
import org.jetbrains.research.testspark.settings.evosuite.EvoSuiteSettingsState
import org.jetbrains.research.testspark.tools.evosuite.SettingsArguments
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class SettingsArgumentsTest {
    private lateinit var settingsState: EvoSuiteSettingsState

    private lateinit var fixture: CodeInsightTestFixture

    @BeforeEach
    fun setUp() {
        val projectBuilder: TestFixtureBuilder<IdeaProjectTestFixture> =
            IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder("project")

        fixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.fixture)
        fixture.setUp()

        val settingsService = ApplicationManager.getApplication().getService(EvoSuiteSettingsService::class.java)
        settingsService.loadState(EvoSuiteSettingsState())

        settingsState = settingsService.state
    }

    @AfterEach
    fun tearDown() {
        fixture.tearDown()
    }

    @Test
    fun testCommandForClass() {
        val settings = SettingsArguments("project/classpath", "project", "serializepath", "lang.java.Dung", "basedir", settingsState)
        val command = mutableListOf(
            "-generateMOSuite",
            "-serializeResult",
            "-serializeResultPath",
            "serializepath",
            "-base_dir",
            """"basedir"""",
            "-projectCP",
            "project/classpath",
            "-Dnew_statistics=false",
            "-class",
            "lang.java.Dung",
            "-Dcatch_undeclared_exceptions=false",
            "-Dtest_naming_strategy=COVERAGE",
            "-Dalgorithm=DYNAMOSA",
            "-Dcriterion=LINE:BRANCH:EXCEPTION:WEAKMUTATION:OUTPUT:METHOD:METHODNOEXCEPTION:CBRANCH",
        )

        val actual = settings.build()

        assertThat(
            actual,
        ).isEqualTo(command)
    }

    @Test
    fun testCommandForMethod() {
        val settings = SettingsArguments(
            "project/classpath",
            "project",
            "serializepath",
            "lang.java.Dung",
            "basedir",
            settingsState,
        ).forMethod("dungMethod(IDLjava/lang/Thread;)Ljava/lang/Object;")
        val command = mutableListOf(
            "-generateMOSuite",
            "-serializeResult",
            "-serializeResultPath",
            "serializepath",
            "-base_dir",
            "\"basedir\"",
            "-projectCP",
            "project/classpath",
            "-Dnew_statistics=false",
            "-class",
            "lang.java.Dung",
            "-Dcatch_undeclared_exceptions=false",
            "-Dtest_naming_strategy=COVERAGE",
            "-Dtarget_method=dungMethod(IDLjava/lang/Thread;)Ljava/lang/Object;",
            "-Dalgorithm=DYNAMOSA",
            "-Dcriterion=LINE:BRANCH:EXCEPTION:WEAKMUTATION:OUTPUT:METHOD:METHODNOEXCEPTION:CBRANCH",
        )

        val actual = settings.build()

        assertThat(
            actual,
        ).isEqualTo(command)
    }

    @Test
    fun testCommandForLine() {
        val settings =
            SettingsArguments("project/classpath", "project", "serializepath", "lang.java.Dung", "basedir", settingsState).forLine(419)
        val command = mutableListOf(
            "-generateMOSuite",
            "-serializeResult",
            "-serializeResultPath",
            "serializepath",
            "-base_dir",
            """"basedir"""",
            "-projectCP",
            "project/classpath",
            "-Dnew_statistics=false",
            "-class",
            "lang.java.Dung",
            "-Dcatch_undeclared_exceptions=false",
            "-Dtest_naming_strategy=COVERAGE",
            "-Dtarget_line=419",
            "-Dalgorithm=DYNAMOSA",
            "-Dcriterion=LINE:BRANCH:",
        )

        val actual = settings.build(true)

        assertThat(
            actual,
        ).isEqualTo(command)
    }

    @Test
    fun testCriterionStringDefaultCriterion() {
        settingsState.criterionBranch = false
        settingsState.criterionException = false
        settingsState.criterionWeakMutation = false
        settingsState.criterionLine = false
        settingsState.criterionCBranch = false
        settingsState.criterionMethodNoException = false
        settingsState.criterionMethod = false
        settingsState.criterionOutput = false

        val settings = SettingsArguments("project/classpath", "project", "serializepath", "lang.java.Dung", "basedir", settingsState)

        val criterion = settings.build().last()

        assertThat(
            "-Dcriterion=LINE",
        ).isEqualTo(criterion)
    }

    @Test
    fun testCriterionStringSomeCriterion() {
        settingsState.criterionBranch = false
        settingsState.criterionException = false
        settingsState.criterionWeakMutation = false
        settingsState.criterionLine = false

        val settings = SettingsArguments("project/classpath", "project", "serializepath", "lang.java.Dung", "basedir", settingsState)

        val criterion = settings.build().last()

        assertThat(
            criterion,
        ).isEqualTo("-Dcriterion=OUTPUT:METHOD:METHODNOEXCEPTION:CBRANCH")
    }

    @Test
    fun testCriterionStringAll() {
        val settings = SettingsArguments("project/classpath", "project", "serializepath", "lang.java.Dung", "basedir", settingsState)

        val criterion = settings.build().last()

        assertThat(
            criterion,
        ).isEqualTo("-Dcriterion=LINE:BRANCH:EXCEPTION:WEAKMUTATION:OUTPUT:METHOD:METHODNOEXCEPTION:CBRANCH")
    }
}
