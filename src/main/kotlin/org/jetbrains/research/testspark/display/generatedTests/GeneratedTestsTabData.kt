package org.jetbrains.research.testspark.display.generatedTests

import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import org.jetbrains.research.testspark.bundles.plugin.PluginLabelsBundle
import org.jetbrains.research.testspark.core.data.TestCase
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JPanel

class GeneratedTestsTabData {
    val testCaseNameToPanel: HashMap<String, JPanel> = HashMap()
    val testCaseNameToSelectedCheckbox: HashMap<String, JCheckBox> = HashMap()
    val testCaseNameToEditorTextField: HashMap<String, EditorTextField> = HashMap()
    var testsSelected: Int = 0
    val unselectedTestCases: HashMap<Int, TestCase> = HashMap()
    val testCasePanelFactories: ArrayList<TestCasePanelBuilder> = arrayListOf()
    var allTestCasePanel: JPanel = JPanel()
    val applyButton: JButton = JButton(PluginLabelsBundle.get("applyButton"))
    var scrollPane: JBScrollPane = JBScrollPane(
        allTestCasePanel,
        JBScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
        JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER,
    )
    var topButtonsPanelBuilder = TopButtonsPanelBuilder()
    var contentManager: ContentManager? = null
    var content: Content? = null
}
