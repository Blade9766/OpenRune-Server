package org.rsmod.content.quest.area.lumbridge.sheepshearer

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.lumbridge.sheepshearer.SheepShearerQuest.Companion.SHEARS
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Thing: the sheep in Fred's field that is really two penguins in a costume. It cannot be
 * sheared, and meeting it unlocks a conversation with Fred.
 */
class TheThing @Inject constructor(private val sheepShearer: SheepShearerQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(THE_THING) { shear(it.npc) }
        onOpNpcU(THE_THING) { if (it.objType.internalName == SHEARS) shear(it.npc) else mes("The sheep isn't interested in that.") }
        onOpNpc3(THE_THING) { talkTo(it.npc) }
    }

    private suspend fun ProtectedAccess.shear(npc: Npc) {
        if (inv.count(SHEARS) <= 0) {
            mes("You need a set of shears to do this.")
            return
        }
        arriveDelay()
        faceEntitySquare(npc)
        anim("seq.human_shearing")
        delay(1)
        npc.say("Baa?")
        mes("The... whatever it is... manages to get away from you!")
        noteSeen()
    }

    private suspend fun ProtectedAccess.talkTo(npc: Npc) {
        arriveDelay()
        faceEntitySquare(npc)
        npc.facePlayer(player)
        npc.say("Baa... baa?")
        startDialogue(npc) {
            chatPlayer(confused, "Hello, sheep.")
            chatNpc(neutral, "Baa... baa?")
            chatPlayer(quiz, "Is it just me, or did that come from two different places?")
            chatNpc(worried, "Baa.")
        }
        noteSeen()
    }

    private fun ProtectedAccess.noteSeen() {
        if (!sheepShearer.seenTheThing.get(player)) {
            sheepShearer.seenTheThing.set(player, true)
        }
    }

    private companion object {
        const val THE_THING = "npc.sheep_shearer_the_thing"
    }
}
