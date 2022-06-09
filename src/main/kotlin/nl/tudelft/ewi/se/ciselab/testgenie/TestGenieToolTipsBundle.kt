package nl.tudelft.ewi.se.ciselab.testgenie

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

const val TOOLTIPS_BUNDLE = "defaults.Tooltips"

/**
 * Loads the tooltip texts from `defaults/Tooltips.properties` file in the `resources` directory.
 */
object TestGenieToolTipsBundle : DynamicBundle(TOOLTIPS_BUNDLE) {

    /**
     * Gets the requested default value.
     */
    @Nls
    fun defaultValue(@PropertyKey(resourceBundle = TOOLTIPS_BUNDLE) key: String): String = getMessage(key)
}
