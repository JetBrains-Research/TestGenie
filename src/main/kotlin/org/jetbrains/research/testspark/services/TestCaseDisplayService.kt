package org.jetbrains.research.testspark.services

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.refactoring.suggested.startOffset
import com.intellij.ui.EditorTextField
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import com.intellij.util.containers.stream
import org.jetbrains.research.testspark.bundles.TestSparkLabelsBundle
import org.jetbrains.research.testspark.bundles.TestSparkToolTipsBundle
import org.jetbrains.research.testspark.data.UIContext
import org.jetbrains.research.testspark.display.TestCasePanelFactory
import org.jetbrains.research.testspark.display.TopButtonsPanelFactory
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.io.File
import java.util.Locale
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.SwingConstants

@Service(Service.Level.PROJECT)
class TestCaseDisplayService(private val project: Project) {

    private var mainPanel: JPanel = JPanel()

    private val topButtonsPanelFactory = TopButtonsPanelFactory(project)

    private var applyButton: JButton = JButton(TestSparkLabelsBundle.defaultValue("applyButton"))

    private var allTestCasePanel: JPanel = JPanel()

    private var scrollPane: JBScrollPane = JBScrollPane(
        allTestCasePanel,
        JBScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
        JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER,
    )

    private var testCasePanels: HashMap<String, JPanel> = HashMap()

    private var testsSelected: Int = 0

    /**
     * Default color for the editors in the tool window
     */
    private var defaultEditorColor: Color? = null

    /**
     * Content Manager to be able to add / remove tabs from tool window
     */
    private var contentManager: ContentManager? = null

    /**
     * Variable to keep reference to the coverage visualisation content
     */
    private var content: Content? = null

    var uiContext: UIContext? = null

    init {
        allTestCasePanel.layout = BoxLayout(allTestCasePanel, BoxLayout.Y_AXIS)
        mainPanel.layout = BorderLayout()

        mainPanel.add(topButtonsPanelFactory.getPanel(), BorderLayout.NORTH)
        mainPanel.add(scrollPane, BorderLayout.CENTER)

        applyButton.isOpaque = false
        applyButton.isContentAreaFilled = false
        mainPanel.add(applyButton, BorderLayout.SOUTH)

        applyButton.addActionListener { applyTests() }
    }

    /**
     * Fill the panel with the generated test cases. Remove all previously shown test cases.
     * Add Tests and their names to a List of pairs (used for highlighting)
     */
    fun displayTestCases() {
        val report = project.service<ReportLockingService>().getReport()
        val editor = project.service<EditorService>().editor!!

        allTestCasePanel.removeAll()
        testCasePanels.clear()

        addSeparator()

        // TestCasePanelFactories array
        val testCasePanelFactories = arrayListOf<TestCasePanelFactory>()

        report.testCaseList.values.forEach {
            val testCase = it
            val testCasePanel = JPanel()
            testCasePanel.layout = BorderLayout()

            // Add a checkbox to select the test
            val checkbox = JCheckBox()
            checkbox.isSelected = true
            checkbox.addItemListener {
                // Update the number of selected tests
                testsSelected -= (1 - 2 * checkbox.isSelected.compareTo(false))

                if (checkbox.isSelected) {
                    project.service<ReportLockingService>().selectTestCase(testCase.id)
                } else {
                    project.service<ReportLockingService>().unselectTestCase(testCase.id)
                }

                updateUI()
            }
            testCasePanel.add(checkbox, BorderLayout.WEST)

            val testCasePanelFactory = TestCasePanelFactory(project, testCase, editor, checkbox, uiContext)
            testCasePanel.add(testCasePanelFactory.getUpperPanel(), BorderLayout.NORTH)
            testCasePanel.add(testCasePanelFactory.getMiddlePanel(), BorderLayout.CENTER)
            testCasePanel.add(testCasePanelFactory.getBottomPanel(), BorderLayout.SOUTH)

            testCasePanelFactories.add(testCasePanelFactory)

            testCasePanel.add(Box.createRigidArea(Dimension(12, 0)), BorderLayout.EAST)

            // Add panel to parent panel
            testCasePanel.maximumSize = Dimension(Short.MAX_VALUE.toInt(), Short.MAX_VALUE.toInt())
            allTestCasePanel.add(testCasePanel)
            addSeparator()
            testCasePanels[testCase.testName] = testCasePanel
        }

        // Update the number of selected tests (all tests are selected by default)
        testsSelected = testCasePanels.size

        topButtonsPanelFactory.setTestCasePanelFactoriesArray(testCasePanelFactories)
        topButtonsPanelFactory.updateTopLabels()

        createToolWindowTab()
    }

