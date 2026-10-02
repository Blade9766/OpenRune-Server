package org.rsmod.content.quest.area.tirannwn.mourningsend

import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.ardougne.undergroundpass.climbOver
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.CRYSTALS
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.CRYSTAL_SEED
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.LLETYA_TELEPORT
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.SONG_OF_THE_ELVES
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The way into Lletya and the crystal that leads back to it.
 *
 * The two trees at the village's western edge let a player through once Mourning's End Part I has
 * started (or the quest policy counts it as done); leaving is always allowed. The teleport crystal
 * (4)…(1) loses one charge per teleport and crumbles into a crystal teleport seed after the last,
 * which Eluned can sing back to life. The charge is only taken in the tick the teleport happens,
 * after it has been re-validated, so logging out or being interrupted while the crystal glows
 * never costs one. "Toggle" swaps which destination is the left-click option, using the cache's
 * `varbit.mourning_teleport_destination_toggle`; Prifddinas answers only after Song of the Elves.
 */
class Lletya
@Inject
constructor(
    private val mourning: MourningsEndQuest,
    private val validator: PlayerTeleportValidator,
    private val areaChecker: AreaChecker,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(TREE_GATE) { passTrees(it.loc) }
        for (crystal in CRYSTALS) {
            onOpHeld1(crystal) { if (player.crystalToggle == 1) prifddinas() else teleport(crystal) }
            onOpHeld2(crystal) { if (player.crystalToggle == 1) teleport(crystal) else prifddinas() }
            onOpHeld3(crystal) { toggle() }
        }
    }

    private suspend fun ProtectedAccess.passTrees(tree: BoundLocInfo) {
        arriveDelay()
        val entering = coords.x < tree.coords.x
        if (entering && !mourning.mayEnterLletya(player)) {
            mes("The trees are too dense to pass through.")
            return
        }
        val z = coords.z.coerceIn(PASS_MIN_Z, PASS_MAX_Z)
        val dest = CoordGrid(if (entering) tree.coords.x + 1 else tree.coords.x - 1, z, tree.coords.level)
        climbOver(dest, WALK_SEQ, ticks = 2)
    }

    suspend fun ProtectedAccess.teleport(crystal: String) {
        val denial = validator.validate(player, TeleportType.Standard, areaChecker)
        if (denial != null) {
            mes(denial)
            return
        }
        anim(TELEPORT_SEQ)
        spotanim(TELEPORT_SPOTANIM)
        delay(TELEPORT_TICKS)
        if (validator.validate(player, TeleportType.Standard, areaChecker) != null) {
            return
        }
        if (invReplace(inv, crystal, 1, nextCharge(crystal)).failure) {
            return
        }
        telejump(LLETYA_TELEPORT)
        if (crystal == CRYSTALS.last()) {
            mes("Your teleport crystal has run out of charges. Eluned may be able to re-enchant it.")
        }
    }

    private fun ProtectedAccess.prifddinas() {
        if (!QuestRequirements.hasCompleted(player, SONG_OF_THE_ELVES)) {
            mes("You need to have completed Song of the Elves to teleport to Prifddinas.")
            return
        }
        mes("The crystal can't find a path to Prifddinas from here.")
    }

    private fun ProtectedAccess.toggle() {
        val next = if (player.crystalToggle == 1) 0 else 1
        MourningsEndQuest.setVarBit(player, "varbit.mourning_teleport_destination_toggle", next)
        mes(if (next == 1) "Your teleport crystal will now teleport you to Prifddinas by default." else "Your teleport crystal will now teleport you to Lletya by default.")
    }

    companion object {
        const val TREE_GATE = "loc.elf_village_treegate"
        const val WALK_SEQ = "seq.human_walk_f"
        const val PASS_MIN_Z = 3192
        const val PASS_MAX_Z = 3196

        const val TELEPORT_SEQ = "seq.teleport_scroll_open"
        const val TELEPORT_SPOTANIM = "spotanim.telescroll_teleport"
        const val TELEPORT_TICKS = 3

        /** The crystal one charge down; the last charge leaves the seed. */
        fun nextCharge(crystal: String): String {
            val index = CRYSTALS.indexOf(crystal)
            return CRYSTALS.getOrNull(index + 1) ?: CRYSTAL_SEED
        }
    }
}
