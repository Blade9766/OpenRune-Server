package org.rsmod.content.quest.area.camelot.holygrail

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.NAPKIN
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_ENTRANA
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.WHISTLE
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.WHISTLE_DOOR
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.inv.Inventory
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The southern room on Draynor Manor's top floor, where the magic whistles lie on the table.
 *
 * Nobody sees them without the holy table napkin: walking in with it in the pack makes them
 * appear on the table, visible to that player alone, as many as the player still needs - two
 * until Percival has his, one after (and one for good after the quest, for the way back). They
 * keep reappearing for as long as some are missing, which is how a lost whistle is replaced.
 */
@Singleton
class WhistleRoom
@Inject
constructor(
    private val quest: HolyGrailQuest,
    private val objRepo: ObjRepository,
    private val passages: GenericPassageScript,
) : PluginScript() {

    private val inRoom = HashSet<PlayerUid>()

    override fun ScriptContext.startup() {
        onOpLoc1(WHISTLE_DOOR) { with(passages) { walkThrough(it.loc, it.type) } }
        onOpHeld1(NAPKIN) { lookAtNapkin(player) }
        onPlayerCoordsChanged {
            if (!isInRoom(player.coords)) {
                inRoom.remove(player.uid)
                return@onPlayerCoordsChanged
            }
            if (inRoom.add(player.uid)) {
                enter(player, bank(player))
            }
        }
    }

    /** What happens as [player] steps into the room; [bank] is null where it cannot be read. */
    fun enter(player: Player, bank: Inventory?) {
        val stage = quest.stage(player)
        if (stage == 0) {
            return
        }
        if (NAPKIN !in player.inv) {
            player.mes("The room is cluttered and dusty. For a moment it felt as if something on the table was watching you.")
            return
        }
        if (stage < STAGE_ENTRANA) {
            player.mes("The napkin in your pack grows faintly warm, but you don't know what you are looking for.")
            return
        }
        val missing = quest.whistlesNeeded(player) - owned(player, bank) - waitingOnTable(player)
        if (missing <= 0) {
            if (waitingOnTable(player) > 0) {
                player.mes("The magic whistles still glimmer on the table.")
            }
            return
        }
        repeat(missing) { objRepo.add(WHISTLE, TABLE, WHISTLE_TICKS, receiver = player) }
        player.whistlesFound = true
        player.mes(
            "The napkin grows warm in your pack. On the table, " +
                (if (missing == 1) "a small tin whistle shimmers" else "$missing small tin whistles shimmer") +
                " into view.",
        )
    }

    fun lookAtNapkin(player: Player) {
        player.mes("A linen napkin from the Grail's own table. Held up to the light, the edges of the room seem to shimmer.")
        if (quest.stage(player) >= STAGE_ENTRANA && !player.whistlesFound) {
            player.mes("It grows faintly warm when you think of Draynor Manor.")
        }
    }

    fun owned(player: Player, bank: Inventory?): Int =
        player.inv.count(WHISTLE) + (bank?.count(WHISTLE) ?: 0)

    fun waitingOnTable(player: Player): Int {
        val whistleId = WHISTLE.asRSCM(RSCMType.OBJ)
        return objRepo.findAll(TABLE).count { it.type == whistleId && it.receiverId == player.observerUUID }
    }

    private fun bank(player: Player): Inventory? = player.invMap["inv.bank"]

    companion object {
        /** The table in the room south of the whistle door, on the manor's top floor. */
        val TABLE = CoordGrid(3107, 3359, 2)

        /** Private whistles linger long enough to be picked up, then fade back out of sight. */
        const val WHISTLE_TICKS = 500

        fun isInRoom(coords: CoordGrid): Boolean =
            coords.level == 2 && coords.x in 3104..3111 && coords.z in 3357..3361
    }
}

internal fun ProtectedAccess.ownsAnywhere(obj: String): Boolean = obj in inv || obj in worn || obj in bank
