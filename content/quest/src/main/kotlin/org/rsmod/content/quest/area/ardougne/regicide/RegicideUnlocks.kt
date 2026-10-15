package org.rsmod.content.quest.area.ardougne.regicide

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.player.hook.PlayerRestrictionHook
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.RestrictedAction
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.QUEST_KEY
import org.rsmod.content.quest.area.ardougne.undergroundpass.climbOver
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * What Regicide opens up, in the order the quest opens it:
 * - The pass through Arandar, from the moment Lord Iorwerth hands over his letter. Before then
 *   the Huge Gate at its northern end only lets travellers out of Tirannwn.
 * - On completion: Iorwerth camp and Zul-andra teleport scrolls, and wielding the dragon halberd
 *   (its usual Attack and Strength levels are checked by the equipment rules as for any weapon).
 *
 * Completion checks go through [QuestRequirements], so they follow the server's quest
 * requirement policy; the Arandar gate reads the player's own stage because it opens part way
 * through the quest.
 */
class RegicideUnlocks
@Inject
constructor(
    private val regicide: RegicideQuest,
    private val validator: PlayerTeleportValidator,
    private val areaChecker: AreaChecker,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (gate in ARANDAR_GATES) {
            onOpLoc1(gate) { useArandarGate(it.loc) }
        }
        onOpHeld1(IORWERTH_CAMP_SCROLL) { readScroll(IORWERTH_CAMP_SCROLL, IORWERTH_CAMP, "Tirannwn") }
        onOpHeld1(ZUL_ANDRA_SCROLL) { readScroll(ZUL_ANDRA_SCROLL, ZUL_ANDRA, "Zul-Andra") }
    }

    private suspend fun ProtectedAccess.useArandarGate(gate: BoundLocInfo) {
        arriveDelay()
        val leaving = coords.z < gate.coords.z
        if (!leaving && !regicide.arandarUnlocked(player) && !QuestRequirements.hasCompleted(player, QUEST_KEY)) {
            mes("The gate is locked from this side.")
            return
        }
        val x = coords.x.coerceIn(GATE_MIN_X, GATE_MAX_X)
        val dest = if (leaving) CoordGrid(x, gate.coords.z + 1, gate.coords.level) else CoordGrid(x, gate.coords.z - 1, gate.coords.level)
        mes("You push open the huge gate.")
        climbOver(dest, WALK_SEQ, ticks = 2)
    }

    /** The scroll is only used up once the teleport is known to be allowed. */
    private suspend fun ProtectedAccess.readScroll(scroll: String, dest: CoordGrid, place: String) {
        if (!QuestRequirements.hasCompleted(player, QUEST_KEY)) {
            mes("You need to complete the Regicide quest before you can teleport to $place.")
            return
        }
        val denial = validator.validate(player, TeleportType.Standard, areaChecker)
        if (denial != null) {
            mes(denial)
            return
        }
        if (invDel(inv, scroll).failure) {
            return
        }
        anim(SCROLL_SEQ)
        spotanim(SCROLL_SPOTANIM)
        delay(SCROLL_TICKS)
        telejump(dest)
    }

    companion object {
        val ARANDAR_GATES = listOf("loc.overpass_gate_left", "loc.overpass_gate_right")
        const val GATE_MIN_X = 2384
        const val GATE_MAX_X = 2387
        const val WALK_SEQ = "seq.human_walk_f"

        const val IORWERTH_CAMP_SCROLL = "obj.teleportscroll_elf"
        const val ZUL_ANDRA_SCROLL = "obj.teleportscroll_zulandra"
        val IORWERTH_CAMP = CoordGrid(2194, 3258, 0)
        val ZUL_ANDRA = CoordGrid(2196, 3056, 0)
        const val SCROLL_SEQ = "seq.teleport_scroll_open"
        const val SCROLL_SPOTANIM = "spotanim.telescroll_teleport"
        const val SCROLL_TICKS = 3
    }
}

/** The dragon halberd answers only to those who have done Regicide. */
class DragonHalberdWearHook @Inject constructor() : PlayerRestrictionHook {
    private val halberd by lazy { "obj.dragon_halberd".asRSCM(RSCMType.OBJ) }

    override fun restriction(player: Player, action: RestrictedAction): String? {
        if (action !is RestrictedAction.Equip || action.obj.id != halberd) {
            return null
        }
        if (QuestRequirements.hasCompleted(player, QUEST_KEY)) {
            return null
        }
        return "You need to have completed the Regicide quest to wield this."
    }
}
