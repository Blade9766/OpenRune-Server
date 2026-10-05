package org.rsmod.content.quest.area.burghderott.inaid

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.player.hook.PlayerRestrictionHook
import org.rsmod.api.player.hook.RestrictedAction
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.UpdateInventory.resendSlot
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.IfOverlayButtonT
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onIfOverlayButtonT
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.ROD_FULL
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.ROD_MOULD
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.SILVTHRILL
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.SILVTHRILL_ENCHANTED
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.SOFT_CLAY
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_BOOK_READ
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_MOULD_MADE
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_TOMB_FOUND
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.TOMB_BOARDS
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Remaking the Rod of Ivandis: the tomb beside the Myreque's old hideout, the clay mould taken
 * from the rod fused to his coffin, and the enchantment.
 *
 * The rod itself is cast at any furnace through the Crafting skill's silver crafting interface
 * (`dbrow.crafting_silvthrill_rod`: silver bar, mithril bar and sapphire, the mould kept), and
 * blessed in the Salve in [PaterdomusLibrary]. Enchanting is the real Lvl-1 Enchant spell cast on
 * the rod: the standard spellbook, Magic level and runes are all checked and taken by
 * [MagicRuneManager], so elemental staves count for the water rune. Enchantment tablets have no
 * effect, as in OSRS.
 */
