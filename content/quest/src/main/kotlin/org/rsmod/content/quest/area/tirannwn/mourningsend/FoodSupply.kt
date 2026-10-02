package org.rsmod.content.quest.area.tirannwn.mourningsend

import jakarta.inject.Inject
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.script.onOpContentMixedLocU
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.ardougne.QuestDoors
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.APPLE_BARREL
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.BARREL
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.BARREL_OF_NAPHTHA
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.BARREL_OF_ROTTEN_APPLES
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.NAPHTHA_APPLE_MIX
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.ROTTEN_APPLE
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.SIEVE
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.STAGE_FOOD_TASK
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.STAGE_SIEVE
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.STAGE_STORES_DONE
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.TOXIC_NAPHTHA
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.TOXIC_POWDER
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Making the toxic powder and spoiling two of West Ardougne's food stores. These are fictional
 * item transformations only.
 *
 * In the untended orchard north of the city (its picket gates open like any other) an empty barrel scoops up a rotten apple pile, and
 * the apple press crushes it into an apple barrel. A barrel of naphtha (from the Chemist's still in
 * Rimmington, as in Regicide) mixed with it gives a naphtha apple mix and the empty barrel back;
 * Elena's sieve (kept) turns that into toxic naphtha; and drying it on a cooking range leaves two
 * piles of toxic powder and the barrel. Every step is one transaction checked against the pack
 * first, so a full inventory never loses an input. On an open fire the naphtha goes up instead,
 * burning three quarters of the player's current hitpoints and destroying it, as Elena warns.
 *
 * Each of the three stores (see [FoodStore]) takes one powder and is then marked on its own cache
 * varbit; the second distinct store completes the step. A store already spoiled, a pile of grain
 * that isn't a store, or a player not on this step costs no powder.
 */
