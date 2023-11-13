package org.jetbrains.research.testspark.settings.llm

import com.intellij.ide.ui.UINumericRange
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.EditorTextField
import com.intellij.ui.JBColor
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.FormBuilder
import org.jdesktop.swingx.JXTitledSeparator
import org.jetbrains.research.testspark.TestSparkLabelsBundle
import org.jetbrains.research.testspark.TestSparkToolTipsBundle
import org.jetbrains.research.testspark.actions.llm.addLLMPanelListeners
import org.jetbrains.research.testspark.actions.llm.isGrazieClassLoaded
import org.jetbrains.research.testspark.services.PromptParserService
import org.jetbrains.research.testspark.settings.SettingsApplicationState
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.JTextField

class SettingsLLMComponent {
    var panel: JPanel? = null

    // LLM Token
    private var llmUserTokenField = JTextField(30)

    // Models
    private val defaultModulesArray = arrayOf("")
    private var modelSelector = ComboBox(defaultModulesArray)
    private var platformSelector = ComboBox(arrayOf("OpenAI"))

    // Prompt Editor
    private var promptSeparator = JXTitledSeparator(TestSparkLabelsBundle.defaultValue("PromptSeparator"))
    private var promptEditorTabbedPane = creatTabbedPane()

    private var lastChosenModule = ""

    // Maximum number of LLM requests
    private var maxLLMRequestsField =
        JBIntSpinner(UINumericRange(SettingsApplicationState.DefaultSettingsApplicationState.maxLLMRequest, 1, 20))

    // The depth of input parameters used in class under tests
    private var maxInputParamsDepthField =
        JBIntSpinner(UINumericRange(SettingsApplicationState.DefaultSettingsApplicationState.maxInputParamsDepth, 1, 5))

    // Maximum polymorphism depth
    private var maxPolyDepthField =
        JBIntSpinner(UINumericRange(SettingsApplicationState.DefaultSettingsApplicationState.maxPolyDepth, 1, 5))

    init {
        // Adds the panel components
        createSettingsPanel()

        // Adds additional style (width, tooltips)
        stylizePanel()

        // Adds listeners
        addListeners()
    }

    fun updateHighlighting(prompt: String, editorType: PromptEditorType) {
        val editorTextField = getEditorTextField(editorType)
        service<PromptParserService>().highlighter(editorTextField, prompt)
        if (!service<PromptParserService>().isPromptValid(prompt)) {
            val border = BorderFactory.createLineBorder(JBColor.RED)
            editorTextField.border = border
        } else {
            editorTextField.border = null
        }
    }

    private fun creatTabbedPane(): JBTabbedPane {
        val tabbedPane = JBTabbedPane()

        // Add tabs for each testing level
        addPromptEditorTab(tabbedPane, PromptEditorType.CLASS)
        addPromptEditorTab(tabbedPane, PromptEditorType.METHOD)
        addPromptEditorTab(tabbedPane, PromptEditorType.LINE)

        return tabbedPane
    }

    private fun addPromptEditorTab(tabbedPane: JBTabbedPane, promptEditorType: PromptEditorType) {
        // initiate the panel
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        // Add editor text field (the prompt editor) to the panel
        val editorTextField = EditorTextField()
        editorTextField.setOneLineMode(false)
        panel.add(editorTextField)
        panel.add(JSeparator())
        // add buttons for inserting keywords to the prompt editor
        addPromptButtons(panel)
        // add the panel as a new tab
        tabbedPane.addTab(promptEditorType.text, panel)
    }

    private fun addPromptButtons(panel: JPanel) {
        val keywords = service<PromptParserService>().getKeywords()
        val editorTextField = panel.getComponent(0) as EditorTextField
        keywords.forEach {
            val btnPanel = JPanel(FlowLayout(FlowLayout.LEFT))

            val button = JButton("\$${it.text}")
            button.setForeground(JBColor.ORANGE)
            button.font = Font("Monochrome", Font.BOLD, 12)

            // add actionListener for button
            button.addActionListener { event ->
                val editor = editorTextField.editor

                editor?.let { editor ->
                    val offset = editor.caretModel.offset
                    val document = editorTextField.document
                    WriteCommandAction.runWriteCommandAction(editor.project) {
                        document.insertString(offset, "\$${it.text}")
                    }
                }
            }

            // add button and it's description to buttons panel
            btnPanel.add(button)
            btnPanel.add(JBLabel("${it.description} - ${if (it.mandatory) "mandatory" else "optional"}"))

            panel.add(btnPanel)
        }
    }

    /**
     * Adds listeners to the document of the user token field.
     * These listeners will update the model selector based on the text entered the user token field.
     */
    private fun addListeners() {
        addLLMPanelListeners(
            platformSelector,
            modelSelector,
            llmUserTokenField,
            defaultModulesArray,
            lastChosenModule,
        )

        addHighlighterListeners()
    }

