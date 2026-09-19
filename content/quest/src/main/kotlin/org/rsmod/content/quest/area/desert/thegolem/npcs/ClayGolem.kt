package org.rsmod.content.quest.area.desert.thegolem.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.craftingLvl
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.api.script.onPlayerSoftQueue
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.CLAY_NEEDED
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.CRAFTING_REQ
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.GOLEM
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.IMPLEMENT
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.PROGRAM
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.PROGRAM_SOUND
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.REPAIR_CLAY_SOUND
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.SOFT_CLAY
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.STAGE_GOLEM_UNCONVINCED
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.STAGE_PORTAL_OPEN
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.STAGE_REPAIRED
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.STAGE_SEEN_DEMON
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.desert.thegolem.golemClay
import org.rsmod.content.quest.area.desert.thegolem.golemHeadOpen
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The clay golem pacing the ruins of Uzer. `npc.golem_golem` is a multi-npc on
 * `varbit.golem_clay`: broken, then damaged, then whole as the player packs soft clay into it.
 * Its skull opens with the strange implement and shuts again by itself unless a program goes in.
 */
class ClayGolem @Inject constructor(private val golem: TheGolemQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(GOLEM) { startDialogue(it.npc) { talk() } }
        onOpNpcU(GOLEM) { useOnGolem(it.npc, it.objType.internalName) }
        onPlayerSoftQueue(SKULL_SHUT_QUEUE) {
            if (player.golemHeadOpen) {
                player.golemHeadOpen = false
                player.mes("The golems skull shuts automatically.")
            }
        }
    }

    private suspend fun Dialogue.talk() {
        val stage = golem.stage(player)
        when {
            golem.isComplete(player) -> afterQuest()
            stage == 0 -> notStarted()
            stage == STAGE_STARTED -> chatNpc(sad, "Damage... severe...")
            stage == STAGE_PORTAL_OPEN -> portalOpened()
            stage == STAGE_SEEN_DEMON -> demonIsDead()
            stage == STAGE_GOLEM_UNCONVINCED -> stillUnconvinced()
            else -> taskIncomplete()
        }
    }

    private suspend fun Dialogue.notStarted() {
        chatNpc(sad, "Damage... severe... Task... incomplete...")
        val start = choice2("Yes.", true, "No.", false, title = "Start The Golem quest?")
        if (!start) {
            chatPlayer(neutral, "I'm not going to find a conversation here!")
            chatNpc(angry, "Graar!")
            chatNpc(neutral, "Must... not... injure... human...")
            return
        }
        chatPlayer(quiz, "Shall I try to repair you?")
        golem.advanceTo(access, STAGE_STARTED)
        chatNpc(neutral, "Repairs... needed...")
    }

    private suspend fun Dialogue.taskIncomplete() {
        chatNpc(neutral, TASK_INCOMPLETE)
        when (
            choice3(
                "How do I open the portal?",
                1,
                "What makes you think you can defeat the demon?",
                2,
                "I'll get right on it.",
                3,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "How do I open the portal?")
                chatNpc(neutral, "The four statuettes in the temple must be turned to the correct pattern.")
                chatNpc(neutral, "I do not know the pattern. Golems are not permitted to open the portal.")
            }
            2 -> {
                chatPlayer(quiz, "What makes you think you can defeat the demon?")
                chatNpc(
                    neutral,
                    "If not I, then who else? No living being can destroy the demon. That is " +
                        "why the golems were created in the first place.",
                )
                chatNpc(
                    neutral,
                    "But the demon was badly wounded and elder-demons heal very slowly indeed. " +
                        "It was almost dead when it retreated to its own dimension.",
                )
                chatNpc(happy, "Now that I am repaired, I will be able to destroy it easily!")
            }
            3 -> chatPlayer(neutral, "I'll get right on it.")
        }
    }

    private suspend fun Dialogue.portalOpened() {
        chatNpc(neutral, TASK_INCOMPLETE)
        chatPlayer(happy, "I opened the portal.")
        chatNpc(
            neutral,
            "Golems cannot pass through the portal. But the demon will soon emerge. I must ready " +
                "myself for combat!",
        )
    }

    private suspend fun Dialogue.demonIsDead() {
        chatNpc(neutral, TASK_INCOMPLETE)
        chatPlayer(happy, "It's okay, the demon is dead!")
        chatNpc(neutral, "The demon must be defeated...")
        chatPlayer(
            neutral,
            "No, you don't understand. I saw the demon's skeleton. It must have died of its wounds.",
        )
        golem.advanceTo(access, STAGE_GOLEM_UNCONVINCED)
        chatNpc(angry, "Demon must be defeated! Task incomplete.")
    }

    private suspend fun Dialogue.stillUnconvinced() {
        chatNpc(neutral, TASK_INCOMPLETE)
        chatPlayer(angry, "I already told you, he's dead!")
        chatNpc(neutral, "Task incomplete.")
        chatPlayer(sad, "Oh, how am I going to convince you?")
    }

    private suspend fun Dialogue.afterQuest() {
        chatNpc(
            happy,
            "Thank you for helping me. A golem can have no greater satisfaction than knowing " +
                "that its task is complete.",
        )
        chatPlayer(quiz, "But the whole city is destroyed! Doesn't that bother you?")
        chatNpc(
            neutral,
            "I was never programmed to appreciate the city. My only purpose was the destruction " +
                "of the demon, and that is achieved!",
        )
    }

    private suspend fun ProtectedAccess.useOnGolem(npc: Npc, obj: String) {
        when (obj) {
            SOFT_CLAY -> repair(npc)
            IMPLEMENT -> openSkull(npc)
            PROGRAM -> insertProgram(npc)
            else -> mes("Nothing interesting happens.")
        }
    }

    private suspend fun ProtectedAccess.repair(npc: Npc) {
        val stage = golem.stage(player)
        if (stage == 0) {
            mes("Maybe you should ask the golem first!")
            return
        }
        if (stage > STAGE_STARTED || player.golemClay >= CLAY_NEEDED) {
            mes("The golem is already fully repaired.")
            return
        }
        if (player.craftingLvl < CRAFTING_REQ) {
            mes("You need a Crafting level of $CRAFTING_REQ to repair the golem.")
            return
        }
        faceEntitySquare(npc)
        if (invDel(inv, SOFT_CLAY).failure) {
            return
        }
        anim(REPAIR_SEQ)
        soundSynth(REPAIR_CLAY_SOUND)
        val applied = player.golemClay + 1
        player.golemClay = applied
        val text =
            when (applied) {
                1 -> "You apply some clay to the golem's wounds. The clay begins to harden in the hot sun."
                2 -> "You fix the golem's legs."
                3 -> "The golem is nearly whole."
                else -> "You repair the golem with a final piece of clay."
            }
        startDialogue(npc) {
            objbox(SOFT_CLAY, text)
            if (applied >= CLAY_NEEDED) {
                repaired()
            }
        }
    }

    private suspend fun Dialogue.repaired() {
        chatNpc(neutral, "Damage repaired...")
        chatNpc(happy, "Thank you. My body and mind are fully healed.")
        chatNpc(neutral, "Now I must complete my task by defeating the great enemy.")
        chatPlayer(quiz, "What enemy?")
        chatNpc(neutral, "A great demon. It broke through from its dimension to attack the city.")
        chatNpc(
            neutral,
            "The golem army was created to fight it. Many were destroyed, but we drove the demon " +
                "back!",
        )
        golem.advanceTo(access, STAGE_REPAIRED)
        chatNpc(
            neutral,
            "The demon is still wounded. You must open the portal so that I can strike the final " +
                "blow and complete my task.",
        )
    }

    private suspend fun ProtectedAccess.openSkull(npc: Npc) {
        if (golem.stage(player) < STAGE_REPAIRED) {
            mes("The golem is too badly damaged for that.")
            return
        }
        faceEntitySquare(npc)
        anim(REPAIR_SEQ)
        mes("You insert the key and the golem's skull hinges open.")
        player.golemHeadOpen = true
        softQueue(SKULL_SHUT_QUEUE, SKULL_OPEN_CYCLES)
    }

    private suspend fun ProtectedAccess.insertProgram(npc: Npc) {
        if (!player.golemHeadOpen) {
            mes("You can't see a way to put the instructions in the golem's skull")
            return
        }
        if (golem.stage(player) != STAGE_GOLEM_UNCONVINCED) {
            mes("Nothing interesting happens.")
            return
        }
        faceEntitySquare(npc)
        if (invDel(inv, PROGRAM).failure) {
            return
        }
        clearQueue(SKULL_SHUT_QUEUE)
        anim(REPAIR_SEQ)
        soundSynth(PROGRAM_SOUND)
        player.golemHeadOpen = false
        startDialogue(npc) {
            chatNpc(neutral, "New instructions... Updating program...")
            chatNpc(happy, "Task complete!")
            chatNpc(happy, "Thank you. Now my mind is at rest.")
        }
        golem.complete(this)
    }

    private companion object {
        const val TASK_INCOMPLETE =
            "My task is incomplete. You must open the portal so I can defeat the great demon."

        const val REPAIR_SEQ = "seq.human_pickuptable"
        const val SKULL_SHUT_QUEUE = "queue.golem_skull_shut"
        const val SKULL_OPEN_CYCLES = 10
    }
}
