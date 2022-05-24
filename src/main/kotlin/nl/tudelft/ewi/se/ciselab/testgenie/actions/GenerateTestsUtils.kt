package nl.tudelft.ewi.se.ciselab.testgenie.actions

import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset

/**
 * This file contains some useful methods related to GenerateTests actions.
 */

/**
 * Gets the class on which the user has clicked (the click has to be inside the contents of the class).
 * NB! This has to be a concrete class, so enums, abstract classes and interfaces do not count.
 *
 * @param psiFile the current PSI file (where the user makes a click)
 * @param caret the current (primary) caret that did the click
 * @return PsiClass element if it has been found, null otherwise
 */
fun getSurroundingClass(psiFile: PsiFile, caret: Caret): PsiClass? {
    // Get the classes of the PSI file
    val classElements = PsiTreeUtil.findChildrenOfAnyType(psiFile, PsiClass::class.java)

    // Get the surrounding PSI class (i.e. the cursor has to be within that class)
    var surroundingClass: PsiClass? = null
    for (psiClass: PsiClass in classElements) {
        if (withinElement(psiClass, caret)) {
            // Check the constraints on a class
            if (!validateClass(psiClass)) continue
            surroundingClass = psiClass
        }
    }
    return surroundingClass
}

/**
 * Gets the method on which the user has clicked (the click has to be inside the contents of the method).
 *
 * @param psiFile the current PSI file (where the user makes the click)
 * @param caret the current (primary) caret that did the click
 * @return PsiMethod element if has been found, null otherwise
 */
fun getSurroundingMethod(psiFile: PsiFile, caret: Caret): PsiMethod? {
    // Get the methods of the PSI file
    val methodElements = PsiTreeUtil.findChildrenOfAnyType(psiFile, PsiMethod::class.java)

    // Get the surrounding PSI method (i.e. the cursor has to be within that method)
    var surroundingMethod: PsiMethod? = null
    for (method: PsiMethod in methodElements) {
        if (isMethodConcrete(method) && withinElement(method, caret)) {
            val surroundingClass: PsiClass = PsiTreeUtil.getParentOfType(method, PsiClass::class.java) ?: continue
            // Check the constraints on the surrounding class
            if (!validateClass(surroundingClass)) continue
            surroundingMethod = method
        }
    }
    return surroundingMethod
}

fun getSurroundingLine(doc: Document, caret: Caret): Int? {
    val line = doc.getLineNumber(caret.offset)

    val text =
        doc.getText(TextRange(doc.getLineStartOffset(line), doc.getLineEndOffset(line))).replace(Regex("[\n\t{} ]"), "")
    return if (text.isBlank()) null else line
}

/**
 * Checks if a method is concrete (non-abstract in case of an abstract class and non-default in case of an interface).
 *
 * @param psiMethod the PSI method to check
 * @return true if the method has a body (thus, is concrete), false otherwise
 */
private fun isMethodConcrete(psiMethod: PsiMethod): Boolean {
    return psiMethod.body != null
}

/**
 * Checks if the method is a default method of an interface.
 *
 * @param psiMethod the PSI method of interest
 * @return true if the method is a default method of an interface, false otherwise
 */
private fun isMethodDefault(psiMethod: PsiMethod): Boolean {
    if (!isMethodConcrete(psiMethod)) return false
    return psiMethod.containingClass?.isInterface ?: return false
}

/**
 * Checks if a PSI method is a default constructor.
 *
 * @param psiMethod the PSI method of interest
 * @return true if the PSI method is a default constructor, false otherwise
 */
private fun isDefaultConstructor(psiMethod: PsiMethod): Boolean {
    return psiMethod.isConstructor && psiMethod.body?.isEmpty ?: false
}

/**
 * Checks if a PSI class is an abstract class.
 *
 * @param psiClass the PSI class of interest
 * @return true if the PSI class is an abstract class, false otherwise
 */
