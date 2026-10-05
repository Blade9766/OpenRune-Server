package org.rsmod.content.quest.area.burghderott.inaid

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.table.FoodRow
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.HAMMER
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.IMCANDO_HAMMER
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.NAILS
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.game.inv.isType
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey

/** Every fixed tile the quest reads or moves players to; each is checked against the map in tests. */
internal object BurghCoords {
    /** The north gate: closed panels face south, so the town is south of z 3244. */
    val GATE_LEFT = CoordGrid(3485, 3244, 0)
    val GATE_RIGHT = CoordGrid(3484, 3244, 0)
    val GATE_OUTSIDE = CoordGrid(3484, 3245, 0)
    val GATE_INSIDE = CoordGrid(3484, 3243, 0)
    const val GATE_Z = 3244
    val FOOD_CHEST = CoordGrid(3483, 3246, 0)
    val FOOD_TABLE = CoordGrid(3484, 3246, 0)

    /** The inn: its broken north wall, the rubble-covered trapdoor and the dump outside. */
    val INN_BROKEN_WALL = CoordGrid(3491, 3230, 0)
    val INN_WALL_OUTSIDE = CoordGrid(3491, 3229, 0)
    val INN_WALL_INSIDE = CoordGrid(3491, 3231, 0)
    val INN_RUBBLE = CoordGrid(3489, 3231, 0)
    val INN_TRAPDOOR = CoordGrid(3490, 3232, 0)
    val RUBBLE_DUMP = CoordGrid(3489, 3227, 0)

    /** The cellar is three copies of one room: 1 full of rubble, 2 cleared, 0 the Myreque's base. */
    const val CELLAR_RUBBLE_LEVEL = 1
    const val CELLAR_CLEARED_LEVEL = 2
    const val CELLAR_HIDEOUT_LEVEL = 0
    val CELLAR_LADDER = CoordGrid(3490, 9632, 0)
    val CELLAR_FOOT = CoordGrid(3490, 9631, 0)
    val CELLAR_PLAQUE = CoordGrid(3494, 9632, 0)

    /** Chosen so that no set of piles still standing cuts any other off from the ladder. */
    val RUBBLE_PILES =
        listOf(
            CoordGrid(3498, 9628, 1),
            CoordGrid(3499, 9628, 1),
            CoordGrid(3499, 9626, 1),
            CoordGrid(3497, 9627, 1),
            CoordGrid(3499, 9625, 1),
            CoordGrid(3499, 9623, 1),
            CoordGrid(3497, 9626, 1),
            CoordGrid(3498, 9624, 1),
            CoordGrid(3496, 9622, 1),
            CoordGrid(3497, 9622, 1),
            CoordGrid(3495, 9622, 1),
            CoordGrid(3494, 9624, 1),
            CoordGrid(3497, 9624, 1),
            CoordGrid(3496, 9624, 1),
            CoordGrid(3496, 9630, 1),
        )

    fun inCellar(coords: CoordGrid): Boolean = coords.x in 3486..3502 && coords.z in 9619..9635

    /** Burgh de Rott inside its walls, for the "don't dump rubble here" check. */
    fun inTown(coords: CoordGrid): Boolean =
        coords.level == 0 && coords.x in 3474..3535 && coords.z in 3196..GATE_Z

    val STORE_LADDER = CoordGrid(3513, 3238, 0)
    val STORE_LADDER_FOOT = CoordGrid(3513, 3237, 0)
    val STORE_ROOF_LADDER = CoordGrid(3513, 3238, 2)
    val STORE_ROOF_ARRIVAL = CoordGrid(3513, 3239, 2)
    val STORE_ROOF_HOLE = CoordGrid(3515, 3240, 2)
    val STORE_WALL = CoordGrid(3517, 3238, 0)

    /** The general store's floor; stepping out of it ends the blood tithe fight. */
    fun inStore(coords: CoordGrid): Boolean = coords.level == 0 && coords.x in 3512..3518 && coords.z in 3239..3243

