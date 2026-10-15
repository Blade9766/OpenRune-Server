package org.rsmod.content.quest.area.gnomestronghold.monkeymadness.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.ChapterCards
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadness
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.DAERO
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.NARNODE_ORDERS
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.STAGE_HANGAR
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.STAGE_HAS_ORDERS
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.TRAINING_XP_MAJOR
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.TRAINING_XP_MINOR
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.mmFadeTeleport
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Daero, the King's new head tree guardian. `npc.mm_daero` is a varbit-multi that grows a Travel
 * option once the player has been blindfolded into the hangar, so every op arrives on the base
 * type. The same type stands in the hangar itself, told apart by its coordinates.
 */
class Daero @Inject constructor(private val monkeyMadness: MonkeyMadnessQuest, private val cards: ChapterCards) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(DAERO) { startDialogue(it.npc) { talk(it.npc) } }
        onOpNpc3(DAERO) { travel(it.npc) }
    }

    private fun Npc.inHangar(): Boolean = coords.z >= HANGAR_MIN_Z

    private suspend fun Dialogue.talk(npc: Npc) {
        if (npc.inHangar()) {
            hangarTalk()
            return
        }
        when {
            monkeyMadness.stage(player) == 0 -> {
                chatNpc(neutral, "Hello there. I'm Daero, the head tree guardian. I'm rather busy, I'm afraid.")
                chatPlayer(neutral, "I'll leave you to it.")
            }
            monkeyMadness.stage(player) < STAGE_HAS_ORDERS -> {
                chatNpc(neutral, "Hello. I'm Daero, the King's head tree guardian. If you have business with me it will have to come through the King.")
            }
            monkeyMadness.stage(player) == STAGE_HAS_ORDERS -> if (player.inv.contains(NARNODE_ORDERS)) readOrders() else {
                chatNpc(quiz, "The King said he had orders for me. Do you have them?")
                chatPlayer(sad, "I seem to have lost them.")
                chatNpc(neutral, "Then go back to the King. He usually keeps a copy.")
            }
            monkeyMadness.stage(player) == STAGE_COMPLETE -> postQuest()
            else -> {
                chatNpc(quiz, "Any news of the 10th squad?")
                chatPlayer(neutral, "I'm still working on it.")
                chatNpc(neutral, "Then I won't keep you. I can take you back to the hangar whenever you're ready.")
                when (choice2("Take me to the hangar.", 1, "Not right now.", 2)) {
                    1 -> access.blindfold()
                    2 -> chatPlayer(neutral, "Not right now.")
                }
            }
        }
    }

    private suspend fun Dialogue.hangarTalk() {
        if (monkeyMadness.glidersReady.get(player)) {
            chatNpc(happy, "The gliders are ready, and Waydar is the finest pilot I have. Good luck out there.")
        } else {
            chatNpc(neutral, "Waydar can't take off until the gliders have been reinitialised. Talk to him about it; he understands the machinery better than I do.")
        }
        chatNpc(neutral, "Say the word when you want to go back to the Grand Tree.")
        when (choice2("Take me back to the Grand Tree.", 1, "I'll stay a while.", 2)) {
            1 -> {
                chatPlayer(neutral, "Take me back to the Grand Tree.")
                access.blindfoldOut()
            }
            2 -> chatPlayer(neutral, "I'll stay a while.")
        }
    }

    private suspend fun Dialogue.readOrders() {
        chatPlayer(neutral, "King Narnode asked me to give you these orders.")
        objbox(NARNODE_ORDERS, "You hand Daero the sealed orders.")
        chatNpc(neutral, "Let me see... This cipher hasn't been used since the wars. The King must think this is serious.")
        chatNpc(shocked, "Well! It seems you are to go on a reconnaissance mission, far to the south of Karamja. Further than any gnome has flown on purpose.")
        chatPlayer(quiz, "South? Why?")
        chatNpc(neutral, "The King wants to know whether Caranock's story about the winds holds up. If the squad were blown south, that is where we will find them.")
        access.invDel(player.inv, NARNODE_ORDERS)
        monkeyMadness.advanceTo(access, STAGE_HANGAR)
        monkeyMadness.syncVars(player)
        briefing()
    }

    private suspend fun Dialogue.briefing() {
        while (true) {
            val choice =
                choice5(
                    "So where exactly are we going?", 1,
                    "How will we get there?", 2,
                    "Tell me about the 10th squad.", 3,
                    "What do you know about Caranock?", 4,
                    "I'm ready to go.", 5,
                )
            when (choice) {
                1 -> {
                    chatPlayer(quiz, "So where exactly are we going?")
                    chatNpc(neutral, "Our early charts show a large atoll south of Karamja. The few who have seen it say it is crawling with monkeys, and not the harmless kind you find on Karamja.")
                    chatPlayer(quiz, "Monkeys?")
                    chatNpc(neutral, "Monkeys. Whether that has anything to do with the squad, I cannot say.")
                }
                2 -> {
                    chatPlayer(quiz, "How will we get there?")
                    chatNpc(neutral, "I will arrange everything. You will be flown down by a colleague of mine; I will introduce you shortly.")
                    chatPlayer(quiz, "Won't you be coming?")
                    chatNpc(neutral, "My duty is to the Grand Tree. Flight Commander Waydar will go with you. He is the best pilot we have left.")
                }
                3 -> {
                    chatPlayer(quiz, "Tell me about the 10th squad.")
                    chatNpc(neutral, "The best of the best. Sergeant Garkor leads them: a veteran of more campaigns than I can count. He has a high mage, Zooknock, two sappers, Bunkwicket and Waymottin, and a handful of foot soldiers.")
                    chatNpc(neutral, "And Karam, their assassin. Nobody sees Karam unless Karam wants to be seen.")
                    chatPlayer(neutral, "They sound formidable.")
                    chatNpc(neutral, "They are. Which is why the King is so worried.")
                }
                4 -> {
                    chatPlayer(quiz, "What do you know about Caranock?")
                    chatNpc(neutral, "Very little. He was in place before I was appointed, which means Glough chose him.")
                    chatPlayer(neutral, "He was very keen to send me on my way.")
                    chatNpc(neutral, "I don't doubt it. Keep your suspicions; I share them.")
                }
                5 -> {
                    chatPlayer(neutral, "I'm ready to go.")
                    if (readiness()) {
                        access.blindfold()
                    }
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.readiness(): Boolean {
        chatNpc(neutral, "Before we go any further, I must be sure. This is no stroll through the stronghold. Are you prepared for a long journey?")
        if (choice2("Yes.", true, "No.", false) != true) {
            chatPlayer(neutral, "Perhaps not.")
            chatNpc(neutral, "Then come back when you are.")
            return false
        }
        chatPlayer(neutral, "Yes.")
        chatNpc(neutral, "Do you have supplies, and the means to get yourself home if things go wrong?")
        if (choice2("Yes.", true, "No.", false) != true) {
            chatPlayer(neutral, "Not yet.")
            chatNpc(neutral, "Then see to it first. I will be here.")
            return false
        }
        chatPlayer(neutral, "Yes.")
        chatNpc(neutral, "Then there is one more thing. Where we are going is a military secret. You will have to wear this blindfold.")
        chatPlayer(quiz, "A blindfold?")
        chatNpc(neutral, "Nobody outside the Royal Guard knows the way, and I intend to keep it so. Do you agree?")
        if (choice2("Yes.", true, "No.", false) != true) {
            chatPlayer(neutral, "I'd rather not.")
            chatNpc(neutral, "Then we go nowhere. Come back if you change your mind.")
            return false
        }
        chatPlayer(neutral, "All right, blindfold me.")
        return true
    }

    private suspend fun ProtectedAccess.travel(npc: Npc) {
        when {
            npc.inHangar() -> blindfoldOut()
            monkeyMadness.hangarVisited.get(player) -> blindfold()
            else -> mes("Daero only takes the King's guests where they need to go.")
        }
    }

    /** Daero blindfolds the player and leads them down to the hangar. */
    private suspend fun ProtectedAccess.blindfold() {
        mesbox("Daero ties a blindfold over your eyes and leads you by the hand. You walk for a long time, down many stairs.")
        val firstVisit = !monkeyMadness.hangarVisited.get(player)
        mmFadeTeleport(MonkeyMadness.HANGAR_ARRIVAL)
        monkeyMadness.hangarVisited.set(player, true)
        monkeyMadness.syncVars(player)
        mesbox("Daero removes the blindfold. You are in an underground hangar full of gnome gliders.")
        if (firstVisit) {
            startDialogue {
                daero(neutral, "Welcome to the Underground Military Glider Hangar. Glough had it built in case his other plans failed; it was one of his few good ideas.")
                daero(neutral, "These gliders are prototypes: reinforced, light and able to stay in the air far longer than the ones you know.")
                daero(neutral, "This is Flight Commander Waydar. He will be flying you south. Talk to him; he will explain what is holding us up.")
            }
            with(cards) { show(ChapterCards.CHAPTER_ONE) }
        }
    }

    private suspend fun ProtectedAccess.blindfoldOut() {
        mesbox("Daero blindfolds you again and leads you back up to the Grand Tree.")
        mmFadeTeleport(MonkeyMadness.DAERO_POST.translateX(-1))
    }

    private suspend fun Dialogue.daero(mood: dev.openrune.types.MesAnimType, text: String) {
        chatNpcSpecific("Daero", MonkeyMadnessQuest.DAERO_HEAD, mood, text)
    }

    private suspend fun Dialogue.postQuest() {
        if (monkeyMadness.trainingClaimed.get(player)) {
            chatNpc(happy, "Good to see you again. The 10th squad talk about you like one of their own.")
            chatPlayer(happy, "They're a good bunch.")
            return
        }
        chatNpc(happy, "You're back! Garkor told me everything. Without you the squad would have died on that island.")
        chatNpc(neutral, "The King asked me to offer you some of our training in return. The Royal Guard has two schools of fighting; I can teach you one of them.")
        val choice =
            choice3(
                "Train my Attack and Defence.", 1,
                "Train my Strength and Hitpoints.", 2,
                "I don't want any training.", 3,
                title = "Which training would you like?",
            )
        when (choice) {
            1 -> {
                chatPlayer(neutral, "Teach me to hit harder and take less.")
                monkeyMadness.trainingClaimed.set(player, true)
                access.statAdvance("stat.attack", TRAINING_XP_MAJOR)
                access.statAdvance("stat.defence", TRAINING_XP_MAJOR)
                access.statAdvance("stat.strength", TRAINING_XP_MINOR)
                access.statAdvance("stat.hitpoints", TRAINING_XP_MINOR)
                mesbox("Daero drills you in the sword forms of the Royal Guard.")
            }
            2 -> {
                chatPlayer(neutral, "Teach me to be stronger and tougher.")
                monkeyMadness.trainingClaimed.set(player, true)
                access.statAdvance("stat.strength", TRAINING_XP_MAJOR)
                access.statAdvance("stat.hitpoints", TRAINING_XP_MAJOR)
                access.statAdvance("stat.attack", TRAINING_XP_MINOR)
                access.statAdvance("stat.defence", TRAINING_XP_MINOR)
                mesbox("Daero drills you in the conditioning routines of the Royal Guard.")
            }
            3 -> {
                chatPlayer(neutral, "I don't want any training, thanks.")
                chatNpc(neutral, "As you wish. The offer stands if you ever change your mind.")
            }
        }
    }

    private companion object {
        const val HANGAR_MIN_Z = 9000
    }
}
