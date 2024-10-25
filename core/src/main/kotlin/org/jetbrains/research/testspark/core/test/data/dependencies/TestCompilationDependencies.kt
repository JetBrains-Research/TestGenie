package org.jetbrains.research.testspark.core.test.data.dependencies

import org.jetbrains.research.testspark.core.data.JarLibraryDescriptor

/**
 * The class represents a list of dependencies required for java test compilation.
 * The libraries listed are used during test suite/test case compilation.
 */
class TestCompilationDependencies {
    companion object {
        fun getJarDescriptors() = listOf(
            JarLibraryDescriptor(
                "mockito-core-5.0.0.jar",
                "https://repo1.maven.org/maven2/org/mockito/mockito-core/5.0.0/mockito-core-5.0.0.jar",
            ),
            JarLibraryDescriptor(
                "hamcrest-core-1.3.jar",
                "https://repo1.maven.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar",
            ),
            JarLibraryDescriptor(
                "byte-buddy-1.14.6.jar",
                "https://repo1.maven.org/maven2/net/bytebuddy/byte-buddy/1.14.6/byte-buddy-1.14.6.jar",
            ),
            JarLibraryDescriptor(
                "byte-buddy-agent-1.14.6.jar",
                "https://repo1.maven.org/maven2/net/bytebuddy/byte-buddy-agent/1.14.6/byte-buddy-agent-1.14.6.jar",
            ),
            JarLibraryDescriptor(
                "opentest4j-1.1.1.jar",
                "https://repo1.maven.org/maven2/org/opentest4j/opentest4j/1.1.1/",
            ),
        )
    }
}
