package org.rsmod.content.quest.area.ardougne.regicide

import jakarta.inject.Inject
import kotlin.math.min
import org.rsmod.api.player.hands
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocCategoryU
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.BARREL
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.BARREL_BOMB
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.BARREL_OF_COAL_TAR
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.BARREL_OF_NAPHTHA
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.GROUND_SULPHUR
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.LIMESTONE
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.NAPHTHA_QUICKLIME_MIX
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.NAPHTHA_SULPHUR_MIX
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.PESTLE_AND_MORTAR
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.POT
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.POT_OF_QUICKLIME
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.QUICKLIME
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STRIP_OF_CLOTH
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.SULPHUR
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.TINDERBOX
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.UNFUSED_BOMB
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The bomb from the Big Book of Bangs, as the game's item steps:
 * - Coal tar is taken into an empty barrel at the edge of the Poison Waste, and sulphur picked up
 *   from the formations on its shore.
 * - Limestone (not limestone bricks) heated in any furnace, or the small furnace in Tyras Camp,
 *   becomes quicklime; without gloves on the player is burnt for [BURN_DAMAGE].
 * - A pestle and mortar turns quicklime into a pot of quicklime, using up an empty pot; with no
 *   pot the quicklime is spilt and lost. Sulphur is ground the same way, with no pot needed.
 * - The pot of quicklime and the ground sulphur go into a barrel of naphtha in either order, each
 *   order with its own half-mixed barrel, and a strip of cloth (not a bolt of cloth) is the fuse.
 *
 * Each step checks every ingredient before it changes anything and swaps items within one cycle,
 * so a step either happens completely or not at all. Tools are never used up, and an ingredient
 * that is already in the barrel is refused rather than wasted.
 */
