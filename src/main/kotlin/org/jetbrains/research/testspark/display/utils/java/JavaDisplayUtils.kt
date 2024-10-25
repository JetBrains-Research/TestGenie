package org.jetbrains.research.testspark.display.utils.java

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.containers.stream
import org.jetbrains.research.testspark.bundles.plugin.PluginLabelsBundle
import org.jetbrains.research.testspark.data.UIContext
import org.jetbrains.research.testspark.display.utils.ErrorMessageManager
import org.jetbrains.research.testspark.display.utils.template.DisplayUtils
import org.jetbrains.research.testspark.java.JavaPsiClassWrapper
import org.jetbrains.research.testspark.langwrappers.PsiClassWrapper
import org.jetbrains.research.testspark.testmanager.java.JavaTestAnalyzer
import org.jetbrains.research.testspark.testmanager.java.JavaTestGenerator
import java.io.File
import java.util.Locale
import javax.swing.JOptionPane

class JavaDisplayUtils : DisplayUtils {
    override fun applyTests(project: Project, uiContext: UIContext?, testCaseComponents: List<String>): Boolean {
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
                fileName = "${className.split('.')[0]}.java"
                filePath = "${chosenFile.path}/$fileName"

                // Check the correctness of a class name
                if (!Regex("[A-Z][a-zA-Z0-9]*(.java)?").matches(className)) {
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
                psiJavaFile = (PsiManager.getInstance(project).findFile(virtualFile!!) as PsiJavaFile)
                psiClass = PsiElementFactory.getInstance(project).createClass(className.split(".")[0])

                if (uiContext!!.testGenerationOutput.runWith.isNotEmpty()) {
                    psiClass!!.modifierList!!.addAnnotation("RunWith(${uiContext.testGenerationOutput.runWith})")
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
            appendTestsToClass(project, uiContext, testCaseComponents, JavaPsiClassWrapper(psiClass!!), psiJavaFile!!)
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
            PsiDocumentManager.getInstance(project).getDocument(outputFile as PsiJavaFile)!!,
        )

        // insert tests to a code
        testCaseComponents.reversed().forEach {
            val testMethodCode =
                JavaTestAnalyzer.extractFirstTestMethodCode(
                    JavaTestGenerator.formatCode(
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

        // insert imports to a code
        PsiDocumentManager.getInstance(project).getDocument(outputFile)!!.insertString(
            outputFile.importList?.startOffset ?: outputFile.packageStatement?.startOffset ?: 0,
            uiContext.testGenerationOutput.importsCode.joinToString("\n") + "\n\n",
        )

        // insert package to a code
        outputFile.packageStatement ?: PsiDocumentManager.getInstance(project).getDocument(outputFile)!!
            .insertString(
                0,
                if (uiContext.testGenerationOutput.packageName.isEmpty()) {
                    ""
                } else {
                    "package ${uiContext.testGenerationOutput.packageName};\n\n"
                },
            )
    }
}