    /**
     * Adds a separator to the allTestCasePanel.
     */
    private fun addSeparator() {
        allTestCasePanel.add(Box.createRigidArea(Dimension(0, 10)))
        allTestCasePanel.add(JSeparator(SwingConstants.HORIZONTAL))
        allTestCasePanel.add(Box.createRigidArea(Dimension(0, 10)))
    }

    /**
     * Highlight the mini-editor in the tool window whose name corresponds with the name of the test provided
     *
     * @param name name of the test whose editor should be highlighted
     */
    fun highlightTestCase(name: String) {
        val myPanel = testCasePanels[name] ?: return
        openToolWindowTab()
        scrollToPanel(myPanel)

        val editor = getEditor(name) ?: return
        val settingsProjectState = project.service<SettingsProjectService>().state
        val highlightColor =
            JBColor(
                TestSparkToolTipsBundle.defaultValue("colorName"),
                Color(
                    settingsProjectState.colorRed,
                    settingsProjectState.colorGreen,
                    settingsProjectState.colorBlue,
                    30,
                ),
            )
        if (editor.background.equals(highlightColor)) return
        defaultEditorColor = editor.background
        editor.background = highlightColor
        returnOriginalEditorBackground(editor)
    }

    /**
     * Method to open the toolwindow tab with generated tests if not already open.
     */
    private fun openToolWindowTab() {
        val toolWindowManager = ToolWindowManager.getInstance(project).getToolWindow("TestSpark")
        contentManager = toolWindowManager!!.contentManager
        if (content != null) {
            toolWindowManager.show()
            toolWindowManager.contentManager.setSelectedContent(content!!)
        }
    }

    /**
     * Scrolls to the highlighted panel.
     *
     * @param myPanel the panel to scroll to
     */
    private fun scrollToPanel(myPanel: JPanel) {
        var sum = 0
        for (component in allTestCasePanel.components) {
            if (component == myPanel) {
                break
            } else {
                sum += component.height
            }
        }
        val scroll = scrollPane.verticalScrollBar
        scroll.value = (scroll.minimum + scroll.maximum) * sum / allTestCasePanel.height
    }

    /**
     * Removes all coverage highlighting from the editor.
     */
    private fun removeAllHighlights() {
        project.service<EditorService>().editor?.markupModel?.removeAllHighlighters()
    }

    /**
     * Reset the provided editors color to the default (initial) one after 10 seconds
     * @param editor the editor whose color to change
     */
    private fun returnOriginalEditorBackground(editor: EditorTextField) {
        Thread {
            Thread.sleep(10000)
            editor.background = defaultEditorColor
        }.start()
    }

    /**
     * Highlight a range of editors
     * @param names list of test names to pass to highlight function
     */
    fun highlightCoveredMutants(names: List<String>) {
        names.forEach {
            highlightTestCase(it)
        }
    }

