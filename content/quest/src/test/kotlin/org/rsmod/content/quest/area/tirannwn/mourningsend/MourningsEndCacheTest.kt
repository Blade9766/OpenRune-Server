package org.rsmod.content.quest.area.tirannwn.mourningsend

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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.table.QuestRow
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.CRYSTALS
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.FIXED_DEVICE
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.HITPOINTS_XP
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.LLETYA_ARRIVAL
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.LLETYA_TELEPORT
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.THIEVING_XP
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Pins the cache and map facts Mourning's End Part I is written against: the quest row, the vars
 * and their layout, the multinpcs and multilocs the quest flags drive, where its npcs stand and
 * its scenery is placed, that every tile a script moves the player to can be stood on, and the
 * client's aiming interface.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class MourningsEndCacheTest {

    @Test fun `the quest row matches the stages, rewards and requirements`() {
        val row = QuestRow.getRow("dbrow.${MourningsEndQuest.QUEST_KEY}".asRSCM())
        assertEquals(STAGE_COMPLETE, row.endstate)
        assertEquals(2, row.questpoints)
        assertEquals(mapOf("ranged" to 60, "thieving" to 50), row.requirementStats.associate { it.t0.displayName to it.t1 })
        assertEquals(
            mapOf("thieving" to (THIEVING_XP * 10).toInt(), "hitpoints" to (HITPOINTS_XP * 10).toInt()),
            row.statXpAwarded.associate { it.t0.displayName to it.t1 },
            "tenths of 40,000 and 25,000 xp",
        )
        assertEquals(
            setOf("dbrow.quest_rovingelves", "dbrow.quest_bigchompybirdhunting", "dbrow.quest_sheepherder").map { it.asRSCM() }.toSet(),
            row.requirementQuests.map { it.rowId }.toSet(),
        )
    }

    @Test fun `every quest var is permanent and no two flags share a bit`() {
        for (name in listOf("varp.mourning_quest", "varp.mourning_quest_bits", "varp.mourning_end_state")) {
            assertEquals(VarpLifetime.Perm, varp(name).scope, name)
        }
        val bits = HashSet<Pair<Int, Int>>()
        for (name in MourningsEndQuest.OWN_VARBITS + "varbit.mourning_eluned_chant") {
            val bit = checkNotNull(ServerCacheManager.getVarbit(name.asRSCM(RSCMType.VARBIT))) { name }
            assertEquals(VarpLifetime.Perm, ServerCacheManager.getVarp(bit.baseVar.id)!!.scope, name)
            for (b in bit.startBit..bit.endBit) assertTrue(bits.add(bit.baseVar.id to b), "$name overlaps another flag")
        }
        assertTrue(width("varbit.mourning_gnome") >= 4, "room for the gnome's 0-8")
        assertTrue(width("varbit.mourning_gun_ammo") >= 3, "room for four colours")
        assertTrue(width("varbit.mourning_eluned_chant") >= 3)
    }

    @Test fun `each flock's field sheep shows dyed only to a player whose flag is set`() {
        for (flock in Flock.entries) {
            val field = npc(flock.field)
            assertEquals(flock.varbit.asRSCM(RSCMType.VARBIT), field.multiVarBit, flock.label)
            val dyed = npc(checkNotNull(field.transforms)[1])
            assertEquals("${flock.label.replaceFirstChar { it.uppercase() }} Sheep", dyed.name)
            assertTrue(dyed.examine.contains("dyed ${flock.label}"))
            assertTrue(item(flock.toad).name.startsWith(flock.label.replaceFirstChar { it.uppercase() }))
            assertTrue(item(flock.bellows).examine.contains("${flock.label} dye"))
            assertTrue(item(flock.dye).name.lowercase().startsWith(flock.label))
            assertTrue(flock.travel.asRSCM(RSCMType.SPOTANIM) >= 0 && flock.impact.asRSCM(RSCMType.SPOTANIM) >= 0)
            assertTrue(spawns(flock.field).size >= 3, "a whole flock")
        }
        assertEquals((1..4).toSet(), Flock.entries.map { it.ammo }.toSet())
    }

    @Test fun `the three food stores are the grain sacks in the civic office, church and general store`() {
        val boxes =
            mapOf(
                FoodStore.CIVIC_OFFICE to (2515..2523 to 3310..3318),
                FoodStore.CHURCH to (2522..2528 to 3283..3290),
                FoodStore.GENERAL_STORE to (2465..2471 to 3285..3291),
            )
        for ((store, box) in boxes) {
            val tiles = store.locs.flatMap { placedAt(it) }
            assertTrue(tiles.size >= 3, "$store has its sacks: $tiles")
            assertTrue(tiles.all { it.x in box.first && it.z in box.second && it.level == 0 }, "$store: $tiles")
            for (sacks in store.locs) assertEquals(checkNotNull(ServerCacheManager.getObject(sacks.asRSCM(RSCMType.LOC))).multiVarBit, loc(store.locs[0]).multiVarBit)
            assertEquals("Grain sacks", loc(checkNotNull(loc(store.locs[0]).transforms)[0]).name)
        }
    }

    @Test fun `the Arandar mourner is the level 11 one at the pass and only his drops are the disguise`() {
        val mourner = npc(ArandarMourner.MOURNER)
        val shown = npc(checkNotNull(mourner.transforms)[0])
        assertEquals(11, shown.combatLevel)
        assertEquals("Attack", shown.actions.getOpOrNull(1))
        assertEquals(listOf(CoordGrid(2299, 3328, 0)), spawns(ArandarMourner.MOURNER))
        val table = file("content/drops/src/main/resources/drops/tables/monsters/mourner_arandar.toml").readText()
        for (obj in MourningsEndQuest.DISGUISE.map { it.second } - MourningsEndQuest.MOURNER_TOP - MourningsEndQuest.MOURNER_LEGS +
            MourningsEndQuest.BLOODY_TOP + MourningsEndQuest.RIPPED_LEGS + MourningsEndQuest.LETTER) {
            assertTrue(table.contains("obj = \"$obj\""), obj)
        }
        assertTrue(table.contains("quest = \"quest_mourningsendpart1\""))
        for (stat in ArandarMourner.DRAINED_STATS) assertTrue(stat.asRSCM(RSCMType.STAT) >= 0)
    }

    @Test fun `the people of the quest stand where the scripts expect`() {
        assertEquals(listOf(CoordGrid(2353, 3172, 0)), spawns("npc.mourning_arianwyn"))
        assertEquals(listOf(CoordGrid(2044, 4628, 0)), spawns("npc.mourner_hideout_head_mourner"))
        assertEquals(listOf(CoordGrid(2913, 3417, 0)), spawns("npc.eadgar_druid_washing"))
        assertTrue(CoordGrid(2324, 3179, 0) in spawns("npc.mourning_seamstress"))
        assertEquals("Essyllt", npc(checkNotNull(npc("npc.mourner_hideout_head_mourner").transforms)[0]).name)
        val gnome = npc("npc.mourner_hideout_gnome")
        assertEquals("varbit.mourning_gnome".asRSCM(RSCMType.VARBIT), gnome.multiVarBit)
        val forms = checkNotNull(gnome.transforms)
        assertEquals(-1, forms[0])
        assertEquals("npc.mourner_hideout_gnome_head".asRSCM(RSCMType.NPC), forms[MourningsEndQuest.GNOME_FREED])
        assertEquals("npc.mourner_hideout_gnome_head".asRSCM(RSCMType.NPC), forms[MourningsEndQuest.GNOME_ASKED_AMMO])
        val rack = loc("loc.mourning_gnome_rack")
        assertEquals(gnome.multiVarBit, rack.multiVarBit)
        assertEquals("loc.mourning_gnome_rack_occupied".asRSCM(RSCMType.LOC), checkNotNull(rack.transforms)[MourningsEndQuest.GNOME_AGREED])
        assertEquals("loc.mourning_gnome_rack_empty".asRSCM(RSCMType.LOC), checkNotNull(rack.transforms)[MourningsEndQuest.GNOME_FREED])
        assertEquals(listOf("Talk-to", "Release"), (0..1).map { loc("loc.mourning_gnome_rack_occupied").actions.getOpOrNull(it) })
    }

    @Test fun `the scenery is placed where the scripts look for it`() {
        assertTrue(placed(MournerHideout.TRAPDOOR, CoordGrid(2542, 3327, 0)))
        assertTrue(placed(MournerHideout.LADDER_UP, CoordGrid(2044, 4650, 0)))
        assertTrue(placed(MournerHideout.CELL_DOOR, CoordGrid(2037, 4633, 0)), "the door into the gnome's room")
        assertTrue(placed("loc.mourning_gnome_rack", CoordGrid(2035, 4629, 0)))
        assertTrue(placed(MournerHideout.OFFICE_CHEST, CoordGrid(2039, 4633, 0)))
        assertTrue(placed("loc.mourning_office_table", CoordGrid(2043, 4629, 0)))
        assertTrue(placed(Lletya.TREE_GATE, CoordGrid(2305, 3191, 0)))
        assertTrue(placed("loc.eadgar_laundry_basket", CoordGrid(2912, 3418, 0)))
        assertTrue(placed(FoodSupply.APPLE_PILE, CoordGrid(2487, 3374, 0)))
        assertTrue(placed("loc.mourning_orchard_applebarrel_empty", CoordGrid(2484, 3374, 0)))
        assertTrue(placed(FoodSupply.GATE_LEFT, CoordGrid(2474, 3364, 0)))
        assertTrue(placed("loc.range", CoordGrid(2547, 3322, 0)), "the range on the Headquarters ground floor")
        assertEquals("content.cooking_range_standard".asRSCM(RSCMType.CONTENT), loc("loc.range").contentGroup)
        for (desk in MournerHideout.DESKS.drop(1)) assertEquals("Search", loc(desk).actions.getOpOrNull(0), desk)
        assertEquals(
            setOf("loc.mourning_office_table_pre", "loc.mourning_office_table_post").map { it.asRSCM(RSCMType.LOC) }.toSet(),
            (checkNotNull(loc("loc.mourning_office_table").transforms).toSet() + loc("loc.mourning_office_table").multiDefault) - -1,
        )
    }

    @Test fun `every tile a script moves the player to can be stood on`() {
        for (tile in listOf(LLETYA_ARRIVAL, LLETYA_TELEPORT, MournerHideout.BASEMENT_ARRIVAL, MournerHideout.TRAPDOOR_ARRIVAL)) {
            assertTrue(open(tile), "$tile")
        }
        for (z in Lletya.PASS_MIN_Z..Lletya.PASS_MAX_Z) {
            assertTrue(open(CoordGrid(2304, z, 0)) && open(CoordGrid(2306, z, 0)), "both sides of the trees at $z")
            assertTrue(!open(CoordGrid(2305, z, 0)), "the trees themselves block at $z")
        }
        assertTrue(MournerHideout.inBasement(MournerHideout.BASEMENT_ARRIVAL))
        assertTrue(MournerHideout.inBasement(CoordGrid(2044, 4628, 0)), "Essyllt's office")
    }

    @Test fun `the items and the aiming interface carry the options the scripts handle`() {
        for (crystal in CRYSTALS) assertEquals(listOf("Lletya", "Prifddinas", "Toggle"), item(crystal).interfaceOptions.take(3))
        val device = item(FIXED_DEVICE)
        assertEquals("Empty", device.interfaceOptions[2])
        assertEquals("Wield", device.interfaceOptions[1])
        assertEquals(3, device.wearpos1, "carried in the weapon slot")
        for (button in listOf(FixedDevice.LEFT, FixedDevice.RIGHT, FixedDevice.UP, FixedDevice.DOWN, FixedDevice.FIRE, FixedDevice.CLOSE)) {
            assertEquals(FixedDevice.INTERFACE.asRSCM(RSCMType.INTERFACE), button.asRSCM(RSCMType.COMPONENT) ushr 16, button)
        }
        assertEquals("Inspect", item(MourningsEndQuest.BLOODY_TOP).interfaceOptions[0])
        assertEquals("Read", item(MourningsEndQuest.LETTER).interfaceOptions[0])
        assertEquals("Pass", loc(Lletya.TREE_GATE).actions.getOpOrNull(0))
        assertEquals("Take-from", loc(FoodSupply.APPLE_PILE).actions.getOpOrNull(0))
    }

    private fun width(varbit: String): Int {
        val type = checkNotNull(ServerCacheManager.getVarbit(varbit.asRSCM(RSCMType.VARBIT)))
        return type.endBit - type.startBit + 1
    }

    private fun npc(name: String) = checkNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC))) { name }

    private fun npc(id: Int) = checkNotNull(ServerCacheManager.getNpc(id)) { "npc $id" }

    private fun loc(name: String) = checkNotNull(ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC))) { name }

    private fun loc(id: Int) = checkNotNull(ServerCacheManager.getObject(id)) { "loc $id" }

    private fun item(name: String) = checkNotNull(ServerCacheManager.getItem(name.asRSCM(RSCMType.OBJ))) { name }

    private fun varp(name: String) = checkNotNull(ServerCacheManager.getVarp(name.asRSCM(RSCMType.VARP))) { name }

    private fun placed(name: String, at: CoordGrid): Boolean = placedLocs.any { it.first == name.asRSCM(RSCMType.LOC) && it.second == at }

    private fun placedAt(name: String): List<CoordGrid> = placedLocs.filter { it.first == name.asRSCM(RSCMType.LOC) }.map { it.second }

    private fun open(tile: CoordGrid): Boolean =
        collision[tile.x, tile.z, tile.level] and (CollisionFlag.BLOCK_WALK or CollisionFlag.LOC) == 0

    private companion object {
        /** Lletya and its gate, Arandar, West Ardougne, the orchard, Taverley lake and the basement. */
        val SQUARES = listOf(35 to 49, 36 to 49, 35 to 52, 36 to 52, 38 to 51, 39 to 51, 38 to 52, 39 to 52, 45 to 53, 31 to 72)

        val collision = CollisionFlagMap()
        val placedLocs = mutableListOf<Pair<Int, CoordGrid>>()
        lateinit var cache: dev.openrune.filesystem.Cache

        fun file(path: String): java.io.File =
            listOf("", "../../").map { java.io.File("$it$path") }.first { it.exists() }

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
