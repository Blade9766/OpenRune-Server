package org.rsmod.content.quest.area.lunarisle.dreammentor

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarIsle
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The potion of shared dreaming, made in the Oneiromancer's dream vial: water, then goutweed, then
 * an astral rune broken up with a hammer and ground with a pestle and mortar. Each step swaps one
 * item for the next, so a step that can't take place takes nothing. Ordinary water sources fill
 * the vial through the generic water-source script; Lunar Isle's own sinks and well are here.
 */
class DreamPotion @Inject constructor(private val town: DreamTown) : PluginScript() {
    override fun ScriptContext.startup() {
        for (source in LunarIsle.WATER_SOURCES) {
            onOpLocU(source, VIAL_EMPTY) { fillVial() }
        }
        onOpHeldU(GOUTWEED, VIAL_WATER) { addGoutweed() }
        for (hammer in HAMMERS) {
            onOpHeldU(hammer, ASTRAL_RUNE) { breakRune() }
        }
        onOpHeldU(PESTLE_AND_MORTAR, ASTRAL_SHARDS) { grindShards() }
        onOpHeldU(GROUND_ASTRAL, VIAL_WEED) { finishPotion() }
        onOpLocU(DreamTown.BRAZIER, VIAL_FULL) { with(town) { potionOnBrazier() } }
    }

    private fun ProtectedAccess.fillVial() {
        if (invReplace(inv, VIAL_EMPTY, 1, VIAL_WATER).failure) {
            return
        }
        anim(FILL_SEQ)
        mes("You fill the dream vial with water.")
    }

    private fun ProtectedAccess.addGoutweed() {
        if (GOUTWEED !in inv || invReplace(inv, VIAL_WATER, 1, VIAL_WEED).failure) {
            return
        }
        if (invDel(inv, GOUTWEED).failure) {
            invReplace(inv, VIAL_WEED, 1, VIAL_WATER)
            return
        }
        mes("You add the goutweed to the dream vial.")
    }

    private fun ProtectedAccess.breakRune() {
        if (invDel(inv, ASTRAL_RUNE).failure) {
            return
        }
        if (invAdd(inv, ASTRAL_SHARDS).failure) {
            invAdd(inv, ASTRAL_RUNE)
            mes("You don't have enough room to hold the shards.")
            return
        }
        anim(HAMMER_SEQ)
        mes("You break the astral rune into shards.")
    }

    private fun ProtectedAccess.grindShards() {
        if (invReplace(inv, ASTRAL_SHARDS, 1, GROUND_ASTRAL).failure) {
            return
        }
        anim(GRIND_SEQ)
        mes("You grind the shards into a fine powder.")
    }

    private suspend fun ProtectedAccess.finishPotion() {
        if (GROUND_ASTRAL !in inv || VIAL_WEED !in inv) {
            return
        }
        anim(MIX_SEQ)
        delay(MIX_TICKS)
        if (invDel(inv, GROUND_ASTRAL).failure) {
            return
        }
        if (invReplace(inv, VIAL_WEED, 1, VIAL_FULL).failure) {
            invAdd(inv, GROUND_ASTRAL)
            return
        }
        statAdvance("stat.herblore", MIX_XP)
        mes("You add the ground astral rune to the vial. It's a dream potion!")
    }

    companion object {
        const val VIAL_EMPTY = "obj.dream_vial_empty"
        const val VIAL_WATER = "obj.dream_vial_water"
        const val VIAL_WEED = "obj.dream_vial_weed"
        const val VIAL_FULL = "obj.dream_vial_full"
        const val ASTRAL_SHARDS = "obj.dream_astral_shards"
        const val GROUND_ASTRAL = "obj.dream_groundastral"
        const val GOUTWEED = "obj.eadgar_goutweed_herb"
        const val ASTRAL_RUNE = "obj.astralrune"
        const val PESTLE_AND_MORTAR = "obj.pestle_and_mortar"

        /** Every stage of the vial, for telling whether the player still has one. */
        val VIALS = listOf(VIAL_EMPTY, VIAL_WATER, VIAL_WEED, VIAL_FULL)

        val HAMMERS = listOf("obj.hammer", "obj.imcando_hammer")

        const val MIX_XP = 84.0
        const val MIX_TICKS = 2

        private const val FILL_SEQ = "seq.human_pickuptable_walkmerge"
        private const val HAMMER_SEQ = "seq.human_smithing"
        private const val GRIND_SEQ = "seq.human_herbing_grind"
        private const val MIX_SEQ = "seq.human_herbing_vial"
    }
}