    /**
     * Show a dialog where the user can select what test class the tests should be applied to,
     * and apply the selected tests to the test class.
     */
    private fun applyTests() {
        // Filter the selected test cases
        val selectedTestCasePanels = testCasePanels.filter { (it.value.getComponent(0) as JCheckBox).isSelected }
        val selectedTestCases = selectedTestCasePanels.map { it.key }

        // Get the test case components (source code of the tests)
        val testCaseComponents = selectedTestCases
            .map { getEditor(it)!! }
            .map { it.document.text }

        // Descriptor for choosing folders and java files
        val descriptor = FileChooserDescriptor(true, true, false, false, false, false)

        // Apply filter with folders and java files with main class
        WriteCommandAction.runWriteCommandAction(project) {
            descriptor.withFileFilter { file ->
                file.isDirectory || (
                    file.extension?.lowercase(Locale.getDefault()) == "java" && (
                        PsiManager.getInstance(project).findFile(file!!) as PsiJavaFile
                        ).classes.stream().map { it.name }
                        .toArray()
                        .contains(
                            (
                                PsiManager.getInstance(project)
                                    .findFile(file) as PsiJavaFile
                                ).name.removeSuffix(".java"),
                        )
                    )
            }
        }

        val fileChooser = FileChooser.chooseFiles(
            descriptor,
            project,
            LocalFileSystem.getInstance().findFileByPath(project.basePath!!),
        )

        /**
         * Cancel button pressed
         */
        if (fileChooser.isEmpty()) return

        /**
         * Chosen files by user
         */
        val chosenFile = fileChooser[0]

        /**
         * Virtual file of a final java file
         */
        var virtualFile: VirtualFile? = null

        /**
         * PsiClass of a final java file
         */
        var psiClass: PsiClass? = null

        /**
         * PsiJavaFile of a final java file
         */
        var psiJavaFile: PsiJavaFile? = null

        if (chosenFile.isDirectory) {
            // Input new file data
            var className: String
            var fileName: String
            var filePath: String
            // Waiting for correct file name input
            while (true) {
                val jOptionPane =
                    JOptionPane.showInputDialog(
                        null,
                        TestSparkLabelsBundle.defaultValue("optionPaneMessage"),
                        TestSparkLabelsBundle.defaultValue("optionPaneTitle"),
                        JOptionPane.PLAIN_MESSAGE,
                        null,
                        null,
                        null,
                    )

                // Cancel button pressed
                jOptionPane ?: return

                // Get class name from user
                className = jOptionPane as String

                // Set file name and file path
                fileName = "${className.split('.')[0]}.java"
                filePath = "${chosenFile.path}/$fileName"

                // Check the correctness of a class name
                if (!Regex("[A-Z][a-zA-Z0-9]*(.java)?").matches(className)) {
                    showErrorWindow(TestSparkLabelsBundle.defaultValue("incorrectFileNameMessage"))
                    continue
                }

                // Check the existence of a file with this name
                if (File(filePath).exists()) {
                    showErrorWindow(TestSparkLabelsBundle.defaultValue("fileAlreadyExistsMessage"))
                    continue
                }
                break
            }

            // Create new file and set services of this file
            WriteCommandAction.runWriteCommandAction(project) {
                chosenFile.createChildData(null, fileName)
                virtualFile = VirtualFileManager.getInstance().findFileByUrl("file://$filePath")!!
                psiJavaFile = (PsiManager.getInstance(project).findFile(virtualFile!!) as PsiJavaFile)
                psiClass = PsiElementFactory.getInstance(project).createClass(className.split(".")[0])

                if (uiContext!!.testGenerationOutput.runWith.isNotEmpty()) {
                    psiClass!!.modifierList!!.addAnnotation("RunWith(${uiContext!!.testGenerationOutput.runWith})")
                }

                psiJavaFile!!.add(psiClass!!)
            }
        } else {
            // Set services of the chosen file
            virtualFile = chosenFile
            psiJavaFile = (PsiManager.getInstance(project).findFile(virtualFile!!) as PsiJavaFile)
            psiClass = psiJavaFile!!.classes[
                psiJavaFile!!.classes.stream().map { it.name }.toArray()
                    .indexOf(psiJavaFile!!.name.removeSuffix(".java")),
            ]
        }

        // Add tests to the file
        WriteCommandAction.runWriteCommandAction(project) {
            appendTestsToClass(testCaseComponents, psiClass!!, psiJavaFile!!)
        }

        // Remove the selected test cases from the cache and the tool window UI
        removeSelectedTestCases(selectedTestCasePanels)

        // Open the file after adding
        FileEditorManager.getInstance(project).openTextEditor(
            OpenFileDescriptor(project, virtualFile!!),
            true,
        )
    }