    val STORE_FIGHT_ENTRY = CoordGrid(3516, 3242, 0)
    val GADDERANKS = CoordGrid(3514, 3241, 0)
    val JUVINATE_ONE = CoordGrid(3513, 3241, 0)
    val JUVINATE_TWO = CoordGrid(3513, 3242, 0)
    val VELIAF_ENTRY = CoordGrid(3517, 3240, 0)
    val STORE_EXIT = CoordGrid(3513, 3237, 0)

    val BANK_BOOTH = CoordGrid(3494, 3211, 0)
    val BANK_WALL = CoordGrid(3491, 3211, 0)
    val FURNACE = CoordGrid(3527, 3209, 0)

    /** Castle Drakan's throne room, seen only from the camera during the furnace scene. */
    val DRAKAN_CAMERA = CoordGrid(3561, 3375, 0)

    val TOMB_BOARDS = CoordGrid(3483, 9832, 0)
    val TOMB_ENTRANCE = CoordGrid(3484, 9832, 0)
    val TOMB_OUTSIDE = CoordGrid(3483, 9832, 0)
    val TOMB_EXIT = CoordGrid(3492, 9861, 0)
    val TOMB_ARRIVAL = CoordGrid(3492, 9862, 0)
    val IVANDIS_COFFIN = CoordGrid(3511, 9863, 0)

    val LIBRARY_KEYHOLE = CoordGrid(3443, 9898, 0)
    val LIBRARY_TRAPDOOR = CoordGrid(3441, 9899, 0)
    val LIBRARY_TRAPDOOR_TOP = CoordGrid(3440, 9898, 0)
    val LIBRARY_LADDER = CoordGrid(3361, 9904, 0)
    val LIBRARY_ARRIVAL = CoordGrid(3360, 9904, 0)
    val HISTORY_BOOKCASE = CoordGrid(3354, 9899, 0)
    val IVANDIS_BOOKCASE = CoordGrid(3354, 9902, 0)
    val SALVE_WELL = CoordGrid(3423, 9890, 0)

    /** Outside the little Morytanian mausoleum whose trapdoor leads down to Drezel. */
    val PATERDOMUS_ARRIVAL = CoordGrid(3433, 3485, 0)
}

internal fun ProtectedAccess.hasHammer(): Boolean =
    inv.contains(HAMMER) || inv.contains(IMCANDO_HAMMER) ||
        player.righthand?.isType(IMCANDO_HAMMER) == true ||
        IMCANDO_OFFHAND in player.worn

internal fun nailCount(inv: Inventory): Int = NAILS.sumOf { inv.count(it) }

/** Takes [count] nails of any kind, plainest first; the caller has checked there are enough. */
internal fun ProtectedAccess.takeNails(count: Int) {
    var left = count
    for (nail in NAILS) {
        if (left == 0) break
        val take = minOf(left, inv.count(nail))
        if (take > 0) {
            invDel(inv, nail, take)
            left -= take
        }
    }
}

internal fun ItemServerType.isObj(symbol: String): Boolean = id == symbol.asRSCM(RSCMType.OBJ)

internal fun objSymbol(objId: Int): String = RSCM.getReverseMapping(RSCMType.OBJ, objId)

/** The first npc of [type] within [radius] tiles of [coords]. */
internal fun NpcRepository.nearby(coords: CoordGrid, type: String, radius: Int): Npc? =
    findAll(ZoneKey.from(coords), 1).firstOrNull { it.isType(type) && it.coords.level == coords.level && it.coords.chebyshevDistance(coords) <= radius }

internal fun objName(obj: String): String =
    ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))?.name ?: obj

/** Food is anything in the server's food table, the same list players eat from. */
internal fun isFood(objId: Int): Boolean = objId in foodIds

/** Hitpoints [objId] restores when eaten, or 0 when it is not food. */
internal fun foodHeal(objId: Int): Int = foodHeals[objId] ?: 0

private val foodHeals: Map<Int, Int> by lazy {
    FoodRow.all().flatMap { row -> row.items.map { it.id to row.heal } }.toMap()
}

private val foodIds: Set<Int> by lazy { foodHeals.keys }

internal fun Player.holdsAnywhere(obj: String): Boolean =
    obj in inv || obj in worn || invMap.getOrPut("inv.bank").contains(obj)

private const val IMCANDO_OFFHAND = "obj.imcando_hammer_offhand"
