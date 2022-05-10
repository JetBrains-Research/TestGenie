package nl.tudelft.ewi.se.ciselab.testgenie.actions

import nl.tudelft.ewi.se.ciselab.testgenie.evosuite.ResultWatcher
import nl.tudelft.ewi.se.ciselab.testgenie.evosuite.Runner
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.AppExecutorUtil

/**
 * This class generates tests for a class.
 */
class GenerateTestsActionClass : AnAction() {
    private val log = Logger.getInstance(this.javaClass)

    /**
     * Performs test generation for a class when the action is invoked.
     *
     * @param e an action event that contains useful information
     */
    override fun actionPerformed(e: AnActionEvent) {
        val project: Project = e.project ?: return

        val psiFile: PsiFile = e.dataContext.getData(CommonDataKeys.PSI_FILE) ?: return
        val caret: Caret = e.dataContext.getData(CommonDataKeys.CARET)?.caretModel?.primaryCaret ?: return

        val psiClass: PsiClass = GenerateTestsUtils.getSurroundingClass(psiFile, caret) ?: return
        val classFQN = psiClass.qualifiedName ?: return

        val projectPath: String = ProjectRootManager.getInstance(project).contentRoots.first().path
        val projectClassPath = "$projectPath/target/classes/"

        log.info("Generating tests for project $projectPath with classpath $projectClassPath")

        log.info("Selected class is $classFQN")

        //TODO: remove these lines
        Messages.showInfoMessage(classFQN, "selected")
        return

        val resultPath = Runner(projectPath, projectClassPath, classFQN).forClass().runEvoSuite()

        AppExecutorUtil.getAppScheduledExecutorService().execute(ResultWatcher(project, resultPath))
    }

    /**
     * Makes the action visible only if a class has been selected.
     * It also updates the action name depending on which class has been selected.
     *
     * @param e an action event that contains useful information
     */
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = false

        val caret: Caret = e.dataContext.getData(CommonDataKeys.CARET)?.caretModel?.primaryCaret ?: return
        val psiFile: PsiFile = e.dataContext.getData(CommonDataKeys.PSI_FILE) ?: return

        val psiClass: PsiClass = GenerateTestsUtils.getSurroundingClass(psiFile, caret) ?: return

        e.presentation.text = "Generate Tests For Class ${psiClass.name}"
        e.presentation.isEnabledAndVisible = true
    }
}