    private fun showErrorWindow(message: String) {
        JOptionPane.showMessageDialog(
            null,
            message,
            TestSparkLabelsBundle.defaultValue("errorWindowTitle"),
            JOptionPane.ERROR_MESSAGE,
        )
    }

    /**
     * Retrieve the editor corresponding to a particular test case
     *
     * @param testCaseName the name of the test case
     * @return the editor corresponding to the test case, or null if it does not exist
     */
    fun getEditor(testCaseName: String): EditorTextField? {
        val middlePanelComponent = testCasePanels[testCaseName]?.getComponent(2) ?: return null
        val middlePanel = middlePanelComponent as JPanel
        return (middlePanel.getComponent(1) as JBScrollPane).viewport.view as EditorTextField
    }

    /**
     * Append the provided test cases to the provided class.
     *
     * @param testCaseComponents the test cases to be appended
     * @param selectedClass the class which the test cases should be appended to
     * @param outputFile the output file for tests
     */
    private fun appendTestsToClass(testCaseComponents: List<String>, selectedClass: PsiClass, outputFile: PsiJavaFile) {
        // block document
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(
            PsiDocumentManager.getInstance(project).getDocument(outputFile)!!,
        )

        // insert tests to a code
        testCaseComponents.reversed().forEach {
            val testMethodCode = project
                .service<JavaClassBuilderService>()
                .getTestMethodCodeFromClassWithTestCase(
                    project.service<JavaClassBuilderService>().formatJavaCode(
                        it.replace("\r\n", "\n")
                            .replace("verifyException(", "// verifyException("),
                        uiContext!!.testGenerationOutput,
                    ),
                )
                // Fix Windows line separators
                .replace("\r\n", "\n")

            PsiDocumentManager.getInstance(project).getDocument(outputFile)!!.insertString(
                selectedClass.rBrace!!.textRange.startOffset,
                testMethodCode,
            )
        }

        // insert other info to a code
        PsiDocumentManager.getInstance(project).getDocument(outputFile)!!.insertString(
            selectedClass.rBrace!!.textRange.startOffset,
            uiContext!!.testGenerationOutput.otherInfo + "\n",
        )

        // insert imports to a code
        PsiDocumentManager.getInstance(project).getDocument(outputFile)!!.insertString(
            outputFile.importList?.startOffset ?: outputFile.packageStatement?.startOffset ?: 0,
            uiContext!!.testGenerationOutput.importsCode.joinToString("\n") + "\n\n",
        )

        // insert package to a code
        outputFile.packageStatement ?: PsiDocumentManager.getInstance(project).getDocument(outputFile)!!
            .insertString(
                0,
                if (uiContext!!.testGenerationOutput.packageLine.isEmpty()) {
                    ""
                } else {
                    "package ${uiContext!!.testGenerationOutput.packageLine};\n\n"
                },
            )
    }

    /**
     * Utility function that returns the editor for a specific file url,
     * in case it is opened in the IDE
     */
    fun updateEditorForFileUrl(fileUrl: String) {
        val documentManager = FileDocumentManager.getInstance()
        // https://intellij-support.jetbrains.com/hc/en-us/community/posts/360004480599/comments/360000703299
        FileEditorManager.getInstance(project).selectedEditors.map { it as TextEditor }.map { it.editor }.map {
            val currentFile = documentManager.getFile(it.document)
            if (currentFile != null) {
                if (currentFile.presentableUrl == fileUrl) {
                    project.service<EditorService>().editor = it
                }
            }
        }
    }

