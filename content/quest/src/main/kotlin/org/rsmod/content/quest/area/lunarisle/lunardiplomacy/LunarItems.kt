package org.rsmod.content.quest.area.lunarisle.lunardiplomacy

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLocCategoryU
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.LUNAR_STAFF
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_STAFF
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Everything the player makes for the quest: the waking sleep potion in Baba Yaga's vial and the
 * soaked kindling, the emerald lantern, the Lunar staff (a Dramen staff imbued at the air, fire,
 * water and earth altars in turn), the lunar helm smithed from ore out of the island's mine, and
 * the four garments sewn from tanned Suqah hide.
 */
class LunarItems @Inject constructor(private val lunar: LunarDiplomacyQuest) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeldU(WATER_VIAL, GUAM) { mixHerb(WATER_VIAL, GUAM, GUAM_VIAL, "guam leaf") }
        onOpHeldU(WATER_VIAL, MARRENTILL) { mixHerb(WATER_VIAL, MARRENTILL, MARR_VIAL, "marrentill") }
        onOpHeldU(GUAM_VIAL, MARRENTILL) { mixHerb(GUAM_VIAL, MARRENTILL, GUAMMARR_VIAL, "marrentill") }
        onOpHeldU(MARR_VIAL, GUAM) { mixHerb(MARR_VIAL, GUAM, GUAMMARR_VIAL, "guam leaf") }
        onOpHeldU(PESTLE_AND_MORTAR, SUQAH_TOOTH) { grindTooth() }
        onOpHeldU(GROUND_TOOTH, GUAMMARR_VIAL) { finishPotion() }
        onOpHeldU(FULL_VIAL, KINDLING) { soakKindling() }

        for (lantern in LENS_LANTERNS) {
            onOpHeldU(EMERALD_LENS, lantern.key) { fitLens(lantern.key, lantern.value) }
        }
        onOpHeldU(TINDERBOX, UNLIT_EMERALD) { lightLantern() }
        onOpHeldU(TINDERBOX, EMPTY_EMERALD) { mes("The lantern has no oil in it.") }
        onOpHeld3(LIT_EMERALD) { extinguish() }

        for (step in StaffStep.entries) {
            onOpLocU(step.altar, step.input) { imbue(step) }
        }

        onOpLocCategoryU(FURNACE, LUNAR_ORE) { smeltOre() }
        onOpLocCategoryU(ANVIL, LUNAR_BAR) { smithHelm() }

        onOpHeldU(NEEDLE, TANNED_HIDE) { sewGarment() }
    }

    private fun ProtectedAccess.mixHerb(vial: String, herb: String, product: String, herbName: String) {
        if (statBase(HERBLORE) < HERBLORE_REQ) {
            mes("You need a Herblore level of $HERBLORE_REQ to make this potion.")
            return
        }
        invDel(inv, herb)
        invReplace(inv, vial, 1, product)
        anim(MIX_SEQ)
        soundSynth(MIX_SOUND)
        mes("You add the $herbName to the vial.")
    }

    private fun ProtectedAccess.grindTooth() {
        anim(GRIND_SEQ)
        invReplace(inv, SUQAH_TOOTH, 1, GROUND_TOOTH)
        mes("You grind the Suqah tooth into a fine powder.")
    }

    private fun ProtectedAccess.finishPotion() {
        invDel(inv, GROUND_TOOTH)
        invReplace(inv, GUAMMARR_VIAL, 1, FULL_VIAL)
        anim(MIX_SEQ)
        soundSynth(MIX_SOUND)
        mes("You add the ground tooth to the vial. The mixture swirls and settles. You have a waking sleep potion.")
    }

    private fun ProtectedAccess.soakKindling() {
        invReplace(inv, FULL_VIAL, 1, EMPTY_VIAL)
        invReplace(inv, KINDLING, 1, SOAKED_KINDLING)
        soundSynth(POUR_SOUND)
        mes("You pour the waking sleep potion over the kindling.")
    }

    private fun ProtectedAccess.fitLens(lantern: String, result: String) {
        invDel(inv, EMERALD_LENS)
        invReplace(inv, lantern, 1, result)
        if (lantern != LANTERN_FRAME) {
            invAddOrDropLens()
        }
        mes("You fit the emerald lens into the lantern.")
    }

    private fun ProtectedAccess.invAddOrDropLens() {
        if (inv.freeSpace() > 0) {
            invAdd(inv, OLD_LENS)
            mes("You keep the old lens.")
        }
    }

    private fun ProtectedAccess.lightLantern() {
        if (statBase(FIREMAKING) < LANTERN_FIREMAKING) {
            mes("You need a Firemaking level of $LANTERN_FIREMAKING to light the lantern.")
            return
        }
        invReplace(inv, UNLIT_EMERALD, 1, LIT_EMERALD)
        mes("You light the emerald lantern.")
    }

    private fun ProtectedAccess.extinguish() {
        invReplace(inv, LIT_EMERALD, 1, UNLIT_EMERALD)
        mes("You extinguish the lantern.")
    }

    private suspend fun ProtectedAccess.imbue(step: StaffStep) {
        if (lunar.stage(player) != STAGE_STAFF) {
            mes("Nothing interesting happens.")
            return
        }
        anim(STAFF_SEQ)
        spotanim(step.spot, height = SPOT_HEIGHT)
        soundSynth(IMBUE_SOUND)
        delay(2)
        invReplace(inv, step.input, 1, step.output)
        mes(step.message)
    }

    private suspend fun ProtectedAccess.smeltOre() {
        if (statBase(SMITHING) < LUNAR_SMITHING) {
            mes("You need a Smithing level of $LUNAR_SMITHING to smelt lunar ore.")
            return
        }
        anim(FURNACE_SEQ)
        soundSynth(FURNACE_SOUND)
        delay(3)
        invReplace(inv, LUNAR_ORE, 1, LUNAR_BAR)
        mes("You smelt the lunar ore into a bar.")
    }

    private suspend fun ProtectedAccess.smithHelm() {
        if (!inv.contains(HAMMER) && !inv.contains(IMCANDO_HAMMER)) {
            mes("You need a hammer to work the metal with.")
            return
        }
        if (statBase(SMITHING) < LUNAR_SMITHING) {
            mes("You need a Smithing level of $LUNAR_SMITHING to smith a lunar helm.")
            return
        }
        val make = startDialogueChoice()
        ifClose()
        if (!make) {
            return
        }
        anim(SMITH_SEQ)
        soundSynth(SMITH_SOUND)
        delay(3)
        invReplace(inv, LUNAR_BAR, 1, LunarPiece.Helm.obj)
        mes("You hammer the lunar bar into a helm.")
    }

    private suspend fun ProtectedAccess.startDialogueChoice(): Boolean {
        var make = false
        startDialogue {
            objbox(LUNAR_BAR, "This lunar bar looks just the right size for the helm of the Moon Clan's ceremonial clothing.")
            make = choice2("Yes.", true, "No.", false, title = "Make a lunar helm?")
        }
        return make
    }

    private suspend fun ProtectedAccess.sewGarment() {
        if (!inv.contains(THREAD)) {
            mes("You need some thread to sew with.")
            return
        }
        if (statBase(CRAFTING) < LUNAR_CRAFTING) {
            mes("You need a Crafting level of $LUNAR_CRAFTING to make this.")
            return
        }
        val garments = listOf(LunarPiece.Torso, LunarPiece.Trousers, LunarPiece.Gloves, LunarPiece.Boots)
        val choice =
            menu(
                "What would you like to make?",
                "Lunar torso",
                "Lunar legs",
                "Lunar gloves",
                "Lunar boots",
            )
        ifClose()
        val garment = garments.getOrNull(choice) ?: return
        anim(SEW_SEQ)
        soundSynth(SEW_SOUND)
        delay(2)
        invDel(inv, THREAD)
        invReplace(inv, TANNED_HIDE, 1, garment.obj)
        mes("You sew the Suqah leather into a lunar ${garment.label}.")
    }

    /** One altar's worth of power on the way from a Dramen staff to a Lunar staff. */
    private enum class StaffStep(
        val altar: String,
        val input: String,
        val output: String,
        val spot: String,
        val message: String,
    ) {
        Air(
            "loc.air_altar",
            "obj.dramen_staff",
            "obj.dramen_staff_air",
            "spotanim.windstrike_impact",
            "The staff is imbued with the power of air.",
        ),
        Fire(
            "loc.fire_altar",
            "obj.dramen_staff_air",
            "obj.dramen_staff_fire",
            "spotanim.firestrike_impact",
            "The staff is imbued with the power of fire.",
        ),
        Water(
            "loc.water_altar",
            "obj.dramen_staff_fire",
            "obj.dramen_staff_water",
            "spotanim.waterstrike_impact",
            "The staff is imbued with the power of water.",
        ),
        Earth(
            "loc.earth_altar",
            "obj.dramen_staff_water",
            LUNAR_STAFF,
            "spotanim.earthstrike_impact",
            "The staff is imbued with the power of earth. You have made a Lunar staff!",
        ),
    }

    private companion object {
        const val WATER_VIAL = "obj.lunar_moonclan_liminal_vial_water"
        const val EMPTY_VIAL = "obj.lunar_moonclan_liminal_vial_empty"
        const val GUAM_VIAL = "obj.lunar_moonclan_liminal_guam"
        const val MARR_VIAL = "obj.lunar_moonclan_liminal_marr"
        const val GUAMMARR_VIAL = "obj.lunar_moonclan_liminal_guammarr"
        const val FULL_VIAL = "obj.lunar_moonclan_liminal_vial_full"
        const val GUAM = "obj.guam_leaf"
        const val MARRENTILL = "obj.marentill"
        const val PESTLE_AND_MORTAR = "obj.pestle_and_mortar"
        const val SUQAH_TOOTH = "obj.suqka_tooth"
        const val GROUND_TOOTH = "obj.lunar_groundtooth"
        const val KINDLING = "obj.lunar_moonclan_kindling"
        const val SOAKED_KINDLING = "obj.lunar_moonclan_kindling_soaked"

        const val EMERALD_LENS = "obj.bullseye_lantern_lens_lunar_quest"
        const val OLD_LENS = "obj.bullseye_lantern_lens"
        const val LANTERN_FRAME = "obj.bullseye_lantern_nolens"
        const val UNLIT_EMERALD = "obj.bullseye_lantern_unlit_lunar_quest"
        const val LIT_EMERALD = "obj.bullseye_lantern_lit_lunar_quest"
        const val EMPTY_EMERALD = "obj.bullseye_lantern_empty_lunar_quest"
        const val TINDERBOX = "obj.tinderbox"

        /** Lanterns the emerald lens fits, and what each becomes; oil stays with the lantern. */
        val LENS_LANTERNS =
            mapOf(
                "obj.bullseye_lantern_unlit" to UNLIT_EMERALD,
                "obj.bullseye_lantern_empty" to EMPTY_EMERALD,
                LANTERN_FRAME to EMPTY_EMERALD,
            )

        const val LUNAR_ORE = "obj.quest_lunar_magic_ore"
        const val LUNAR_BAR = "obj.quest_lunar_magic_bar"
        const val FURNACE = "category.furnace"
        const val ANVIL = "category.anvil"
        const val HAMMER = "obj.hammer"
        const val IMCANDO_HAMMER = "obj.imcando_hammer"

        const val NEEDLE = "obj.needle"
        const val THREAD = "obj.thread"
        const val TANNED_HIDE = "obj.suqka_hide"

        const val HERBLORE = "stat.herblore"
        const val FIREMAKING = "stat.firemaking"
        const val SMITHING = "stat.smithing"
        const val CRAFTING = "stat.crafting"
        const val HERBLORE_REQ = 5
        const val LANTERN_FIREMAKING = 49
        const val LUNAR_SMITHING = 1
        const val LUNAR_CRAFTING = 61

        const val MIX_SEQ = "seq.human_herbing_vial"
        const val MIX_SOUND = "synth.vial_mix"
        const val GRIND_SEQ = "seq.human_herbing_grind"
        const val POUR_SOUND = "synth.vial_pour"
        const val STAFF_SEQ = "seq.quest_lunar_staff_ruins"
        const val IMBUE_SOUND = "synth.enchant_ruby_ring"
        const val SPOT_HEIGHT = 92
        const val FURNACE_SEQ = "seq.human_furnace"
        const val FURNACE_SOUND = "synth.furnace"
        const val SMITH_SEQ = "seq.human_smithing"
        const val SMITH_SOUND = "synth.anvil02"
        const val SEW_SEQ = "seq.human_leather_crafting"
        const val SEW_SOUND = "synth.stiching"
    }
}
