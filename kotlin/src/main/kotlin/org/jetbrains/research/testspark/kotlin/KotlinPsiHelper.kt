package org.jetbrains.research.testspark.kotlin

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.research.testspark.core.test.SupportedLanguage
import org.jetbrains.research.testspark.core.test.data.CodeType
import org.jetbrains.research.testspark.langwrappers.CodeTypeDisplayName
import org.jetbrains.research.testspark.langwrappers.PsiClassWrapper
import org.jetbrains.research.testspark.langwrappers.PsiHelper
import org.jetbrains.research.testspark.langwrappers.PsiMethodWrapper

class KotlinPsiHelper(private val psiFile: PsiFile) : PsiHelper {

    override val language: SupportedLanguage get() = SupportedLanguage.Kotlin

    /**
     * When dealing with Kotlin PSI files, we expect that only classes, their methods,
     * top-level functions are tested.
     * Therefore, we expect either a class or a method (top-level function) to surround a cursor offset.
     *
     * This requirement ensures that the user is not trying
     * to generate tests for a line of code outside the aforementioned scopes.
     *
     * @param e `AnActionEvent` representing the current action event.
     * @return `true` if the cursor is inside a class or method, `false` otherwise.
     */
    override fun availableForGeneration(e: AnActionEvent): Boolean =
        getCurrentListOfCodeTypes(e).any { (it.first == CodeType.CLASS) || (it.first == CodeType.METHOD) }

    private val log = Logger.getInstance(this::class.java)

    override fun generateMethodDescriptor(psiMethod: PsiMethodWrapper): String {
        val methodDescriptor = psiMethod.methodDescriptor
        log.info("Method description: $methodDescriptor")
        return methodDescriptor
    }

    override fun getSurroundingClass(caretOffset: Int): PsiClassWrapper? {
        val element = psiFile.findElementAt(caretOffset)
        val cls = element?.parentOfType<KtClassOrObject>(withSelf = true)

        if (cls != null && cls.name != null && cls.fqName != null) {
            val kotlinClassWrapper = KotlinPsiClassWrapper(cls)
            log.info("Surrounding class for caret in $caretOffset is ${kotlinClassWrapper.qualifiedName}")
            return kotlinClassWrapper
        }

        log.info("No surrounding class for caret in $caretOffset")
        return null
    }

    override fun getSurroundingMethod(caretOffset: Int): PsiMethodWrapper? {
        val element = psiFile.findElementAt(caretOffset)
        val method = element?.parentOfType<KtFunction>(withSelf = true)

        if (method != null && method.name != null) {
            val wrappedMethod = KotlinPsiMethodWrapper(method)
            log.info("Surrounding method for caret at $caretOffset is ${wrappedMethod.methodDescriptor}")
            return wrappedMethod
        }

        log.info("No surrounding method for caret at $caretOffset")
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
            log.info("Line $selectedLine at caret $caretOffset is not valid")
            return null
        }
        log.info("Surrounding line at caret $caretOffset is $selectedLine")
        return selectedLine + 1
    }

    override fun collectClassesToTest(
        project: Project,
        classesToTest: MutableList<PsiClassWrapper>,
        caretOffset: Int,
        maxPolymorphismDepth: Int,
    ) {
        val cutPsiClass = getSurroundingClass(caretOffset) ?: return
        // will be null for the top level function
        var currentPsiClass = cutPsiClass
        for (index in 0 until maxPolymorphismDepth) {
            if (!classesToTest.contains(currentPsiClass)) {
                classesToTest.add(currentPsiClass)
            }

            if (currentPsiClass.superClass == null ||
                currentPsiClass.superClass!!.qualifiedName.startsWith("kotlin.")
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
        val interestingPsiClasses: MutableSet<KotlinPsiClassWrapper> = mutableSetOf()

        var currentLevelClasses = mutableListOf<PsiClassWrapper>().apply { addAll(classesToTest) }

        repeat(maxInputParamsDepth) {
            val tempListOfClasses = mutableSetOf<KotlinPsiClassWrapper>()
            currentLevelClasses.forEach { classIt ->
                classIt.methods.forEach { methodIt ->
                    (methodIt as KotlinPsiMethodWrapper).parameterList?.parameters?.forEach { paramIt ->
                        KtPsiUtil.getClassIfParameterIsProperty(paramIt)?.let { typeIt ->
                            KotlinPsiClassWrapper(typeIt).let {
                                if (!it.qualifiedName.startsWith("kotlin.")) {
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
        val interestingPsiClasses =
            cut?.getInterestingPsiClassesWithQualifiedNames(psiMethod)
                ?: (psiMethod as KotlinPsiMethodWrapper).getInterestingPsiClassesWithQualifiedNames()
        log.info("There are ${interestingPsiClasses.size} interesting psi classes from method ${psiMethod.methodDescriptor}")
        return interestingPsiClasses
    }

    override fun getCurrentListOfCodeTypes(e: AnActionEvent): List<CodeTypeDisplayName> {
        val result: ArrayList<CodeTypeDisplayName> = arrayListOf()
        val caret: Caret =
            e.dataContext.getData(CommonDataKeys.CARET)?.caretModel?.primaryCaret ?: return result

        val ktClass = getSurroundingClass(caret.offset)
        val ktFunction = getSurroundingMethod(caret.offset)
        val line: Int? = getSurroundingLineNumber(caret.offset)

        ktClass?.let { result.add(CodeType.CLASS to getClassHTMLDisplayName(it)) }
        ktFunction?.let { result.add(CodeType.METHOD to getMethodHTMLDisplayName(it)) }
        line?.let { result.add(CodeType.LINE to getLineHTMLDisplayName(it)) }

        log.info(
            "The test can be generated for: \n " +
                " 1) Class ${ktClass?.qualifiedName ?: "no class"} \n" +
                " 2) Method ${ktFunction?.name ?: "no method"} \n" +
                " 3) Line $line",
        )

        return result
    }

    override fun getPackageName() = (psiFile as KtFile).packageFqName.asString()

    override fun getModuleFromPsiFile() = ModuleUtilCore.findModuleForFile(psiFile.virtualFile, psiFile.project)!!

    override fun getDocumentFromPsiFile() = psiFile.fileDocument

    override fun getLineHTMLDisplayName(line: Int) = "<html><b><font color='orange'>line</font> $line</b></html>"

    override fun getClassHTMLDisplayName(psiClass: PsiClassWrapper): String =
        "<html><b><font color='orange'>${psiClass.classType.representation}</font> ${psiClass.qualifiedName}</b></html>"

    override fun getMethodHTMLDisplayName(psiMethod: PsiMethodWrapper): String {
        psiMethod as KotlinPsiMethodWrapper
        return when {
            psiMethod.isTopLevelFunction -> "<html><b><font color='orange'>top-level function</font> ${psiMethod.name}</b></html>"
            psiMethod.isSecondaryConstructor -> "<html><b><font color='orange'>secondary constructor</font></b></html>"
            psiMethod.isPrimaryConstructor -> "<html><b><font color='orange'>constructor</font></b></html>"
            psiMethod.isDefaultMethod -> "<html><b><font color='orange'>default method</font> ${psiMethod.name}</b></html>"
            else -> "<html><b><font color='orange'>method</font> ${psiMethod.name}</b></html>"
        }
    }
}
