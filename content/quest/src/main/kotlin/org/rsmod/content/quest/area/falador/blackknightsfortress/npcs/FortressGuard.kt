package org.rsmod.content.quest.area.falador.blackknightsfortress.npcs

import dev.openrune.types.MesAnimType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortressQuest
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortressQuest.Companion.GUARD
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortressQuest.Companion.GUARDS
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** The Fortress Guards on the gate of the Black Knights' Fortress. */
class FortressGuard @Inject constructor(private val bkf: BlackKnightsFortressQuest) : PluginScript() {
    override fun ScriptContext.startup() {
        for (guard in GUARDS) {
            onOpNpc1(guard) { startDialogue(it.npc) { fortressGuard(bkf) } }
        }
    }

    private suspend fun Dialogue.fortressGuard(bkf: BlackKnightsFortressQuest) {
        when {
            bkf.isBlackKnightDisguise(player) -> guard(happy, "Good day to you.")
            bkf.isDisguised(player) -> {
                guard(angry, "Hey! Get back on duty!")
                chatPlayer(confused, "Uh...")
            }
            else -> {
                guard(angry, "Get lost. This is private property.")
                notAGuard(bkf)
            }
        }
    }
}

internal suspend fun Dialogue.guardsEntrance(bkf: BlackKnightsFortressQuest) {
    guard(angry, "Hey! You can't come in here! This is a high security military installation!")
    when (
        choice3(
            "Yes, but I work here!", 1,
            "Oh, sorry.", 2,
            "So who does it belong to?", 3,
        )
    ) {
        1 -> notAGuard(bkf)
        2 -> {
            chatPlayer(neutral, "Oh, sorry.")
            guard(angry, "Don't let it happen again.")
        }
        3 -> {
            chatPlayer(quiz, "So who does it belong to?")
            guard(neutral, "This fortress belongs to the order of Black Knights known as the Kinshra.")
            chatPlayer(neutral, "Oh. Okay, thanks.")
        }
    }
}

internal suspend fun Dialogue.meetingWarning(): Boolean {
    guard(neutral, "I wouldn't go in there if I were you. Those Black Knights are in an important meeting. They said they'd kill anyone who went in there!")
    val goIn = choice2("Okay, I won't.", false, "I don't care. I'm going in anyway.", true)
    if (goIn) {
        chatPlayer(angry, "I don't care. I'm going in anyway.")
    } else {
        chatPlayer(neutral, "Okay, I won't.")
        guard(neutral, "Wise move.")
    }
    return goIn
}

private suspend fun Dialogue.notAGuard(bkf: BlackKnightsFortressQuest) {
    chatPlayer(angry, "Yes, but I work here!")
    guard(angry, "Well, this is the guards' entrance. I might be new here but I can tell you're not a guard.")
    guard(angry, "You're not even wearing proper guards uniform!")
    when (choice2("Oh pleeeaaase let me in!", 1, "So what is this uniform?", 2)) {
        1 -> {
            chatPlayer(sad, "Oh pleeeaaase let me in!")
            guard(angry, "Go away. You're getting annoying.")
        }
        2 -> {
            chatPlayer(quiz, "So what is this uniform?")
            guard(neutral, "Well you can see me wearing it. It's an iron chainbody and a medium bronze helm.")
            bkf.learnUniform(player)
            chatPlayer(shifty, "Hmmm... I wonder if I can make that or get some in the local towns....")
            guard(quiz, "What was that you muttered?")
            chatPlayer(happy, "Oh, nothing important!")
        }
    }
}

private suspend fun Dialogue.guard(mesanim: MesAnimType, text: String) {
    if (npc != null) {
        chatNpc(mesanim, text)
    } else {
        chatNpcSpecific("Fortress Guard", GUARD, mesanim, text)
    }
}
