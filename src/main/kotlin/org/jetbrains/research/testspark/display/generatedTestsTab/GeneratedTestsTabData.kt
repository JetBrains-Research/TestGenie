package org.jetbrains.research.testspark.display.generatedTestsTab

import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import org.jetbrains.research.testspark.core.data.TestCase
import org.jetbrains.research.testspark.display.TestCasePanel
import javax.swing.JPanel

class GeneratedTestsTabData {
    val testCaseNameToPanels: HashMap<String, JPanel> = HashMap()
    var testsSelected: Int = 0
    val unselectedTestCases: HashMap<Int, TestCase> = HashMap()
    val testCasePanelFactories: ArrayList<TestCasePanel> = arrayListOf()
    var allTestCasePanel: JPanel = JPanel()
    var scrollPane: JBScrollPane = JBScrollPane(
        allTestCasePanel,
        JBScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
        JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER,
    )
    var topButtonsPanelBuilder = TopButtonsPanelBuilder()
    var contentManager: ContentManager? = null
    var content: Content? = null
}
