package org.rsmod.content.quest.area.ikov.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.ikov.IkovCoords
import org.rsmod.content.quest.area.ikov.TempleOfIkovQuest
import org.rsmod.content.quest.area.ikov.TempleOfIkovQuest.Companion.LIMPWURT_ROOT
import org.rsmod.content.quest.area.ikov.TempleOfIkovQuest.Companion.LIMPWURT_ROOTS
import org.rsmod.content.quest.area.ikov.TempleOfIkovQuest.Companion.WINELDA
import org.rsmod.content.quest.area.ikov.ikovWineldaPaid
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Winelda, the witch on the lava shore north of the Fire Warrior's corridor.
 *
 * Her price is twenty limpwurt roots handed over in one go; once they are paid she will throw the
 * player across the lava as often as they ask. The roots have to be loose, not noted, and all
 * twenty at once - nineteen buys nothing.
 */
class Winelda : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(WINELDA) { startDialogue(it.npc) { talk(it.npc) } }
        onOpNpcU(WINELDA) { event ->
            if (event.objType.internalName == LIMPWURT_ROOT) {
                startDialogue(event.npc) { handOverRoots(event.npc) }
            } else {
                mes("Nothing interesting happens.")
            }
        }
    }

    private suspend fun Dialogue.talk(npc: Npc) {
        if (access.player.ikovWineldaPaid) {
            chatNpc(happy, "Back again? Hold still, dearie.")
            access.throwAcross(npc)
            return
        }
        chatNpc(quiz, "Hello, dearie. Nasty hot place for a walk, isn't it?")
        chatPlayer(quiz, "I need to get across the lava.")
        chatNpc(
            happy,
            "Well now. I know a little magic that would put you on the far side, and I have a " +
                "potion on the boil that is twenty limpwurt roots short.",
        )
        val deal =
            choice3(
                "I'll find you twenty roots.",
                1,
                "Can you tell me about the temple?",
                2,
                "Nah, not bothered.",
                3,
            )
        when (deal) {
            1 -> {
                chatPlayer(neutral, "I'll find you twenty roots.")
                chatNpc(
                    happy,
                    "Twenty, mind. All together, in your hands. I'll not count them out one at a " +
                        "time like a market girl.",
                )
                if (access.player.inv.count(LIMPWURT_ROOT) >= LIMPWURT_ROOTS) {
                    handOverRoots(npc)
                }
            }
            2 -> aboutTheTemple()
            else -> {
                chatPlayer(neutral, "Nah, not bothered.")
                chatNpc(bored, "Suit yourself. Mind the lava on your way past.")
            }
        }
    }

    private suspend fun Dialogue.aboutTheTemple() {
        chatPlayer(quiz, "Can you tell me about the temple?")
        chatNpc(
            neutral,
            "The birdmen's place is over the lava, and they keep to themselves. Lesser demons in " +
                "the tunnels west of here, and one of them carries a shiny little key.",
        )
        chatNpc(
            neutral,
            "That key opens a door up in McGrubor's Wood. Saves a long walk, if you fancy your " +
                "chances against a demon.",
        )
    }

    private suspend fun Dialogue.handOverRoots(npc: Npc) {
        if (access.player.ikovWineldaPaid) {
            chatNpc(happy, "I have all the roots I need, dearie. Hold still.")
            access.throwAcross(npc)
            return
        }
        val held = access.player.inv.count(LIMPWURT_ROOT)
        if (held < LIMPWURT_ROOTS) {
            chatNpc(
                bored,
                "That's only $held. I said twenty, and twenty is what I meant. Come back when " +
                    "you have the lot.",
            )
            return
        }
        if (access.invDel(access.inv, LIMPWURT_ROOT, LIMPWURT_ROOTS).failure) {
            return
        }
        chatNpc(happy, "Twenty! Lovely. That'll be the potion sorted.")
        chatNpc(neutral, "Now then. Stand still, dearie, and don't scream.")
        access.player.ikovWineldaPaid = true
        access.throwAcross(npc)
    }

    /** The old woman's own teleport: a puff of fire and the player is on the far shore. */
    private suspend fun ProtectedAccess.throwAcross(npc: Npc) {
        npc.facePlayer(player)
        npc.anim(CAST_SEQ)
        spotanim(CAST_SPOTANIM, height = SPOTANIM_HEIGHT)
        soundSynth(TempleOfIkovQuest.SOUND_FIRE_TELEPORT)
        delay(2)
        telejump(IkovCoords.WINELDA_LANDING, TeleportType.Exempt)
        resetAnim()
        mes("Winelda's magic snatches you over the lava.")
    }

    private companion object {
        const val CAST_SEQ = "seq.human_castteleport"
        const val CAST_SPOTANIM = "spotanim.teleport_casting"
        const val SPOTANIM_HEIGHT = 92
    }
}
