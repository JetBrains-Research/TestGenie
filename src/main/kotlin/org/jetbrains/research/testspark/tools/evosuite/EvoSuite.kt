package org.jetbrains.research.testspark.tools.evosuite

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import org.jetbrains.research.testspark.actions.controllers.TestGenerationController
import org.jetbrains.research.testspark.core.test.data.CodeType
import org.jetbrains.research.testspark.data.FragmentToTestData
import org.jetbrains.research.testspark.display.TestSparkDisplayManager
import org.jetbrains.research.testspark.langwrappers.PsiHelper
import org.jetbrains.research.testspark.langwrappers.PsiMethodWrapper
import org.jetbrains.research.testspark.services.PluginSettingsService
import org.jetbrains.research.testspark.tools.Pipeline
import org.jetbrains.research.testspark.tools.TestsExecutionResultManager
import org.jetbrains.research.testspark.tools.evosuite.generation.EvoSuiteProcessManager
import org.jetbrains.research.testspark.tools.template.Tool
import java.io.File

/**
 * Represents the EvoSuite class, which is a tool used to generate tests for Java code.
 * Implements the Tool interface.
 *
 * @param name The name of the EvoSuite tool.
 */
class EvoSuite(override val name: String = "EvoSuite") : Tool {
    private val log = Logger.getInstance(this::class.java)

    /**
     * Returns a new instance of EvoSuiteProcessManager for the given project.
     *
     * @param project The IntelliJ IDEA project for which the EvoSuiteProcessManager is created.
     * @return The EvoSuiteProcessManager instance created for the given project.
     */
    private fun getEvoSuiteProcessManager(project: Project): EvoSuiteProcessManager {
        val projectClassPath: String = ProjectRootManager.getInstance(project).contentRoots.first().path
        val settingsProjectState = project.service<PluginSettingsService>().state
        val buildPath = "$projectClassPath${File.separatorChar}${settingsProjectState.buildPath}"
        return EvoSuiteProcessManager(project, buildPath)
    }

    /**
     * Generates tests for a class using EvoSuite.
     *
     * @param project The current project.
     * @param psiFile The PsiFile containing the class to generate tests for.
     * @param caret The caret position within the PsiFile.
     * @param fileUrl The URL of the file being tested.
     * @param testSamplesCode The code to be used as test samples.
     */
    override fun generateTestsForClass(
        project: Project,
        psiHelper: PsiHelper,
        caretOffset: Int,
        fileUrl: String?,
        testSamplesCode: String,
        testGenerationController: TestGenerationController,
        testSparkDisplayManager: TestSparkDisplayManager,
        testsExecutionResultManager: TestsExecutionResultManager,
    ) {
        log.info("Starting tests generation for class by EvoSuite")
        createPipeline(
            project,
            psiHelper,
            caretOffset,
            fileUrl,
            testGenerationController,
            testSparkDisplayManager,
            testsExecutionResultManager,
        ).runTestGeneration(
            getEvoSuiteProcessManager(project),
            FragmentToTestData(
                CodeType.CLASS,
            ),
        )
    }

    /**
     * Generates tests for a given method using EvoSuite.
     *
     * @param project The current project.
     * @param psiFile The PSI file containing the method.
     * @param caret The caret position within the PSI file.
     * @param fileUrl The URL of the file containing the method.
     * @param testSamplesCode The sample code for the test cases.
     */
    override fun generateTestsForMethod(
        project: Project,
        psiHelper: PsiHelper,
        caretOffset: Int,
        fileUrl: String?,
        testSamplesCode: String,
        testGenerationController: TestGenerationController,
        testSparkDisplayManager: TestSparkDisplayManager,
        testsExecutionResultManager: TestsExecutionResultManager,
    ) {
        log.info("Starting tests generation for method by EvoSuite")
        val psiMethod: PsiMethodWrapper = psiHelper.getSurroundingMethod(caretOffset)!!
        createPipeline(
            project,
            psiHelper,
            caretOffset,
            fileUrl,
            testGenerationController,
            testSparkDisplayManager,
            testsExecutionResultManager,
        ).runTestGeneration(
            getEvoSuiteProcessManager(project),
            FragmentToTestData(
                CodeType.METHOD,
                psiHelper.generateMethodDescriptor(psiMethod),
            ),
        )
    }

    /**
     * Generates tests for a specific line of code using EvoSuite.
     *
     * @param project The project in which the code is located.
     * @param psiFile The PsiFile object representing the code file.
     * @param caret The Caret object representing the current cursor position.
     * @param fileUrl The URL of the code file.
     * @param testSamplesCode The code samples used for testing.
     */
    override fun generateTestsForLine(
        project: Project,
        psiHelper: PsiHelper,
        caretOffset: Int,
        fileUrl: String?,
        testSamplesCode: String,
        testGenerationController: TestGenerationController,
        testSparkDisplayManager: TestSparkDisplayManager,
        testsExecutionResultManager: TestsExecutionResultManager,
    ) {
        log.info("Starting tests generation for line by EvoSuite")
        val selectedLine: Int = psiHelper.getSurroundingLineNumber(caretOffset)!!
        createPipeline(
            project,
            psiHelper,
            caretOffset,
            fileUrl,
            testGenerationController,
            testSparkDisplayManager,
            testsExecutionResultManager,
        ).runTestGeneration(
            getEvoSuiteProcessManager(project),
            FragmentToTestData(
                CodeType.LINE,
                selectedLine,
            ),
        )
    }

    /**
     * Creates a pipeline object for the given project, psiFile, caret, and fileUrl.
     * The packageName is determined based on the projectClassPath and the buildPath from the project settings.
     *
     * @param project The project for which to create the pipeline.
     * @param psiFile The psiFile associated with the pipeline.
     * @param caret The caret position in the psiFile.
     * @param fileUrl The URL of the file associated with the pipeline. Can be null.
     * @return The created pipeline object.
     */
    private fun createPipeline(
        project: Project,
        psiHelper: PsiHelper,
        caretOffset: Int,
        fileUrl: String?,
        testGenerationController: TestGenerationController,
        testSparkDisplayManager: TestSparkDisplayManager,
        testsExecutionResultManager: TestsExecutionResultManager,
    ): Pipeline {
        val projectClassPath: String = ProjectRootManager.getInstance(project).contentRoots.first().path

        val settingsProjectState = project.service<PluginSettingsService>().state
        val packageName = "$projectClassPath/${settingsProjectState.buildPath}"

        return Pipeline(
            project,
            psiHelper,
            caretOffset,
            fileUrl,
            packageName,
            testGenerationController,
            testSparkDisplayManager,
            testsExecutionResultManager,
            name,
        )
    }
}
