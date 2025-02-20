package org.jetbrains.research.testspark.tools

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtilRt
import org.jetbrains.research.testspark.actions.controllers.TestGenerationController
import org.jetbrains.research.testspark.bundles.plugin.PluginMessagesBundle
import org.jetbrains.research.testspark.core.data.TestGenerationData
import org.jetbrains.research.testspark.core.exception.TestSparkException
import org.jetbrains.research.testspark.core.utils.DataFilesUtil
import org.jetbrains.research.testspark.data.FragmentToTestData
import org.jetbrains.research.testspark.data.ProjectContext
import org.jetbrains.research.testspark.data.UIContext
import org.jetbrains.research.testspark.display.TestSparkDisplayManager
import org.jetbrains.research.testspark.display.custom.IJProgressIndicator
import org.jetbrains.research.testspark.langwrappers.PsiHelper
import org.jetbrains.research.testspark.tools.llm.error.LLMErrorManager
import org.jetbrains.research.testspark.tools.template.generation.ProcessManager
import java.util.*

/**
 * Pipeline class represents a pipeline for generating tests in a project.
 *
 * @param project the project in which the pipeline is executed.
 * @param psiHelper The PsiHelper in the context of which the pipeline is executed.
 * @param caretOffset the offset of the caret position in the PSI file.
 * @param fileUrl the URL of the file being processed, if applicable.
 * @param packageName the package name of the file being processed.
 * @param generationToolName the tool name chosen for generation
 */
class Pipeline(
    private val project: Project,
    private val psiHelper: PsiHelper,
    private val caretOffset: Int,
    private val fileUrl: String?,
    private val packageName: String,
    private val testGenerationController: TestGenerationController,
    private val testSparkDisplayManager: TestSparkDisplayManager,
    private val testsExecutionResultManager: TestsExecutionResultManager,
    generationToolName: String,
) {
    val projectContext: ProjectContext = ProjectContext()
    val generatedTestsData = TestGenerationData()
    val generationTool = GenerationTool.from(generationToolName)

    init {
        val cutPsiClass = psiHelper.getSurroundingClass(caretOffset)

        // get generated test path
        val testResultDirectory = "${FileUtilRt.getTempDirectory()}${ToolUtils.sep}testSparkResults${ToolUtils.sep}"
        val id = UUID.randomUUID().toString()
        val testResultName = "test_gen_result_$id"

        ApplicationManager.getApplication().runWriteAction {
            projectContext.projectClassPath = ProjectRootManager.getInstance(project).contentRoots.first().path
            projectContext.fileUrlAsString = fileUrl
            cutPsiClass?.let { projectContext.classFQN = it.qualifiedName }
            projectContext.cutModule = psiHelper.getModuleFromPsiFile()
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
        testGenerationController.errorMonitor.clear()
        testSparkDisplayManager.clear()
        testsExecutionResultManager.clear()

        val projectBuilder = ProjectBuilder(project, testGenerationController.errorMonitor)

        var editor: Editor? = null

        var uiContext: UIContext? = null

        ProgressManager.getInstance()
            .run(object : Task.Backgroundable(project, PluginMessagesBundle.get("testGenerationMessage")) {
                override fun run(indicator: ProgressIndicator) {
                    try {
                        val ijIndicator = IJProgressIndicator(indicator)
                        testGenerationController.indicator = ijIndicator

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
                                testsExecutionResultManager,
                            )
                        }

                        if (ToolUtils.isProcessStopped(testGenerationController.errorMonitor, ijIndicator)) return

                        ijIndicator.stop()
                    } catch (err: TestSparkException) {
                        LLMErrorManager().errorProcess(err.message!!, project, testGenerationController.errorMonitor)
                    }
                }

                override fun onFinished() {
                    super.onFinished()

                    testGenerationController.finished()

                    if (testGenerationController.errorMonitor.hasErrorOccurred()) return

                    updateEditor(uiContext!!.testGenerationOutput.fileUrl)

                    if (editor != null) {
                        val report = uiContext!!.testGenerationOutput.testGenerationResultList[0]!!
                        testSparkDisplayManager.display(
                            report,
                            editor!!,
                            uiContext!!,
                            psiHelper.language,
                            project,
                            testsExecutionResultManager,
                            generationTool,
                        )
                    }
                }

                private fun updateEditor(fileUrl: String) {
                    val documentManager = FileDocumentManager.getInstance()
                    // https://intellij-support.jetbrains.com/hc/en-us/community/posts/360004480599/comments/360000703299
                    FileEditorManager.getInstance(project).selectedEditors.map { it as TextEditor }.map { it.editor }
                        .map {
                            val currentFile = documentManager.getFile(it.document)
                            if (currentFile != null) {
                                if (currentFile.presentableUrl == fileUrl) {
                                    editor = it
                                }
                            }
                        }
                }
            })
    }
}
