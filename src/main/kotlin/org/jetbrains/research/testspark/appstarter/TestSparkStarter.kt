package org.jetbrains.research.testspark.appstarter

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SimpleJavaSdkType
import com.intellij.openapi.projectRoots.impl.MockSdk
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.util.containers.MultiMap
import kotlinx.serialization.ExperimentalSerializationApi
import org.jetbrains.research.testspark.bundles.llm.LLMDefaultsBundle
import org.jetbrains.research.testspark.core.data.JUnitVersion
import org.jetbrains.research.testspark.core.data.TestGenerationData
import org.jetbrains.research.testspark.core.monitor.DefaultErrorMonitor
import org.jetbrains.research.testspark.data.CodeType
import org.jetbrains.research.testspark.data.FragmentToTestData
import org.jetbrains.research.testspark.data.ProjectContext
import org.jetbrains.research.testspark.data.llm.JsonEncoding
import org.jetbrains.research.testspark.helpers.psi.PsiHelperFactory
import org.jetbrains.research.testspark.progress.HeadlessProgressIndicator
import org.jetbrains.research.testspark.services.LLMSettingsService
import org.jetbrains.research.testspark.services.PluginSettingsService
import org.jetbrains.research.testspark.tools.ProjectUnderTestFileCreator
import org.jetbrains.research.testspark.tools.TestProcessor
import org.jetbrains.research.testspark.tools.ToolUtils
import org.jetbrains.research.testspark.tools.llm.Llm
import java.io.File
import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 * This class is responsible for generating and running tests based on the provided arguments in headless mode.
 */
class TestSparkStarter : ApplicationStarter {
    @Deprecated("Specify it as `id` for extension definition in a plugin descriptor")
    override val commandName: String = "testspark"

    /** Sets main (start) thread for IDE in headless as not edt. */
    override val requiredModality: Int = ApplicationStarter.NOT_IN_EDT

