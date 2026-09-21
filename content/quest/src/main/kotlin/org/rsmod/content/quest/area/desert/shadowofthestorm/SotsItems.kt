package org.rsmod.content.quest.area.desert.shadowofthestorm

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeldU
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.BLACK_DYE
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.BLACK_ITEMS_REQ
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.DESERT_ROBE
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.DESERT_SHIRT
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.DYED_DESERT_ROBE
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.DYED_DESERT_SHIRT
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.DYED_SILVERLIGHT
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.MUSHROOM
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.SILVERLIGHT
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.STAGE_BRIEFED
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.TOME
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The disguise, and the tome the player reads before handing it over.
 *
 * Silverlight and the desert clothes are stained with the same black mushrooms that grow around
 * the temple stairs: the sword takes the raw mushroom, the cloth needs it ground into dye first.
 * Nothing here is reversible, which matches the cache - there is no undyed variant to change back
 * into, and Father Reen hands out a replacement Silverlight if one goes missing.
 */
@Singleton
class SotsItems @Inject constructor(private val sots: ShadowOfTheStormQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeldU(SILVERLIGHT, MUSHROOM) { dyeSilverlight() }
        onOpHeldU(DESERT_SHIRT, BLACK_DYE) { dyeCloth(DESERT_SHIRT, DYED_DESERT_SHIRT) }
        onOpHeldU(DESERT_ROBE, BLACK_DYE) { dyeCloth(DESERT_ROBE, DYED_DESERT_ROBE) }
        onOpHeld1(TOME) { readTome() }
    }

    private suspend fun ProtectedAccess.dyeSilverlight() {
        if (!sots.inProgress(player)) {
            mes("There is no reason to ruin a perfectly good sword.")
            return
        }
        if (sots.stage(player) < STAGE_BRIEFED) {
            mes("Father Badden has not told me what to do with these yet.")
            return
        }
        if (invDel(inv, MUSHROOM).failure) {
            return
        }
        if (invDel(inv, SILVERLIGHT).failure) {
            invAdd(inv, MUSHROOM)
            return
        }
        anim(RUB_SEQ)
        invAdd(inv, DYED_SILVERLIGHT)
        objbox(
            DYED_SILVERLIGHT,
            "You crush the mushroom along the blade. The silver drinks the stain in until the " +
                "sword looks as black as anything the cult wears.",
        )
    }

    private suspend fun ProtectedAccess.dyeCloth(from: String, into: String) {
        if (invDel(inv, BLACK_DYE).failure) {
            return
        }
        if (invDel(inv, from).failure) {
            invAdd(inv, BLACK_DYE)
            return
        }
        anim(RUB_SEQ)
        invAdd(inv, VIAL)
        invAdd(inv, into)
        objbox(into, "You work the dye into the cloth until it is black through and through.")
    }

    private suspend fun ProtectedAccess.readTome() {
        if (!Incantation.assigned(player)) {
            mesbox(
                "The writing crawls and slides under your eye. Without someone to tell you where " +
                    "it starts, none of it settles into words.",
            )
            return
        }
        mesbox(
            "The tome names the words of the summoning, and the order they must be spoken in:" +
                "<br><br><col=7f0000>${Incantation.render(Incantation.tomeOrder(player))}</col>",
        )
        mesbox("That is the same five words Denath gave you, and exactly backwards to them.")
    }

    companion object {
        private const val RUB_SEQ = "seq.human_pickuptable"
        private const val VIAL = "obj.vial_empty"

        /**
         * Everything the cult will accept as black. The list follows the items the live game
         * takes: plain black armour and robes, the dyed desert clothes, and the holiday and
         * novelty black pieces. Trimmed, heraldic and gilded variants are deliberately absent.
         */
        private val BLACK_ITEMS: Set<Int> by lazy {
            BLACK_ITEM_NAMES.mapTo(HashSet()) { it.asRSCM(RSCMType.OBJ) }
        }

        private val BLACK_ITEM_NAMES =
            listOf(
                "obj.agrith_desert_shirt_dyed",
                "obj.agrith_desert_robe_dyed",
                "obj.black_chainbody",
                "obj.black_platebody",
                "obj.black_platelegs",
                "obj.black_full_helm",
                "obj.black_med_helm",
                "obj.black_kiteshield",
                "obj.black_sq_shield",
                "obj.hundred_gauntlets_level_5",
                "obj.priest_gown",
                "obj.priest_robe",
                "obj.mystic_hat_dark",
                "obj.mystic_robe_top_dark",
                "obj.mystic_robe_bottom_dark",
                "obj.secret_ghost_hat",
                "obj.secret_ghost_top",
                "obj.secret_ghost_bottom",
                "obj.secret_ghost_gloves",
                "obj.secret_ghost_boots",
                "obj.secret_ghost_cloak",
                "obj.blackrobetop",
                "obj.blackrobebottom",
                "obj.black_robe",
                "obj.blackwizhat",
                "obj.black_cape",
                "obj.black_partyhat",
                "obj.halloweenmask_black",
                "obj.dragonmask_black",
                "obj.black_unicorn_mask",
                "obj.black_demon_mask",
                "obj.black_dragonhide_body",
                "obj.black_dragonhide_chaps",
                "obj.black_dragon_vambraces",
                "obj.antisanta_mask",
                "obj.antisanta_jacket",
                "obj.antisanta_pants",
                "obj.antisanta_gloves",
                "obj.antisanta_boots",
                "obj.graceful_hood_hallowed",
                "obj.graceful_top_hallowed",
                "obj.graceful_legs_hallowed",
                "obj.graceful_gloves_hallowed",
                "obj.graceful_boots_hallowed",
                "obj.graceful_cape_hallowed",
            )

        fun blackItemsWorn(player: Player): Int =
            player.worn.count { it != null && it.id in BLACK_ITEMS }

        fun dressedInBlack(player: Player): Boolean = blackItemsWorn(player) >= BLACK_ITEMS_REQ

        fun carriesDyedSilverlight(player: Player): Boolean =
            player.righthand.isType(DYED_SILVERLIGHT) ||
                player.inv.any { it.isType(DYED_SILVERLIGHT) }

        /** Either colour of Silverlight counts; a replacement from Father Reen is undyed. */
        fun wieldsSilverlight(player: Player): Boolean =
            player.righthand.isType(DYED_SILVERLIGHT) || player.righthand.isType(SILVERLIGHT)
    }
}