    /**
     * Creates a new toolWindow tab for the coverage visualisation.
     */
    private fun createToolWindowTab() {
        // Remove generated tests tab from content manager if necessary
        val toolWindowManager = ToolWindowManager.getInstance(project).getToolWindow("TestSpark")
        contentManager = toolWindowManager!!.contentManager
        if (content != null) {
            contentManager!!.removeContent(content!!, true)
        }

        // If there is no generated tests tab, make it
        val contentFactory: ContentFactory = ContentFactory.getInstance()
        content = contentFactory.createContent(
            mainPanel,
            TestSparkLabelsBundle.defaultValue("generatedTests"),
            true,
        )
        contentManager!!.addContent(content!!)

        // Focus on generated tests tab and open toolWindow if not opened already
        contentManager!!.setSelectedContent(content!!)
        toolWindowManager.show()
    }

    /**
     * Closes the tool window and destroys the content of the tab.
     */
    private fun closeToolWindow() {
        contentManager?.removeContent(content!!, true)
        ToolWindowManager.getInstance(project).getToolWindow("TestSpark")?.hide()
        val coverageVisualisationService = project.service<CoverageVisualisationService>()
        coverageVisualisationService.closeToolWindowTab()
    }

    /**
     * Removes the selected tests from the cache, removes all the highlights from the editor and closes the tool window.
     * This function is called when the user clicks "Apply to test suite" button,
     *  and it is also called with all test cases as selected when the user clicks "Remove All" button.
     *
     * @param selectedTestCasePanels the panels of the selected tests
     */
    private fun removeSelectedTestCases(selectedTestCasePanels: Map<String, JPanel>) {
        selectedTestCasePanels.forEach { removeTestCase(it.key) }
        removeAllHighlights()
        closeToolWindow()
    }

    fun clear() {
        // Remove the tests
        val testCasePanelsToRemove = testCasePanels.toMap()
        removeSelectedTestCases(testCasePanelsToRemove)

        topButtonsPanelFactory.clear()
    }

    /**
     * A helper method to remove a test case from the cache and from the UI.
     *
     * @param testCaseName the name of the test
     */
    fun removeTestCase(testCaseName: String) {
        // Update the number of selected test cases if necessary
        if ((testCasePanels[testCaseName]!!.getComponent(0) as JCheckBox).isSelected) {
            testsSelected--
        }

        // Remove the test panel from the UI
        allTestCasePanel.remove(testCasePanels[testCaseName])

        // Remove the test panel
        testCasePanels.remove(testCaseName)
    }

    /**
     * Updates the user interface of the tool window.
     *
     * This method updates the UI of the tool window tab by calling the updateUI
     * method of the allTestCasePanel object and the updateTopLabels method
     * of the topButtonsPanel object. It also checks if there are no more tests remaining
     * and closes the tool window if that is the case.
     */
    fun updateUI() {
        // Update the UI of the tool window tab
        allTestCasePanel.updateUI()

        topButtonsPanelFactory.updateTopLabels()

        // If no more tests are remaining, close the tool window
        if (testCasePanels.size == 0) closeToolWindow()
    }

    /**
     * Retrieves the list of test case panels.
     *
     * @return The list of test case panels.
     */
    fun getTestCasePanels() = testCasePanels

    /**
     * Retrieves the currently selected tests.
     *
     * @return The list of tests currently selected.
     */
    fun getTestsSelected() = testsSelected

    /**
     * Sets the number of tests selected.
     *
     * @param testsSelected The number of tests selected.
     */
    fun setTestsSelected(testsSelected: Int) {
        this.testsSelected = testsSelected
    }
}
