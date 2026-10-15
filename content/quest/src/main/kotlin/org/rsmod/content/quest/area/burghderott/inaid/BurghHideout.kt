package org.rsmod.content.quest.area.burghderott.inaid

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.POLMAFI_BURGH
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.RADIGAD_BURGH
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.SILVTHRILL
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.SILVTHRILL_ENCHANTED
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_LIBRARY_KEY
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_MOULD_MADE
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_TOMB_FOUND
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.VELIAF_BURGH
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Myreque's new base in the inn's cellar (level 0 of the cellar, only reached once they have
 * moved in). Veliaf finishes the quest: the Rod of Ivandis, of any charge, is taken from the
 * player's pack - not while it is wielded - and the rewards follow, once.
 */
class BurghHideout @Inject constructor(private val iaom: InAidOfTheMyrequeQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(VELIAF_BURGH) { startDialogue(it.npc) { veliaf(it.npc, offered = false) } }
        onOpNpcU(VELIAF_BURGH) {
            if (it.objType.id in rodIds || it.objType.isObj(SILVTHRILL) || it.objType.isObj(SILVTHRILL_ENCHANTED)) {
                startDialogue(it.npc) { veliaf(it.npc, offered = true) }
            } else {
                mes("Nothing interesting happens.")
            }
        }
        onOpNpc1(POLMAFI_BURGH) {
            startDialogue(it.npc) {
                chatNpc(happy, "Hey there. It's great of you to do all this work for Veliaf. I've heard him say good things about you. Keep it up and I'm sure you'll be asked to join the Myreque properly!")
            }
        }
        onOpNpc1(RADIGAD_BURGH) {
            startDialogue(it.npc) { chatNpc(happy, "Thanks for all your work on the new hideout. We've tidied up a bit since, as you can see.") }
        }
    }

    private suspend fun Dialogue.veliaf(npc: Npc, offered: Boolean) {
        if (iaom.isComplete(player)) {
            chatNpc(happy, "Well met, ${player.displayName}. Thanks to you we have a home, and a weapon worth fearing.")
            return
        }
        val stage = iaom.stage(player)
        val rod = ROD_IDS.firstOrNull { it in player.inv }
        val wielded = ROD_IDS.any { it in player.worn }
        if (stage >= STAGE_MOULD_MADE && (rod != null || wielded || offered)) {
            handOver(rod, wielded)
            return
        }
        if (stage >= STAGE_MOULD_MADE && (SILVTHRILL in player.inv || SILVTHRILL_ENCHANTED in player.inv)) {
            chatNpc(happy, "Well met. How's it going?")
            chatPlayer(happy, "I have brought you the Rod of Ivandis!")
            if (SILVTHRILL_ENCHANTED in player.inv) {
                chatNpc(neutral, "Hmm, it certainly looks authentic, and I can see it's been enchanted. But there's no divine blessing on it. Perhaps there's a step you haven't completed yet?")
            } else {
                chatNpc(neutral, "Hmm, it certainly looks authentic. But there's no enchantment on it, mortal or divine. Perhaps there's a step you haven't completed yet?")
            }
            chatPlayer(quiz, "Where would I find out how to complete the rod?")
            chatNpc(neutral, "If the truth is anywhere, I'd wager it's at Paterdomus.")
            return
        }
        when {
            stage >= STAGE_MOULD_MADE -> afterMould()
            stage >= STAGE_TOMB_FOUND -> afterTomb()
            else -> beforeTomb()
        }
    }

    private suspend fun Dialogue.handOver(rod: String?, wielded: Boolean) {
        chatNpc(happy, "Well met. How's it going?")
        chatPlayer(happy, "I have brought you the Rod of Ivandis!")
        chatNpc(happy, "Very impressive. Good work.")
        if (rod == null) {
            if (wielded) {
                chatNpc(neutral, "Hmmm, I can't take it off your hands while you're still using it. If you want to give it to me, you'll need to unequip it.")
            }
            return
        }
        if (access.invDel(access.inv, rod).failure) {
            return
        }
        objbox(rod, "You give the Rod of Ivandis to Veliaf.")
        chatNpc(shocked, "I... I don't believe it! You've really done it!")
        chatNpc(happy, "Well, ${player.displayName}, you've certainly excelled yourself. I believe you may soon be true Myreque material.")
        iaom.complete(access)
    }

    private suspend fun Dialogue.beforeTomb() {
        chatNpc(happy, "Thanks for getting Ivan to Paterdomus safely. Let me know if you have any luck finding out about Ivandis.")
        while (true) {
            val library = iaom.stage(player) >= STAGE_LIBRARY_KEY
            when (
                choice3(
                    if (library) "I've found a library under Paterdomus." else "Do you know where Ivandis' tomb could be?", if (library) 1 else 2,
                    "What do you know about Ivandis?", 3,
                    "Okay, thanks.", 4,
                )
            ) {
                1 -> {
                    chatPlayer(happy, "I've found a library under Paterdomus.")
                    chatNpc(happy, "That can only be good news. I imagine it holds all sorts of useful things. Do tell me if you find anything of note in there.")
                }
                2 -> whereTomb()
                3 -> aboutIvandis()
                else -> {
                    chatPlayer(neutral, "Okay, thanks.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.whereTomb() {
        chatPlayer(quiz, "Do you know where Ivandis' tomb could be?")
        chatNpc(laugh, "If I knew where it was, I'd be there now.")
        chatPlayer(neutral, "Well, yes, but maybe you have a rough idea.")
        chatNpc(neutral, "I suspect he's buried somewhere in Morytania. He's said to have led countless charges into these lands and died doing so.")
        chatNpc(neutral, "As for where, I can't imagine he got far from Paterdomus.")
    }

    private suspend fun Dialogue.aboutIvandis() {
        chatPlayer(quiz, "What do you know about Ivandis?")
        chatNpc(neutral, "Ivandis Seergaze was one of the Seven Priestly Warriors. With the other six, he held back the vampyres when they tried to invade Misthalin.")
        chatNpc(neutral, "The seven of them also blessed the Salve, so the vampyres could never cross it.")
        chatNpc(neutral, "They say Ivandis wielded a great weapon against the vampyres, one that could render them helpless for a short time.")
        chatNpc(neutral, "There are also stories crediting him with something called 'Guthix balance'.")
        chatPlayer(quiz, "Guthix? Interesting.")
        chatNpc(neutral, "Indeed. Guthix, god of balance, is said to make sure neither Saradomin nor Zamorak gains the upper hand. Why a warrior-priest of Saradomin would be tied to him, I couldn't say.")
    }

    private suspend fun Dialogue.afterTomb() {
        chatNpc(happy, "Well met. How's it going?")
        while (true) {
            when (choice3("I think I've found Ivandis' tomb.", 1, "What do you know about Ivandis?", 2, "Okay, thanks.", 3)) {
                1 -> {
                    chatPlayer(happy, "I think I've found Ivandis' tomb.")
                    chatNpc(happy, "That's great news! Hopefully you'll find something of use in there.")
                    chatPlayer(neutral, "There's just a coffin, as far as I can tell. There's some sort of rod fused to the top of it though.")
                    chatNpc(shocked, "A rod, you say? That sounds like it could be the weapon we seek.")
                    chatPlayer(sad, "I suspect it is. I don't think there's any way to remove it though.")
                    chatNpc(neutral, "I hope you're wrong about that. If we can't break the mould of our historical defeats, we'll never stop Drakan.")
                }
                2 -> aboutIvandis()
                else -> {
                    chatPlayer(neutral, "Okay, thanks.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.afterMould() {
        chatNpc(happy, "Well met. How's it going?")
        chatPlayer(happy, "I think I've found Ivandis' tomb.")
        chatNpc(happy, "That's great news! Hopefully you'll find something of use in there.")
        chatPlayer(neutral, "Well, there's a coffin with some sort of rod fused to the top of it.")
        chatPlayer(neutral, "I don't think the rod can be removed. But I made a clay mould of it, so I might be able to make a new one.")
        chatNpc(happy, "Excellent work. Let me know how it goes.")
        while (true) {
            when (
                choice4(
                    "Do you know which metal I should use to make the rod?", 1,
                    "Do you know what the rod does?", 2,
                    "What do you know about Ivandis?", 3,
                    "Okay, thanks.", 4,
                )
            ) {
                1 -> {
                    chatPlayer(quiz, "Do you know which metal I should use to make the rod?")
                    chatNpc(neutral, "I don't, I'm afraid. But the original is still in Ivandis' tomb. Studying it should give you a clue as to how it was made.")
                    chatNpc(neutral, "If that doesn't work, you might learn something at Paterdomus.")
                }
                2 -> {
                    chatPlayer(quiz, "Do you know what the rod does?")
                    chatNpc(neutral, "I believe it's the weapon Ivandis used against the vampyres. The stories say it left them helpless for a short time. You might learn more at Paterdomus.")
                }
                3 -> aboutIvandis()
                else -> {
                    chatPlayer(neutral, "Okay, thanks.")
                    return
                }
            }
        }
    }

    private companion object {
        val ROD_IDS = (1..10).map { "obj.burgh_rod_command_final_$it" }
        val rodIds by lazy { ROD_IDS.map { dev.openrune.rscm.RSCM.getRSCM(it) }.toSet() }
    }
}
