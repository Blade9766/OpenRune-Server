package org.rsmod.content.quest.area.tirannwn.rovingelves

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
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.config.refs.params
import org.rsmod.api.table.QuestRow
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.CRYSTAL_BOW
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.CRYSTAL_SHIELD
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.ELUNED
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.ELUNED_TALK
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.FULL_CHARGES
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.ILFEEN
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.ILFEEN_ENCHANT
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.ILFEEN_TALK
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.ISLWYN
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.ISLWYN_TALK
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.ISLWYN_TRADE
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.MOSS_GUARDIAN
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.NEW_SEED
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.OLD_SEED
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.STRENGTH_XP
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Pins the cache and map facts Roving Elves is written against: the quest row and its vars, the
 * elves' multinpcs and their single spawns, the Moss Guardian, the seeds and crystal items, and
 * where the chalices, the tomb and its guardians stand.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class RovingElvesCacheTest {

    @Test fun `the quest row matches the stages, reward and requirements`() {
        val row = QuestRow.getRow("dbrow.${RovingElvesQuest.QUEST_KEY}".asRSCM())
        assertEquals(STAGE_COMPLETE, row.endstate)
        assertEquals(1, row.questpoints)
        assertEquals(mapOf("strength" to (STRENGTH_XP * 10).toInt()), row.statXpAwarded.associate { it.t0.displayName to it.t1 }, "tenths of 10,000 xp")
        assertTrue(row.requirementStats.isEmpty(), "Agility is a travel requirement, not a quest one")
        assertEquals(
            setOf("dbrow.quest_regicide".asRSCM(), "dbrow.quest_waterfall".asRSCM()),
            row.requirementQuests.map { it.rowId }.toSet(),
        )
        val mourning = QuestRow.getRow("dbrow.quest_mourningsendpart1".asRSCM())
        assertTrue("dbrow.quest_rovingelves".asRSCM() in mourning.requirementQuests.map { it.rowId }, "Mourning's End Part I follows on")
    }

    @Test fun `every quest var is permanent`() {
        for (name in listOf("varp.roving_elves_quest", "varp.roving_elves_bits", "varp.roving_elves_state", "varp.roving_update_ilfeen_chant")) {
            assertEquals(VarpLifetime.Perm, varp(name).scope, name)
        }
        for (name in RovingElvesQuest.OWN_VARBITS + "varbit.roving_ilfeen_chantcount") {
            val bit = checkNotNull(ServerCacheManager.getVarbit(name.asRSCM(RSCMType.VARBIT))) { name }
            assertEquals(VarpLifetime.Perm, ServerCacheManager.getVarp(bit.baseVar.id)!!.scope, name)
        }
        val choice = checkNotNull(ServerCacheManager.getVarbit("varbit.roving_reward_choice".asRSCM(RSCMType.VARBIT)))
        assertEquals("varp.roving_elves_state".asRSCM(RSCMType.VARP), choice.baseVar.id)
        assertTrue(choice.endBit - choice.startBit >= 1, "room for both choices")
    }

    @Test fun `the elves are multinpcs on the Song of the Elves varp, each spawned once`() {
        val tertiary = "varp.sote_tertiary".asRSCM(RSCMType.VARP)
        for ((base, varbit, forms) in listOf(
            Triple(ISLWYN, RovingElvesQuest.ISLWYN_VARBIT, listOf(ISLWYN_TALK, ISLWYN_TRADE)),
            Triple(ILFEEN, RovingElvesQuest.ILFEEN_VARBIT, listOf(ILFEEN_TALK, ILFEEN_ENCHANT)),
            Triple(ELUNED, "varbit.roving_female_woodelf", listOf(ELUNED_TALK, RovingElvesQuest.ELUNED_ENCHANT)),
        )) {
            val type = npc(base)
            assertEquals(varbit.asRSCM(RSCMType.VARBIT), type.multiVarBit, base)
            assertEquals(tertiary, ServerCacheManager.getVarbit(type.multiVarBit)!!.baseVar.id, base)
            assertEquals(forms.map { it.asRSCM(RSCMType.NPC) }, checkNotNull(type.transforms).take(2), base)
        }
        assertEquals("Talk-to", npc(ISLWYN_TALK).actions.getOpOrNull(0))
        assertEquals("Trade", npc(ISLWYN_TRADE).actions.getOpOrNull(2))
        assertEquals("Enchant", npc(ILFEEN_ENCHANT).actions.getOpOrNull(2))
        assertEquals(listOf("Islwyn", "Eluned", "Ilfeen"), listOf(npc(ISLWYN_TALK).name, npc(ELUNED_TALK).name, npc(ILFEEN_TALK).name))
        assertEquals("Elven Scout", npc(checkNotNull(npc(ISLWYN).transforms)[RovingElvesQuest.SOTE_SCOUT]).name)

        assertEquals(listOf(CoordGrid(2291, 3147, 0)), spawns(ISLWYN), "once, west of Lletya, by the quest start icon")
        assertEquals(listOf(CoordGrid(2289, 3145, 0)), spawns(ELUNED))
        assertEquals(listOf(CoordGrid(2260, 3213, 0)), spawns(ILFEEN).filter { it.x in 2140..2400 && it.z in 3040..3340 }, "Ilfeen's Isafdar spawn")
        assertTrue(placed("loc.quest_start_icon_rovingelves", CoordGrid(2288, 3144, 0)))
        for (tile in spawns(ISLWYN) + spawns(ELUNED)) assertTrue(open(tile), "$tile is walkable")
    }

    @Test fun `the Moss Guardian is the level-84 one and only it carries the quest drop`() {
        val guardian = npc(MOSS_GUARDIAN)
        assertEquals("Moss Guardian", guardian.name)
        assertEquals(84, guardian.combatLevel)
        assertEquals(listOf(60, 60, 60, 120), listOf(guardian.attack, guardian.strength, guardian.defence, guardian.hitpoints))
        assertEquals(10, guardian.respawnRate, "the server config's 10-tick respawn")
        val moss = npc("npc.mossgiant")
        assertNotEquals(guardian.id, moss.id)
        assertEquals(42, moss.combatLevel)

        val table = dropFile("moss_guardian.toml")
        assertTrue(table.contains("npcs = [\"$MOSS_GUARDIAN\"]"))
        assertTrue(table.contains("obj = \"$OLD_SEED\"") && table.contains("quest = \"quest_rovingelves\""))
        for (other in listOf("moss_guardian_nightmare_zone.toml")) assertFalse(dropFile(other).contains("consecration"), other)
        assertFalse(dropSource("MossGiantDropTable.kt").contains("consecration"), "ordinary moss giants never drop it")

        assertTrue("spotanim.roving_mossgiant_impact".asRSCM(RSCMType.SPOTANIM) >= 0)
        val tomb = spawns(MOSS_GUARDIAN)
        assertEquals(3, tomb.size)
        assertTrue(tomb.all { it.level == 0 && it.x in 2524..2557 && it.z in 9801..9849 }, "all inside Glarial's tomb: $tomb")
    }

    @Test fun `only the enchanted seed can be planted`() {
        assertEquals("Consecration seed", item(OLD_SEED).name)
        assertEquals("Consecration seed", item(NEW_SEED).name)
        assertTrue(item(OLD_SEED).interfaceOptions.take(4).all { it == null }, "the dead seed has no Plant option")
        assertEquals("Plant", item(NEW_SEED).interfaceOptions[0])
        assertTrue("seq.human_dig".asRSCM(RSCMType.SEQ) >= 0)
        val growth = loc(Consecration.GROWTH_LOC)
        assertEquals("Crystal growth", growth.name)
        assertTrue((0..4).all { growth.actions.getOpOrNull(it) == null }, "the growth has nothing to click")
        assertTrue(Consecration.GROWTH_SEQ.asRSCM(RSCMType.SEQ) >= 0)
    }

    @Test fun `the reward items carry their charges, requirements and inactive forms`() {
        val bits = checkNotNull(ServerCacheManager.getVarObj(RovingElvesQuest.CRYSTAL_CHARGES.asRSCM(RSCMType.VAROBJ))).bits
        assertTrue(FULL_CHARGES < 1 shl (bits.last - bits.first + 1), "2,500 charges fit the varobj")
        assertEquals(500, CrystalSinging.varsFor(500) ushr bits.first)

        val bow = item(CRYSTAL_BOW)
        assertEquals("Crystal bow", bow.name)
        assertEquals("obj.crystal_bow_inactive".asRSCM(RSCMType.OBJ), bow.param(params.uncharged_variant).id)
        assertEquals(listOf("ranged" to 70, "agility" to 50), requirements(CRYSTAL_BOW))
        assertEquals(listOf("ranged" to 70, "agility" to 50), requirements("obj.crystal_bow_inactive"))

        val shield = item(CRYSTAL_SHIELD)
        assertEquals("Crystal shield", shield.name)
        assertEquals("obj.crystal_shield_inactive".asRSCM(RSCMType.OBJ), shield.param(params.uncharged_variant).id)
        assertEquals(shield.id, item("obj.crystal_shield_inactive").param(params.charged_variant).id)
        assertEquals(listOf("defence" to 70, "agility" to 50), requirements(CRYSTAL_SHIELD))
        assertEquals(listOf("Check", "Revert"), shield.interfaceOptions.subList(2, 4))
        assertEquals("Crystal weapon seed", item(RovingElvesQuest.WEAPON_SEED).name)
    }

    @Test fun `the chalices and the tomb are where the scripts look for them`() {
        assertTrue(placed("loc.baxtorian_chalice_waterfall_quest", CoordGrid(2603, 9910, 0)), "on the floor of the raised copy")
        assertTrue(placed("loc.baxtorian_chalice_waterfall_quest", CoordGrid(2565, 9911, 1)), "floating over the real room")
        assertTrue(Consecration.inRitualArea(CoordGrid(2602, 9910, 0)))
        assertTrue(Consecration.inRitualArea(CoordGrid(2605, 9912, 0)))
        assertTrue(Consecration.inRitualArea(CoordGrid(2565, 9911, 0)))
        assertFalse(Consecration.inRitualArea(CoordGrid(2601, 9910, 0)), "two tiles away")
        assertFalse(Consecration.inRitualArea(CoordGrid(2565, 9911, 1)))
        assertFalse(Consecration.inRitualArea(CoordGrid(2589, 9888, 0)), "the crate room")
        val ritual = (2564..2567).flatMap { x -> (9910..9913).map { z -> CoordGrid(x, z, 0) } } +
            (2602..2605).flatMap { x -> (9909..9912).map { z -> CoordGrid(x, z, 0) } }
        assertTrue(ritual.count(::open) >= 20, "most of the ring around each chalice can be stood on")

        assertTrue(placed("loc.glarials_tombstone_waterfall_quest", CoordGrid(2558, 3444, 0)))
        assertTrue(placed("loc.glarials_chest_closed_waterfall_quest", CoordGrid(2530, 9844, 0)))
        assertTrue(placed("loc.baxtorian_crate_waterfall_quest", CoordGrid(2589, 9888, 0)), "the key crate, in the east room")
        assertTrue(placed("loc.baxtorian_door_2_waterfall_quest", CoordGrid(2566, 9901, 0)), "the door into the chalice room")
        assertTrue(placed("loc.waterfall_ledge_door", CoordGrid(2511, 3464, 0)))
    }

    private fun requirements(obj: String): List<Pair<String, Int>> {
        val type = item(obj)
        return listOfNotNull(
            type.paramOrNull(params.statreq1_skill)?.let { it.displayName to type.param(params.statreq1_level) },
            type.paramOrNull(params.statreq2_skill)?.let { it.displayName to type.param(params.statreq2_level) },
        )
    }

    private fun npc(name: String) = checkNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC))) { name }

    private fun npc(id: Int) = checkNotNull(ServerCacheManager.getNpc(id)) { "npc $id" }

    private fun loc(name: String) = checkNotNull(ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC))) { name }

    private fun item(name: String) = checkNotNull(ServerCacheManager.getItem(name.asRSCM(RSCMType.OBJ))) { name }

    private fun varp(name: String) = checkNotNull(ServerCacheManager.getVarp(name.asRSCM(RSCMType.VARP))) { name }

    private fun placed(name: String, at: CoordGrid): Boolean = placedLocs.any { it.first == name.asRSCM(RSCMType.LOC) && it.second == at }

    private fun open(tile: CoordGrid): Boolean =
        collision[tile.x, tile.z, tile.level] and (CollisionFlag.BLOCK_WALK or CollisionFlag.LOC) == 0

    private companion object {
        val SQUARES = listOf(35 to 49, 39 to 153, 40 to 154, 40 to 155, 39 to 53, 40 to 53, 39 to 54)

        val collision = CollisionFlagMap()
        val placedLocs = mutableListOf<Pair<Int, CoordGrid>>()
        lateinit var cache: dev.openrune.filesystem.Cache

        fun file(path: String): java.io.File =
            listOf("", "../../").map { java.io.File("$it$path") }.first { it.exists() }

        fun dropFile(name: String): String = file("content/drops/src/main/resources/drops/tables/monsters/$name").readText()

        fun dropSource(name: String): String =
            file("content/drops/src/main/kotlin/org/rsmod/content/drops/tables/monsters/$name").readText()

        /** Every map spawn of [npc], from the raw spawn tables the server loads. */
        fun spawns(npc: String): List<CoordGrid> =
            file(".data/raw-cache/map/npcs").listFiles()!!.filter { it.extension == "toml" }.flatMap { spawnFile ->
                val lines = spawnFile.readLines()
                lines.indices.filter { lines[it].trim() == "npc = \"$npc\"" }.map { index ->
                    val packed = lines[index + 1].substringAfter('"').substringBefore('"').split('_').map(String::toInt)
                    CoordGrid(packed[1] * 64 + packed[3], packed[2] * 64 + packed[4], packed[0])
                }
            }

        @JvmStatic @BeforeAll fun load() {
            cache = ServerCacheManager.init(240)
            for ((sx, sz) in SQUARES) {
                val group = (sx shl 8) or sz
                val tileData = cache.data(MAPS, group, 0) ?: continue
                val locData = cache.data(MAPS, group, 1) ?: continue
                val square = MapSquareKey(sx, sz)
                for (level in 0..3) for (x in sx * 64 until sx * 64 + 64 step 8) {
                    for (z in sz * 64 until sz * 64 + 64 step 8) collision.allocateIfAbsent(x, z, level)
                }
                val tiles = MapTileDecoder.decode(InlineByteBuf(tileData))
                val builder = GameMapBuilder()
                GameMapDecoder.putMaps(collision, square, tiles)
                GameMapDecoder.putLocs(builder, collision, square, tiles, MapLocListDecoder.decode(InlineByteBuf(locData)))
                for ((packed, zone) in builder.zoneBuilders) {
                    val base = ZoneKey(packed).toCoords()
                    for (entry in zone.build().byte2IntEntrySet()) {
                        val key = LocZoneKey(entry.byteKey)
                        placedLocs += LocEntity(entry.intValue).id to base.translate(key.x, key.z)
                    }
                }
            }
        }

        @JvmStatic @AfterAll fun close() {
            cache.close()
        }
    }
}
