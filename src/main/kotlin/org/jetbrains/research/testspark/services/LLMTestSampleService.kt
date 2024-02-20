package org.jetbrains.research.testspark.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.util.containers.stream

@Service(Service.Level.PROJECT)
class LLMTestSampleService {
    private var testSample: String? = null

    fun setTestSample(testSample: String?) {
        this.testSample = testSample
    }

    fun getTestSample(): String = testSample ?: ""

    /**
     * Retrieves a list of test samples from the given project.
     *
     * @return A list of strings, representing the names of the test samples.
     */
    fun collectTestSamples(project: Project, testNames: MutableList<String>, initialTestCodes: MutableList<String>, currentTestCodes: MutableList<String>) {
        val projectFileIndex: ProjectFileIndex = ProjectRootManager.getInstance(project).fileIndex
        val javaFileType: FileType = FileTypeManager.getInstance().getFileTypeByExtension("java")

        projectFileIndex.iterateContent { file ->
            if (file.fileType === javaFileType) {
                val psiJavaFile = (PsiManager.getInstance(project).findFile(file) as PsiJavaFile)
                val psiClass = psiJavaFile.classes[
                    psiJavaFile.classes.stream().map { it.name }.toArray()
                        .indexOf(psiJavaFile.name.removeSuffix(".java")),
                ]
                val imports = psiJavaFile.importList?.allImportStatements?.map { it.text }?.toList()
                    ?.joinToString("\n") ?: ""
                psiClass.allMethods.forEach { method ->
                    val annotations = method.modifierList.annotations
                    annotations.forEach { annotation ->
                        if (annotation.qualifiedName == "org.junit.jupiter.api.Test" || annotation.qualifiedName == "org.junit.Test") {
                            val code: String = createTestSampleClass(imports, method.text)
                            testNames.add(createMethodName(method.name))
                            initialTestCodes.add(code)
                            currentTestCodes.add(code)
                        }
                    }
                }
            }
            true
        }
    }

    fun createTestSampleClass(imports: String, methodCode: String): String {
        var normalizedImports = imports
        if (normalizedImports.isNotBlank()) normalizedImports += "\n\n"
        return normalizedImports +
            "public class TestSample {\n" +
            "   $methodCode\n" +
            "}"
    }

    fun createMethodName(methodName: String): String =
        "<html><b><font color='orange'>method</font> $methodName</b></html>"
}