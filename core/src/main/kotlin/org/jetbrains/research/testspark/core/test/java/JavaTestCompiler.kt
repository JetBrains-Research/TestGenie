package org.jetbrains.research.testspark.core.test.java

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.research.testspark.core.test.TestCompiler
import org.jetbrains.research.testspark.core.utils.CommandLineRunner
import org.jetbrains.research.testspark.core.utils.DataFilesUtil
import java.io.File

class JavaTestCompiler(
    libPaths: List<String>,
    junitLibPaths: List<String>,
    javaHomeDirectoryPath: String,
) : TestCompiler(libPaths, junitLibPaths) {
    private val logger = KotlinLogging.logger { this::class.java }
    private val javac: String

    // init block to find the javac compiler
    init {
        // find the proper javac
        val javaCompiler = File(javaHomeDirectoryPath).walk()
            .filter {
                val isCompilerName = if (DataFilesUtil.isWindows()) {
                    it.name.equals("javac.exe")
                } else {
                    it.name.equals("javac")
                }
                isCompilerName && it.isFile
            }
            .firstOrNull()

        if (javaCompiler == null) {
            val msg = "Cannot find java compiler 'javac' at '$javaHomeDirectoryPath'"
            logger.error { msg }
            throw RuntimeException(msg)
        }
        javac = javaCompiler.absolutePath
    }


    override fun compileCode(path: String, projectBuildPath: String): Pair<Boolean, String> {
        val classPaths = "\"${getClassPaths(projectBuildPath)}\""
        // compile file
        val errorMsg = CommandLineRunner.run(
            arrayListOf(
                javac,
                "-cp",
                classPaths,
                path,
            ),
        )

        logger.info { "Error message: '$errorMsg'" }
        // create .class file path
        val classFilePath = path.replace(".java", ".class")

        // check is .class file exists
        return Pair(File(classFilePath).exists(), errorMsg)
    }

    override fun getClassPaths(buildPath: String): String {
        var path = commonPath.plus(buildPath)

        if (path.endsWith(separator)) path = path.removeSuffix(separator.toString())

        return path
    }
}
