package org.jetbrains.research.testspark.tools.llm.generation

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import org.jetbrains.research.testspark.bundles.llm.LLMMessagesBundle
import org.jetbrains.research.testspark.bundles.plugin.PluginMessagesBundle
import org.jetbrains.research.testspark.core.data.TestGenerationData
import org.jetbrains.research.testspark.core.error.LlmError
import org.jetbrains.research.testspark.core.error.TestSparkResult
import org.jetbrains.research.testspark.core.exception.JavaSDKMissingException
import org.jetbrains.research.testspark.core.generation.llm.LLMWithFeedbackCycle
import org.jetbrains.research.testspark.core.generation.llm.getImportsCodeFromTestSuiteCode
import org.jetbrains.research.testspark.core.generation.llm.getPackageFromTestSuiteCode
import org.jetbrains.research.testspark.core.generation.llm.prompt.PromptSizeReductionStrategy
import org.jetbrains.research.testspark.core.monitor.ErrorMonitor
import org.jetbrains.research.testspark.core.progress.CustomProgressIndicator
import org.jetbrains.research.testspark.core.test.SupportedLanguage
import org.jetbrains.research.testspark.core.test.TestsPersistentStorage
import org.jetbrains.research.testspark.core.test.TestsPresenter
import org.jetbrains.research.testspark.core.test.data.TestSuiteGeneratedByLLM
import org.jetbrains.research.testspark.data.FragmentToTestData
import org.jetbrains.research.testspark.data.IJReport
import org.jetbrains.research.testspark.data.ProjectContext
import org.jetbrains.research.testspark.data.UIContext
import org.jetbrains.research.testspark.services.LLMSettingsService
import org.jetbrains.research.testspark.services.PluginSettingsService
import org.jetbrains.research.testspark.tools.TestProcessor
import org.jetbrains.research.testspark.tools.TestsExecutionResultManager
import org.jetbrains.research.testspark.tools.ToolUtils
import org.jetbrains.research.testspark.tools.factories.TestCompilerFactory
import org.jetbrains.research.testspark.tools.factories.TestsAssemblerFactory
import org.jetbrains.research.testspark.tools.llm.LlmSettingsArguments
import org.jetbrains.research.testspark.tools.llm.error.LLMErrorManager
import org.jetbrains.research.testspark.tools.llm.test.JUnitTestSuitePresenter
import org.jetbrains.research.testspark.tools.template.generation.ProcessManager
import java.nio.file.Path

/**
 * LLMProcessManager is a class that implements the ProcessManager interface
 * and is responsible for generating tests using the LLM tool.
 *
 * @property project The project in which the test generation is being performed.
 * @property testFileName The name of the generated test file.
 * @property log An instance of the logger class for logging purposes.
 * @property llmErrorManager An instance of the LLMErrorManager class.
 * @property maxRequests The maximum number of requests to be sent to LLM.
 */
