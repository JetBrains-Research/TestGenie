package org.jetbrains.research.testspark.display.utils.kotlin

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.containers.stream
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.research.testspark.bundles.plugin.PluginLabelsBundle
import org.jetbrains.research.testspark.data.UIContext
import org.jetbrains.research.testspark.display.utils.ErrorMessageManager
import org.jetbrains.research.testspark.display.utils.template.DisplayUtils
import org.jetbrains.research.testspark.kotlin.KotlinPsiClassWrapper
import org.jetbrains.research.testspark.langwrappers.PsiClassWrapper
import org.jetbrains.research.testspark.testmanager.kotlin.KotlinTestAnalyzer
import org.jetbrains.research.testspark.testmanager.kotlin.KotlinTestGenerator
import java.io.File
import java.util.Locale
import javax.swing.JOptionPane

class KotlinDisplayUtils : DisplayUtils {
    override fun applyTests(project: Project, uiContext: UIContext?, testCaseComponents: List<String>): Boolean {
        val descriptor = FileChooserDescriptor(true, true, false, false, false, false)

        // Apply filter with folders and java files with main class
        WriteCommandAction.runWriteCommandAction(project) {
            descriptor.withFileFilter { file ->
                file.isDirectory || (
                    file.extension?.lowercase(Locale.getDefault()) == "kotlin" && (
                        PsiManager.getInstance(project).findFile(file!!) as KtFile
                        ).classes.stream().map { it.name }
                        .toArray()
                        .contains(
                            (
                                PsiManager.getInstance(project)
                                    .findFile(file) as PsiJavaFile
                                ).name.removeSuffix(".kt"),
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
        if (fileChooser.isEmpty()) return false

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
        var ktClass: KtClass? = null

        /**
         * PsiJavaFile of a final java file
         */
        var psiKotlinFile: KtFile? = null

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
                        PluginLabelsBundle.get("optionPaneMessage"),
                        PluginLabelsBundle.get("optionPaneTitle"),
                        JOptionPane.PLAIN_MESSAGE,
                        null,
                        null,
                        null,
                    )

                // Cancel button pressed
                jOptionPane ?: return false

                // Get class name from user
                className = jOptionPane as String

                // Set file name and file path
                fileName = "${className.split('.')[0]}.kt"
                filePath = "${chosenFile.path}/$fileName"

                // Check the correctness of a class name
                if (!Regex("[A-Z][a-zA-Z0-9]*(.kt)?").matches(className)) {
                    ErrorMessageManager.showErrorWindow(PluginLabelsBundle.get("incorrectFileNameMessage"))
                    continue
                }

                // Check the existence of a file with this name
                if (File(filePath).exists()) {
                    ErrorMessageManager.showErrorWindow(PluginLabelsBundle.get("fileAlreadyExistsMessage"))
                    continue
                }
                break
            }

            // Create new file and set services of this file
            WriteCommandAction.runWriteCommandAction(project) {
                chosenFile.createChildData(null, fileName)
                virtualFile = VirtualFileManager.getInstance().findFileByUrl("file://$filePath")!!
                psiKotlinFile = (PsiManager.getInstance(project).findFile(virtualFile!!) as KtFile)

                val ktPsiFactory = KtPsiFactory(project)
                ktClass = ktPsiFactory.createClass("class ${className.split(".")[0]} {}")

                if (uiContext!!.testGenerationOutput.runWith.isNotEmpty()) {
                    val annotationEntry =
                        ktPsiFactory.createAnnotationEntry("@RunWith(${uiContext.testGenerationOutput.runWith})")
                    ktClass!!.addBefore(annotationEntry, ktClass!!.body)
                }

                psiKotlinFile!!.add(ktClass!!)
            }
        } else {
            // Set services of the chosen file
            virtualFile = chosenFile
            psiKotlinFile = (PsiManager.getInstance(project).findFile(virtualFile!!) as KtFile)
            val classNameNoSuffix = psiKotlinFile!!.name.removeSuffix(".kt")
            ktClass = psiKotlinFile?.declarations?.filterIsInstance<KtClass>()?.find { it.name == classNameNoSuffix }
        }

        // Add tests to the file
        WriteCommandAction.runWriteCommandAction(project) {
            appendTestsToClass(
                project,
                uiContext,
                testCaseComponents,
                KotlinPsiClassWrapper(ktClass as KtClass),
                psiKotlinFile!!,
            )
        }

        // Open the file after adding
        FileEditorManager.getInstance(project).openTextEditor(
            OpenFileDescriptor(project, virtualFile!!),
            true,
        )

        return true
    }

    override fun appendTestsToClass(
        project: Project,
        uiContext: UIContext?,
        testCaseComponents: List<String>,
        selectedClass: PsiClassWrapper,
        outputFile: PsiFile,
    ) {
        // block document
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(
            PsiDocumentManager.getInstance(project).getDocument(outputFile as KtFile)!!,
        )

        // insert tests to a code
        testCaseComponents.reversed().forEach {
            val testMethodCode =
                KotlinTestAnalyzer.extractFirstTestMethodCode(
                    KotlinTestGenerator.formatCode(
                        project,
                        it.replace("\r\n", "\n")
                            .replace("verifyException(", "// verifyException("),
                        uiContext!!.testGenerationOutput,
                    ),
                )
                    // Fix Windows line separators
                    .replace("\r\n", "\n")

            PsiDocumentManager.getInstance(project).getDocument(outputFile)!!.insertString(
                selectedClass.rBrace!!,
                testMethodCode,
            )
        }

        // insert other info to a code
        PsiDocumentManager.getInstance(project).getDocument(outputFile)!!.insertString(
            selectedClass.rBrace!!,
            uiContext!!.testGenerationOutput.otherInfo + "\n",
        )

        // Create the imports string
        val importsString = uiContext.testGenerationOutput.importsCode.joinToString("\n") + "\n\n"

        // Find the insertion offset
        val insertionOffset = outputFile.importList?.startOffset
            ?: outputFile.packageDirective?.endOffset
            ?: 0

        // Insert the imports into the document
        PsiDocumentManager.getInstance(project).getDocument(outputFile)?.let { document ->
            document.insertString(insertionOffset, importsString)
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }

        val packageName = uiContext.testGenerationOutput.packageName
        val packageStatement = if (packageName.isEmpty()) "" else "package $packageName\n\n"

        // Insert the package statement at the beginning of the document
        PsiDocumentManager.getInstance(project).getDocument(outputFile)?.let { document ->
            document.insertString(0, packageStatement)
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
    }
}