class FoodSupply @Inject constructor(private val mourning: MourningsEndQuest, private val doors: QuestDoors) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(GATE_LEFT) { openGate(it.loc, left = true) }
        onOpLoc1(GATE_RIGHT) { openGate(it.loc, left = false) }
        onOpLocU(APPLE_PILE, BARREL) { fillBarrel() }
        onOpLoc1(APPLE_PILE) { takeFromPile() }
        for (press in PRESSES) {
            onOpLocU(press, BARREL_OF_ROTTEN_APPLES) { press() }
            onOpLocU(press, ROTTEN_APPLE) { mes("It would take forever to fill the press like this. You need a way to carry lots of rotten apples.") }
        }
        onOpHeldU(BARREL_OF_NAPHTHA, APPLE_BARREL) { mixNaphtha() }
        onOpHeldU(SIEVE, NAPHTHA_APPLE_MIX) { sieve() }
        for (content in RANGE_CONTENT) {
            onOpContentMixedLocU(content, TOXIC_NAPHTHA) { dry() }
        }
        for (range in OTHER_RANGES) {
            onOpLocU(range, TOXIC_NAPHTHA) { dry() }
        }
        for (fire in FIRES) {
            onOpLocU(fire, TOXIC_NAPHTHA) { ignite() }
        }
        for (store in FoodStore.entries) {
            for (sacks in store.locs) {
                onOpLocU(sacks, TOXIC_POWDER) { spoil(store) }
            }
        }
    }

    private suspend fun ProtectedAccess.openGate(gate: BoundLocInfo, left: Boolean) {
        arriveDelay()
        faceLoc(gate)
        val leftGate = if (left) doors.asInfo(gate) else doors.leftOfGate(gate, GATE_LEFT)
        val rightGate = if (left) doors.rightOfGate(gate, GATE_RIGHT) else doors.asInfo(gate)
        doors.openGate(this, leftGate, GATE_LEFT_OPEN, rightGate, GATE_RIGHT_OPEN, symmetric = false)
    }

    private suspend fun ProtectedAccess.fillBarrel() {
        arriveDelay()
        anim(SCOOP_SEQ)
        if (invReplace(inv, BARREL, 1, BARREL_OF_ROTTEN_APPLES).failure) {
            return
        }
        mes("You scoop up a barrel full of the rotten apples.")
    }

    private suspend fun ProtectedAccess.takeFromPile() {
        if (inv.contains(BARREL)) {
            fillBarrel()
            return
        }
        arriveDelay()
        mes("You'll need something to carry the rotten apples in, like an empty barrel.")
    }

    private suspend fun ProtectedAccess.press() {
        arriveDelay()
        anim(PRESS_SEQ)
        mes("You use the apple press to crush your rotten apples.")
        delay(PRESS_TICKS)
        if (invReplace(inv, BARREL_OF_ROTTEN_APPLES, 1, APPLE_BARREL).failure) {
            return
        }
        mes("You get a barrel full of crushed rotten apples.")
    }

    private fun ProtectedAccess.mixNaphtha() {
        if (!swap(listOf(BARREL_OF_NAPHTHA to 1, APPLE_BARREL to 1), listOf(NAPHTHA_APPLE_MIX to 1, BARREL to 1))) {
            return
        }
        mes("You mix the naphtha with the apple mush.")
    }

    private fun ProtectedAccess.sieve() {
        if (invReplace(inv, NAPHTHA_APPLE_MIX, 1, TOXIC_NAPHTHA).failure) {
            return
        }
        mes("You sieve the solids out of the mixture.")
    }

    private suspend fun ProtectedAccess.dry() {
        arriveDelay()
        if (inv.freeSpace() < 2) {
            mes("You'll need two free spaces in your inventory for what's left after drying this.")
            return
        }
        anim(RANGE_SEQ)
        delay(DRY_TICKS)
        if (!swap(listOf(TOXIC_NAPHTHA to 1), listOf(TOXIC_POWDER to 2, BARREL to 1))) {
            return
        }
        mes("You evaporate the naphtha and you're left with a powdery residue on the insides of the barrel.")
    }

    private suspend fun ProtectedAccess.ignite() {
        arriveDelay()
        anim(FIRE_SEQ)
        if (invReplace(inv, TOXIC_NAPHTHA, 1, BARREL).failure) {
            return
        }
        spotanim(EXPLOSION_SPOTANIM)
        mes("The naphtha catches light and explodes in your face!")
        val damage = player.hitpoints * BURN_PERCENT / 100
        player.queueHit(delay = 0, type = HitType.Typeless, damage = damage, modifier = NoopPlayerHitModifier)
    }

    private suspend fun ProtectedAccess.spoil(store: FoodStore) {
        arriveDelay()
        val stage = mourning.stage(player)
        if (stage !in STAGE_FOOD_TASK..STAGE_SIEVE) {
            mes("You have no reason to do that.")
            return
        }
        if (mourning.storePoisoned(player, store)) {
            mes("You've already poisoned this store.")
            return
        }
        anim(SPOIL_SEQ)
        if (invDel(inv, TOXIC_POWDER, 1).failure) {
            return
        }
        MourningsEndQuest.setVarBit(player, store.varbit, 1)
        mes("You add the toxin to the grain. After a few seconds of mixing you can't tell the difference.")
        if (mourning.storesPoisoned(player) >= STORES_NEEDED) {
            mourning.advanceTo(this, STAGE_STORES_DONE)
            mes("That's two food stores spoiled. You should report back to Essyllt.")
        }
    }

    companion object {
        const val APPLE_PILE = "loc.mourning_orchard_applepile"
        const val GATE_LEFT = "loc.mourning_orchard_fencegate_l"
        const val GATE_RIGHT = "loc.mourning_orchard_fencegate_r"
        const val GATE_LEFT_OPEN = "loc.mourning_orchard_fencegate_open_l"
        const val GATE_RIGHT_OPEN = "loc.mourning_orchard_fencegate_open_r"
        val PRESSES = listOf("loc.mourning_orchard_applebarrel_empty", "loc.mourning_orchard_applebarrel_mush")
        val RANGE_CONTENT = listOf("content.cooking_range_standard", "content.cooking_range_lumbridge", "content.cooking_range_hosidius")
        val OTHER_RANGES = listOf("loc.elf_village_range")
        val FIRES = listOf("loc.fire", "loc.forestry_fire")
        const val STORES_NEEDED = 2
        const val BURN_PERCENT = 75

        const val SCOOP_SEQ = "seq.human_pickupfloor"
        const val PRESS_SEQ = "seq.human_pickuptable"
        const val RANGE_SEQ = "seq.human_cooking"
        const val FIRE_SEQ = "seq.human_firecooking"
        const val SPOIL_SEQ = "seq.human_pickuptable"
        const val EXPLOSION_SPOTANIM = "spotanim.explodingvial"
        const val PRESS_TICKS = 2
        const val DRY_TICKS = 2
    }
}
