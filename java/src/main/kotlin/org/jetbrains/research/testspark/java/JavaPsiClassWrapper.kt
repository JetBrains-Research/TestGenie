package org.jetbrains.research.testspark.java

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiModifier
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.PsiTypesUtil
import org.jetbrains.research.testspark.core.data.ClassType
import org.jetbrains.research.testspark.core.utils.javaImportPattern
import org.jetbrains.research.testspark.core.utils.javaPackagePattern
import org.jetbrains.research.testspark.langwrappers.PsiClassWrapper
import org.jetbrains.research.testspark.langwrappers.PsiMethodWrapper
import org.jetbrains.research.testspark.langwrappers.strategies.JavaKotlinClassTextExtractor

class JavaPsiClassWrapper(private val psiClass: PsiClass) : PsiClassWrapper {
    override val name: String get() = psiClass.name ?: ""

    override val qualifiedName: String get() = psiClass.qualifiedName ?: ""

    override val text: String get() = psiClass.text

    override val methods: List<PsiMethodWrapper> get() = psiClass.methods.map { JavaPsiMethodWrapper(it) }

    override val allMethods: List<PsiMethodWrapper> get() = psiClass.allMethods.map { JavaPsiMethodWrapper(it) }

    override val constructorSignatures: List<String> get() = psiClass.constructors.map { JavaPsiMethodWrapper.buildSignature(it) }

    override val superClass: PsiClassWrapper? get() = psiClass.superClass?.let { JavaPsiClassWrapper(it) }

    override val virtualFile: VirtualFile get() = psiClass.containingFile.virtualFile

    override val containingFile: PsiFile get() = psiClass.containingFile

    override val fullText: String
        get() = JavaKotlinClassTextExtractor().extract(
            psiClass.containingFile,
            psiClass.text,
            javaPackagePattern,
            javaImportPattern,
        )

    override val classType: ClassType
        get() {
            if (psiClass.isInterface) {
                return ClassType.INTERFACE
            }
            if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                return ClassType.ABSTRACT_CLASS
            }
            return ClassType.CLASS
        }

    override val rBrace: Int? = psiClass.rBrace?.textRange?.startOffset

    override fun searchSubclasses(project: Project): Collection<PsiClassWrapper> {
        val scope = GlobalSearchScope.projectScope(project)
        val query = ClassInheritorsSearch.search(psiClass, scope, false)
        return query.findAll().map { JavaPsiClassWrapper(it) }
    }

    override fun getInterestingPsiClassesWithQualifiedNames(
        psiMethod: PsiMethodWrapper,
    ): MutableSet<PsiClassWrapper> {
        val interestingMethods = mutableSetOf(psiMethod as JavaPsiMethodWrapper)
        for (currentPsiMethod in allMethods) {
            if ((currentPsiMethod as JavaPsiMethodWrapper).isConstructor) interestingMethods.add(currentPsiMethod)
        }
        val interestingPsiClasses = mutableSetOf(this)
        interestingMethods.forEach { methodIt ->
            methodIt.parameterList.parameters.forEach { paramIt ->
                PsiTypesUtil.getPsiClass(paramIt.type)?.let { typeIt ->
                    JavaPsiClassWrapper(typeIt).let {
                        if (it.qualifiedName != "" && !it.qualifiedName.startsWith("java.")) {
                            interestingPsiClasses.add(it)
                        }
                    }
                }
            }
        }

        return interestingPsiClasses.toMutableSet()
    }

    /**
     * Checks if the constraints on the selected class are satisfied, so that EvoSuite can generate tests for it.
     * Namely, it is not an enum and not an anonymous inner class.
     *
     * @return true if the constraints are satisfied, false otherwise
     */
    fun isTestableClass(): Boolean {
        return !psiClass.isEnum && psiClass !is PsiAnonymousClass
    }
}
