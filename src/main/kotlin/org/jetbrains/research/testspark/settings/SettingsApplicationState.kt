package org.jetbrains.research.testspark.settings

import org.jetbrains.research.testspark.TestSparkDefaultsBundle
import org.jetbrains.research.testspark.data.ContentDigestAlgorithm

/**
 * This class is the actual data class that stores the values of the EvoSuite Settings entries.
 */
data class SettingsApplicationState(
    var javaPath: String = DefaultSettingsApplicationState.javaPath,
    var sandbox: Boolean = DefaultSettingsApplicationState.sandbox,
    var assertions: Boolean = DefaultSettingsApplicationState.assertions,
    var seed: String = DefaultSettingsApplicationState.seed,
    var algorithm: ContentDigestAlgorithm = DefaultSettingsApplicationState.algorithm,
    var configurationId: String = DefaultSettingsApplicationState.configurationId,
    var clientOnThread: Boolean = DefaultSettingsApplicationState.clientOnThread,
    var junitCheck: Boolean = DefaultSettingsApplicationState.junitCheck,
    var criterionLine: Boolean = DefaultSettingsApplicationState.criterionLine,
    var criterionBranch: Boolean = DefaultSettingsApplicationState.criterionBranch,
    var criterionException: Boolean = DefaultSettingsApplicationState.criterionException,
    var criterionWeakMutation: Boolean = DefaultSettingsApplicationState.criterionWeakMutation,
    var criterionOutput: Boolean = DefaultSettingsApplicationState.criterionOutput,
    var criterionMethod: Boolean = DefaultSettingsApplicationState.criterionMethod,
    var criterionMethodNoException: Boolean = DefaultSettingsApplicationState.criterionMethodNoException,
    var criterionCBranch: Boolean = DefaultSettingsApplicationState.criterionCBranch,
    var minimize: Boolean = DefaultSettingsApplicationState.minimize,
    var llmUserToken: String = DefaultSettingsApplicationState.llmUserToken,
    var model: String = DefaultSettingsApplicationState.model,
    var llmPlatform: String = DefaultSettingsApplicationState.llmPlatform,
    var maxLLMRequest: Int = DefaultSettingsApplicationState.maxLLMRequest,
    var maxInputParamsDepth: Int = DefaultSettingsApplicationState.maxInputParamsDepth,
    var maxPolyDepth: Int = DefaultSettingsApplicationState.maxPolyDepth,
    var classPrompt: String = DefaultSettingsApplicationState.classPrompt,
    var methodPrompt: String = DefaultSettingsApplicationState.methodPrompt,
    var linePrompt: String = DefaultSettingsApplicationState.linePrompt,
) {

    /**
     * Default values of SettingsApplicationState.
     */
    object DefaultSettingsApplicationState {
        var javaPath: String = TestSparkDefaultsBundle.defaultValue("javaPath")
        val sandbox: Boolean = TestSparkDefaultsBundle.defaultValue("sandbox").toBoolean()
        val assertions: Boolean = TestSparkDefaultsBundle.defaultValue("assertions").toBoolean()
        val seed: String = TestSparkDefaultsBundle.defaultValue("seed")
        val junitCheck: Boolean = TestSparkDefaultsBundle.defaultValue("junitCheck").toBoolean()
        val minimize: Boolean = TestSparkDefaultsBundle.defaultValue("minimize").toBoolean()
        val algorithm: ContentDigestAlgorithm = ContentDigestAlgorithm.DYNAMOSA
        val configurationId: String = TestSparkDefaultsBundle.defaultValue("configurationId")
        val clientOnThread: Boolean = TestSparkDefaultsBundle.defaultValue("clientOnThread").toBoolean()
        val criterionLine: Boolean = TestSparkDefaultsBundle.defaultValue("criterionLine").toBoolean()
        val criterionBranch: Boolean = TestSparkDefaultsBundle.defaultValue("criterionBranch").toBoolean()
        val criterionException: Boolean = TestSparkDefaultsBundle.defaultValue("criterionException").toBoolean()
        val criterionWeakMutation: Boolean = TestSparkDefaultsBundle.defaultValue("criterionWeakMutation").toBoolean()
        val criterionOutput: Boolean = TestSparkDefaultsBundle.defaultValue("criterionOutput").toBoolean()
        val criterionMethod: Boolean = TestSparkDefaultsBundle.defaultValue("criterionMethod").toBoolean()
        val criterionMethodNoException: Boolean = TestSparkDefaultsBundle.defaultValue("criterionMethodNoException").toBoolean()
        val criterionCBranch: Boolean = TestSparkDefaultsBundle.defaultValue("criterionCBranch").toBoolean()
        val llmUserToken: String = TestSparkDefaultsBundle.defaultValue("llmToken")
        var model: String = TestSparkDefaultsBundle.defaultValue("model")
        var llmPlatform: String = TestSparkDefaultsBundle.defaultValue("llmPlatform")
        val maxLLMRequest: Int = TestSparkDefaultsBundle.defaultValue("maxLLMRequest").toInt()
        val maxInputParamsDepth: Int = TestSparkDefaultsBundle.defaultValue("maxInputParamsDepth").toInt()
        val maxPolyDepth: Int = TestSparkDefaultsBundle.defaultValue("maxPolyDepth").toInt()
        val classPrompt: String = TestSparkDefaultsBundle.defaultValue("classPrompt")
        val methodPrompt: String = TestSparkDefaultsBundle.defaultValue("methodPrompt")
        val linePrompt: String = TestSparkDefaultsBundle.defaultValue("linePrompt")
    }

    // TODO remove from here
    fun serializeChangesFromDefault(): List<String> {
        val params = mutableListOf<String>()
        // Parameters from settings menu
        if (this.sandbox != DefaultSettingsApplicationState.sandbox) {
            params.add("-Dsandbox=${this.sandbox}")
        }
        if (this.assertions != DefaultSettingsApplicationState.assertions) {
            params.add("-Dassertions=${this.assertions}")
        }
        params.add("-Dalgorithm=${this.algorithm}")
        if (this.junitCheck != DefaultSettingsApplicationState.junitCheck) {
            params.add("-Djunit_check=${this.junitCheck}")
        }
        if (this.minimize != DefaultSettingsApplicationState.minimize) {
            params.add("-Dminimize=${this.minimize}")
        }
        return params
    }
}
