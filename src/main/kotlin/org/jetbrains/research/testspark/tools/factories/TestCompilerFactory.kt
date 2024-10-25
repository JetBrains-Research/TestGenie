package org.jetbrains.research.testspark.tools.factories

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.research.testspark.bundles.llm.LLMMessagesBundle
import org.jetbrains.research.testspark.core.data.JUnitVersion
import org.jetbrains.research.testspark.core.exception.JavaSDKMissingException
import org.jetbrains.research.testspark.core.test.SupportedLanguage
import org.jetbrains.research.testspark.core.test.TestCompiler
import org.jetbrains.research.testspark.core.test.java.JavaTestCompiler
import org.jetbrains.research.testspark.core.test.kotlin.KotlinTestCompiler
import org.jetbrains.research.testspark.tools.LibraryPathsProvider

object TestCompilerFactory {
    fun create(
        project: Project,
        junitVersion: JUnitVersion,
        language: SupportedLanguage,
        javaHomeDirectory: String? = null,
    ): TestCompiler {
        val libraryPaths = LibraryPathsProvider.getTestCompilationLibraryPaths()
        val junitLibraryPaths = LibraryPathsProvider.getJUnitLibraryPaths(junitVersion)

        // TODO add the warning window that for Java we always need the javaHomeDirectoryPath
        return when (language) {
            SupportedLanguage.Java -> createJavaCompiler(project, libraryPaths, junitLibraryPaths, javaHomeDirectory)
            SupportedLanguage.Kotlin -> createKotlinCompiler(libraryPaths, junitLibraryPaths)
        }
    }

    private fun createJavaCompiler(
        project: Project,
        libraryPaths: List<String>,
        junitLibraryPaths: List<String>,
        javaHomeDirectory: String? = null,
    ): JavaTestCompiler {
        val javaSDKHomePath = javaHomeDirectory
            ?: ProjectRootManager.getInstance(project).projectSdk?.homeDirectory?.path

        if (javaSDKHomePath == null) {
            throw JavaSDKMissingException(LLMMessagesBundle.get("javaSdkNotConfigured"))
        }

        return JavaTestCompiler(libraryPaths, junitLibraryPaths, javaSDKHomePath)
    }

    private fun createKotlinCompiler(
        libraryPaths: List<String>,
        junitLibraryPaths: List<String>,
    ): KotlinTestCompiler {
        // kotlinc should be under `[kotlinSDKHomeDirectory]/bin/kotlinc`
        val kotlinSDKHomeDirectory = KotlinPluginLayout.kotlinc.absolutePath
        return KotlinTestCompiler(libraryPaths, junitLibraryPaths, kotlinSDKHomeDirectory)
    }
}
