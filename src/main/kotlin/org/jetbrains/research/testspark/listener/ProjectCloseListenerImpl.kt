package org.jetbrains.research.testspark.listener

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import org.jetbrains.research.testspark.services.TestCaseDisplayService
import org.jetbrains.research.testspark.services.TestSparkTelemetryService

class ProjectCloseListenerImpl : ProjectManagerListener {
    private val log = Logger.getInstance(this.javaClass)

    /**
     * Attempts to submit the telemetry into a file when the project is closed.
     *
     * @param project the current project
     */
    override fun projectClosing(project: Project) {
        log.info("Checking generated telemetry for the project ${project.name} before closing...")

        val telemetryService = project.service<TestSparkTelemetryService>()
        telemetryService.submitModificationTelemetry()
        telemetryService.submitFeedbackTelemetry()
    }
}
