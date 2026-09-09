package org.rsmod.content.areas.misc.stronghold

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Count Check in the Lumbridge graveyard: security advice, and a one-time teleport to the
 * Stronghold's entrance for players who have not been there yet.
 */
class CountCheckScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(Stronghold.COUNT_CHECK) { talk(it.npc) }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc) {
        startDialogue(npc) {
            chatNpc(
                happy,
                "Greetings, adventurer! I am Count Check. I make sure people's accounts are as " +
                    "secure as they can be. May I check yours?",
            )
            menu()
        }
    }

    private suspend fun Dialogue.menu() {
        val option =
            choice3(
                "Where can I learn more about security?",
                1,
                "What can I do to keep my account secure?",
                2,
                "No thanks, I'm fine.",
                3,
            )
        when (option) {
            1 -> learnMore()
            2 -> tips()
            else -> chatPlayer(neutral, "No thanks, I'm fine.")
        }
    }

    private suspend fun Dialogue.learnMore() {
        chatPlayer(quiz, "Where can I learn more about security?")
        val visited = vars[Stronghold.TELEPORTED_BY_COUNT] != 0 || Level.WAR.isCompleted(player)
        if (visited) {
            chatNpc(
                happy,
                "The Stronghold of Security, in Barbarian Village. There is much to learn there. " +
                    "But while you are here, may I check your account for you?",
            )
            menu()
            return
        }
        chatNpc(
            happy,
            "The Stronghold of Security, in Barbarian Village. I see you have not visited it. " +
                "Would you like to? I can send you straight there - but only once.",
        )
        val go = choice2("Yes, send me there.", true, "No thanks.", false)
        if (!go) {
            chatPlayer(neutral, "No thanks.")
            chatNpc(happy, "So may I check your account for you?")
            menu()
            return
        }
        chatPlayer(happy, "Yes, send me there.")
        vars[Stronghold.TELEPORTED_BY_COUNT] = 1
        access.sendToStronghold()
    }

    private suspend fun Dialogue.tips() {
        chatPlayer(quiz, "What can I do to keep my account secure?")
        chatNpc(
            neutral,
            "Three things, and they cost nothing. Use a password you use nowhere else, turn on " +
                "two-factor authentication for your account and your email, and set a bank PIN " +
                "with any banker.",
        )
        chatNpc(
            neutral,
            "And never, ever tell anyone your password - not friends, not family, and certainly " +
                "not anyone claiming to work for Jagex. We would never ask.",
        )
        chatPlayer(happy, "Thanks, I'll remember that.")
    }

    private suspend fun ProtectedAccess.sendToStronghold() {
        ifClose()
        anim(TELEPORT_ANIM)
        spotanim(TELEPORT_SPOTANIM, height = TELEPORT_SPOTANIM_HEIGHT)
        soundSynth(TELEPORT_SOUND)
        delay(TELEPORT_DELAY)
        telejump(Stronghold.SURFACE)
        anim(TELEPORT_END_ANIM)
        mes("Count Check sends you to the entrance of the Stronghold of Security.")
    }

    private companion object {
        private const val TELEPORT_ANIM = "seq.human_castteleport"
        private const val TELEPORT_END_ANIM = "seq.human_castteleport_reverse"
        private const val TELEPORT_SPOTANIM = "spotanim.teleport_casting"
        private const val TELEPORT_SPOTANIM_HEIGHT = 92
        private const val TELEPORT_SOUND = "synth.teleport_all"
        private const val TELEPORT_DELAY = 3
    }
}
