package org.rsmod.content.quest.area.falador.blackknightsfortress

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.npc.MapNpcDefinition
import dev.openrune.map.npc.MapNpcListDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.table.QuestRow
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortress.Companion.CAULDRON
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortress.Companion.KNIGHT_RADIUS
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortress.Companion.MEETING_ROOM
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortressQuest.Companion.BLACK_KNIGHTS
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortressQuest.Companion.CAULDRON_BREWING
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortressQuest.Companion.CAULDRON_SABOTAGED
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortressQuest.Companion.REQUIRED_QUEST_POINTS
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortressQuest.Companion.STAGE_COMPLETE
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey

/**
 * Pins the cache facts Black Knights' Fortress is written against: the quest row, the cauldron
 * multiloc, and where each door, wall, grill and hole stands, since the door scripts work out
 * which side the player is on from those coordinates.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class BlackKnightsFortressCacheTest {
    @Test
    fun questRowMatchesTheStages() {
        val row = QuestRow.getRow("dbrow.${BlackKnightsFortressQuest.QUEST_KEY}".asRSCM())
        assertEquals(STAGE_COMPLETE, row.endstate)
        assertEquals(3, row.questpoints)
        assertEquals(REQUIRED_QUEST_POINTS, row.requirementQuestpoints)
    }

    @Test
    fun theCauldronFollowsItsOwnVarbit() {
        val cauldron = loc("loc.bkf_cauldron_multi")
        assertEquals("varbit.spy_cauldron_multi".asRSCM(RSCMType.VARBIT), cauldron.multiVarBit)
        val transforms = checkNotNull(cauldron.transforms)
        assertEquals("loc.dk_cauldron".asRSCM(RSCMType.LOC), transforms[CAULDRON_BREWING])
        assertEquals("loc.dk_cauldron_sabotaged".asRSCM(RSCMType.LOC), transforms[CAULDRON_SABOTAGED])
        assertLocAt("loc.bkf_cauldron_multi", CAULDRON)
    }

    @Test
    fun theDoorsFaceTheWayTheSideChecksAssume() {
        assertLocAt("loc.bkfortressdoor1", CoordGrid(3016, 3514, 0), angle = NORTH)
        assertLocAt("loc.bkfortressdoor2", CoordGrid(3020, 3515, 0), angle = WEST)
        assertLocAt("loc.bkfortressdoor3", CoordGrid(3025, 3511, 1), angle = EAST)
        assertLocAt("loc.bksecretdoor", CoordGrid(3016, 3517, 0))
        assertLocAt("loc.bksecretdoor", CoordGrid(3030, 3510, 1))
    }

    @Test
    fun theGrillAndHoleSitOverTheWitchsRoom() {
        assertLocAt("loc.witchgrill", CoordGrid(3026, 3507, 0))
        assertLocAt("loc.blackknighthole", CoordGrid(CAULDRON.x, CAULDRON.z, 1))
        assertEquals("Listen-at", loc("loc.witchgrill").actions.getOpOrNull(0))
        assertEquals("Push", loc("loc.bksecretdoor").actions.getOpOrNull(0))
    }

    @Test
    fun blackKnightsMeetInTheMeetingRoom() {
        val ids = BLACK_KNIGHTS.map { it.asRSCM(RSCMType.NPC) }.toSet()
        val square = MapSquareKey.from(MEETING_ROOM)
        val data = checkNotNull(cache.data(MAPS, square.id, 5)) { "no npcs in ${square.id}" }
        val knights =
            MapNpcListDecoder.decode(InlineByteBuf(data)).packedSpawns.map(::MapNpcDefinition).count {
                val coords = square.toCoords(it.level).translate(it.localX, it.localZ)
                it.id in ids && coords.level == MEETING_ROOM.level &&
                    coords.chebyshevDistance(MEETING_ROOM) <= KNIGHT_RADIUS
            }
        assertTrue(knights > 0, "no black knights within $KNIGHT_RADIUS of $MEETING_ROOM")
    }

    @Test
    fun theDraynorManorPatchIsPickable() {
        assertEquals("Pick", loc("loc.draynor_magic_cabbage").actions.getOpOrNull(0))
        assertEquals("Read", item(BlackKnightsFortressQuest.DOSSIER).interfaceOptions.getOrNull(0))
    }

    @Test
    fun everyAnimationAndSpotanimResolves() {
        for (seq in listOf(
            BlackKnightsFortress.LISTEN_SEQ,
            BlackKnightsFortress.THROW_SEQ,
            BlackKnightsFortress.READ_SEQ,
            BlackKnightsFortress.EXPLODE_SEQ,
        )) {
            assertNotNull(ServerCacheManager.getAnim(seq.asRSCM(RSCMType.SEQ)), seq)
        }
        for (spot in listOf(
            BlackKnightsFortress.THROWN_SPOT,
            BlackKnightsFortress.FALLING_SPOT,
            BlackKnightsFortress.BUBBLES_SPOT,
            BlackKnightsFortress.EXPLODE_SPOT,
        )) {
            assertTrue(spot.asRSCM(RSCMType.SPOTANIM) > 0, spot)
        }
    }

    private fun loc(name: String) =
        checkNotNull(ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC))) { "$name missing" }

    private fun item(name: String) =
        checkNotNull(ServerCacheManager.getItem(name.asRSCM(RSCMType.OBJ))) { "$name missing" }

    private fun assertLocAt(loc: String, coords: CoordGrid, angle: Int? = null) {
        val id = loc.asRSCM(RSCMType.LOC)
        val square = MapSquareKey.from(coords)
        val data = checkNotNull(cache.data(MAPS, square.id, 1)) { "no locs in ${square.id}" }
        val spawns = MapLocListDecoder.decode(InlineByteBuf(data)).spawns.map(::MapLocDefinition)
        val match =
            spawns.any {
                it.id == id &&
                    square.toCoords(it.level).translate(it.localX, it.localZ) == coords &&
                    (angle == null || it.angle == angle)
            }
        assertTrue(match, "$loc is not at $coords (angle=$angle)")
    }

    private fun CoordGrid.chebyshevDistance(other: CoordGrid): Int =
        maxOf(kotlin.math.abs(x - other.x), kotlin.math.abs(z - other.z))

    private companion object {
        const val WEST = 0
        const val NORTH = 1
        const val EAST = 2

        lateinit var cache: dev.openrune.filesystem.Cache

        @JvmStatic
        @BeforeAll
        fun loadCache() {
            cache = ServerCacheManager.init(240)
        }
    }
}
