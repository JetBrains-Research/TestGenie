package org.jetbrains.research.testspark.core.data

/**
 * Stores generated test cases and their coverage.
 * Implemented on the basis of `org.evosuite.utils.CompactReport` structure.
 *
 * `Report`'s member fields were created based on the fields in
 * `org.evosuite.utils.CompactReport` for easier transformation.
 */
open class Report {
    // Fields were created based on the fields in org.evosuite.utils.CompactReport for easier transformation
    /**
     * Unit Under Test. This variable stores the name of the class or component that is being tested.
     */
    var UUT: String = ""
    var allCoveredLines: Set<Int> = setOf()
    var allUncoveredLines: Set<Int> = setOf()
    var testCaseList: HashMap<Int, TestCase> = hashMapOf()

    /**
     * Calculates the normalized report by updating the set of all covered lines.
     *
     * @return The normalized report.
     */
    fun normalized(): Report {
        allCoveredLines = testCaseList.values.map { it.coveredLines }.flatten().toSet()
        return this
    }
}
