package org.rsmod.content.drops.toml

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.dataformat.toml.TomlFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dtx.core.ArgMap
import dtx.core.RollResult
import dtx.core.flatten
import dtx.core.with
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.KillRollContext
import org.rsmod.api.droptable.toml.DropTableTomlParser
import org.rsmod.api.droptable.toml.DropTableTomlWriter
import org.rsmod.api.droptable.toml.TomlDropTableDef
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

class TomlAreaGateTest {
    private val mapper =
        ObjectMapper(TomlFactory())
            .registerKotlinModule()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

    private val toml =
        """
        id = "Test demon"
        npcs = ["npc.test"]

        [[tertiary]]
        numerator = 1
        denominator = 1
        obj = "obj.ashes"

        [[tertiary]]
        numerator = 1
        denominator = 1
        obj = "obj.yama_special_contract"
        area = "area.chasm_of_fire"
        """.trimIndent()

    private val areaChecker =
        mockk<AreaChecker> {
            every { inArea(any(), any()) } returns false
            every { inArea("area.chasm_of_fire", CHASM_TILE) } returns true
        }

    private val resolver = ContentDropTableTomlResolver(areaChecker, mockk(relaxed = true))

    @Test
    fun `area key parses onto tertiary and round-trips through the writer`() {
        val def = mapper.readValue<TomlDropTableDef>(toml)

        assertEquals(listOf(null, "area.chasm_of_fire"), def.tertiary.map { it.area })
        val written = DropTableTomlWriter.write(def)
        assertTrue("area = \"area.chasm_of_fire\"" in written)
        assertEquals(def, mapper.readValue<TomlDropTableDef>(written))
    }

    @Test
    fun `area-gated tertiary only rolls when the npc is inside the area`() {
        val table = DropTableTomlParser.parse(mapper.readValue(toml), resolver)

        assertEquals(setOf("obj.ashes", "obj.yama_special_contract"), rolledObjs(table, CHASM_TILE))
        assertEquals(setOf("obj.ashes"), rolledObjs(table, ELSEWHERE_TILE))
    }

    @Test
    fun `comma-separated area rolls inside any of the listed areas`() {
        val anyOf =
            """
            id = "Test giant"
            npcs = ["npc.test"]

            [[tertiary]]
            numerator = 1
            denominator = 1
            obj = "obj.cata_shard"
            area = "area.catacombs_of_kourend, area.chasm_of_fire"
            """.trimIndent()
        val table = DropTableTomlParser.parse(mapper.readValue(anyOf), resolver)

        assertEquals(setOf("obj.cata_shard"), rolledObjs(table, CHASM_TILE))
        assertEquals(emptySet<String>(), rolledObjs(table, ELSEWHERE_TILE))
    }

    private fun rolledObjs(
        table: dtx.rs.RSDropTable<Player, DropRollItem>,
        npcCoords: CoordGrid,
    ): Set<String> {
        val npc = mockk<Npc> { every { coords } returns npcCoords }
        val player = mockk<Player>(relaxed = true)
        val args = ArgMap(KillRollContext.npc with npc, KillRollContext.areaChecker with areaChecker)
        val result = table.roll(player, args).flatten()
        return when (result) {
            is RollResult.Nothing -> emptySet()
            is RollResult.Single -> setOf(result.result.obj)
            is RollResult.ListOf -> result.results.map { it.obj }.toSet()
        }
    }

    private companion object {
        val CHASM_TILE = CoordGrid(1440, 10080, 0)
        val ELSEWHERE_TILE = CoordGrid(3222, 3218, 0)
    }
}
