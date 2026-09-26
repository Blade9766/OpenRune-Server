package org.rsmod.content.quest.area.lumbridge.losttribe.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.NARDOK
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Nardok, the Dorgeshuun mines' whispering arms dealer. He only trades once there is peace. */
class Nardok
@Inject
constructor(private val lostTribe: LostTribeQuest, private val shops: Shops) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(NARDOK) { startDialogue(it.npc) { nardok(it.npc) } }
        onOpNpc3(NARDOK) { trade(it.npc) }
    }

    private suspend fun ProtectedAccess.trade(npc: Npc) {
        if (!lostTribe.isComplete(player)) {
            startDialogue(npc) { refuse() }
            return
        }
        openShop(npc)
    }

    private fun ProtectedAccess.openShop(npc: Npc) {
        shops.open(player, npc, SHOP_TITLE, SHOP_INV)
    }

    private suspend fun Dialogue.refuse() {
        chatNpc(angry, "You ain't getting nuthin' off me, surface-dweller!")
    }

    private suspend fun Dialogue.nardok(npc: Npc) {
        if (!lostTribe.isComplete(player)) {
            refuse()
            return
        }
        chatNpc(shifty, "Psst... wanna buy some weapons?")
        when (choice2("What have you got?", 1, "Why are you whispering?", 2)) {
            1 -> wares(npc)
            2 -> whispering()
        }
    }

    private suspend fun Dialogue.wares(npc: Npc) {
        chatPlayer(quiz, "What have you got?")
        chatNpc(
            shifty,
            "Well, first up there's the normal bone club and bone spear. Good, solid frog-bone, and the spear " +
                "has an iron tip. Can withstand a lot of punishment, if you know what I mean!",
        )
        chatNpc(
            shifty,
            "But if you're after something a bit fancier, a bit more sophisticated, there's the bone " +
                "crossbow and dagger.",
        )
        chatNpc(
            shifty,
            "These are specially good for getting your prey unawares, but you're not going to get the best " +
                "out of them unless you've been taught the special technique.",
        )
        chatNpc(quiz, "So, you interested?")
        while (true) {
            when (
                choice3(
                    "I might be.",
                    1,
                    "What is this special technique?",
                    2,
                    "No thanks.",
                    3,
                )
            ) {
                1 -> {
                    chatPlayer(neutral, "I might be.")
                    access.openShop(npc)
                    return
                }
                2 -> technique()
                3 -> {
                    chatPlayer(neutral, "No thanks.")
                    chatNpc(neutral, "Yeah, you stay out of trouble.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.technique() {
        chatPlayer(quiz, "What is this special technique?")
        chatNpc(
            shifty,
            "Special aiming technique. If you hit an enemy with it their defence will get weaker so you can " +
                "hit them again!",
        )
        chatNpc(
            shifty,
            "Plus, if you use this technique on an enemy who isn't expecting it, you'll always hit them! " +
                "Pretty good, eh?",
        )
        chatNpc(
            neutral,
            "'Course, there's only a few people who know it, so unless you can get someone to show you you " +
                "won't be able to do it.",
        )
        chatNpc(quiz, "So, are you interested?")
    }

    private suspend fun Dialogue.whispering() {
        chatPlayer(quiz, "Why are you whispering?")
        chatNpc(
            shifty,
            "Well, y'know, it's not the most reputable profession, is it? Selling weapons?",
        )
        chatPlayer(quiz, "Why not?")
        chatNpc(
            neutral,
            "Well, they're used to hurt people, aren't they? That's not considered very, y'know, respectable. " +
                "Even though the guards use them, and the hunters, they're still considered a bit, y'know, dirty.",
        )
        chatNpc(quiz, "Isn't it like that where you come from?")
        chatPlayer(neutral, "No, on the surface weapon-making is considered a noble profession.")
        chatNpc(confused, "I don't know, you surface people are strange!")
    }

    private companion object {
        const val SHOP_TITLE = "Nardok's Bone Weapons"
        const val SHOP_INV = "inv.dorgeshuun_weapon_shop"
    }
}