    private fun addHighlighterListeners() {
        PromptEditorType.values().forEach {
            getEditorTextField(it).document.addDocumentListener(
                object : com.intellij.openapi.editor.event.DocumentListener {
                    override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
                        updateHighlighting(event.document.text, it)
                    }
                },
            )
        }
    }

    private fun stylizePanel() {
        llmUserTokenField.toolTipText = TestSparkToolTipsBundle.defaultValue("llmToken")
        maxLLMRequestsField.toolTipText = TestSparkToolTipsBundle.defaultValue("maximumNumberOfRequests")
        modelSelector.toolTipText = TestSparkToolTipsBundle.defaultValue("model")
        modelSelector.isEnabled = false
        maxInputParamsDepthField.toolTipText = TestSparkToolTipsBundle.defaultValue("parametersDepth")
        maxPolyDepthField.toolTipText = TestSparkToolTipsBundle.defaultValue("maximumPolyDepth")
        promptSeparator.toolTipText = TestSparkToolTipsBundle.defaultValue("promptEditor")
    }

    /**
     * Create the main panel for LLM-related settings page
     */
    private fun createSettingsPanel() {
        // Check if the Grazie platform access is available in the current build
        if (isGrazieClassLoaded()) {
            platformSelector.model = DefaultComboBoxModel(arrayOf("Grazie", "OpenAI"))
        } else {
            platformSelector.isEnabled = false
        }

        panel = FormBuilder.createFormBuilder()
            .addComponent(JXTitledSeparator(TestSparkLabelsBundle.defaultValue("LLMSettings")))
            .addLabeledComponent(
                JBLabel(TestSparkLabelsBundle.defaultValue("llmPlatform")),
                platformSelector,
                10,
                false,
            )
            .addLabeledComponent(
                JBLabel(TestSparkLabelsBundle.defaultValue("llmToken")),
                llmUserTokenField,
                10,
                false,
            )
            .addLabeledComponent(
                JBLabel(TestSparkLabelsBundle.defaultValue("model")),
                modelSelector,
                10,
                false,
            )
            .addLabeledComponent(
                JBLabel(TestSparkLabelsBundle.defaultValue("parametersDepth")),
                maxInputParamsDepthField,
                10,
                false,
            )
            .addLabeledComponent(
                JBLabel(TestSparkLabelsBundle.defaultValue("maximumPolyDepth")),
                maxPolyDepthField,
                10,
                false,
            )
            .addLabeledComponent(
                JBLabel(TestSparkLabelsBundle.defaultValue("maximumNumberOfRequests")),
                maxLLMRequestsField,
                10,
                false,
            )
            .addComponent(promptSeparator, 15)
            .addComponent(promptEditorTabbedPane, 15)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    private fun getEditorTextField(editorType: PromptEditorType): EditorTextField {
        return (promptEditorTabbedPane.getComponentAt(editorType.index) as JPanel).getComponent(0) as EditorTextField
    }

    var llmUserToken: String
        get() = llmUserTokenField.text
        set(newText) {
            llmUserTokenField.text = newText
        }

    var model: String
        get() = modelSelector.item
        set(newAlg) {
            lastChosenModule = newAlg
            modelSelector.item = newAlg
        }

    var llmPlatform: String
        get() = platformSelector.item
        set(newAlg) {
            platformSelector.item = newAlg
        }

    var maxLLMRequest: Int
        get() = maxLLMRequestsField.number
        set(value) {
            maxLLMRequestsField.number = value
        }

    var maxInputParamsDepth: Int
        get() = maxInputParamsDepthField.number
        set(value) {
            maxInputParamsDepthField.number = value
        }

    var maxPolyDepth: Int
        get() = maxPolyDepthField.number
        set(value) {
            maxPolyDepthField.number = value
        }

    var classPrompt: String
        get() = getEditorTextField(PromptEditorType.CLASS).document.text
        set(value) {
            ApplicationManager.getApplication().runWriteAction {
                val editorTextField =
                    getEditorTextField(PromptEditorType.CLASS)
                editorTextField.document.setText(value)
            }
        }

    var methodPrompt: String
        get() = getEditorTextField(PromptEditorType.METHOD).document.text
        set(value) {
            ApplicationManager.getApplication().runWriteAction {
                val editorTextField =
                    getEditorTextField(PromptEditorType.METHOD)
                editorTextField.document.setText(value)
            }
        }

    var linePrompt: String
        get() = getEditorTextField(PromptEditorType.LINE).document.text
        set(value) {
            ApplicationManager.getApplication().runWriteAction {
                val editorTextField =
                    getEditorTextField(PromptEditorType.LINE)
                editorTextField.document.setText(value)
            }
        }
}
