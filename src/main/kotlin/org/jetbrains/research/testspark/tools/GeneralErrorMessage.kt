package org.jetbrains.research.testspark.tools

import org.jetbrains.research.testspark.core.exception.ClassFileNotFoundException
import org.jetbrains.research.testspark.core.exception.TestCompilerException
import org.jetbrains.research.testspark.core.exception.JavaCompilerNotFoundException
import org.jetbrains.research.testspark.core.exception.JavaSDKMissingException
import org.jetbrains.research.testspark.core.exception.KotlinCompilerNotFoundException

val TestCompilerException.generalDisplayMessage: String
    get() = when (this) {
        is KotlinCompilerNotFoundException -> "General error: $message"
        is JavaCompilerNotFoundException -> ""
        is JavaSDKMissingException -> ""
        is ClassFileNotFoundException -> ""
    }