    @Suppress("TooGenericExceptionCaught")
    @OptIn(ExperimentalSerializationApi::class)
    override fun main(args: List<String>) {
        // Project path
        val projectPath = args[1]
        // Path to the target file (.java file)
        val cutSourceFilePath = Paths.get(projectPath, args[2]).toAbsolutePath()
        // CUT name (<package-name>.<class-name>)
        val classUnderTestName = args[3]
        // Paths to compilation output of the project under test (seperated by ':')
        val projectClassPath = args[4]
        val classPath = "$projectPath${ToolUtils.sep}$projectClassPath"
        // JUnit Version
        val jUnitVersion = args[5]
        // Selected model
        val model = args[6]
        // Token
        val token = args[7]
        // Filepath to a file containing the prompt template
        val promptTemplateFile = args[8]
        // Output directory
        val output = args[9]
        // Java directory
        val javaSDKHomePath: String = args[10]

        /**
         * Set output directory in order to save the produced prompts and LLM responses during test generation.
         * **The solution of having a public static field is bad but fast.**
         */
        ProjectUnderTestFileCreator.projectUnderTestOutputDirectory = output

        ProjectUnderTestFileCreator.log("Test generation requested for $projectPath")
        println("Test generation requested for $projectPath")

        ProjectUnderTestFileCreator.log("classPath: '$classPath'")

        ProjectUnderTestFileCreator.log("Prompt is located under file '$promptTemplateFile'")


        ApplicationManager.getApplication().invokeAndWait {
            val project = ProjectUtil.openOrImport(projectPath, null, true) ?: run {
                ProjectUnderTestFileCreator.log("Could not find project in '$projectPath'")
                println("Couldn't find project in $projectPath")
                exitProcess(1)
            }

            ProjectUnderTestFileCreator.log("Detected project: $project")
            println("Detected project: $project")
            // Continue when the project is indexed
            ProjectUnderTestFileCreator.log("Indexing project...")
            println("Indexing project...")

            project.let {
                DumbService.getInstance(it).runWhenSmart {
                    // open target file
                    val cutSourceVirtualFile = LocalFileSystem.getInstance().findFileByPath(cutSourceFilePath.toString()) ?: run {
                        println("Couldn't open file $cutSourceFilePath")
                        ProjectUnderTestFileCreator.log("Could not open file '$cutSourceFilePath'")
                        exitProcess(1)
                    }

                    // get target PsiClass
                    val psiFile = PsiManager.getInstance(project).findFile(cutSourceVirtualFile) as PsiJavaFile
                    val targetPsiClass = detectPsiClass(psiFile.classes, classUnderTestName) ?: run {
                        ProjectUnderTestFileCreator.log("Could not find '$classUnderTestName' in '$cutSourceFilePath'")
                        println("Couldn't find $classUnderTestName in $cutSourceFilePath")
                        exitProcess(1)
                    }

                    ProjectUnderTestFileCreator.log(
                        "PsiClass '${targetPsiClass.qualifiedName}' is detected! Start the test generation process.")
                    println("PsiClass ${targetPsiClass.qualifiedName} is detected! Start the test generation process.")

                    // Get project SDK
                    val projectSDK = getProjectSdk(project, javaSDKHomePath)
                    // update settings
                    val settingsState = project.getService(LLMSettingsService::class.java).state
                    settingsState.currentLLMPlatformName = LLMDefaultsBundle.get("grazieName")
                    settingsState.grazieToken = token
                    settingsState.grazieModel = model
                    settingsState.classPrompts = JsonEncoding.encode(mutableListOf(File(promptTemplateFile).readText()))
                    settingsState.junitVersion = when (jUnitVersion.filter { it.isDigit() }) {
                        "4" -> JUnitVersion.JUnit4
                        "5" -> JUnitVersion.JUnit5
                        else -> {
                            throw IllegalArgumentException("JUnit version $jUnitVersion is not supported. Supported JUnit versions are '4' and '5'")
                        }
                    }
                    project.service<PluginSettingsService>().state.buildPath = classPath

                    // Prepare Project Context
                    // First, get CUT Module
                    val cutModule =
                        ProjectFileIndex.getInstance(project)
                            .getModuleForFile(targetPsiClass.containingFile.virtualFile)!!
                    // Then, instantiate the project context
                    val projectContext = ProjectContext(
                        classPath,
                        output,
                        targetPsiClass.qualifiedName,
                        cutModule,
                    )
                    // Prepare the test generation data
                    val testGenerationData = TestGenerationData(
                        resultPath = output,
                        testResultName = "HeadlessGeneratedTests",
                    )
                    ProjectUnderTestFileCreator.log("Indexing is done")
                    println("[TestSpark Starter] Indexing is done")

                    // get package name
                    val packageList = targetPsiClass.qualifiedName.toString().split(".").dropLast(1).toMutableList()
                    val packageName = packageList.joinToString(".")

                    // Get PsiHelper
                    val psiHelper = PsiHelperFactory.getPsiHelper(psiFile)!!
                    // Create a process Manager
                    val llmProcessManager = Llm()
                        .getLLMProcessManager(
                            project,
                            psiHelper,
                            targetPsiClass.textRange.startOffset,
                            testSamplesCode = "", // we don't provide samples to LLM
                            projectSDK = projectSDK
                        )

                    ProjectUnderTestFileCreator.log("[TestSpark Starter] Starting the test generation process")
                    println("[TestSpark Starter] Starting the test generation process")
                    // Start test generation
                    val indicator = HeadlessProgressIndicator()
                    val errorMonitor = DefaultErrorMonitor()
                    val uiContext = llmProcessManager.runTestGenerator(
                        indicator,
                        FragmentToTestData(CodeType.CLASS),
                        packageName,
                        projectContext,
                        testGenerationData,
                        errorMonitor,
                    )

                    // Check test Generation Output
                    if (uiContext != null) {
                        ProjectUnderTestFileCreator.log("[TestSpark Starter] Test generation completed successfully")
                        println("[TestSpark Starter] Test generation completed successfully")
                        // Run test file
                        runTestsWithCoverageCollection(project, output, packageList, classPath, projectContext, projectSDK)
                    } else {
                        ProjectUnderTestFileCreator.log("[TestSpark Starter] Test generation failed")
                        println("[TestSpark Starter] Test generation failed")
                    }

                    ProjectManager.getInstance().closeAndDispose(project)

                    ProjectUnderTestFileCreator.log("[TestSpark Starter] Exiting the headless mode")
                    println("[TestSpark Starter] Exiting the headless mode")
                    exitProcess(0)
                }
            }
        }
    }

