package org.jetbrains.research.testspark.core.progress

interface CustomProgressIndicator {
    fun setText(text: String)
    fun getText(): String
    fun setIndeterminate(value: Boolean)
    fun isIndeterminate(): Boolean
    fun setFraction(value: Double)
    fun getFraction(): Double
    fun cancel()
    fun isCanceled(): Boolean
    fun start()
    fun stop()
    fun isRunning(): Boolean
}
