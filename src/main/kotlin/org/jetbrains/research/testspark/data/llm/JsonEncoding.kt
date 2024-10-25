package org.jetbrains.research.testspark.data.llm

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * JsonEncoding is required to work with an array of data in the form of strings.
 * You need to be able to concatenate this data into a single string to save it in SettingsApplicationState,
 * and convert it back to a list of strings correctly.
 */
class JsonEncoding {
    companion object {

        /**
         * Decode a string into a list of strings
         */
        fun decode(jsonString: String): MutableList<String> =
            Json.decodeFromString(ListSerializer(String.serializer()), jsonString) as MutableList<String>

        /**
         * Encode a list of strings into a string
         */
        fun encode(values: MutableList<String>): String = Json.encodeToString(
            ListSerializer(String.serializer()),
            values,
        )
    }
}
