package org.jetbrains.research.testspark.helpers

import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.EditorTextField
import com.intellij.ui.JBColor
import org.jetbrains.research.testspark.core.generation.llm.prompt.PromptKeyword
import java.awt.Font

object PromptParserHelper {
    fun highlighter(
        textField: EditorTextField,
        prompt: String,
    ): EditorTextField {
        val attributes = TextAttributes(JBColor.ORANGE, null, null, null, Font.BOLD or Font.ITALIC)

        val editor = textField.editor
        var markup: MarkupModel? = null
        if (editor != null) {
            markup = textField.editor!!.markupModel
            markup.removeAllHighlighters()
        }

        PromptKeyword.entries.forEach {
            it.getOffsets(prompt)?.let { offsets ->
                val startOffset = offsets.first
                val endOffset = offsets.second

                if (editor != null) {
                    markup!!.addRangeHighlighter(
                        startOffset,
                        endOffset,
                        HighlighterLayer.LAST,
                        attributes,
                        HighlighterTargetArea.EXACT_RANGE,
                    )
                } else {
                    textField.addSettingsProvider { textFieldSettings ->
                        textFieldSettings.markupModel.addRangeHighlighter(
                            startOffset,
                            endOffset,
                            HighlighterLayer.LAST,
                            attributes,
                            HighlighterTargetArea.EXACT_RANGE,
                        )
                    }
                }
            }
        }

        return textField
    }

    fun getKeywords(): Array<PromptKeyword> {
        return PromptKeyword.entries.toTypedArray()
    }

    fun isPromptValid(prompt: String): Boolean {
        PromptKeyword.entries.forEach {
            if (it.mandatory) {
                if (!prompt.contains(it.variable)) {
                    return false
                }
            }
        }
        return true
    }
}
