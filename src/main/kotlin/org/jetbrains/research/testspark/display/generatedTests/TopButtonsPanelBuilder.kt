package org.jetbrains.research.testspark.display.generatedTests

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import org.jetbrains.research.testspark.bundles.plugin.PluginLabelsBundle
import org.jetbrains.research.testspark.bundles.plugin.PluginMessagesBundle
import org.jetbrains.research.testspark.core.progress.CustomProgressIndicator
import org.jetbrains.research.testspark.display.TestSparkIcons
import org.jetbrains.research.testspark.display.custom.IJProgressIndicator
import org.jetbrains.research.testspark.display.utils.IconButtonCreator
import java.awt.Dimension
import java.util.LinkedList
import java.util.Queue
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel

class TopButtonsPanelBuilder {
    private var runAllButton: JButton = createRunAllTestButton()
    private var selectAllButton: JButton =
        IconButtonCreator.getButton(TestSparkIcons.selectAll, PluginLabelsBundle.get("selectAllTip"))
    private var unselectAllButton: JButton =
        IconButtonCreator.getButton(TestSparkIcons.unselectAll, PluginLabelsBundle.get("unselectAllTip"))
    private var removeAllButton: JButton =
        IconButtonCreator.getButton(TestSparkIcons.removeAll, PluginLabelsBundle.get("removeAllTip"))

    private var testsSelectedText: String = "${PluginLabelsBundle.get("testsSelected")}: %d/%d"
    private var testsSelectedLabel: JLabel = JLabel(testsSelectedText)

    private val testsPassedText: String = "${PluginLabelsBundle.get("testsPassed")}: %d/%d"
    private var testsPassedLabel: JLabel = JLabel(testsPassedText)

    /**
     * Updates the labels.
     */
    fun update(generatedTestsTabData: GeneratedTestsTabData) {
        val passedTestsCount = generatedTestsTabData.testCasePanelFactories
            .filter { !it.isRemoved() }
            .count { it.getError()?.isEmpty() == true }

        val removedTestsCount = generatedTestsTabData.testCasePanelFactories.count { it.isRemoved() }

        if (generatedTestsTabData.testCasePanelFactories.size == removedTestsCount) {
            removeAllButton.doClick()
            return
        }
        testsSelectedLabel.text = String.format(
            testsSelectedText,
            generatedTestsTabData.testsSelected,
            generatedTestsTabData.testCaseNameToPanel.size,
        )
        testsPassedLabel.text =
            String.format(
                testsPassedText,
                passedTestsCount,
                generatedTestsTabData.testCaseNameToPanel.size,
            )
        runAllButton.isEnabled = false
        for (testCasePanelFactory in generatedTestsTabData.testCasePanelFactories) {
            runAllButton.isEnabled = runAllButton.isEnabled || testCasePanelFactory.isRunEnabled()
        }
    }

    fun getRemoveAllButton() = removeAllButton

    /**
     * Toggles check boxes so that they are either all selected or all not selected,
     *  depending on the provided parameter.
     *
     *  @param selected whether the checkboxes have to be selected or not
     */
    private fun toggleAllCheckboxes(selected: Boolean, generatedTestsTabData: GeneratedTestsTabData) {
        generatedTestsTabData.testCaseNameToPanel.forEach { (_, jPanel) ->
            val checkBox = jPanel.getComponent(0) as JCheckBox
            checkBox.isSelected = selected
        }
        generatedTestsTabData.testsSelected = if (selected) generatedTestsTabData.testCaseNameToPanel.size else 0
    }

    /**
     * Executes all test cases.
     *
     * This method presents a caution message to the user and asks for confirmation before executing the test cases.
     * If the user confirms, it iterates through each test case panel factory and runs the corresponding test.
     */
    private fun runAllTestCases(project: Project, generatedTestsTabData: GeneratedTestsTabData) {
        val choice = JOptionPane.showConfirmDialog(
            null,
            PluginMessagesBundle.get("runCautionMessage"),
            PluginMessagesBundle.get("confirmationTitle"),
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE,
        )

        if (choice == JOptionPane.CANCEL_OPTION) return

        runAllButton.isEnabled = false

        // add each test generation task to queue
        val tasks: Queue<(CustomProgressIndicator) -> Unit> = LinkedList()

        for (testCasePanelFactory in generatedTestsTabData.testCasePanelFactories) {
            testCasePanelFactory.addTask(tasks)
        }
        // run tasks one after each other
        executeTasks(project, tasks, generatedTestsTabData)
    }

    private fun executeTasks(
        project: Project,
        tasks: Queue<(CustomProgressIndicator) -> Unit>,
        generatedTestsTabData: GeneratedTestsTabData,
    ) {
        val nextTask = tasks.poll()

        nextTask?.let { task ->
            ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Test execution") {
                var globalIndicator: ProgressIndicator? = null

                override fun run(indicator: ProgressIndicator) {
                    globalIndicator = indicator
                    task(IJProgressIndicator(indicator))
                }

                override fun onFinished() {
                    super.onFinished()
                    if (globalIndicator != null && !globalIndicator!!.isCanceled) {
                        executeTasks(project, tasks, generatedTestsTabData)
                    } else {
                        if (tasks.isNotEmpty()) {
                            runAllButton.isEnabled = true
                            val firstTestPanelFactoryIndex = generatedTestsTabData.testCasePanelFactories.size - tasks.size - 1
                            val lastTestPanelFactoryIndex = generatedTestsTabData.testCasePanelFactories.size
                            for (index in firstTestPanelFactoryIndex until lastTestPanelFactoryIndex) {
                                generatedTestsTabData.testCasePanelFactories[index].removeTask()
                            }
                        }
                    }
                }
            })
        }
        if (nextTask == null) {
            generatedTestsTabData.topButtonsPanelBuilder.getRemoveAllButton().isEnabled = true
            generatedTestsTabData.applyButton.isEnabled = true
        }
    }

    fun getPanel(project: Project, generatedTestsTabData: GeneratedTestsTabData): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
        panel.preferredSize = Dimension(0, 30)
        panel.add(Box.createRigidArea(Dimension(10, 0)))
        panel.add(testsPassedLabel)
        panel.add(Box.createRigidArea(Dimension(10, 0)))
        panel.add(testsSelectedLabel)
        panel.add(Box.createHorizontalGlue())
        panel.add(runAllButton)
        panel.add(selectAllButton)
        panel.add(unselectAllButton)
        panel.add(removeAllButton)

        selectAllButton.addActionListener { toggleAllCheckboxes(true, generatedTestsTabData) }
        unselectAllButton.addActionListener { toggleAllCheckboxes(false, generatedTestsTabData) }
        runAllButton.addActionListener { runAllTestCases(project, generatedTestsTabData) }

        return panel
    }

    fun clear(generatedTestsTabData: GeneratedTestsTabData) {
        generatedTestsTabData.testCasePanelFactories.clear()
    }

    private fun createRunAllTestButton(): JButton {
        val runTestButton = JButton(PluginLabelsBundle.get("runAll"), TestSparkIcons.runTest)
        runTestButton.isOpaque = false
        runTestButton.isContentAreaFilled = false
        runTestButton.isBorderPainted = true
        return runTestButton
    }
}
