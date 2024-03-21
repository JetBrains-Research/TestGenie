package org.jetbrains.research.testspark.display

import com.intellij.lang.Language
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diff.DiffColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBColor
import com.intellij.ui.LanguageTextField
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.research.testspark.bundles.TestSparkBundle
import org.jetbrains.research.testspark.bundles.TestSparkLabelsBundle
import org.jetbrains.research.testspark.core.data.TestCase
import org.jetbrains.research.testspark.core.progress.CustomProgressIndicator
import org.jetbrains.research.testspark.core.test.data.TestSuiteGeneratedByLLM
import org.jetbrains.research.testspark.data.UIContext
import org.jetbrains.research.testspark.services.ErrorService
import org.jetbrains.research.testspark.services.JavaClassBuilderService
import org.jetbrains.research.testspark.services.ReportLockingService
import org.jetbrains.research.testspark.services.SettingsApplicationService
import org.jetbrains.research.testspark.services.TestCaseDisplayService
import org.jetbrains.research.testspark.services.TestsExecutionResultService
import org.jetbrains.research.testspark.settings.SettingsApplicationState
import org.jetbrains.research.testspark.tools.generatedTests.TestProcessor
import org.jetbrains.research.testspark.tools.llm.getClassWithTestCaseName
import org.jetbrains.research.testspark.tools.llm.test.TestSuitePresenter
import org.jetbrains.research.testspark.tools.llm.testModificationRequest
import org.jetbrains.research.testspark.tools.processStopped
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.StringSelection
import java.util.Queue
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities
import javax.swing.border.Border
import javax.swing.border.MatteBorder

