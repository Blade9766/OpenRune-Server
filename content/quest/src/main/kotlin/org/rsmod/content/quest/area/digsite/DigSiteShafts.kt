package org.rsmod.content.quest.area.digsite

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.AGILITY_REQ
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.INVITATION_VARBIT
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.ROPE
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The two winches on the surface and the ropes at the bottom of each shaft.
 *
 * A winch on its own only lowers the bucket part of the way; a rope tied to it turns the shaft
 * into a climb, which takes ten Agility to squeeze down. Which copy of the dungeon the player
 * lands in depends on whether they have blown the bricks open - see [DigSiteCoords].
 */
class DigSiteShafts : PluginScript() {

    override fun ScriptContext.startup() {
        for (shaft in DigSiteShaft.entries) {
            onOpLocU(shaft.winch, ROPE) { tieRope(shaft) }
            onOpLoc1(shaft.winch) { operateWinch(shaft) }
            onOpLoc1(shaft.ladder) { climbUp(shaft) }
        }
    }

    private suspend fun ProtectedAccess.tieRope(shaft: DigSiteShaft) {
        if (!allowedDownShafts()) {
            return
        }
        if (player.vars[shaft.ropeVarbit] != 0) {
            mes("There is already a rope tied to this winch.")
            return
        }
        arriveDelay()
        if (invDel(inv, ROPE).failure) {
            return
        }
        anim(TIE_SEQ)
        setVarBit(player, shaft.ropeVarbit, 1)
        mes("You tie the rope to the bucket.")
    }

    private suspend fun ProtectedAccess.operateWinch(shaft: DigSiteShaft) {
        if (!allowedDownShafts()) {
            return
        }
        arriveDelay()
        anim(WINCH_SEQ)
        soundSynth(WINCH_SOUND)
        delay(1)
        if (player.vars[shaft.ropeVarbit] == 0) {
            mes("You operate the winch.")
            mes("The bucket descends, but does not reach the bottom.")
            startDialogue {
                chatPlayer(
                    quiz,
                    "Hey, I think I could fit down here. I need something to help me get all the " +
                        "way down.",
                )
            }
            return
        }
        mes("You try to climb down the rope.")
        if (player.agilityLvl < AGILITY_REQ) {
            mes("You need an Agility level of $AGILITY_REQ to squeeze down the shaft.")
            return
        }
        anim(CLIMB_DOWN_SEQ)
        soundSynth(ROPE_SOUND)
        delay(1)
        mes("You lower yourself into the shaft.")
        telejump(DigSiteCoords.dungeonLevel(player, shaft.landing))
        mes("You find yourself in a cavern...")
    }

    private suspend fun ProtectedAccess.climbUp(shaft: DigSiteShaft) {
        arriveDelay()
        anim(CLIMB_UP_SEQ)
        soundSynth(ROPE_SOUND)
        delay(1)
        telejump(shaft.surface)
    }

    /** Terry's letter has to have been shown to a workman before the shafts are open to anyone. */
    private suspend fun ProtectedAccess.allowedDownShafts(): Boolean {
        if (player.vars[INVITATION_VARBIT] != 0) {
            return true
        }
        arriveDelay()
        startDialogue {
            chatNpcSpecific(
                "Digsite workman",
                "npc.qip_digsite_digworkman_03",
                bored,
                "Hoi! Nobody goes down the shafts without a letter from the archaeological expert.",
            )
        }
        return false
    }

    private companion object {
        const val TIE_SEQ = "seq.rope_tie"
        const val WINCH_SEQ = "seq.human_turn_iron_winch"
        const val CLIMB_DOWN_SEQ = "seq.human_climbing_down"
        const val CLIMB_UP_SEQ = "seq.human_climbing"
        const val WINCH_SOUND = "synth.grill_pulled"
        const val ROPE_SOUND = "synth.ropeclimb"
    }
}