    /**
     * Retrieves the project SDK based on the provided parameters.
     *
     * @param project the project inder test
     * @param javaSDKHomePath the path to the Java SDK home directory provided by user in headless mode
     * @return the project SDK for running and compiling tests generated in headless mode
     */
    private fun getProjectSdk(project: Project, javaSDKHomePath: String): Sdk {
        return when {
            // User provided a java sdk home path
            javaSDKHomePath.isNotBlank() -> MockSdk(
                "Provided Mock SDK",
                javaSDKHomePath,
                "",
                MultiMap.create(),
                SimpleJavaSdkType(),
            )
            // IntelliJ did not recognize the SDK and user also did not provide the sdk
            ProjectRootManager.getInstance(project).projectSdk == null -> MockSdk(
                "JavaHome Mock SDK",
                LocalFileSystem.getInstance().findFileByPath(System.getProperty("java.home"))!!.path,
                "",
                MultiMap.create(),
                SimpleJavaSdkType(),
            )
            // IntelliJ managed to recognize the sdk
            else -> ProjectRootManager.getInstance(project).projectSdk!!
        }
    }

    private fun runTestsWithCoverageCollection(
        project: Project,
        out: String,
        packageList: MutableList<String>,
        classPath: String,
        projectContext: ProjectContext,
        projectSDK: Sdk
    ) {
        val targetDirectory = "$out${File.separator}${packageList.joinToString(File.separator)}"

        ProjectUnderTestFileCreator.log("Collecting coverage for tests in '$targetDirectory")
        println("Run tests in $targetDirectory")

        File(targetDirectory).walk().forEach {
            ProjectUnderTestFileCreator.log("Considering file '${it.name}'")

            if (it.name.endsWith(".class")) {
                ProjectUnderTestFileCreator.log("Running test '${it.name}'")
                println("Running test ${it.name}")

                // TODO: test case may start with un uppercase letter!
                var testcaseName = it.nameWithoutExtension.removePrefix("Generated")
                testcaseName = testcaseName[0].lowercaseChar() + testcaseName.substring(1)
                // The current test is compiled and is ready to run jacoco
                val testExecutionError = TestProcessor(project, projectSDK).createXmlFromJacoco(
                    it.nameWithoutExtension,
                    "$targetDirectory${File.separator}jacoco-${it.nameWithoutExtension}",
                    testcaseName,
                    classPath,
                    packageList.joinToString("."),
                    out,
                    projectContext,
                )
                // Saving exception (if exists) thrown during the test execution
                saveException(testcaseName, targetDirectory, testExecutionError)
            }
        }
    }

    private fun saveException(
        testcaseName: String,
        targetDirectory: String,
        testExecutionError: String,
    ) {
        if (testExecutionError.isBlank() || !testExecutionError.contains("Exception", ignoreCase = false)) {
            return
        }
        val targetPath = Paths.get(targetDirectory, "$testcaseName-exception.log")

        // Save the exception
        targetPath.toFile().writeText(testExecutionError.replace("\tat ", "\nat "))
    }

    private fun detectPsiClass(classes: Array<PsiClass>, classUnderTestName: String): PsiClass? {
        for (psiClass in classes) {
            if (psiClass.qualifiedName == classUnderTestName) {
                return psiClass
            }
        }
        return null
    }
}
