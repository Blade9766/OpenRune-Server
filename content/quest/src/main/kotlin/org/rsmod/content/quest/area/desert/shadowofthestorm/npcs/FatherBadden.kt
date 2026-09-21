package org.rsmod.content.quest.area.desert.shadowofthestorm.npcs

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.BADDEN
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.BADDEN_SIGIL
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.BADDEN_UZER_SPAWN
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.BLACK_ITEMS_REQ
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.RECRUITED
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.SIGIL
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.STAGE_BRIEFED
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.STAGE_RECRUITING
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.desert.shadowofthestorm.baddenAtUzer
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Father Badden, keeping watch over the ruins of Uzer from a safe distance. He explains what the
 * cult will and will not let past them, and later takes a sigil into the circle himself.
 */
@Singleton
class FatherBadden
@Inject
constructor(private val sots: ShadowOfTheStormQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        for (name in listOf(BADDEN_UZER_SPAWN, BADDEN, BADDEN_SIGIL)) {
            onOpNpc1(name) { startDialogue(it.npc) { badden() } }
        }
    }

    private suspend fun Dialogue.badden() {
        val stage = sots.stage(player)
        when {
            sots.isComplete(player) -> afterQuest()
            stage < STAGE_STARTED -> stranger()
            stage == STAGE_STARTED -> brief()
            stage >= STAGE_RECRUITING && player.baddenAtUzer < RECRUITED -> recruit()
            else -> advice()
        }
    }

    private suspend fun Dialogue.stranger() {
        chatNpc(worried, "Keep moving, friend. There is nothing out here worth stopping for.")
    }

    private suspend fun Dialogue.brief() {
        chatPlayer(neutral, "Your brother Reen sent me.")
        chatNpc(
            happy,
            "Reen! Then he got my letters after all. I had begun to think the desert had eaten " +
                "them.",
        )
        var done = false
        while (!done) {
            when (
                choice4(
                    "Who are these people?",
                    Topic.Cult,
                    "Why haven't you stopped them yourself?",
                    Topic.Why,
                    "So what do you want me to do?",
                    Topic.Task,
                    "How can I do that?",
                    Topic.How,
                )
            ) {
                Topic.Cult -> {
                    chatPlayer(quiz, "Who are these people?")
                    chatNpc(
                        neutral,
                        "A dozen fools and one man who is not a fool at all. They call him " +
                            "Denath. The others came out here for the thrill of it; he came out " +
                            "here for something else.",
                    )
                }
                Topic.Why -> {
                    chatPlayer(quiz, "Why haven't you stopped them yourself?")
                    chatNpc(
                        sad,
                        "Look at me. I am sixty-one and I carry a book. They would put me in the " +
                            "circle instead of on it.",
                    )
                }
                Topic.Task -> {
                    chatPlayer(quiz, "So what do you want me to do?")
                    chatNpc(
                        neutral,
                        "Join them. They lost one of their number a fortnight ago - a boy named " +
                            "Josef - and they are short a caster because of it. Find out what " +
                            "they mean to summon, and stop it before they do.",
                    )
                }
                Topic.How -> {
                    chatPlayer(quiz, "How can I do that?")
                    chatNpc(
                        neutral,
                        "They will not look at you twice if you dress like them. " +
                            "$BLACK_ITEMS_REQ pieces of black, no less, and nothing bright.",
                    )
                    chatNpc(
                        worried,
                        "And that sword of yours. Silverlight is the most recognisable blade in " +
                            "the kingdom. Stain it - there are black mushrooms growing by the " +
                            "temple stairs that will do it.",
                    )
                    chatNpc(neutral, "Their doorman is a lad called Evil Dave. Tell him you want in.")
                    sots.advanceTo(access, STAGE_BRIEFED)
                    done = true
                }
            }
            if (!done && sots.stage(player) >= STAGE_BRIEFED) {
                done = true
            }
        }
    }

    private suspend fun Dialogue.advice() {
        chatNpc(
            neutral,
            "$BLACK_ITEMS_REQ pieces of black, a stained Silverlight, and a word with Evil Dave " +
                "at the portal. That is all there is to it.",
        )
    }

    private suspend fun Dialogue.recruit() {
        chatPlayer(neutral, "I need you in the circle, Badden. Five casters, and I have a sigil for you.")
        chatNpc(shocked, "You want me to summon it?")
        chatPlayer(neutral, "I want you to summon it somewhere I can reach it with a sword.")
        chatNpc(worried, "Reen would have my hide.")
        chatPlayer(neutral, "Reen is already holding one.")
        chatNpc(laugh, "Of course he is. Very well.")
        if (access.invDel(player.inv, SIGIL).failure) {
            chatNpc(neutral, "Bring me a sigil, then, and I will go down with you.")
            return
        }
        player.baddenAtUzer = RECRUITED
        access.mes("Father Badden pockets the sigil and sets off for the temple stairs.")
    }

    private suspend fun Dialogue.afterQuest() {
        chatNpc(happy, "I have written the whole thing down. Nobody in Al Kharid believes a word of it.")
        chatPlayer(laugh, "I was there and I barely believe it.")
    }

    private enum class Topic {
        Cult,
        Why,
        Task,
        How,
    }
}
