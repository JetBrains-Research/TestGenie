package org.jetbrains.research.testspark.display.generatedTests

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import org.jetbrains.research.testspark.bundles.plugin.PluginLabelsBundle
import org.jetbrains.research.testspark.core.data.Report
import org.jetbrains.research.testspark.core.test.SupportedLanguage
import org.jetbrains.research.testspark.data.UIContext
import org.jetbrains.research.testspark.display.coverage.CoverageVisualisationTabBuilder
import org.jetbrains.research.testspark.display.utils.ReportUpdater
import org.jetbrains.research.testspark.display.utils.java.JavaDisplayUtils
import org.jetbrains.research.testspark.display.utils.kotlin.KotlinDisplayUtils
import org.jetbrains.research.testspark.display.utils.template.DisplayUtils
import org.jetbrains.research.testspark.tools.GenerationTool
import org.jetbrains.research.testspark.tools.TestsExecutionResultManager
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.SwingConstants

/**
 * This class is responsible for building and managing the "Generated Tests" tab in TestSpark.
 * It handles the GUI components, their interactions, and the application of test cases.
 */
class GeneratedTestsTabBuilder(
    private val project: Project,
    private val report: Report,
    private val editor: Editor,
    private val uiContext: UIContext,
    private val coverageVisualisationTabBuilder: CoverageVisualisationTabBuilder,
    private val testsExecutionResultManager: TestsExecutionResultManager,
    private val generationTool: GenerationTool,
) {
    private val generatedTestsTabData: GeneratedTestsTabData = GeneratedTestsTabData()

    private var mainPanel: JPanel = JPanel()

    private var displayUtils: DisplayUtils? = null

    fun generatedTestsTabData() = generatedTestsTabData

    fun getRemoveAllButton() = generatedTestsTabData.topButtonsPanelBuilder.getRemoveAllButton()

    fun getApplyButton() = generatedTestsTabData.applyButton

    /**
     * Displays the generated tests tab in the tool window.
     * This method initializes necessary components based on the selected language and shows the tab.
     */
    fun show(contentManager: ContentManager, language: SupportedLanguage) {
        generatedTestsTabData.allTestCasePanel.removeAll()
        generatedTestsTabData.allTestCasePanel.layout =
            BoxLayout(generatedTestsTabData.allTestCasePanel, BoxLayout.Y_AXIS)
        generatedTestsTabData.testCaseNameToPanel.clear()

        generatedTestsTabData.contentManager = contentManager

        setDisplayUtils(language)

        fillMainPanel()

        fillAllTestCasePanel(language)

        addSeparator()

        createToolWindowTab()
    }

    /**
     * Sets the display utility object based on the supported language.
     *
     * @param language The programming language.
     */
    private fun setDisplayUtils(language: SupportedLanguage) {
        displayUtils = when (language) {
            SupportedLanguage.Java -> {
                JavaDisplayUtils()
            }

            SupportedLanguage.Kotlin -> {
                KotlinDisplayUtils()
            }
        }
    }

    /**
     * Initializes and fills the main panel with subcomponents.
     */
    private fun fillMainPanel() {
        mainPanel.layout = BorderLayout()

        mainPanel.add(
            generatedTestsTabData.topButtonsPanelBuilder.getPanel(project, generatedTestsTabData),
            BorderLayout.NORTH,
        )
        mainPanel.add(generatedTestsTabData.scrollPane, BorderLayout.CENTER)
        mainPanel.add(generatedTestsTabData.applyButton, BorderLayout.SOUTH)

        generatedTestsTabData.applyButton.isOpaque = false
        generatedTestsTabData.applyButton.isContentAreaFilled = false
    }

    /**
     * Initializes and fills the main panel with subcomponents.
     */
    private fun fillAllTestCasePanel(language: SupportedLanguage) {
        // TestCasePanelFactories array
        val testCasePanelFactories = arrayListOf<TestCasePanelBuilder>()

        report.testCaseList.values.forEach {
            val testCase = it
            val testCasePanel = JPanel()
            testCasePanel.layout = BorderLayout()

            // Add a checkbox to select the test
            val checkbox = JCheckBox()
            checkbox.isSelected = true
            checkbox.addItemListener {
                // Update the number of selected tests
                generatedTestsTabData.testsSelected -= (1 - 2 * checkbox.isSelected.compareTo(false))

                if (checkbox.isSelected) {
                    ReportUpdater.selectTestCase(
                        report,
                        generatedTestsTabData.unselectedTestCases,
                        testCase.id,
                        coverageVisualisationTabBuilder,
                        generatedTestsTabData,
                    )
                } else {
                    ReportUpdater.unselectTestCase(
                        report,
                        generatedTestsTabData.unselectedTestCases,
                        testCase.id,
                        coverageVisualisationTabBuilder,
                        generatedTestsTabData,
                    )
                }

                GenerateTestsTabHelper.update(generatedTestsTabData)
            }
            testCasePanel.add(checkbox, BorderLayout.WEST)

            val testCasePanelBuilder =
                TestCasePanelBuilder(
                    project,
                    language,
                    testCase,
                    editor,
                    checkbox,
                    uiContext,
                    report,
                    coverageVisualisationTabBuilder,
                    generatedTestsTabData,
                    testsExecutionResultManager,
                    generationTool,
                )
            testCasePanel.add(testCasePanelBuilder.getUpperPanel(), BorderLayout.NORTH)
            testCasePanel.add(testCasePanelBuilder.getMiddlePanel(), BorderLayout.CENTER)
            testCasePanel.add(testCasePanelBuilder.getBottomPanel(), BorderLayout.SOUTH)

            testCasePanelFactories.add(testCasePanelBuilder)

            testCasePanel.add(Box.createRigidArea(Dimension(12, 0)), BorderLayout.EAST)

            // Add panel to parent panel
            testCasePanel.maximumSize = Dimension(Short.MAX_VALUE.toInt(), Short.MAX_VALUE.toInt())
            generatedTestsTabData.allTestCasePanel.add(testCasePanel)
            addSeparator()

            generatedTestsTabData.testCaseNameToPanel[testCase.testName] = testCasePanel
            generatedTestsTabData.testCaseNameToSelectedCheckbox[testCase.testName] = checkbox
            generatedTestsTabData.testCaseNameToEditorTextField[testCase.testName] =
                testCasePanelBuilder.getEditorTextField()
        }
        generatedTestsTabData.testsSelected = generatedTestsTabData.testCaseNameToPanel.size
        generatedTestsTabData.testCasePanelFactories.addAll(testCasePanelFactories)
        generatedTestsTabData.topButtonsPanelBuilder.update(generatedTestsTabData)
    }

    /**
     * Adds a visual separator component to the panel to distinguish sections.
     */
    private fun addSeparator() {
        generatedTestsTabData.allTestCasePanel.add(Box.createRigidArea(Dimension(0, 10)))
        generatedTestsTabData.allTestCasePanel.add(JSeparator(SwingConstants.HORIZONTAL))
        generatedTestsTabData.allTestCasePanel.add(Box.createRigidArea(Dimension(0, 10)))
    }

    /**
     * Applies the selected test cases by passing them to the display utility for execution.
     */
    fun applyTests(): Boolean {
        // Filter the selected test cases
        val selectedTestCasePanels =
            generatedTestsTabData.testCaseNameToPanel.filter { (it.value.getComponent(0) as JCheckBox).isSelected }
        val selectedTestCases = selectedTestCasePanels.map { it.key }

        // Get the test case components (source code of the tests)
        val testCaseComponents = selectedTestCases
            .map { generatedTestsTabData.testCaseNameToEditorTextField[it]!! }
            .map { it.document.text }

        val applyingResult = displayUtils!!.applyTests(project, uiContext, testCaseComponents)

        // Remove the selected test cases from the cache and the tool window UI
        if (applyingResult) clear()

        return applyingResult
    }

    /**
     * Creates a new tab in the tool window for displaying the generated tests.
     */
    private fun createToolWindowTab() {
        // Remove generated tests tab from content manager if necessary
        val toolWindowManager = ToolWindowManager.getInstance(project).getToolWindow("TestSpark")
        generatedTestsTabData.contentManager = toolWindowManager!!.contentManager
        if (generatedTestsTabData.content != null) {
            generatedTestsTabData.contentManager!!.removeContent(generatedTestsTabData.content!!, true)
        }

        // If there is no generated tests tab, make it
        val contentFactory: ContentFactory = ContentFactory.getInstance()
        generatedTestsTabData.content = contentFactory.createContent(
            mainPanel,
            PluginLabelsBundle.get("generatedTests"),
            true,
        )
        generatedTestsTabData.contentManager!!.addContent(generatedTestsTabData.content!!)
        generatedTestsTabData.contentManager!!.setSelectedContent(generatedTestsTabData.content!!)

        toolWindowManager.show()
    }

    /**
     * Closes the tool window by removing the content and hiding the window.
     */
    private fun closeToolWindow() {
        try {
            generatedTestsTabData.contentManager?.removeContent(generatedTestsTabData.content!!, true)
            ToolWindowManager.getInstance(project).getToolWindow("TestSpark")?.hide()
            coverageVisualisationTabBuilder.closeToolWindowTab()
        } catch (_: AlreadyDisposedException) {
        } // Make sure the process continues if the tool window is already closed
    }

    /**
     * Clears all the generated test cases from the UI and the internal cache.
     */
    fun clear() {
        generatedTestsTabData.testCaseNameToPanel.toMap()
            .forEach { GenerateTestsTabHelper.removeTestCase(it.key, generatedTestsTabData) }
        generatedTestsTabData.testCasePanelFactories.clear()
        generatedTestsTabData.topButtonsPanelBuilder.clear(generatedTestsTabData)

        closeToolWindow()
    }
}
