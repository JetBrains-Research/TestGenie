package org.jetbrains.research.testspark.java

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiTypesUtil
import org.jetbrains.research.testspark.core.test.SupportedLanguage
import org.jetbrains.research.testspark.core.test.data.CodeType
import org.jetbrains.research.testspark.langwrappers.CodeTypeDisplayName
import org.jetbrains.research.testspark.langwrappers.PsiClassWrapper
import org.jetbrains.research.testspark.langwrappers.PsiHelper
import org.jetbrains.research.testspark.langwrappers.PsiMethodWrapper

class JavaPsiHelper(private val psiFile: PsiFile) : PsiHelper {

    override val language: SupportedLanguage get() = SupportedLanguage.Java

    /**
     * When dealing with Java PSI files, we expect that only classes and their methods are tested.
     * Therefore, we expect a **class** to surround a cursor offset.
     *
     * This requirement ensures that the user is not trying
     * to generate tests for a line of code outside the class scope.
     *
     * @param e `AnActionEvent` representing the current action event.
     * @return `true` if the cursor is inside a class, `false` otherwise.
     */
    override fun availableForGeneration(e: AnActionEvent): Boolean =
        getCurrentListOfCodeTypes(e).any { it.first == CodeType.CLASS }

    private val log = Logger.getInstance(this::class.java)

    override fun generateMethodDescriptor(psiMethod: PsiMethodWrapper): String {
        val methodDescriptor = psiMethod.methodDescriptor
        log.info("Method description: $methodDescriptor")
        return methodDescriptor
    }

    override fun getSurroundingClass(caretOffset: Int): PsiClassWrapper? {
        val classElements = PsiTreeUtil.findChildrenOfAnyType(psiFile, PsiClass::class.java)
        for (cls in classElements) {
            if (cls.containsOffset(caretOffset)) {
                val javaClassWrapper = JavaPsiClassWrapper(cls)
                if (javaClassWrapper.isTestableClass()) {
                    log.info("Surrounding class for caret in $caretOffset is ${javaClassWrapper.qualifiedName}")
                    return javaClassWrapper
                }
            }
        }
        log.info("No surrounding class for caret in $caretOffset")
        return null
    }

    override fun getSurroundingMethod(caretOffset: Int): PsiMethodWrapper? {
        val methodElements = PsiTreeUtil.findChildrenOfAnyType(psiFile, PsiMethod::class.java)
        for (method in methodElements) {
            if (method.body != null && method.containsOffset(caretOffset)) {
                val surroundingClass =
                    PsiTreeUtil.getParentOfType(method, PsiClass::class.java) ?: continue
                val surroundingClassWrapper = JavaPsiClassWrapper(surroundingClass)
                if (surroundingClassWrapper.isTestableClass()) {
                    val javaMethod = JavaPsiMethodWrapper(method)
                    log.info("Surrounding method for caret in $caretOffset is ${javaMethod.methodDescriptor}")
                    return javaMethod
                }
            }
        }
        log.info("No surrounding method for caret in $caretOffset")
        return null
    }

    override fun getSurroundingLineNumber(caretOffset: Int): Int? {
        val doc = PsiDocumentManager.getInstance(psiFile.project).getDocument(psiFile) ?: return null

        /**
         * See `getLineNumber`'s documentation for details on the numbering.
         * It returns an index of the line in the document, starting from 0.
         *
         * Therefore, we need to increase the result by one to get the line number.
         */
        val selectedLine = doc.getLineNumber(caretOffset)
        val selectedLineText =
            doc.getText(TextRange(doc.getLineStartOffset(selectedLine), doc.getLineEndOffset(selectedLine)))

        if (selectedLineText.isBlank()) {
            log.info("Line $selectedLine at caret $caretOffset is blank")
            return null
        }
        log.info("Surrounding line at caret $caretOffset is $selectedLine")
        // increase by one is necessary due to different start of numbering
        return selectedLine + 1
    }

    override fun collectClassesToTest(
        project: Project,
        classesToTest: MutableList<PsiClassWrapper>,
        caretOffset: Int,
        maxPolymorphismDepth: Int,
    ) {
        val cutPsiClass = getSurroundingClass(caretOffset)!!
        var currentPsiClass = cutPsiClass
        for (index in 0 until maxPolymorphismDepth) {
            if (!classesToTest.contains(currentPsiClass)) {
                classesToTest.add(currentPsiClass)
            }

            if (currentPsiClass.superClass == null ||
                currentPsiClass.superClass!!.qualifiedName.startsWith("java.")
            ) {
                break
            }
            currentPsiClass = currentPsiClass.superClass!!
        }
        log.info("There are ${classesToTest.size} classes to test")
    }

