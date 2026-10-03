package org.rsmod.content.quest.area.lumbridge.tearsofguthix

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.area.lumbridge.tearsofguthix.TearStreams.Colour
import org.rsmod.content.quest.area.lumbridge.tearsofguthix.TearsOfGuthixQuest.Companion.STONE_BOWL
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid

/**
 * The Tears of Guthix minigame: Juna puts the stone bowl in the player's hands and lets them into
 * the weeping cave for one cycle per quest point, a tear a cycle from whichever stream they hold
 * the bowl under, and they drink the lot on the way out.
 */
@Singleton
class TearsCave
@Inject
constructor(private val rules: TearsOfGuthixRules, private val streams: TearStreams) {
    fun handsFree(player: Player): Boolean =
        player.worn[Wearpos.RightHand.slot] == null && player.worn[Wearpos.LeftHand.slot] == null

    fun isCollecting(player: Player): Boolean = player.togCollecting

    fun enter(access: ProtectedAccess) {
        val player = access.player
        player.togTears = 0
        player.togCountdown = rules.timeLimit(player)
        player.togCollecting = true
        access.invAdd(access.worn, STONE_BOWL, slot = Wearpos.RightHand.slot)
        access.rebuildAppearance()
        access.ifOpenOverlay(PANEL)
        access.telejump(CHAMBER_ENTRY, TeleportType.Exempt)
        streams.advance()
        player.softTimer(TIMER, 1)
    }

    suspend fun ProtectedAccess.collect(wall: BoundLocInfo) {
        if (!player.togCollecting || player.worn[Wearpos.RightHand.slot]?.id != BOWL_ID) {
            mes("You need a bowl to collect the tears in.")
            return
        }
        faceSquare(wall.coords)
        anim(LEAN_FORWARD_SEQ)
        delay(1)
        if (!player.togCollecting) {
            return
        }
        streams.advance()
        when (streams.colourAt(wall.coords)) {
            Colour.Blue -> player.togTears = (player.togTears + 1).coerceAtMost(MAX_TEARS)
            Colour.Green -> player.togTears = (player.togTears - 1).coerceAtLeast(0)
            Colour.Dry -> {}
        }
        opLoc1(wall)
    }

    /** One cycle in the cave: the streams move on and the time left runs down. */
    fun tick(player: Player) {
        if (!player.togCollecting) {
            player.clearSoftTimer(TIMER)
            return
        }
        streams.advance()
        val left = (player.togCountdown - 1).coerceAtLeast(0)
        player.togCountdown = left
        if (left == 0 || !inChamber(player.coords)) {
            player.clearSoftTimer(TIMER)
            player.strongQueue(DRINK_QUEUE, 1)
        }
    }

    suspend fun ProtectedAccess.drink() {
        if (!player.togCollecting) {
            return
        }
        val tears = player.togTears
        player.togCollecting = false
        player.togCountdown = 0
        clearSoftTimer(TIMER)
        ifCloseSub(PANEL)
        if (!inChamber(player.coords)) {
            player.togTears = 0
            removeBowl(player)
            return
        }
        if (tears > player.togBestTears) {
            player.togBestTears = tears
        }
        val skill = rules.rewardSkill(player)
        val xp = rules.reward(player, skill, tears)
        telejump(CAVE_EXIT, TeleportType.Exempt)
        anim(DRINK_SEQ)
        if (xp > 0) {
            statAdvance(skill.stat, xp)
            mes(skill.message)
            if (rules.hasDiaryBonus(player)) {
                mes(DIARY_MESSAGE)
            }
        }
        rules.recordVisit(player)
        player.togTears = 0
        delay(DRINK_TICKS)
        removeBowl(player)
    }

    /** Puts a player who left the cave without drinking (logout, death) back to normal. */
    fun reset(player: Player) {
        if (player.togCollecting) {
            player.togCollecting = false
            player.togTears = 0
            player.togCountdown = 0
            player.clearSoftTimer(TIMER)
        }
        removeBowl(player)
    }

    private fun removeBowl(player: Player) {
        val slot = Wearpos.RightHand.slot
        if (player.worn[slot]?.id == BOWL_ID) {
            player.worn[slot] = null
            player.rebuildAppearance()
        }
    }

    companion object {
        const val PANEL = "interface.tog_sidepanel"
        const val TIMER = "timer.tog_tears"
        const val DRINK_QUEUE = "queue.tog_drink"
        const val LEAN_FORWARD_SEQ = "seq.tog_lean_forward_bowl"
        const val DRINK_SEQ = "seq.tog_drink_bowl"
        const val DRINK_TICKS = 3
        const val MAX_TEARS = 511
        const val DIARY_MESSAGE =
            "<col=dc143c>You are awarded an additional 10% experience for completing the hard " +
                "Lumbridge achievement diary.</col>"

        val CHAMBER_ENTRY = CoordGrid(3257, 9517, 2)
        val CAVE_EXIT = CoordGrid(3250, 9516, 2)
        private val CHAMBER_X = 3253..3261
        private val CHAMBER_Z = 9513..9521

        fun inChamber(coords: CoordGrid): Boolean =
            coords.level == CHAMBER_ENTRY.level && coords.x in CHAMBER_X && coords.z in CHAMBER_Z

        private val BOWL_ID by lazy { STONE_BOWL.asRSCM(RSCMType.OBJ) }
    }
}
