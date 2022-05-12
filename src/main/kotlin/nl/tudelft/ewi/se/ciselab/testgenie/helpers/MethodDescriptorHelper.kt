package nl.tudelft.ewi.se.ciselab.testgenie.helpers

import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.util.containers.stream
import java.util.stream.Collectors

// Grammar taken from: https://docs.oracle.com/javase/specs/jvms/se11/html/jvms-4.html#jvms-4.3

fun generateMethodDescriptor(psiMethod: PsiMethod): String {
    val parameterTypes = psiMethod.getSignature(PsiSubstitutor.EMPTY)
        .parameterTypes
        .stream()
        .map { i -> generateFieldType(i) }
        .collect(Collectors.joining())

    val returnType = generateReturnDescriptor(psiMethod)

    return "${psiMethod.name}($parameterTypes)$returnType"
}

fun generateReturnDescriptor(psiMethod: PsiMethod): String {
    if (psiMethod.returnType == null) {
        // void method
        return "V"
    }

    return generateFieldType(psiMethod.returnType!!)
}

fun generateFieldType(psiType: PsiType): String {
    // arrays (ArrayType)
    if (psiType.arrayDimensions > 0) {
        val arrayType = generateFieldType(psiType.deepComponentType)
        return "[".repeat(psiType.arrayDimensions) + arrayType
    }

    //  objects (ObjectType)
    if (psiType is PsiClassType) {
        val classType = psiType.resolve()
        if (classType != null) {
            val className = classType.qualifiedName?.replace('.', '/')

            // no need to handle generics: they are not part of method descriptors

            return "L$className;"
        }
    }

    // primitives (BaseType)
    psiType.canonicalText.let {
        return when (it) {
            "int" -> "I"
            "long" -> "J"
            "float" -> "F"
            "double" -> "D"
            "boolean" -> "Z"
            "byte" -> "B"
            "char" -> "C"
            "short" -> "S"
            else -> throw IllegalArgumentException("Unknown type: $it")
        }
    }
}