    override fun getInterestingPsiClassesWithQualifiedNames(
        project: Project,
        classesToTest: List<PsiClassWrapper>,
        polyDepthReducing: Int,
        maxInputParamsDepth: Int,
    ): MutableSet<PsiClassWrapper> {
        val interestingPsiClasses: MutableSet<JavaPsiClassWrapper> = mutableSetOf()

        var currentLevelClasses =
            mutableListOf<PsiClassWrapper>().apply { addAll(classesToTest) }

        repeat(maxInputParamsDepth) {
            val tempListOfClasses = mutableSetOf<JavaPsiClassWrapper>()

            currentLevelClasses.forEach { classIt ->
                classIt.methods.forEach { methodIt ->
                    (methodIt as JavaPsiMethodWrapper).parameterList.parameters.forEach { paramIt ->
                        PsiTypesUtil.getPsiClass(paramIt.type)?.let { typeIt ->
                            JavaPsiClassWrapper(typeIt).let {
                                if (!it.qualifiedName.startsWith("java.")) {
                                    interestingPsiClasses.add(it)
                                }
                            }
                        }
                    }
                }
            }
            currentLevelClasses = mutableListOf<PsiClassWrapper>().apply { addAll(tempListOfClasses) }
            interestingPsiClasses.addAll(tempListOfClasses)
        }
        log.info("There are ${interestingPsiClasses.size} interesting psi classes")
        return interestingPsiClasses.toMutableSet()
    }

    override fun getInterestingPsiClassesWithQualifiedNames(
        cut: PsiClassWrapper?,
        psiMethod: PsiMethodWrapper,
    ): MutableSet<PsiClassWrapper> {
        // The cut is always not null for Java, because all functions are always inside the class
        val interestingPsiClasses = cut!!.getInterestingPsiClassesWithQualifiedNames(psiMethod)
        log.info("There are ${interestingPsiClasses.size} interesting psi classes from method ${psiMethod.methodDescriptor}")
        return interestingPsiClasses
    }

    override fun getCurrentListOfCodeTypes(e: AnActionEvent): List<CodeTypeDisplayName> {
        val result: ArrayList<CodeTypeDisplayName> = arrayListOf()
        val caret: Caret =
            e.dataContext.getData(CommonDataKeys.CARET)?.caretModel?.primaryCaret ?: return result

        val javaPsiClassWrapped = getSurroundingClass(caret.offset) as JavaPsiClassWrapper?
        val javaPsiMethodWrapped = getSurroundingMethod(caret.offset) as JavaPsiMethodWrapper?
        val line: Int? = getSurroundingLineNumber(caret.offset)

        javaPsiClassWrapped?.let { result.add(CodeType.CLASS to getClassHTMLDisplayName(it)) }
        javaPsiMethodWrapped?.let { result.add(CodeType.METHOD to getMethodHTMLDisplayName(it)) }
        line?.let { result.add(CodeType.LINE to getLineHTMLDisplayName(it)) }

        log.info(
            "The test can be generated for: \n " +
                " 1) Class ${javaPsiClassWrapped?.qualifiedName ?: "no class"} \n" +
                " 2) Method ${javaPsiMethodWrapped?.name ?: "no method"} \n" +
                " 3) Line $line",
        )

        return result
    }

    override fun getPackageName() = (psiFile as PsiJavaFile).packageName

    override fun getModuleFromPsiFile() = ModuleUtilCore.findModuleForFile(psiFile.virtualFile, psiFile.project)!!

    override fun getDocumentFromPsiFile() = psiFile.fileDocument

    override fun getLineHTMLDisplayName(line: Int) = "<html><b><font color='orange'>line</font> $line</b></html>"

    override fun getClassHTMLDisplayName(psiClass: PsiClassWrapper): String =
        "<html><b><font color='orange'>${psiClass.classType.representation}</font> ${psiClass.qualifiedName}</b></html>"

    override fun getMethodHTMLDisplayName(psiMethod: PsiMethodWrapper): String {
        return if ((psiMethod as JavaPsiMethodWrapper).isDefaultConstructor) {
            "<html><b><font color='orange'>default constructor</font></b></html>"
        } else if (psiMethod.isConstructor) {
            "<html><b><font color='orange'>constructor</font></b></html>"
        } else if (psiMethod.isMethodDefault) {
            "<html><b><font color='orange'>default method</font> ${psiMethod.name}</b></html>"
        } else {
            "<html><b><font color='orange'>method</font> ${psiMethod.name}</b></html>"
        }
    }

    private fun PsiElement.containsOffset(caretOffset: Int): Boolean {
        return (textRange.startOffset <= caretOffset) && (textRange.endOffset >= caretOffset)
    }
}