class TestCasePanelFactory(
    private val project: Project,
    private val testCase: TestCase,
    editor: Editor,
    private val checkbox: JCheckBox,
    val uiContext: UIContext?,
) {
    private val settingsState: SettingsApplicationState
        get() = SettingsApplicationService.getInstance().state!!

    private val panel = JPanel()
    private val previousButtons =
        createButton(TestSparkIcons.previous, TestSparkLabelsBundle.defaultValue("previousRequest"))
    private var requestNumber: String = "%d / %d"
    private var requestLabel: JLabel = JLabel(requestNumber)
    private val nextButtons = createButton(TestSparkIcons.next, TestSparkLabelsBundle.defaultValue("nextRequest"))
    private val errorLabel = JLabel(TestSparkIcons.showError)
    private val copyButton = createButton(TestSparkIcons.copy, TestSparkLabelsBundle.defaultValue("copyTip"))
    private val likeButton = createButton(TestSparkIcons.like, TestSparkLabelsBundle.defaultValue("likeTip"))
    private val dislikeButton = createButton(TestSparkIcons.dislike, TestSparkLabelsBundle.defaultValue("dislikeTip"))

    private var allRequestsNumber = 1
    private var currentRequestNumber = 1

    private val testCaseCodeToListOfCoveredLines: HashMap<String, Set<Int>> = hashMapOf()

    private val dimensionSize = 7

    private var isRemoved = false

    // Add an editor to modify the test source code
    private val languageTextField = LanguageTextField(
        Language.findLanguageByID("JAVA"),
        editor.project,
        testCase.testCode,
        TestCaseDocumentCreator(
            getClassWithTestCaseName(testCase.testName),
        ),
        false,
    )

    private val languageTextFieldScrollPane = JBScrollPane(
        languageTextField,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS,
    )

    // Create "Remove" button to remove the test from cache
    private val removeButton = createButton(TestSparkIcons.remove, TestSparkLabelsBundle.defaultValue("removeTip"))

    // Create "Reset" button to reset the changes in the source code of the test
    private val resetButton = createButton(TestSparkIcons.reset, TestSparkLabelsBundle.defaultValue("resetTip"))

    // Create "Reset" button to reset the changes to last run in the source code of the test
    private val resetToLastRunButton =
        createButton(TestSparkIcons.resetToLastRun, TestSparkLabelsBundle.defaultValue("resetToLastRunTip"))

    // Create "Run tests" button to remove the test from cache
    private val runTestButton = createRunTestButton()

    private val requestJLabel = JLabel(TestSparkLabelsBundle.defaultValue("requestJLabel"))
    private val requestComboBox = ComboBox(arrayOf("") + Json.decodeFromString(ListSerializer(String.serializer()), settingsState.defaultLLMRequests))

    private val sendButton = createButton(TestSparkIcons.send, TestSparkLabelsBundle.defaultValue("send"))

    private val loadingLabel: JLabel = JLabel(TestSparkIcons.loading)

    private val initialCodes: MutableList<String> = mutableListOf()
    private val lastRunCodes: MutableList<String> = mutableListOf()
    private val currentCodes: MutableList<String> = mutableListOf()

    /**
     * Retrieves the upper panel for the GUI.
     *
     * This panel contains various components such as buttons, labels, and checkboxes. It is used to display information and
     * perform actions related to the GUI.
     *
     * @return The JPanel object representing the upper panel.
     */
    fun getUpperPanel(): JPanel {
        updateErrorLabel()
        panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
        panel.add(Box.createRigidArea(Dimension(checkbox.preferredSize.width, checkbox.preferredSize.height)))
        panel.add(previousButtons)
        panel.add(requestLabel)
        panel.add(nextButtons)
        panel.add(errorLabel)
        panel.add(Box.createHorizontalGlue())
        panel.add(copyButton)
        panel.add(likeButton)
        panel.add(dislikeButton)
        panel.add(Box.createRigidArea(Dimension(12, 0)))

        previousButtons.addActionListener {
            WriteCommandAction.runWriteCommandAction(project) {
                if (currentRequestNumber > 1) currentRequestNumber--
                switchToAnotherCode()
                updateRequestLabel()
            }
        }

        nextButtons.addActionListener {
            WriteCommandAction.runWriteCommandAction(project) {
                if (currentRequestNumber < allRequestsNumber) currentRequestNumber++
                switchToAnotherCode()
                updateRequestLabel()
            }
        }

        likeButton.addActionListener {
            if (likeButton.icon == TestSparkIcons.likeSelected) {
                likeButton.icon = TestSparkIcons.like
            } else if (likeButton.icon == TestSparkIcons.like) {
                likeButton.icon = TestSparkIcons.likeSelected
            }
            dislikeButton.icon = TestSparkIcons.dislike
//            TODO add implementation
        }

        dislikeButton.addActionListener {
            if (dislikeButton.icon == TestSparkIcons.dislikeSelected) {
                dislikeButton.icon = TestSparkIcons.dislike
            } else if (dislikeButton.icon == TestSparkIcons.dislike) {
                dislikeButton.icon = TestSparkIcons.dislikeSelected
            }
            likeButton.icon = TestSparkIcons.like
//            TODO add implementation
        }

        copyButton.addActionListener {
            val clipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(
                StringSelection(
                    project.service<TestCaseDisplayService>().getEditor(testCase.testName)!!.document.text,
                ),
                null,
            )
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Test case copied")
                .createNotification(
                    "",
                    TestSparkBundle.message("testCaseCopied"),
                    NotificationType.INFORMATION,
                )
                .notify(project)
        }

        updateRequestLabel()

        return panel
    }

    /**
     * Retrieves the middle panel of the application.
     * This method sets the border of the languageTextField and
     * adds it to the middlePanel with appropriate spacing.
     */
    fun getMiddlePanel(): JPanel {
        initialCodes.add(testCase.testCode)
        lastRunCodes.add(testCase.testCode)
        currentCodes.add(testCase.testCode)

        // Set border
        updateBorder()

        val panel = JPanel()

        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.add(Box.createRigidArea(Dimension(0, 5)))
        panel.add(languageTextFieldScrollPane)
        panel.add(Box.createRigidArea(Dimension(0, 5)))

        addLanguageTextFieldListener(languageTextField)

        return panel
    }

    /**
     * Returns the bottom panel.
     */
    fun getBottomPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        val requestPanel = JPanel()
        requestPanel.layout = BoxLayout(requestPanel, BoxLayout.X_AXIS)
        requestPanel.add(Box.createRigidArea(Dimension(checkbox.preferredSize.width, checkbox.preferredSize.height)))
        requestPanel.add(requestJLabel)
        requestPanel.add(Box.createRigidArea(Dimension(dimensionSize, 0)))

        // temporary panel to avoid IDEA's bug
        val requestComboBoxAndSendButtonPanel = JPanel()
        requestComboBoxAndSendButtonPanel.layout = BoxLayout(requestComboBoxAndSendButtonPanel, BoxLayout.X_AXIS)
        requestComboBoxAndSendButtonPanel.add(requestComboBox)
        requestComboBoxAndSendButtonPanel.add(Box.createRigidArea(Dimension(dimensionSize, 0)))
        requestComboBoxAndSendButtonPanel.add(sendButton)
        requestPanel.add(requestComboBoxAndSendButtonPanel)
        requestPanel.add(Box.createRigidArea(Dimension(15, 0)))

        val buttonsPanel = JPanel()
        buttonsPanel.layout = BoxLayout(buttonsPanel, BoxLayout.X_AXIS)
        buttonsPanel.add(Box.createRigidArea(Dimension(checkbox.preferredSize.width, checkbox.preferredSize.height)))
        runTestButton.isEnabled = true
        buttonsPanel.add(runTestButton)
        loadingLabel.isVisible = false
        buttonsPanel.add(loadingLabel)
        buttonsPanel.add(Box.createHorizontalGlue())
        resetButton.isEnabled = false
        buttonsPanel.add(resetButton)
        resetToLastRunButton.isEnabled = false
        buttonsPanel.add(resetToLastRunButton)
        buttonsPanel.add(removeButton)
        buttonsPanel.add(Box.createRigidArea(Dimension(12, 0)))

        panel.add(requestPanel)
        panel.add(buttonsPanel)

        runTestButton.addActionListener {
            val choice = JOptionPane.showConfirmDialog(
                null,
                TestSparkBundle.message("runCautionMessage"),
                TestSparkBundle.message("confirmationTitle"),
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE,
            )

            if (choice == JOptionPane.OK_OPTION) runTest()
        }
        resetButton.addActionListener { reset() }
        resetToLastRunButton.addActionListener { resetToLastRun() }
        removeButton.addActionListener { remove() }

        sendButton.addActionListener { sendRequest() }

        requestComboBox.isEditable = true

        return panel
    }

    /**
     * Updates the label displaying the request number information.
     * Uses the requestNumber template to format the label text.
     */
    private fun updateRequestLabel() {
        requestLabel.text = String.format(
            requestNumber,
            currentRequestNumber,
            allRequestsNumber,
        )
    }

    /**
     * Updates the error label with a new message.
     */
    private fun updateErrorLabel() {
        val error = project.service<TestsExecutionResultService>().getCurrentError(testCase.id)
        if (error.isBlank()) {
            errorLabel.isVisible = false
        } else {
            errorLabel.isVisible = true
            errorLabel.toolTipText = error
        }
    }

    /**
     * Adds a document listener to the provided LanguageTextField.
     * The listener triggers the updateUI() method whenever the document of the LanguageTextField changes.
     *
     * @param languageTextField the LanguageTextField to add the listener to
     */
    private fun addLanguageTextFieldListener(languageTextField: LanguageTextField) {
        languageTextField.document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                updateUI()
            }
        })
    }

    /**
     * Updates the user interface based on the provided code.
     */
    private fun updateUI() {
        updateTestCaseInformation()

        val lastRunCode = lastRunCodes[currentRequestNumber - 1]
        languageTextField.editor?.markupModel?.removeAllHighlighters()

        resetButton.isEnabled = testCase.testCode != initialCodes[currentRequestNumber - 1]
        resetToLastRunButton.isEnabled = testCase.testCode != lastRunCode

        val error = getError()
        if (error.isNullOrBlank()) {
            project.service<TestsExecutionResultService>().addCurrentPassedTest(testCase.id)
        } else {
            project.service<TestsExecutionResultService>().addCurrentFailedTest(testCase.id, error)
        }
        updateErrorLabel()
        runTestButton.isEnabled = (error == null)

        updateBorder()

        val modifiedLineIndexes = getModifiedLines(
            lastRunCode.split("\n"),
            testCase.testCode.split("\n"),
        )

        for (index in modifiedLineIndexes) {
            languageTextField.editor!!.markupModel.addLineHighlighter(
                DiffColors.DIFF_MODIFIED,
                index,
                HighlighterLayer.FIRST,
            )
        }

        currentCodes[currentRequestNumber - 1] = testCase.testCode

        // select checkbox
        checkbox.isSelected = true

        if (testCaseCodeToListOfCoveredLines.containsKey(testCase.testCode)) {
            testCase.coveredLines = testCaseCodeToListOfCoveredLines[testCase.testCode]!!
        } else {
            testCase.coveredLines = setOf()
        }

        project.service<ReportLockingService>().updateTestCase(testCase)
        project.service<TestCaseDisplayService>().updateUI()
    }

    /**
     * Sends a request and adds a new code created based on the request.
     * The request is obtained from the `requestField` text field.
     * The code includes the request and the working code obtained from `initialCodes` based on the current request number.
     * After adding the code, it switches to another code.
     */
    private fun sendRequest() {
        loadingLabel.isVisible = true
        enableComponents(false)

        ProgressManager.getInstance()
            .run(object : Task.Backgroundable(project, TestSparkBundle.message("sendingFeedback")) {
                override fun run(indicator: ProgressIndicator) {
                    val ijIndicator = IJProgressIndicator(indicator)

                    if (processStopped(project, ijIndicator)) return

                    val modifiedTest = testModificationRequest(
                        initialCodes[currentRequestNumber - 1],
                        requestComboBox.editor.item.toString(),
                        ijIndicator,
                        uiContext!!.requestManager!!,
                        project,
                        uiContext.testGenerationOutput,
                    )

                    if (modifiedTest != null) {
                        modifiedTest.setTestFileName(
                            getClassWithTestCaseName(testCase.testName),
                        )
                        addTest(modifiedTest)
                    } else {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("LLM Execution Error")
                            .createNotification(
                                TestSparkBundle.message("llmWarningTitle"),
                                TestSparkBundle.message("noRequestFromLLM"),
                                NotificationType.WARNING,
                            )
                            .notify(project)

                        loadingLabel.isVisible = false
                        enableComponents(true)
                    }

                    if (processStopped(project, ijIndicator)) return

                    ijIndicator.stop()
                }
            })
    }

    private fun enableComponents(isEnabled: Boolean) {
        nextButtons.isEnabled = isEnabled
        previousButtons.isEnabled = isEnabled
        runTestButton.isEnabled = isEnabled
        resetToLastRunButton.isEnabled = isEnabled
        resetButton.isEnabled = isEnabled
        removeButton.isEnabled = isEnabled
        sendButton.isEnabled = isEnabled
    }

    private fun addTest(testSuite: TestSuiteGeneratedByLLM) {
        val testSuitePresenter = TestSuitePresenter(project, uiContext!!.testGenerationOutput)

        WriteCommandAction.runWriteCommandAction(project) {
            project.service<ErrorService>().clear()
            val code = testSuitePresenter.toString(testSuite)
            testCase.testName =
                project.service<JavaClassBuilderService>()
                    .getTestMethodNameFromClassWithTestCase(testCase.testName, code)
            testCase.testCode = code

            // update numbers
            allRequestsNumber++
            currentRequestNumber = allRequestsNumber
            updateRequestLabel()

            // update lists
            initialCodes.add(code)
            lastRunCodes.add(code)
            currentCodes.add(code)

            requestComboBox.selectedItem = requestComboBox.getItemAt(0)
            sendButton.isEnabled = true

            loadingLabel.isVisible = false
            enableComponents(true)

            switchToAnotherCode()
        }
    }

    /**
     * Listens for a click event on the "Run Test" button and runs the test.
     * It updates the test case data in the workspace with the current language input
     * and test name. It disables the "Reset to Last Run" and "Run Test" buttons,
     * updates the border of the language text field with the test name, updates the error
     * label in the test case upper panel, removes all highlighters from the language text field,
     * and updates the UI.
     */
    private fun runTest() {
        if (isRemoved) return
        if (!runTestButton.isEnabled) return

        loadingLabel.isVisible = true
        enableComponents(false)

        ProgressManager.getInstance()
            .run(object : Task.Backgroundable(project, TestSparkBundle.message("sendingFeedback")) {
                override fun run(indicator: ProgressIndicator) {
                    runTest(IJProgressIndicator(indicator))
                }
            })
    }

    fun addTask(tasks: Queue<(CustomProgressIndicator) -> Unit>) {
        if (isRemoved) return
        if (!runTestButton.isEnabled) return

        loadingLabel.isVisible = true
        enableComponents(false)

        tasks.add { indicator ->
            runTest(indicator)
        }
    }

    private fun runTest(indicator: CustomProgressIndicator) {
        indicator.setText("Executing ${testCase.testName}")

        val newTestCase = TestProcessor(project)
            .processNewTestCase(
                "${project.service<JavaClassBuilderService>().getClassFromTestCaseCode(testCase.testCode)}.java",
                testCase.id,
                testCase.testName,
                testCase.testCode,
                uiContext!!.testGenerationOutput.packageLine,
                uiContext.testGenerationOutput.resultPath,
                uiContext.projectContext,
            )

        testCase.coveredLines = newTestCase.coveredLines

        testCaseCodeToListOfCoveredLines[testCase.testCode] = testCase.coveredLines

        lastRunCodes[currentRequestNumber - 1] = testCase.testCode

        loadingLabel.isVisible = false
        enableComponents(true)

        SwingUtilities.invokeLater {
            updateUI()
        }
    }

    /**
     * Resets the button listener for the reset button. When the reset button is clicked,
     * this method is called to perform the necessary actions.
     *
     * This method does the following:
     * 1. Updates the language text field with the test code from the current test case.
     * 2. Sets the border of the language text field based on the test name and test code.
     * 3. Updates the current test case in the workspace.
     * 4. Disables the reset button.
     * 5. Adds the current test to the passed or failed tests in the
     */
    private fun reset() {
        WriteCommandAction.runWriteCommandAction(project) {
            languageTextField.document.setText(initialCodes[currentRequestNumber - 1])
            currentCodes[currentRequestNumber - 1] = testCase.testCode
            lastRunCodes[currentRequestNumber - 1] = testCase.testCode

            updateUI()
        }
    }

    /**
     * Resets the language text field to the code from the last test run and updates the UI accordingly.
     */
    private fun resetToLastRun() {
        WriteCommandAction.runWriteCommandAction(project) {
            languageTextField.document.setText(lastRunCodes[currentRequestNumber - 1])
            currentCodes[currentRequestNumber - 1] = testCase.testCode

            updateUI()
        }
    }

    /**
     * Removes the button listener for the test case.
     *
     * This method is responsible for:
     * 1. Removing the highlighting of the test.
     * 2. Removing the test case from the cache.
     * 3. Updating the UI.
     */
    private fun remove() {
        // Remove the test case from the cache
        project.service<TestCaseDisplayService>().removeTestCase(testCase.testName)

        runTestButton.isEnabled = false
        isRemoved = true

        project.service<ReportLockingService>().removeTestCase(testCase)
        project.service<TestCaseDisplayService>().updateUI()
    }

    /**
     * Determines if the "Run" button is enabled.
     *
     * @return true if the "Run" button is enabled, false otherwise.
     */
    fun isRunEnabled() = runTestButton.isEnabled

    /**
     * Updates the border of the languageTextField based on the provided test name and text.
     */
    private fun updateBorder() {
        languageTextField.border = getBorder()
    }

    /**
     * Retrieves the error message for a given test case.
     *
     * @return the error message for the test case
     */
    fun getError() = project.service<TestsExecutionResultService>().getError(testCase.id, testCase.testCode)

    /**
     * Returns the border for a given test case.
     *
     * @return the border for the test case
     */
    private fun getBorder(): Border {
        val size = 3
        return when (getError()) {
            null -> JBUI.Borders.empty()
            "" -> MatteBorder(size, size, size, size, JBColor.GREEN)
            else -> MatteBorder(size, size, size, size, JBColor.RED)
        }
    }

    /**
     * Creates a button to reset the changes in the test source code.
     *
     * @return the created button
     */
    private fun createRunTestButton(): JButton {
        val runTestButton = JButton(TestSparkLabelsBundle.defaultValue("run"), TestSparkIcons.runTest)
        runTestButton.isOpaque = false
        runTestButton.isContentAreaFilled = false
        runTestButton.isBorderPainted = true
        return runTestButton
    }

    /**
     * Switches to another code in the language text field.
     * Retrieves the current request number from the test case upper panel factory and uses it to retrieve the corresponding code from the current codes array.
     * Sets the retrieved code as the text of the language text field document.
     */
    private fun switchToAnotherCode() {
        languageTextField.document.setText(currentCodes[currentRequestNumber - 1])
        updateUI()
    }

    /**
     * Checks if the item is marked as removed.
     *
     * @return true if the item is removed, false otherwise.
     */
    fun isRemoved() = isRemoved

    /**
     * Updates the current test case with the specified test name and test code.
     */
    private fun updateTestCaseInformation() {
        testCase.testName =
            project.service<JavaClassBuilderService>()
                .getTestMethodNameFromClassWithTestCase(testCase.testName, languageTextField.document.text)
        testCase.testCode = languageTextField.document.text
    }
}