class LLMProcessManager(
    private val project: Project,
    private val language: SupportedLanguage,
    private val promptManager: PromptManager,
    private val testSamplesCode: String,
    projectSDKPath: Path? = null,
) : ProcessManager {

    private val homeDirectory = projectSDKPath?.toString() ?: run {
        val sdk = ProjectRootManager.getInstance(project).projectSdk?.homeDirectory?.path
            ?: throw JavaSDKMissingException(LLMMessagesBundle.get("javaSdkNotConfigured"))

        return@run sdk
    }

    private val testFileName: String = when (language) {
        SupportedLanguage.Java -> "GeneratedTest.java"
        SupportedLanguage.Kotlin -> "GeneratedTest.kt"
    }
    private val log = Logger.getInstance(this::class.java)
    private val llmErrorManager: LLMErrorManager = LLMErrorManager()
    private val maxRequests = LlmSettingsArguments(project).maxLLMRequest()
    private val testProcessor: TestsPersistentStorage = TestProcessor(project, projectSDKPath)

    /**
     * Runs the test generator process.
     *
     * @param indicator The progress indicator for tracking the progress of the test generation process.
     * @param codeType The type of code to generate tests for.
     * @param packageName The package name of the code being tested.
     */
    override fun runTestGenerator(
        indicator: CustomProgressIndicator,
        codeType: FragmentToTestData,
        packageName: String,
        projectContext: ProjectContext,
        generatedTestsData: TestGenerationData,
        errorMonitor: ErrorMonitor,
        testsExecutionResultManager: TestsExecutionResultManager,
    ): UIContext? {
        log.info("LLM test generation begins")

        if (ToolUtils.isProcessStopped(errorMonitor, indicator)) return null

        // update build path
        var buildPath = projectContext.projectClassPath!!
        if (project.service<PluginSettingsService>().state.buildPath.isEmpty()) {
            // User did not set own path
            buildPath = ToolUtils.getBuildPath(project)
        }

        if (buildPath.isEmpty() || buildPath.isBlank()) {
            llmErrorManager.errorProcess(LLMMessagesBundle.get("emptyBuildPath"), project, errorMonitor)
            return null
        }
        indicator.setText(PluginMessagesBundle.get("searchMessage"))

        val report = IJReport()

        // PROMPT GENERATION
        val initialPromptMessage =
            promptManager.generatePrompt(codeType, testSamplesCode, generatedTestsData.polyDepthReducing)

        // initiate a new RequestManager
        val requestManager = StandardRequestManagerFactory(project).getRequestManager(project)

        // adapter for the existing prompt reduction functionality
        val promptSizeReductionStrategy = object : PromptSizeReductionStrategy {
            override fun isReductionPossible(): Boolean =
                promptManager.isPromptSizeReductionPossible(generatedTestsData)

            override fun reduceSizeAndGeneratePrompt(): String {
                if (!isReductionPossible()) {
                    throw IllegalStateException("Prompt size reduction is not possible yet requested")
                }
                val reductionSuccess = promptManager.reducePromptSize(generatedTestsData)
                assert(reductionSuccess)

                return promptManager.generatePrompt(codeType, testSamplesCode, generatedTestsData.polyDepthReducing)
            }
        }

        // adapter for the existing test case/test suite string representing functionality
        val testsPresenter = object : TestsPresenter {
            private val testSuitePresenter = JUnitTestSuitePresenter(project, generatedTestsData, language)

            override fun representTestSuite(testSuite: TestSuiteGeneratedByLLM): String {
                return testSuitePresenter.toStringWithoutExpectedException(testSuite)
            }

            override fun representTestCase(testSuite: TestSuiteGeneratedByLLM, testCaseIndex: Int): String {
                return testSuitePresenter.toStringSingleTestCaseWithoutExpectedException(testSuite, testCaseIndex)
            }
        }

        // Creation of JUnit specific parser, printer and assembler
        val jUnitVersion = project.getService(LLMSettingsService::class.java).state.junitVersion
        val testBodyPrinter = TestBodyPrinterFactory.create(language)
        val testSuiteParser = TestSuiteParserFactory.createJUnitTestSuiteParser(
            jUnitVersion,
            language,
            testBodyPrinter,
            packageName,
        )
        val testsAssembler = TestsAssemblerFactory.create(
            indicator,
            generatedTestsData,
            testSuiteParser,
            jUnitVersion,
        )

        val testCompiler = TestCompilerFactory.create(
            project,
            jUnitVersion,
            language,
            homeDirectory,
        )

        // Asking LLM to generate a test suite. Here we have a feedback cycle for LLM in case of wrong responses
        val llmFeedbackCycle = LLMWithFeedbackCycle(
            language = language,
            report = report,
            initialPromptMessage = initialPromptMessage,
            promptSizeReductionStrategy = promptSizeReductionStrategy,
            testSuiteFilename = testFileName,
            packageName = packageName,
            resultPath = generatedTestsData.resultPath,
            buildPath = buildPath,
            requestManager = requestManager,
            testsAssembler = testsAssembler,
            testCompiler = testCompiler,
            testStorage = testProcessor,
            testsPresenter = testsPresenter,
            indicator = indicator,
            requestsCountThreshold = maxRequests,
            errorMonitor = errorMonitor,
        )

        val feedbackResponse = llmFeedbackCycle.run { warning ->
            when (warning) {
                LLMWithFeedbackCycle.WarningType.TEST_SUITE_PARSING_FAILED ->
                    llmErrorManager.warningProcess(LLMMessagesBundle.get("emptyResponse"), project)

                LLMWithFeedbackCycle.WarningType.NO_TEST_CASES_GENERATED ->
                    llmErrorManager.warningProcess(LLMMessagesBundle.get("emptyResponse"), project)

                LLMWithFeedbackCycle.WarningType.COMPILATION_ERROR_OCCURRED ->
                    llmErrorManager.warningProcess(LLMMessagesBundle.get("compilationError"), project)
            }
        }

        // Process stopped checking
        if (ToolUtils.isProcessStopped(errorMonitor, indicator)) return null
//        log.info("Feedback cycle finished execution with ${feedbackResponse.executionResult} result code")

        when (feedbackResponse) {
            is TestSparkResult.Success -> {
                log.info("Add ${feedbackResponse.data.compilableTestCases.size} compilable test cases into generatedTestsData")
            }

            is TestSparkResult.Failure -> {
                when (feedbackResponse.error) {
                    is LlmError.NoCompilableTestCasesGenerated -> {
                        llmErrorManager.errorProcess(LLMMessagesBundle.get("invalidLLMResult"), project, errorMonitor)
                    }

                    is LlmError.FeedbackCycleCancelled -> {
                        log.info("Process stopped")
                        return null
                    }

                    is LlmError.PromptTooLong -> {
                        llmErrorManager.errorProcess(
                            LLMMessagesBundle.get("tooLongPromptRequest"),
                            project,
                            errorMonitor
                        )
                        return null
                    }

                    is LlmError.FailedToSaveTestFiles -> {
                        llmErrorManager.errorProcess(
                            LLMMessagesBundle.get("savingTestFileIssue"),
                            project,
                            errorMonitor
                        )
                    }
                }
            }
        }

        if (ToolUtils.isProcessStopped(errorMonitor, indicator)) return null

        // Error during the collecting
        if (errorMonitor.hasErrorOccurred()) return null

        log.info("Save generated test suite and test cases into the project workspace")

        val testSuitePresenter = JUnitTestSuitePresenter(project, generatedTestsData, language)
        val generatedTestSuite: TestSuiteGeneratedByLLM? = feedbackResponse.getDataOrNull()?.generatedTestSuite
        val testSuiteRepresentation =
            if (generatedTestSuite != null) testSuitePresenter.toString(generatedTestSuite) else null

        ToolUtils.transferToIJTestCases(report)

        ToolUtils.saveData(
            project,
            report,
            getPackageFromTestSuiteCode(testSuiteCode = testSuiteRepresentation, language),
            getImportsCodeFromTestSuiteCode(testSuiteRepresentation, projectContext.classFQN),
            projectContext.fileUrlAsString!!,
            generatedTestsData,
            testsExecutionResultManager,
            language,
        )

        return UIContext(projectContext, generatedTestsData, requestManager, errorMonitor)
    }
}
