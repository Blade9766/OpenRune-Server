package org.rsmod.content.quest.area.burghderott.inaid

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.baseVar
import dev.openrune.types.varp.bits
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.table.QuestRow
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_COMPLETE
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.StepValidator
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Pins the cache and map facts In Aid of the Myreque is written against: the quest row, the
 * varbits and the multilocs and multinpcs they drive, the npcs' combat levels, where every loc the
 * scripts touch stands, that every tile a player or npc is put on is open ground, and that the
 * rubble piles, the gate, the ambush clearing and the tomb join up as the scripts assume.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class InAidOfTheMyrequeCacheTest {

    @Test fun `the quest row matches the stages, requirements and rewards`() {
        val row = QuestRow.getRow("dbrow.${InAidOfTheMyrequeQuest.QUEST_KEY}".asRSCM())
        assertEquals(STAGE_COMPLETE, row.endstate)
        assertEquals(2, row.questpoints)
        assertEquals(mapOf("crafting" to 25, "mining" to 15, "magic" to 7), row.requirementStats.associate { it.t0.displayName to it.t1 })
        assertEquals(listOf("In Search of the Myreque"), row.requirementQuests.map { it.displayname })
        assertEquals(
            mapOf("attack" to 20000, "strength" to 20000, "crafting" to 20000, "defence" to 20000),
            row.statXpAwarded.associate { it.t0.displayName to it.t1 },
        )
        val stage = checkNotNull(ServerCacheManager.getVarbit("varbit.myreque_2_quest".asRSCM(RSCMType.VARBIT)))
        assertTrue(STAGE_COMPLETE < (1 shl stage.bits.count()), "the endstate fits the stage varbit")
    }

    @Test fun `the server flags use free bits of myreque2_extravar and every quest varp persists`() {
        val extra = "varp.myreque2_extravar".asRSCM(RSCMType.VARP)
        val ours = SERVER_VARBITS.map { it.asRSCM(RSCMType.VARBIT) }.toSet()
        val taken = HashSet<Int>()
        for ((id, varbit) in ServerCacheManager.getVarbits()) {
            if (varbit.baseVar.id == extra && id !in ours) taken += varbit.bits
        }
        for (name in SERVER_VARBITS) {
            val varbit = checkNotNull(ServerCacheManager.getVarbit(name.asRSCM(RSCMType.VARBIT))) { name }
            assertEquals(extra, varbit.baseVar.id, name)
            assertFalse(varbit.bits.any { it in taken }, name)
        }
        assertEquals(15, checkNotNull(ServerCacheManager.getVarbit(InAidOfTheMyrequeQuest.RUBBLE_REMOVED.asRSCM(RSCMType.VARBIT))).bits.count())
        for (varp in listOf("varp.myreque_2_main_var", "varp.myreque2_multivar", "varp.myreque2_extravar")) {
            assertEquals(VarpLifetime.Perm, ServerCacheManager.getVarp(varp.asRSCM(RSCMType.VARP))!!.scope, varp)
        }
    }

    @Test fun `every multiloc and multinpc follows the varbit the scripts set`() {
        assertMultiloc("loc.burgh_inn_colapsed_wall_multiloc", InAidOfTheMyrequeQuest.INN_WALL, "loc.burgh_inn_rubble_blocked", "loc.burgh_inn_rubble_cleared")
        assertMultiloc("loc.burgh_inn_trapdoor_multiloc", InAidOfTheMyrequeQuest.INN_TRAPDOOR, InnCellar.TRAPDOOR_CLOSED, InnCellar.TRAPDOOR_OPEN)
        assertMultiloc("loc.burgh_general_store_roof_multiloc", InAidOfTheMyrequeQuest.STORE_ROOF, GeneralStore.ROOF_HOLE, "loc.burgh_store_roof_fixed")
        assertMultiloc("loc.burgh_general_store_wall_multiloc", InAidOfTheMyrequeQuest.STORE_WALL, BurghRepairs.WALL_CLICKZONE, "loc.burgh_boared_up_wall")
        assertMultiloc("loc.burgh_bank_wall_multiloc", InAidOfTheMyrequeQuest.BANK_WALL, BurghRepairs.WALL_CLICKZONE, "loc.burgh_boared_up_wall")
        assertMultiloc("loc.burgh_bank_booth_multiloc", InAidOfTheMyrequeQuest.BANK_BOOTH, BurghRepairs.BOOTH_DAMAGED, BurghRepairs.BOOTH_REPAIRED)
        assertMultiloc(
            "loc.burgh_furnace_multiloc", InAidOfTheMyrequeQuest.FURNACE,
            BurghRepairs.FURNACE_BROKEN, BurghRepairs.FURNACE_REPAIRED_LOC, BurghRepairs.FURNACE_COAL_LOC, "loc.burgh_furnace_fired",
        )
        assertMultiloc("loc.burgh_temple_trapdoor_multiloc", InAidOfTheMyrequeQuest.LIBRARY_TRAPDOOR, null, PaterdomusLibrary.TRAPDOOR_OPEN)
        assertMultiloc("loc.burgh_ivandis_tombdoor_board_multiloc", InAidOfTheMyrequeQuest.TOMB_BOARDS, RodOfIvandis.BOARDS, null)
        assertMultiloc("loc.burgh_general_store_axes_shelves", InAidOfTheMyrequeQuest.STORE_STOCKED, "loc.burgh_shelves", "loc.burgh_shelves_stocked_axes")

        assertMultinpc("npc.burgh_gadderanks_multinpc", InAidOfTheMyrequeQuest.TITHE_VISIBLE, null, BloodTithe.GADDERANKS, null)
        assertMultinpc("npc.burgh_villager_tithe_multinpc", InAidOfTheMyrequeQuest.TITHE_VISIBLE, null, BloodTithe.WISKIT, BloodTithe.VELIAF_TALK, null)
        assertMultinpc("npc.burgh_juve1_multinpc", InAidOfTheMyrequeQuest.TITHE_VISIBLE, null, BloodTithe.TITHE_JUVINATES[0], null)
        assertMultinpc("npc.burgh_potential_bank_teller_multinpc", InAidOfTheMyrequeQuest.BANK_TELLER, BurghCitizens.CORNELIUS, null)
        assertMultinpc("npc.burgh_actual_bank_teller_multinpc", InAidOfTheMyrequeQuest.BANK_TELLER, null, BurghCitizens.CORNELIUS_BANKER, null)
        for (parent in listOf("npc.route_veliaf_hurtz_parent", "npc.route_ivan_strom_parent", "npc.route_polmafi_ferdygris_parent", "npc.route_radigad_ponfit_parent")) {
            val type = npc(parent)
            assertEquals(InAidOfTheMyrequeQuest.HIDEOUT_NPCS.asRSCM(RSCMType.VARBIT), type.multiVarBit, parent)
            assertEquals(-1, type.transforms!![1], "$parent hides once the Myreque have moved")
        }
        for ((parent, child) in listOf(
            "npc.myq5_polmafi_burgh_hideout" to InAidOfTheMyrequeQuest.POLMAFI_BURGH,
            "npc.myq5_radigad_burgh_hideout" to InAidOfTheMyrequeQuest.RADIGAD_BURGH,
            "npc.myq5_veliaf_burgh_hideout" to InAidOfTheMyrequeQuest.VELIAF_BURGH,
        )) {
            assertEquals(child.asRSCM(RSCMType.NPC), npc(parent).transforms!![0], "$parent shows $child before Sins of the Father")
        }
    }

    @Test fun `the lit furnace is a furnace for smelting and silver crafting`() {
        assertEquals("category.furnace".asRSCM(RSCMType.CATEGORY), loc("loc.burgh_furnace_fired").category)
        assertEquals("Smelt", loc("loc.burgh_furnace_fired").actions.getOpOrNull(1))
    }

    @Test fun `the fighters keep their osrs combat levels`() {
        assertEquals(35, npc(BloodTithe.GADDERANKS_FIGHTING).combatLevel)
        assertEquals(20, npc(BloodTithe.GADDERANKS_FIGHTING).hitpoints)
        assertEquals(50, npc(BloodTithe.JUVINATE_ONE).combatLevel)
        assertEquals(54, npc(BloodTithe.JUVINATE_TWO).combatLevel)
        assertEquals(75, npc(IvanEscort.Route.Short.juvinate).combatLevel)
        assertEquals(50, npc(IvanEscort.Route.Long.juvinate).combatLevel)
        assertEquals(2, IvanEscort.Route.Short.count)
        assertEquals(4, IvanEscort.Route.Long.count)
        assertEquals(40, npc(IvanEscort.IVAN_PLAIN).hitpoints)
        assertEquals(40, npc(IvanEscort.IVAN_WITH_SICKLE).hitpoints)
        for (juvinate in VampyreFights.JUVINATES) {
            assertEquals("Vampyre Juvinate", npc(juvinate).name, juvinate)
        }
    }

    @Test fun `ivan's foods are real food and heal what the wiki says`() {
        assertEquals(11, foodHeal("obj.stew".asRSCM(RSCMType.OBJ)))
        assertEquals(9, foodHeal("obj.salmon".asRSCM(RSCMType.OBJ)))
        assertEquals(5, foodHeal("obj.snail_corpse_cooked1".asRSCM(RSCMType.OBJ)))
        assertEquals(8, foodHeal("obj.snail_corpse_cooked3".asRSCM(RSCMType.OBJ)))
        for (food in InAidHollows.IVAN_FOODS) {
            assertTrue(isFood(food.asRSCM(RSCMType.OBJ)), food)
        }
    }

    @Test fun `every quest loc stands where the scripts expect`() {
        assertLoc(BurghGate.GATE_LEFT, BurghCoords.GATE_LEFT)
        assertLoc(BurghGate.GATE_RIGHT, BurghCoords.GATE_RIGHT)
        assertLoc(BurghGate.CHEST, BurghCoords.FOOD_CHEST)
        assertLoc(BurghGate.TABLE, BurghCoords.FOOD_TABLE)
        assertLoc(InnCellar.CLIMB_OVER, BurghCoords.INN_BROKEN_WALL)
        assertLoc("loc.burgh_inn_colapsed_wall_multiloc", BurghCoords.INN_RUBBLE)
        assertLoc("loc.burgh_inn_trapdoor_multiloc", BurghCoords.INN_TRAPDOOR)
        assertLoc(InnCellar.RUBBLE_DUMP, BurghCoords.RUBBLE_DUMP)
        for (level in 0..2) {
            assertLoc(InnCellar.CELLAR_LADDER, BurghCoords.CELLAR_LADDER.copy(level = level))
            assertLoc(InnCellar.PLAQUE, BurghCoords.CELLAR_PLAQUE.copy(level = level))
        }
        assertLoc(GeneralStore.LADDER_UP, BurghCoords.STORE_LADDER)
        assertLoc(GeneralStore.LADDER_DOWN, BurghCoords.STORE_ROOF_LADDER)
        assertLoc("loc.burgh_general_store_roof_multiloc", BurghCoords.STORE_ROOF_HOLE)
        assertLoc("loc.burgh_general_store_wall_multiloc", BurghCoords.STORE_WALL)
        assertLoc("loc.burgh_bank_booth_multiloc", BurghCoords.BANK_BOOTH)
        assertLoc("loc.burgh_bank_wall_multiloc", BurghCoords.BANK_WALL)
        assertLoc("loc.burgh_furnace_multiloc", BurghCoords.FURNACE)
        assertLoc("loc.burgh_ivandis_tombdoor_board_multiloc", BurghCoords.TOMB_BOARDS)
        assertLoc(RodOfIvandis.TOMB_ENTRANCE, BurghCoords.TOMB_ENTRANCE)
        assertLoc(RodOfIvandis.TOMB_EXIT, BurghCoords.TOMB_EXIT)
        assertLoc(RodOfIvandis.COFFIN, BurghCoords.IVANDIS_COFFIN)
        assertLoc(PaterdomusLibrary.KEYHOLE, BurghCoords.LIBRARY_KEYHOLE)
        assertLoc("loc.burgh_temple_trapdoor_multiloc", BurghCoords.LIBRARY_TRAPDOOR)
        assertLoc(PaterdomusLibrary.LADDER_UP, BurghCoords.LIBRARY_LADDER)
        assertLoc(PaterdomusLibrary.IVANDIS_BOOKCASE, BurghCoords.IVANDIS_BOOKCASE)
        assertLoc(PaterdomusLibrary.HISTORY_BOOKCASE, BurghCoords.HISTORY_BOOKCASE)
        assertLoc(PaterdomusLibrary.WELL, BurghCoords.SALVE_WELL)
        assertLoc(IvanEscort.ESCAPE_PATH, CoordGrid(1999, 5028, 0))
        assertLoc(IvanEscort.CONTINUE_PATH, CoordGrid(1999, 5051, 0))
        assertFalse(placed.any { it.first == RodOfIvandis.BOARDS.asRSCM(RSCMType.LOC) }, "only the multiloc is placed")
        for (stage in InnCellar.RUBBLE_STAGES) {
            assertFalse(placed.any { it.first == stage.asRSCM(RSCMType.LOC) }, "$stage is spawned by the script, never the map")
        }
    }

    @Test fun `every tile a player or npc is put on is open ground`() {
        val tiles =
            listOf(
                BurghCoords.GATE_OUTSIDE, BurghCoords.GATE_INSIDE, BurghCoords.INN_WALL_OUTSIDE, BurghCoords.INN_WALL_INSIDE,
                BurghCoords.CELLAR_FOOT, BurghCoords.CELLAR_FOOT.copy(level = 1), BurghCoords.CELLAR_FOOT.copy(level = 2),
                BurghCoords.STORE_LADDER_FOOT, BurghCoords.STORE_ROOF_ARRIVAL, BurghCoords.STORE_FIGHT_ENTRY,
                BurghCoords.GADDERANKS, BurghCoords.JUVINATE_ONE, BurghCoords.JUVINATE_TWO, BurghCoords.VELIAF_ENTRY,
                BurghCoords.STORE_EXIT, BurghCoords.TOMB_OUTSIDE, BurghCoords.TOMB_ARRIVAL, BurghCoords.LIBRARY_ARRIVAL,
                BurghCoords.LIBRARY_TRAPDOOR_TOP, BurghCoords.PATERDOMUS_ARRIVAL, IvanEscort.TREK_START, IvanEscort.IVAN_START,
                IvanEscort.ESCAPE_EXIT,
            ) + IvanEscort.JUVINATE_SPAWNS + BurghCoords.RUBBLE_PILES
        for (tile in tiles) {
            assertEquals(0, collision[tile.x, tile.z, tile.level] and (CollisionFlag.BLOCK_WALK or CollisionFlag.LOC), "$tile")
        }
        for (tile in listOf(BurghCoords.GADDERANKS, BurghCoords.JUVINATE_ONE, BurghCoords.JUVINATE_TWO, BurghCoords.STORE_FIGHT_ENTRY, BurghCoords.VELIAF_ENTRY)) {
            assertTrue(BurghCoords.inStore(tile), "$tile is inside the store")
        }
        assertFalse(BurghCoords.inStore(BurghCoords.STORE_EXIT))
    }

    @Test fun `the north gate is the only way into burgh de rott`() {
        assertFalse(walks(BurghCoords.GATE_OUTSIDE, BurghCoords.GATE_INSIDE, intArrayOf(3456, 3184, 3583, 3263)))
        assertTrue(walks(BurghCoords.GATE_INSIDE, CoordGrid(3515, 3236, 0), intArrayOf(3456, 3184, 3583, 3263)), "the store")
        assertTrue(walks(BurghCoords.GATE_INSIDE, CoordGrid(3494, 3216, 0), intArrayOf(3456, 3184, 3583, 3263)), "the bank")
        assertTrue(walks(BurghCoords.GATE_INSIDE, CoordGrid(3526, 3214, 0), intArrayOf(3456, 3184, 3583, 3263)), "the furnace")
        assertTrue(walks(BurghCoords.GATE_INSIDE, BurghCoords.INN_WALL_OUTSIDE, intArrayOf(3456, 3184, 3583, 3263)))
        assertFalse(walks(BurghCoords.INN_WALL_OUTSIDE, BurghCoords.INN_WALL_INSIDE, intArrayOf(3456, 3184, 3583, 3263)), "only climbing gets into the inn")
    }

    @Test fun `no set of rubble piles still standing cuts the cellar off from its ladder`() {
        val foot = BurghCoords.CELLAR_FOOT.copy(level = BurghCoords.CELLAR_RUBBLE_LEVEL)
        val bounds = intArrayOf(3484, 9616, 3502, 9638)
        val all = BurghCoords.RUBBLE_PILES.toSet()
        val open = flood(foot, bounds, emptySet())
        val withPiles = flood(foot, bounds, all)
        assertEquals(open - all, withPiles, "every free tile stays reachable with all fifteen piles standing")
        val steps = StepValidator(collision)
        for (pile in all) {
            assertTrue(pile in open, "$pile is part of the cellar")
            val reachable = listOf(0 to 1, 1 to 0, 0 to -1, -1 to 0).any { (dx, dz) ->
                val side = pile.translate(dx, dz)
                side in withPiles && steps.canTravel(pile.level, side.x, side.z, -dx, -dz)
            }
            assertTrue(reachable, "$pile can be worked from an open tile")
        }
        assertEquals(15, all.size)
    }

    @Test fun `the ambush clearing, the tomb and paterdomus join up`() {
        val trek = intArrayOf(1984, 4992, 2047, 5055)
        assertTrue(walks(IvanEscort.TREK_START, CoordGrid(1999, 5050, 0), trek))
        for (spawn in IvanEscort.JUVINATE_SPAWNS) assertTrue(walks(spawn, IvanEscort.IVAN_START, trek), "$spawn")
        assertTrue(walks(BurghCoords.TOMB_ARRIVAL, BurghCoords.IVANDIS_COFFIN.translate(-1, 1), intArrayOf(3456, 9856, 3519, 9919)))
        assertTrue(walks(BurghCoords.TOMB_OUTSIDE, CoordGrid(3480, 9836, 0), intArrayOf(3456, 9792, 3519, 9855)), "the tomb sits beside the tunnel to the canifis cellar")
        assertTrue(walks(BurghCoords.LIBRARY_ARRIVAL, BurghCoords.IVANDIS_BOOKCASE.translateX(-1), intArrayOf(3328, 9856, 3391, 9919)), "the library")
        val mausoleum = intArrayOf(3392, 9856, 3455, 9919)
        assertTrue(walks(BurghCoords.LIBRARY_TRAPDOOR_TOP, CoordGrid(3432, 9897, 0), mausoleum), "Drezel's passage reaches the east gate")
        assertTrue(walks(CoordGrid(3430, 9897, 0), BurghCoords.SALVE_WELL.translateX(1), mausoleum), "the well room is past the east gate")
    }

    @Test fun `every juvinate's route reaches ivan round the clearing's scenery`() {
        val routes = RouteFactory(collision)
        val ivan = Npc(IvanEscort.IVAN_PLAIN, IvanEscort.IVAN_START)
        val stuck =
            IvanEscort.JUVINATE_SPAWNS.filter { spawn ->
                val juvinate = Npc(IvanEscort.Route.Long.juvinate, spawn)
                val end = routes.create(juvinate.avatar, ivan.avatar).lastOrNull()?.let { CoordGrid(it.x, it.z, it.level) } ?: spawn
                end.chebyshevDistance(IvanEscort.IVAN_START) > 1
            }
        assertEquals(emptyList<CoordGrid>(), stuck, "a straight walk snags on scenery from most spawns")
    }

    @Test fun `the rod is enchanted by the spellbook's own lvl-1 enchant`() {
        val spell = checkNotNull(RodOfIvandis.enchantSpellFrom(checkNotNull(RodOfIvandis.enchantObj())))
        assertEquals("Lvl-1 Enchant", spell.name)
        assertEquals(Spellbook.Standard, spell.spellbook)
        assertEquals(7, spell.levelReq)
        assertEquals("component.magic_spellbook:enchant_1".asRSCM(RSCMType.COMPONENT), spell.component.packed)
        assertEquals(
            setOf("obj.cosmicrune" to 1, "obj.waterrune" to 1).map { it.first.asRSCM(RSCMType.OBJ) to it.second }.toSet(),
            spell.objReqs.map { it.obj.id to it.count }.toSet(),
        )
    }

    @Test fun `every symbol the scripts use resolves`() {
        for (name in SEQS) assertNotNull(ServerCacheManager.getAnim(name.asRSCM(RSCMType.SEQ)), name)
        for (name in SPOTANIMS) assertTrue(name.asRSCM(RSCMType.SPOTANIM) >= 0, name)
        for (name in SYNTHS) assertTrue(name.asRSCM(RSCMType.SYNTH) >= 0, name)
        for (name in OBJS) assertNotNull(ServerCacheManager.getItem(name.asRSCM(RSCMType.OBJ)), name)
        for (name in NPCS) assertNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC)), name)
        for (name in COMPONENTS) assertTrue(name.asRSCM(RSCMType.COMPONENT) >= 0, name)
        assertTrue("inv.burgh_general_store".asRSCM(RSCMType.INV) >= 0)
        assertTrue("timer.burgh_tithe_fight".asRSCM(RSCMType.TIMER) >= 0)
        assertTrue("timer.burgh_escort".asRSCM(RSCMType.TIMER) >= 0)
        assertTrue("dbrow.crafting_silvthrill_rod".asRSCM(RSCMType.DBROW) >= 0)
        assertEquals("Search", ServerCacheManager.getItem(InAidOfTheMyrequeQuest.CRATE.asRSCM(RSCMType.OBJ))!!.interfaceOptions[0])
        assertEquals("Read", ServerCacheManager.getItem(InAidOfTheMyrequeQuest.SLEEPING_SEVEN.asRSCM(RSCMType.OBJ))!!.interfaceOptions[0])
        assertEquals("Empty", ServerCacheManager.getItem(InAidOfTheMyrequeQuest.RUBBLE_BUCKETS[0].asRSCM(RSCMType.OBJ))!!.interfaceOptions[3])
        assertEquals("Mine", loc(InnCellar.RUBBLE_STAGES[0]).actions.getOpOrNull(0))
        assertEquals("Remove", loc(InnCellar.RUBBLE_STAGES.last()).actions.getOpOrNull(0))
        assertEquals("Escape", loc(IvanEscort.ESCAPE_PATH).actions.getOpOrNull(0))
        assertEquals("Bank", loc(BurghRepairs.BOOTH_REPAIRED).actions.getOpOrNull(0))
    }

    private fun assertMultiloc(base: String, varbit: String, vararg variants: String?) {
        val type = loc(base)
        assertEquals(varbit.asRSCM(RSCMType.VARBIT), type.multiVarBit, base)
        for ((value, variant) in variants.withIndex()) {
            assertEquals(variant?.asRSCM(RSCMType.LOC) ?: -1, type.transforms!![value], "$base at $value")
        }
    }

    private fun assertMultinpc(base: String, varbit: String, vararg variants: String?) {
        val type = npc(base)
        assertEquals(varbit.asRSCM(RSCMType.VARBIT), type.multiVarBit, base)
        for ((value, variant) in variants.withIndex()) {
            assertEquals(variant?.asRSCM(RSCMType.NPC) ?: -1, type.transforms!![value], "$base at $value")
        }
    }

    private fun walks(from: CoordGrid, to: CoordGrid, bounds: IntArray): Boolean = to in flood(from, bounds, emptySet())

    private fun flood(from: CoordGrid, bounds: IntArray, blocked: Set<CoordGrid>): Set<CoordGrid> {
        val steps = StepValidator(collision)
        val seen = hashSetOf(from)
        val queue = ArrayDeque(listOf(from))
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            for (dx in -1..1) for (dz in -1..1) {
                if ((dx == 0 && dz == 0) || !steps.canTravel(c.level, c.x, c.z, dx, dz)) continue
                val n = c.translate(dx, dz)
                if (n.x !in bounds[0]..bounds[2] || n.z !in bounds[1]..bounds[3] || n in blocked) continue
                if (seen.add(n)) queue += n
            }
        }
        return seen
    }

    private fun npc(name: String) = checkNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC))) { name }

    private fun loc(name: String) = checkNotNull(ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC))) { name }

    private fun assertLoc(name: String, at: CoordGrid) {
        val id = name.asRSCM(RSCMType.LOC)
        assertTrue(placed.any { it.first == id && it.second == at }, "$name is not at $at")
    }

    private companion object {
        val SERVER_VARBITS = listOf(InAidOfTheMyrequeQuest.RUBBLE_REMOVED, InAidOfTheMyrequeQuest.FLORIN_REFUSED, InAidOfTheMyrequeQuest.IVAN_FOOD_HEAL)

        val SEQS =
            listOf(
                InnCellar.CLIMB_OVER_SEQ, InnCellar.DEFAULT_MINING_SEQ, InnCellar.OPEN_SEQ, InnCellar.CLIMB_DOWN_SEQ, InnCellar.CLIMB_UP_SEQ,
                InnCellar.SPADE_SCOOP_SEQ, InnCellar.POT_SCOOP_SEQ, BloodTithe.VELIAF_ATTACK_SEQ, IvanEscort.JUVINATE_ATTACK_SEQ,
                IvanEscort.IVAN_ATTACK_SEQ, RodOfIvandis.ENCHANT_SEQ, RodOfIvandis.MIX_SEQ, RodOfIvandis.HAMMER_SEQ, BurghGate.THROW_SEQ,
            )
        val SPOTANIMS = listOf(VampyreFights.MIST_SPOT, RodOfIvandis.ENCHANT_SPOT, IvanEscort.ESCAPE_SPOT)
        val SYNTHS =
            listOf(
                BurghGate.GATE_OPEN_SOUND, BurghGate.GATE_CLOSE_SOUND, InnCellar.TRAPDOOR_OPEN_SOUND, InnCellar.TRAPDOOR_CLOSE_SOUND,
                InnCellar.EMPTY_SOUND, BurghRepairs.LIGHT_SOUND, RodOfIvandis.ENCHANT_SOUND, PaterdomusLibrary.UNLOCK_SOUND,
                PaterdomusLibrary.PAGE_SOUND, "synth.hammer_and_build",
            )
        val OBJS =
            InAidOfTheMyrequeQuest.NAILS + InAidOfTheMyrequeQuest.RUBBLE_BUCKETS + VampyreFights.SILVER_WEAPONS + InAidHollows.IVAN_FOODS +
                InAidHollows.Armour.entries.map { it.obj } +
                InAidOfTheMyrequeQuest.CrateFood.entries.flatMap { it.objs } +
                listOf(
                    InAidOfTheMyrequeQuest.ROD_MOULD, InAidOfTheMyrequeQuest.SILVTHRILL, InAidOfTheMyrequeQuest.SILVTHRILL_ENCHANTED,
                    InAidOfTheMyrequeQuest.ROD_FULL, InAidOfTheMyrequeQuest.GADDERHAMMER, InAidOfTheMyrequeQuest.CRATE,
                    InAidOfTheMyrequeQuest.LIBRARY_KEY, InAidOfTheMyrequeQuest.SLEEPING_SEVEN, InAidOfTheMyrequeQuest.HISTORIES,
                    InAidOfTheMyrequeQuest.MODERN_MORYTANIA, InAidOfTheMyrequeQuest.DUSTY_SCROLL, InAidOfTheMyrequeQuest.PLASTER_FRAGMENT,
                    InAidOfTheMyrequeQuest.HAMMER, InAidOfTheMyrequeQuest.IMCANDO_HAMMER, InAidOfTheMyrequeQuest.PLANK,
                    InAidOfTheMyrequeQuest.SWAMP_PASTE, InAidOfTheMyrequeQuest.STEEL_BAR, InAidOfTheMyrequeQuest.COAL,
                    InAidOfTheMyrequeQuest.TINDERBOX, InAidOfTheMyrequeQuest.BRONZE_AXE, InAidOfTheMyrequeQuest.SPADE,
                    InAidOfTheMyrequeQuest.POT, InAidOfTheMyrequeQuest.BUCKET, InAidOfTheMyrequeQuest.SOFT_CLAY,
                    InAidOfTheMyrequeQuest.ROPE, VampyreFights.EFARITAYS_AID, RodOfIvandis.GARLIC, RodOfIvandis.SILVER_DUST,
                    "obj.limestone", "obj.broken_glass", "obj.burgh_guthix_balance_4", "obj.burgh_unfinished_guthix_balance_4",
                    "obj.4dosestatrestore", InAidHollows.SILVER_SICKLE,
                )
        val NPCS =
            BurghCitizens.CITIZENS.keys + BurghCitizens.CHILDREN + VampyreFights.JUVINATES + BloodTithe.TITHE_JUVINATES +
                listOf(
                    BurghCitizens.CORNELIUS, BurghCitizens.CORNELIUS_BANKER, BurghCitizens.MARIUS, GeneralStore.AUREL,
                    BloodTithe.GADDERANKS, BloodTithe.WISKIT, BloodTithe.VELIAF_TALK, BloodTithe.GADDERANKS_WOUNDED,
                    BloodTithe.VELIAF_FIGHTING, BurghRepairs.VANSTROM, BurghRepairs.GADDERANKS, BurghRepairs.GABRIELA,
                    InAidOfTheMyrequeQuest.VELIAF_HOLLOWS, InAidOfTheMyrequeQuest.IVAN_HOLLOWS, InAidOfTheMyrequeQuest.VELIAF_BURGH,
                )
        val COMPONENTS =
            listOf(RodOfIvandis.INVENTORY, PaterdomusLibrary.PAGE_LEFT, PaterdomusLibrary.PAGE_RIGHT)

        val collision = CollisionFlagMap()
        val placed = mutableListOf<Pair<Int, CoordGrid>>()
        lateinit var cache: dev.openrune.filesystem.Cache

        @JvmStatic @BeforeAll fun load() {
            cache = ServerCacheManager.init(240)
            val squares = listOf(54 to 50, 55 to 50, 54 to 150, 31 to 78, 54 to 153, 54 to 154, 52 to 154, 53 to 154, 53 to 54, 54 to 54)
            for ((mx, mz) in squares) {
                val square = MapSquareKey(mx, mz)
                val group = (mx shl 8) or mz
                val tiles = MapTileDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 0))))
                val spawns = MapLocListDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, group, 1))))
                for (level in 0..3) for (x in mx * 64 until mx * 64 + 64 step 8) for (z in mz * 64 until mz * 64 + 64 step 8) {
                    collision.allocateIfAbsent(x, z, level)
                }
                val builder = GameMapBuilder()
                GameMapDecoder.putMaps(collision, square, tiles)
                GameMapDecoder.putLocs(builder, collision, square, tiles, spawns)
                for ((packed, zone) in builder.zoneBuilders) {
                    val base = ZoneKey(packed).toCoords()
                    for (entry in zone.build().byte2IntEntrySet()) {
                        val key = LocZoneKey(entry.byteKey)
                        placed += LocEntity(entry.intValue).id to base.translate(key.x, key.z)
                    }
                }
            }
        }

        @JvmStatic @AfterAll fun close() {
            cache.close()
        }
    }
}
