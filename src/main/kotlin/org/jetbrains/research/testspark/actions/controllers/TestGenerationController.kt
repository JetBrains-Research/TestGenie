package org.jetbrains.research.testspark.actions.controllers

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import org.jetbrains.research.testspark.bundles.plugin.PluginMessagesBundle
import org.jetbrains.research.testspark.core.monitor.DefaultErrorMonitor
import org.jetbrains.research.testspark.core.monitor.ErrorMonitor
import org.jetbrains.research.testspark.core.progress.CustomProgressIndicator

/**
 * Manager used for monitoring the unit test generation process.
 * It also limits TestSpark to generate tests only once at a time.
 */
class TestGenerationController {
    var indicator: CustomProgressIndicator? = null

    // errorMonitor is passed in many places in the project
    // and reflects if any bug happened in the test generation process
    val errorMonitor: ErrorMonitor = DefaultErrorMonitor()

    /**
     * Method to show notification that test generation is already running.
     */
    private fun showGenerationRunningNotification(project: Project) {
        val terminateButton: AnAction = object : AnAction("Terminate") {
            override fun actionPerformed(e: AnActionEvent) {
                indicator?.stop()
                errorMonitor.notifyErrorOccurrence()
            }
        }

        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("Execution Error")
            .createNotification(
                PluginMessagesBundle.get("alreadyRunningNotificationTitle"),
                PluginMessagesBundle.get("alreadyRunningTextNotificationText"),
                NotificationType.WARNING,
            )

        notification.addAction(terminateButton)

        notification.notify(project)
    }

    fun finished() {
        if (indicator != null &&
            indicator!!.isRunning()
        ) {
            indicator?.stop()
        }
    }

    /**
     * Check if generator is running.
     *
     * @return true if it is already running
     */
    fun isGeneratorRunning(project: Project): Boolean {
        // If indicator is null, we have never initiated an indicator before and there is no running test generation
        if (indicator == null) {
            return false
        }

        if (indicator!!.isRunning()) {
            showGenerationRunningNotification(project)
            return true
        }
        return false
    }
}
