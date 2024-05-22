package org.jetbrains.research.testspark.tools

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.PsiFile
import org.jetbrains.research.testspark.actions.controllers.TestGenerationController
import org.jetbrains.research.testspark.bundles.plugin.PluginMessagesBundle
import org.jetbrains.research.testspark.core.data.TestGenerationData
import org.jetbrains.research.testspark.core.utils.DataFilesUtil
import org.jetbrains.research.testspark.data.FragmentToTestData
import org.jetbrains.research.testspark.data.ProjectContext
import org.jetbrains.research.testspark.data.UIContext
import org.jetbrains.research.testspark.display.custom.IJProgressIndicator
import org.jetbrains.research.testspark.helpers.psiHelpers.PsiHelperFactory
import org.jetbrains.research.testspark.services.CoverageVisualisationService
import org.jetbrains.research.testspark.services.EditorService
import org.jetbrains.research.testspark.services.TestCaseDisplayService
import org.jetbrains.research.testspark.services.TestsExecutionResultService
import org.jetbrains.research.testspark.tools.template.generation.ProcessManager
import java.util.UUID

/**
 * Pipeline class represents a pipeline for generating tests in a project.
 *
 * @param project the project in which the pipeline is executed
 * @param psiFile the PSI file in which the pipeline is executed
 * @param caretOffset the offset of the caret position in the PSI file
 * @param fileUrl the URL of the file being processed, if applicable
 * @param packageName the package name of the file being processed
 */
class Pipeline(
    private val project: Project,
    psiFile: PsiFile,
    caretOffset: Int,
    fileUrl: String?,
    private val packageName: String,
    private val testGenerationController: TestGenerationController,
) {
    val projectContext: ProjectContext = ProjectContext()
    val generatedTestsData = TestGenerationData()

    init {

        val cutPsiClass = PsiHelperFactory.getPsiHelper(psiFile).getSurroundingClass(psiFile, caretOffset)!!

        // get generated test path
        val testResultDirectory = "${FileUtilRt.getTempDirectory()}${ToolUtils.sep}testSparkResults$ToolUtils.sep"
        val id = UUID.randomUUID().toString()
        val testResultName = "test_gen_result_$id"

        ApplicationManager.getApplication().runWriteAction {
            projectContext.projectClassPath = ProjectRootManager.getInstance(project).contentRoots.first().path
            projectContext.fileUrlAsString = fileUrl
            projectContext.cutPsiClass = cutPsiClass
            projectContext.classFQN = cutPsiClass.qualifiedName!!
            projectContext.cutModule =
                ProjectFileIndex.getInstance(project).getModuleForFile(cutPsiClass.containingFile.virtualFile)!!
        }

        generatedTestsData.resultPath = ToolUtils.getResultPath(id, testResultDirectory)
        generatedTestsData.baseDir = "${testResultDirectory}$testResultName-validation"
        generatedTestsData.testResultName = testResultName

        DataFilesUtil.makeTmp(FileUtilRt.getTempDirectory())
        DataFilesUtil.makeDir(generatedTestsData.baseDir!!)
    }

    /**
     * Builds the project and launches generation on a separate thread.
     */
    fun runTestGeneration(processManager: ProcessManager, codeType: FragmentToTestData) {
        clear(project)
        val projectBuilder = ProjectBuilder(project, testGenerationController.errorMonitor)

        var uiContext: UIContext? = null

        ProgressManager.getInstance()
            .run(object : Task.Backgroundable(project, PluginMessagesBundle.get("testGenerationMessage")) {
                override fun run(indicator: ProgressIndicator) {
                    val ijIndicator = IJProgressIndicator(indicator)

                    if (ToolUtils.isProcessStopped(testGenerationController.errorMonitor, ijIndicator)) return

                    if (projectBuilder.runBuild(ijIndicator)) {
                        if (ToolUtils.isProcessStopped(testGenerationController.errorMonitor, ijIndicator)) return

                        uiContext = processManager.runTestGenerator(
                            ijIndicator,
                            codeType,
                            packageName,
                            projectContext,
                            generatedTestsData,
                            testGenerationController.errorMonitor,
                        )
                    }

                    if (ToolUtils.isProcessStopped(testGenerationController.errorMonitor, ijIndicator)) return

                    ijIndicator.stop()
                }

                override fun onFinished() {
                    super.onFinished()
                    testGenerationController.finished()
                    uiContext?.let {
                        project.service<TestCaseDisplayService>()
                            .updateEditorForFileUrl(it.testGenerationOutput.fileUrl)

                        if (project.service<EditorService>().editor != null) {
                            val report = it.testGenerationOutput.testGenerationResultList[0]!!
                            project.service<TestCaseDisplayService>().displayTestCases(report, it)
                            project.service<CoverageVisualisationService>().showCoverage(report)
                        }
                    }
                }
            })
    }

    private fun clear(project: Project) { // should be removed totally!
        testGenerationController.errorMonitor.clear()
        project.service<TestCaseDisplayService>().clear()
        project.service<CoverageVisualisationService>().clear()
        project.service<TestsExecutionResultService>().clear()
    }
}
