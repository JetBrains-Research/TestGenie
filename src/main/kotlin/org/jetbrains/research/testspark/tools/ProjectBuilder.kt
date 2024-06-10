package org.jetbrains.research.testspark.tools

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.task.ProjectTaskManager
import com.intellij.util.concurrency.Semaphore
import org.jetbrains.research.testspark.bundles.plugin.PluginMessagesBundle
import org.jetbrains.research.testspark.core.monitor.ErrorMonitor
import org.jetbrains.research.testspark.core.progress.CustomProgressIndicator
import org.jetbrains.research.testspark.core.utils.DataFilesUtil
import org.jetbrains.research.testspark.services.PluginSettingsService
import org.jetbrains.research.testspark.settings.plugin.PluginSettingsState
import java.util.concurrent.CountDownLatch

/**
 * This class builds the project before running EvoSuite and before validating the tests.
 */
class ProjectBuilder(private val project: Project, private val errorMonitor: ErrorMonitor) {
    private val pluginSettingsState: PluginSettingsState
        get() = project.getService(PluginSettingsService::class.java).state

    private val log = Logger.getInstance(this::class.java)

    private val builderTimeout: Long = 12000000

    private val projectPath: String = ProjectRootManager.getInstance(project).contentRoots.first().path

    /**
     * Runs the build process.
     *
     * @param indicator The progress indicator to show the build progress.
     * @return True if the build is successful, false otherwise.
     */
    fun runBuild(indicator: CustomProgressIndicator): Boolean {
        val handle = CountDownLatch(1)
        log.info("Starting build!")
        var isSuccessful = true

        try {
            indicator.setIndeterminate(true)
            indicator.setText(PluginMessagesBundle.get("buildMessage"))

            if (pluginSettingsState.buildCommand.isEmpty()) {
                // User did not put own command line
                val promise = ProjectTaskManager.getInstance(project).buildAllModules()
                val finished = Semaphore()
                finished.down()
                promise.onSuccess {
                    if (it.isAborted || it.hasErrors()) {
                        errorProcess()
                        isSuccessful = false
                    }
                    finished.up()
                }
                promise.onError {
                    errorProcess()
                    isSuccessful = false
                    finished.up()
                }
                finished.waitFor()
            } else {
                // User put own command line
                // Save all open editors
                val cmd = ArrayList<String>()

                if (DataFilesUtil.isWindows()) {
                    cmd.add("cmd.exe")
                    cmd.add("/c")
                } else {
                    cmd.add("sh")
                    cmd.add("-c")
                }

                cmd.add(pluginSettingsState.buildCommand)

                val cmdString = cmd.fold(String()) { acc, e -> acc.plus(e).plus(" ") }
                log.info("Starting build process with arguments: $cmdString")

                val buildProcess = GeneralCommandLine(cmd)
                buildProcess.setWorkDirectory(projectPath)
                val handler = OSProcessHandler(buildProcess)
                handler.startNotify()

                if (!handler.waitFor(builderTimeout)) {
                    errorProcess()
                    isSuccessful = false
                }

                if (indicator.isCanceled()) {
                    return false
                }

                val exitCode = handler.exitCode

                if (exitCode != 0) {
                    errorProcess()
                    isSuccessful = false
                }
                handle.countDown()
            }
        } catch (e: Exception) {
            errorProcess()
            e.printStackTrace()
            isSuccessful = false
        }
        log.info("Build finished!")
        return isSuccessful
    }

    private fun errorProcess() {
        if (errorMonitor.notifyErrorOccurrence()) {
            NotificationGroupManager.getInstance().getNotificationGroup("Build Execution Error").createNotification(
                PluginMessagesBundle.get("buildErrorTitle"),
                PluginMessagesBundle.get("commonBuildErrorMessage"),
                NotificationType.ERROR,
            ).notify(project)
        }
    }
}
