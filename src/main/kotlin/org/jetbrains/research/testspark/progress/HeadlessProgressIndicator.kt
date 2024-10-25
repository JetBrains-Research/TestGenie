package org.jetbrains.research.testspark.progress

import org.jetbrains.research.testspark.core.progress.CustomProgressIndicator

/**
 * A class that mocking the IDE progress indicator in headless environment.
 * It implements the CustomProgressIndicator interface.
 */
class HeadlessProgressIndicator : CustomProgressIndicator {
    override fun setText(text: String) {}

    override fun getText(): String = ""

    override fun setIndeterminate(value: Boolean) {}

    override fun isIndeterminate(): Boolean = false

    override fun setFraction(value: Double) {}

    override fun getFraction(): Double = 0.0

    override fun cancel() {}

    override fun isCanceled(): Boolean = false

    override fun isRunning(): Boolean = true

    override fun start() {}

    override fun stop() {}
}