class BarrelBomb @Inject constructor() : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(COAL_TAR) { takeCoalTar() }
        onOpLocU(COAL_TAR, BARREL) { takeCoalTar() }
        for (sulphur in SULPHUR_LOCS) {
            onOpLoc1(sulphur) { takeSulphur() }
        }
        onOpLocCategoryU("category.furnace", LIMESTONE) { heatLimestone() }
        onOpLocU(SMALL_FURNACE, LIMESTONE) { heatLimestone() }
        onOpHeldU(PESTLE_AND_MORTAR, QUICKLIME) { grindQuicklime() }
        onOpHeldU(PESTLE_AND_MORTAR, SULPHUR) { grindSulphur() }

        onOpHeldU(BARREL_OF_NAPHTHA, POT_OF_QUICKLIME) { mix(BARREL_OF_NAPHTHA, NAPHTHA_QUICKLIME_MIX, quicklime = true) }
        onOpHeldU(BARREL_OF_NAPHTHA, GROUND_SULPHUR) { mix(BARREL_OF_NAPHTHA, NAPHTHA_SULPHUR_MIX, quicklime = false) }
        onOpHeldU(NAPHTHA_SULPHUR_MIX, POT_OF_QUICKLIME) { mix(NAPHTHA_SULPHUR_MIX, UNFUSED_BOMB, quicklime = true) }
        onOpHeldU(NAPHTHA_QUICKLIME_MIX, GROUND_SULPHUR) { mix(NAPHTHA_QUICKLIME_MIX, UNFUSED_BOMB, quicklime = false) }
        onOpHeldU(UNFUSED_BOMB, STRIP_OF_CLOTH) { addFuse() }

        onOpHeldU(NAPHTHA_QUICKLIME_MIX, POT_OF_QUICKLIME) { mes("There is already quicklime in the barrel.") }
        onOpHeldU(NAPHTHA_SULPHUR_MIX, GROUND_SULPHUR) { mes("There is already sulphur in the barrel.") }
        onOpHeldU(UNFUSED_BOMB, POT_OF_QUICKLIME) { mes("The barrel is already full.") }
        onOpHeldU(UNFUSED_BOMB, GROUND_SULPHUR) { mes("The barrel is already full.") }
        for (unfinished in listOf(BARREL_OF_NAPHTHA, NAPHTHA_QUICKLIME_MIX, NAPHTHA_SULPHUR_MIX)) {
            onOpHeldU(unfinished, STRIP_OF_CLOTH) { mes("The mixture isn't ready for a fuse yet.") }
        }
        for (barrel in listOf(UNFUSED_BOMB, BARREL_BOMB)) {
            onOpHeldU(barrel, TINDERBOX) { mes("Lighting that here would not end well.") }
        }
        onOpHeldU(BARREL_OF_NAPHTHA, QUICKLIME) { mes("The quicklime needs grinding into a pot first.") }
        onOpHeldU(BARREL_OF_NAPHTHA, SULPHUR) { mes("The sulphur needs grinding first.") }
    }

    private suspend fun ProtectedAccess.takeCoalTar() {
        arriveDelay()
        if (!inv.contains(BARREL)) {
            mes("You need an empty barrel to collect the coal tar.")
            return
        }
        anim(PICKUP_SEQ)
        delay(1)
        if (invReplace(inv, BARREL, 1, BARREL_OF_COAL_TAR).success) {
            mes("You fill the barrel with coal tar.")
        }
    }

    private suspend fun ProtectedAccess.takeSulphur() {
        arriveDelay()
        anim(PICKUP_SEQ)
        delay(1)
        if (invAdd(inv, SULPHUR).failure) {
            mes("You don't have enough inventory space to carry any sulphur.")
            return
        }
        mes("You take some sulphur.")
    }

    private suspend fun ProtectedAccess.heatLimestone() {
        arriveDelay()
        if (!inv.contains(LIMESTONE)) {
            return
        }
        anim(FURNACE_SEQ)
        delay(HEAT_TICKS)
        if (invReplace(inv, LIMESTONE, 1, QUICKLIME).failure) {
            return
        }
        mes("You heat the limestone until it crumbles into quicklime.")
        if (player.hands == null) {
            mes("The hot quicklime burns your bare hands!")
            takeInstantHit(HitType.Typeless, BURN_DAMAGE)
        }
    }

    private suspend fun ProtectedAccess.grindQuicklime() {
        anim(GRIND_SEQ)
        delay(1)
        if (!inv.contains(QUICKLIME)) {
            return
        }
        if (!inv.contains(POT)) {
            if (invDel(inv, QUICKLIME).failure) {
                return
            }
            mes("You grind the quicklime to dust and store it in a pot.")
            mes("You burn yourself on the dust before spilling it on the ground. Maybe you need something to store it in.")
            takeInstantHit(HitType.Typeless, min(SPILL_DAMAGE, player.hitpoints - 1).coerceAtLeast(0))
            return
        }
        if (invDel(inv, POT).failure) {
            return
        }
        invReplace(inv, QUICKLIME, 1, POT_OF_QUICKLIME)
        mes("You grind the quicklime to dust and store it in a pot.")
    }

    private suspend fun ProtectedAccess.grindSulphur() {
        anim(GRIND_SEQ)
        delay(1)
        if (invReplace(inv, SULPHUR, 1, GROUND_SULPHUR).success) {
            mes("You grind the sulphur into a fine powder.")
        }
    }

    /**
     * Adds one ingredient to [barrel], turning it into [into]. The pot that held the quicklime is
     * handed back empty into the slot it came from.
     */
    private fun ProtectedAccess.mix(barrel: String, into: String, quicklime: Boolean) {
        val ingredient = if (quicklime) POT_OF_QUICKLIME else GROUND_SULPHUR
        if (!inv.contains(barrel) || !inv.contains(ingredient)) {
            return
        }
        val emptied = if (quicklime) invReplace(inv, POT_OF_QUICKLIME, 1, POT) else invDel(inv, GROUND_SULPHUR)
        if (emptied.failure) {
            return
        }
        invReplace(inv, barrel, 1, into)
        mes(if (quicklime) "You add the quicklime to the barrel." else "You add the ground sulphur to the barrel.")
        if (into == UNFUSED_BOMB) {
            mes("The barrel is full. It just needs a fuse.")
        }
    }

    private fun ProtectedAccess.addFuse() {
        if (!inv.contains(UNFUSED_BOMB) || invDel(inv, STRIP_OF_CLOTH).failure) {
            return
        }
        invReplace(inv, UNFUSED_BOMB, 1, BARREL_BOMB)
        mes("You push the strip of cloth into the barrel as a fuse.")
    }

    companion object {
        const val COAL_TAR = "loc.regicide_tar_collection"
        val SULPHUR_LOCS = listOf("loc.regicide_sulphar1", "loc.regicide_sulphar2", "loc.regicide_sulphar3")
        const val SMALL_FURNACE = "loc.regicide_furnace"

        const val BURN_DAMAGE = 8
        const val SPILL_DAMAGE = 2
        const val HEAT_TICKS = 3

        const val PICKUP_SEQ = "seq.human_pickupfloor"
        const val FURNACE_SEQ = "seq.human_furnace"
        const val GRIND_SEQ = "seq.human_herbing_grind"
    }
}
