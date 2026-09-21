package org.rsmod.content.quest.area.varrock.familycrest

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.quest.area.varrock.familycrest.FamilyCrestQuest.Companion.AVAN
import org.rsmod.content.quest.area.varrock.familycrest.FamilyCrestQuest.Companion.CALEB
import org.rsmod.content.quest.area.varrock.familycrest.FamilyCrestQuest.Companion.CHAOS_GAUNTLETS
import org.rsmod.content.quest.area.varrock.familycrest.FamilyCrestQuest.Companion.COINS
import org.rsmod.content.quest.area.varrock.familycrest.FamilyCrestQuest.Companion.COOKING_GAUNTLETS
import org.rsmod.content.quest.area.varrock.familycrest.FamilyCrestQuest.Companion.ENCHANT_COST
import org.rsmod.content.quest.area.varrock.familycrest.FamilyCrestQuest.Companion.ENCHANT_SOUND
import org.rsmod.content.quest.area.varrock.familycrest.FamilyCrestQuest.Companion.ENCHANT_SPOTANIM
import org.rsmod.content.quest.area.varrock.familycrest.FamilyCrestQuest.Companion.GOLDSMITH_GAUNTLETS
import org.rsmod.content.quest.area.varrock.familycrest.FamilyCrestQuest.Companion.JOHNATHON
import org.rsmod.content.quest.area.varrock.familycrest.FamilyCrestQuest.Companion.STEEL_GAUNTLETS
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The four forms the reward gauntlets can take. The ordinal is stored in
 * `varbit.famcrest_gauntlets_kind` so Dimintheis can hand back the pair the player was wearing
 * when they lost it.
 */
enum class Gauntlets(val obj: String, val displayName: String, val blessing: String) {
    STEEL(STEEL_GAUNTLETS, "Steel gauntlets", "plain steel"),
    COOKING(COOKING_GAUNTLETS, "Cooking gauntlets", "the skill of the kitchen"),
    GOLDSMITH(GOLDSMITH_GAUNTLETS, "Goldsmith gauntlets", "the goldsmith's touch"),
    CHAOS(CHAOS_GAUNTLETS, "Chaos gauntlets", "the chaos of the elements");

    companion object {
        fun of(player: Player): Gauntlets = entries.getOrElse(player.gauntletsKind) { STEEL }

        fun heldBy(access: ProtectedAccess): Gauntlets? =
            entries.firstOrNull { access.inv.contains(it.obj) }

        fun wornBy(access: ProtectedAccess): Gauntlets? =
            entries.firstOrNull { access.worn.contains(it.obj) }
    }
}

/**
 * Each brother's "Gauntlets" option, which only exists once the quest is finished. The first
 * enchantment is free; after that the brothers charge [ENCHANT_COST] to undo a sibling's work.
 */
class GauntletEnchanting @Inject constructor(private val familyCrest: FamilyCrestQuest) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc3(CALEB) { startDialogue(it.npc) { enchant(Gauntlets.COOKING) } }
        onOpNpc3(AVAN) { startDialogue(it.npc) { enchant(Gauntlets.GOLDSMITH) } }
        onOpNpc3(JOHNATHON) { startDialogue(it.npc) { enchant(Gauntlets.CHAOS) } }
    }

    private suspend fun Dialogue.enchant(into: Gauntlets) {
        if (!familyCrest.isComplete(player)) {
            chatNpc(neutral, "I have nothing for you.")
            return
        }
        val held = Gauntlets.heldBy(access)
        if (held == null) {
            if (Gauntlets.wornBy(access) != null) {
                chatNpc(neutral, "Take them off and hand them to me, and I'll see what I can do.")
                return
            }
            chatNpc(quiz, "You'll need to bring me the gauntlets first.")
            return
        }
        if (held == into) {
            chatNpc(happy, "Those are already my work. There's nothing more I can do to them.")
            return
        }
        if (player.usedFreeEnchant) {
            chatNpc(
                neutral,
                "Undoing my brother's work is no small thing. It'll cost you " +
                    "${"%,d".format(ENCHANT_COST)} coins.",
            )
            val pay =
                choice2(
                    "Pay ${"%,d".format(ENCHANT_COST)} coins.",
                    true,
                    "Not at that price.",
                    false,
                )
            if (!pay) {
                chatPlayer(neutral, "Not at that price.")
                return
            }
            if (access.inv.count(COINS) < ENCHANT_COST) {
                chatPlayer(sad, "I don't have that much with me.")
                return
            }
            if (access.invDel(access.inv, COINS, ENCHANT_COST).failure) {
                return
            }
        }
        if (!swap(held, into)) {
            return
        }
        player.usedFreeEnchant = true
        player.gauntletsKind = into.ordinal
        access.spotanim(ENCHANT_SPOTANIM, height = 100)
        access.soundSynth(ENCHANT_SOUND)
        objbox(into.obj, "The gauntlets take on ${into.blessing}.")
        chatNpc(happy, "There you go. ${into.displayName}, as good as father's own.")
    }

    private fun Dialogue.swap(from: Gauntlets, into: Gauntlets): Boolean {
        if (access.invDel(access.inv, from.obj).failure) {
            return false
        }
        access.invAdd(access.inv, into.obj)
        return true
    }
}
