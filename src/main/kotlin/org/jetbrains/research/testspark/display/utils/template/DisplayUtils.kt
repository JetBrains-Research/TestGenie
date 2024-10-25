package org.jetbrains.research.testspark.display.utils.template

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.research.testspark.data.UIContext
import org.jetbrains.research.testspark.langwrappers.PsiClassWrapper

/**
 * Interface for utility functions related to tests applying to the project.
 * Each language utils class implements DisplayUtils interface.
 */
interface DisplayUtils {
    /**
     * Applies specified tests to a given project.
     *
     * @returns true, if tests applying is successful, otherwise false
     */
    fun applyTests(project: Project, uiContext: UIContext?, testCaseComponents: List<String>): Boolean

    /**
     * Appends specified tests to a class within the given project.
     */
    fun appendTestsToClass(
        project: Project,
        uiContext: UIContext?,
        testCaseComponents: List<String>,
        selectedClass: PsiClassWrapper,
        outputFile: PsiFile,
    )
}