open class RodOfIvandis
@Inject
constructor(
    private val iaom: InAidOfTheMyrequeQuest,
    private val spells: MagicSpellRegistry,
    private val runes: MagicRuneManager,
    private val launcher: ProtectedAccessLauncher,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLocU(BOARDS) { useOnBoards(it.objType.id) }
        onOpLoc1(TOMB_ENTRANCE) { enterTomb() }
        onOpLoc1(TOMB_EXIT) {
            arriveDelay()
            anim(CRAWL_SEQ)
            delay(CRAWL_TICKS)
            telejump(BurghCoords.TOMB_OUTSIDE, TeleportType.Exempt)
        }
        onOpLoc1(COFFIN) { inspectCoffin() }
        onOpLocU(COFFIN, SOFT_CLAY) { makeMould() }
        onOpHeld1(SILVTHRILL) { mesbox("This rod looks very similar to the rod you saw fused to the top of the coffin. However, this rod lacks both the mortal and divine energies that the original gave off.") }
        onOpHeld1(SILVTHRILL_ENCHANTED) { mesbox("This rod looks very similar to the rod you saw fused to the top of the coffin. However, this rod lacks the divine energies that the original gave off.") }
        onIfOverlayButtonT(ENCHANT_SPELL, INVENTORY) { castOnItem() }
        for (dose in 1..4) {
            onOpHeldU("obj.${dose}dosestatrestore", GARLIC) { mixUnfinished(dose) }
            onOpHeldU("obj.burgh_unfinished_guthix_balance_$dose", SILVER_DUST) { mixBalance(dose) }
        }
    }

    private suspend fun ProtectedAccess.useOnBoards(objId: Int) {
        arriveDelay()
        if (objId != HAMMER_ID && objId != IMCANDO_ID) {
            mes("Nothing interesting happens.")
            return
        }
        mesbox("There's some boards blocking the cave entrance.")
        if (!iaom.reached(player, STAGE_BOOK_READ)) {
            return
        }
        var yes = false
        startDialogue { yes = choice2("Yes.", true, "No.", false, title = "Remove the boards?") }
        if (!yes || player.vars[TOMB_BOARDS] == 1) return
        anim(HAMMER_SEQ)
        soundSynth(BREAK_SOUND)
        VarPlayerIntMapSetter.set(player, TOMB_BOARDS, 1)
        objbox(InAidOfTheMyrequeQuest.HAMMER, "You manage to break up the boards using a hammer.")
    }

    private suspend fun ProtectedAccess.enterTomb() {
        arriveDelay()
        if (player.vars[TOMB_BOARDS] != 1) {
            mes("The cave entrance is boarded up.")
            return
        }
        anim(CRAWL_SEQ)
        delay(CRAWL_TICKS)
        telejump(BurghCoords.TOMB_ARRIVAL, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.inspectCoffin() {
        arriveDelay()
        objbox(ROD_FULL, "It's an ancient coffin with a strange rod fused to the top. The rod gives out a mixture of both mortal and divine energies and seems to be made out of a unique silver and mithril alloy.")
        if (iaom.stage(player) == STAGE_BOOK_READ) {
            iaom.advanceTo(this, STAGE_TOMB_FOUND)
        }
    }

    private suspend fun ProtectedAccess.makeMould() {
        arriveDelay()
        if (!iaom.reached(player, STAGE_BOOK_READ)) {
            mes("Nothing interesting happens.")
            return
        }
        anim(MOULD_SEQ)
        if (invReplace(inv, SOFT_CLAY, 1, ROD_MOULD).failure) {
            return
        }
        if (iaom.stage(player) in STAGE_BOOK_READ until STAGE_MOULD_MADE) {
            iaom.advanceTo(this, STAGE_MOULD_MADE)
        }
        objbox(ROD_MOULD, "You use some clay to create a mould of the rod.")
    }

    private fun IfOverlayButtonT.castOnItem() {
        val target = targetObj ?: return resendSlot(player.inv, 0)
        if (player.isAccessProtected) {
            resendSlot(player.inv, 0)
            return
        }
        val slot = targetSlot
        launcher.launch(player) {
            clearPendingAction()
            enchant(target.id, slot)
        }
    }

    internal suspend fun ProtectedAccess.enchant(targetId: Int, slot: Int) {
        if (targetId != SILVTHRILL.asRSCM(RSCMType.OBJ)) {
            mes(if (targetId == SILVTHRILL_ENCHANTED.asRSCM(RSCMType.OBJ)) "The rod is already enchanted." else "That spell has no effect on that item.")
            return
        }
        if (inv[slot]?.id != targetId) {
            return
        }
        val xp = castEnchant(player) ?: return
        anim(ENCHANT_SEQ)
        spotanim(ENCHANT_SPOT)
        soundSynth(ENCHANT_SOUND)
        invReplace(inv, SILVTHRILL, 1, SILVTHRILL_ENCHANTED)
        statAdvance(MAGIC, xp)
        objbox(SILVTHRILL_ENCHANTED, "As you cast the enchantment on the rod, it seems to vibrate and glow.")
    }

    /** Checks and takes Lvl-1 Enchant's requirements; the cast xp on success, null otherwise. */
    protected open fun castEnchant(player: Player): Double? {
        val spell = spells.allSpells().firstOrNull { it.component.packed == ENCHANT_SPELL.asRSCM(RSCMType.COMPONENT) } ?: return null
        if (!runes.canCastSpell(player, spell) || runes.attemptCast(player, spell).isFailure()) {
            return null
        }
        return spell.castXp
    }

    private suspend fun ProtectedAccess.mixUnfinished(dose: Int) {
        if (!canMixBalance()) return
        val restore = "obj.${dose}dosestatrestore"
        if (invDel(inv, GARLIC).failure) return
        invReplace(inv, restore, 1, "obj.burgh_unfinished_guthix_balance_$dose")
        anim(MIX_SEQ)
        statAdvance(HERBLORE, MIX_XP)
        mes("You add the garlic to the potion.")
    }

    private suspend fun ProtectedAccess.mixBalance(dose: Int) {
        if (!canMixBalance()) return
        if (invDel(inv, SILVER_DUST).failure) return
        invReplace(inv, "obj.burgh_unfinished_guthix_balance_$dose", 1, "obj.burgh_guthix_balance_$dose")
        anim(MIX_SEQ)
        statAdvance(HERBLORE, MIX_XP)
        mes("You add the silver dust to the potion and make a Guthix balance potion.")
    }

    /** Gadderanks' dying words are the only way to learn the recipe. */
    private fun ProtectedAccess.canMixBalance(): Boolean {
        if (!iaom.knowsGuthixBalance(player)) {
            mes("Nothing interesting happens.")
            return false
        }
        if (stat(HERBLORE) < BALANCE_LEVEL) {
            mes("You need a Herblore level of $BALANCE_LEVEL to make this potion.")
            return false
        }
        return true
    }

    internal companion object {
        const val BOARDS = "loc.burgh_ivandis_boardedupdoor"
        const val TOMB_ENTRANCE = "loc.burgh_ivandis_tomb_entrance"
        const val TOMB_EXIT = "loc.burgh_ivandis_tomb_exit"
        const val COFFIN = "loc.burgh_ancient_coffin"
        const val ENCHANT_SPELL = "component.magic_spellbook:enchant_1"
        const val INVENTORY = "component.inventory:items"
        const val MAGIC = "stat.magic"
        const val HERBLORE = "stat.herblore"
        const val GARLIC = "obj.garlic"
        const val SILVER_DUST = "obj.silver_dust"
        const val BALANCE_LEVEL = 22
        const val MIX_XP = 25.0

        val HAMMER_ID by lazy { InAidOfTheMyrequeQuest.HAMMER.asRSCM(RSCMType.OBJ) }
        val IMCANDO_ID by lazy { InAidOfTheMyrequeQuest.IMCANDO_HAMMER.asRSCM(RSCMType.OBJ) }

        const val CRAWL_SEQ = "seq.human_pickupfloor"
        const val HAMMER_SEQ = "seq.human_hammer_hit"
        const val MOULD_SEQ = "seq.human_pickuptable"
        const val ENCHANT_SEQ = "seq.burgh_human_quest_enchanting_rod"
        const val ENCHANT_SPOT = "spotanim.burgh_enchanting_rod_glow_spotanim"
        const val MIX_SEQ = "seq.human_herbing_vial"
        const val BREAK_SOUND = "synth.hammer_and_build"
        const val ENCHANT_SOUND = "synth.enchant_sapphire_ring"
        const val CRAWL_TICKS = 2
    }
}

/** The Gadderhammer can't be wielded until Gadderanks has been beaten (OSRS wiki). */
class GadderhammerWearHook @Inject constructor(private val iaom: InAidOfTheMyrequeQuest) : PlayerRestrictionHook {
    private val hammer by lazy { InAidOfTheMyrequeQuest.GADDERHAMMER.asRSCM(RSCMType.OBJ) }

    override fun restriction(player: Player, action: RestrictedAction): String? {
        if (action !is RestrictedAction.Equip || action.obj.id != hammer || iaom.canWieldGadderhammer(player)) {
            return null
        }
        return "You need to have defeated Gadderanks to wield this."
    }
}
