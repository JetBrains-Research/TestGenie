package org.jetbrains.research.testspark.display

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.ui.content.ContentManager
import org.jetbrains.research.testspark.bundles.plugin.PluginLabelsBundle
import org.jetbrains.research.testspark.bundles.plugin.PluginMessagesBundle
import org.jetbrains.research.testspark.core.data.Report
import org.jetbrains.research.testspark.core.test.SupportedLanguage
import org.jetbrains.research.testspark.data.UIContext
import org.jetbrains.research.testspark.display.coverage.CoverageVisualisationTabBuilder
import org.jetbrains.research.testspark.display.generatedTests.GeneratedTestsTabBuilder
import org.jetbrains.research.testspark.tools.GenerationTool
import org.jetbrains.research.testspark.tools.TestsExecutionResultManager
import java.awt.Component
import javax.swing.JOptionPane

/**
 * The TestSparkDisplayBuilder class is responsible for displaying the generated test cases and related information in the TestSpark tool window.
 * It provides methods to fill the panel with the generated test cases, remove all previously shown test cases, and clear the panel.
 * The TestSparkDisplayBuilder class uses the CoverageVisualisationTabBuilder and GeneratedTestsTabBuilder classes to build and show the tabs containing the coverage visualisation and generated test data.
 *
 * @property toolWindow The ToolWindow object representing the TestSpark tool window.
 * @property contentManager The ContentManager object responsible for managing the contents of the tool window.
 * @property editor The Editor object used to display and edit the code.
 * @property coverageVisualisationTabBuilder The CoverageVisualisationTabBuilder object used to build and show the coverage visualisation tab.
 * @property generatedTestsTabBuilder The GeneratedTestsTabBuilder object used to build and show the generated tests tab.
 */
class TestSparkDisplayManager {
    private var toolWindow: ToolWindow? = null

    private var contentManager: ContentManager? = null

    private var editor: Editor? = null

    private var coverageVisualisationTabBuilder: CoverageVisualisationTabBuilder? = null
    private var generatedTestsTabBuilder: GeneratedTestsTabBuilder? = null

    /**
     * Fill the panel with the generated test cases.
     */
    fun display(
        report: Report,
        editor: Editor,
        uiContext: UIContext,
        language: SupportedLanguage,
        project: Project,
        testsExecutionResultManager: TestsExecutionResultManager,
        generationTool: GenerationTool,
    ) {
        this.toolWindow = ToolWindowManager.getInstance(project).getToolWindow("TestSpark")
        this.contentManager = toolWindow!!.contentManager

        this.editor = editor

        coverageVisualisationTabBuilder = CoverageVisualisationTabBuilder(project, editor)
        generatedTestsTabBuilder = GeneratedTestsTabBuilder(
            project,
            report,
            editor,
            uiContext,
            coverageVisualisationTabBuilder!!,
            testsExecutionResultManager,
            generationTool,
        )

        generatedTestsTabBuilder!!.show(contentManager!!, language)
        coverageVisualisationTabBuilder!!.show(report, generatedTestsTabBuilder!!.generatedTestsTabData())

        toolWindow!!.show()

        // removing all tests
        generatedTestsTabBuilder!!.getRemoveAllButton().addActionListener {
            // in case of empty list -- just call clear method
            if (generatedTestsTabBuilder!!.generatedTestsTabData().testCaseNameToPanel.isEmpty()) {
                clear()
                return@addActionListener
            }

            val parentComponent: Component? = null
            val choice = JOptionPane.showConfirmDialog(
                parentComponent,
                PluginMessagesBundle.get("removeAllMessage"),
                PluginMessagesBundle.get("confirmationTitle"),
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE,
            )

            if (choice == JOptionPane.OK_OPTION) {
                clear()
            }
        }

        generatedTestsTabBuilder!!.getApplyButton().addActionListener {
            if (generatedTestsTabBuilder!!.applyTests()) clear()
        }
    }

    fun clear() {
        editor?.markupModel?.removeAllHighlighters()

        coverageVisualisationTabBuilder?.clear()
        generatedTestsTabBuilder?.clear()

        if (contentManager != null) {
            for (content in contentManager!!.contents) {
                if (content.tabName != PluginLabelsBundle.get("descriptionWindow")) {
                    contentManager?.removeContent(content, true)
                }
            }
        }
        try {
            toolWindow?.hide()
        } catch (_: AlreadyDisposedException) {} // Make sure the process continues if the tool window is already closed
    }
}