private fun isAbstractClass(psiClass: PsiClass): Boolean {
    if (psiClass.isInterface) return false

    val methods = PsiTreeUtil.findChildrenOfType(psiClass, PsiMethod::class.java)
    for (psiMethod: PsiMethod in methods) {
        if (!isMethodConcrete(psiMethod)) {
            return true
        }
    }
    return false
}

/**
 * Checks if the constraints on the selected class are satisfied, so that EvoSuite can generate tests for it.
 * Namely, it is not an enum and not an anonymous inner class.
 *
 * @param psiClass the PSI class of interest
 * @return true if the constraints are satisfied, false otherwise
 */
private fun validateClass(psiClass: PsiClass): Boolean {
    return !psiClass.isEnum && psiClass !is PsiAnonymousClass
}

fun validateLine(offset: Int, psiMethod: PsiMethod, doc: Document): Boolean {
    val line: Int = doc.getLineNumber(offset)

    val openingBraceOffset: Int = psiMethod.body?.lBrace?.startOffset!!
    val closingBraceOffset: Int = psiMethod.body?.rBrace?.startOffset!!

    val openingBraceLine: Int = doc.getLineNumber(openingBraceOffset)
    val closingBraceLine: Int = doc.getLineNumber(closingBraceOffset)

    println("Opening brace offset: $openingBraceOffset")
    println("Closing brace offset: $closingBraceOffset")

    println("Opening brace line: ${openingBraceLine + 1}")
    println("Closing brace line: ${closingBraceLine + 1}")

    println(
        "Opening brace line offsets: (${doc.getLineStartOffset(openingBraceLine)},${
        doc.getLineEndOffset(
            openingBraceLine
        )
        }"
    )
    println(
        "Closing brace line offsets: (${doc.getLineStartOffset(closingBraceLine)},${
        doc.getLineEndOffset(
            closingBraceLine
        )
        }"
    )

    if (openingBraceLine == closingBraceLine) return line == openingBraceLine

    // Figure out case 3.1, 3.1.1 and 3.2
    if (openingBraceLine + 1 == closingBraceLine) {
        // do something
    }
    // ...

    if (line <= openingBraceLine) return false
    val text =
        doc.getText(TextRange(doc.getLineStartOffset(line), doc.getLineEndOffset(line))).replace(Regex("[\n\t{} ]"), "")
    return !text.isBlank()
}

/**
 * Checks if the caret is within the given PsiElement.
 *
 * @param psiElement PSI element of interest
 * @param caret the current (primary) caret that did the click
 * @return true if the caret is within the PSI element, false otherwise
 */
private fun withinElement(psiElement: PsiElement, caret: Caret): Boolean {
    return (psiElement.startOffset <= caret.offset) && (psiElement.endOffset >= caret.offset)
}

/**
 * Gets the display name of a class, depending on if it is a normal class, an abstract class or an interface.
 * This is used when displaying the name of a class in GenerateTestsActionClass menu entry.
 *
 * @param psiClass the PSI class of interest
 * @return the display name of the PSI class
 */
fun getClassDisplayName(psiClass: PsiClass): String {
    return if (psiClass.isInterface) "Interface ${psiClass.qualifiedName}"
    else if (isAbstractClass(psiClass)) "Abstract Class ${psiClass.qualifiedName}"
    else "Class ${psiClass.qualifiedName}"
}

/**
 * Gets the display name of a method, depending on if it is a (default) constructor or a normal method.
 * This is used when displaying the name of a method in GenerateTestsActionMethod menu entry.
 *
 * @param psiMethod the PSI method of interest
 * @return the display name of the PSI method
 */
fun getMethodDisplayName(psiMethod: PsiMethod): String {
    return if (isDefaultConstructor(psiMethod)) "Default Constructor"
    else if (psiMethod.isConstructor) "Constructor"
    else if (isMethodDefault(psiMethod)) "Default Method ${psiMethod.name}"
    else "Method ${psiMethod.name}"
}
