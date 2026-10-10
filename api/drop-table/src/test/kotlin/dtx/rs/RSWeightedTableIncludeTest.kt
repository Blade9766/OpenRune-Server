package dtx.rs

import dtx.core.ArgMap
import dtx.core.RollResult
import dtx.core.Rollable
import dtx.core.flatten
import dtx.core.singleRollable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RSWeightedTableIncludeTest {
    private fun excluded(name: String): Rollable<String, String> =
        singleRollable {
            shouldInclude { _, _ -> false }
            selectResult { _, _ -> RollResult.Single(name) }
        }

    @Test
    fun `table with every entry excluded rolls nothing`() {
        val table =
            rsWeightedTable<String, String> {
                3 weight excluded("a")
                5 weight excluded("b")
            }

        repeat(1_000) { assertTrue(table.roll("p", ArgMap.Empty) is RollResult.Nothing) }
    }

    @Test
    fun `single entry table respects includeInRoll`() {
        val table = rsWeightedTable<String, String> { 1 weight excluded("a") }

        repeat(100) { assertTrue(table.roll("p", ArgMap.Empty) is RollResult.Nothing) }
    }

    @Test
    fun `single included entry always rolls`() {
        val table = rsWeightedTable<String, String> { 1 weight "a" }

        repeat(100) { assertEquals(RollResult.Single("a"), table.roll("p", ArgMap.Empty).flatten()) }
    }

    @Test
    fun `excluded entries never roll and the rest keep their ratio`() {
        val table =
            rsWeightedTable<String, String> {
                64 weight excluded("x")
                48 weight "a"
                16 weight "b"
            }
        val samples = 200_000
        val counts = HashMap<String, Int>()
        repeat(samples) {
            val result = table.roll("p", ArgMap.Empty).flatten()
            if (result is RollResult.Single) {
                counts.merge(result.result, 1, Int::plus)
            }
        }

        assertFalse("x" in counts)
        assertEquals(samples, counts.values.sum())
        assertEquals(0.75, counts.getValue("a").toDouble() / samples, 0.005)
    }

    @Test
    fun `empty table has no max roll and rolls nothing`() {
        val table = RSWeightedTable.Empty<String, String>()

        assertEquals(0.0, table.maxRoll)
        assertTrue(table.roll("p", ArgMap.Empty) is RollResult.Nothing)
    }
}
