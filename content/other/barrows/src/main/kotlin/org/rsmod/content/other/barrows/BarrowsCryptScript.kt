package org.rsmod.content.other.barrows

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.SpadeDigging
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class BarrowsCryptScript
@Inject
constructor(
    private val spadeDigging: SpadeDigging,
    private val spawns: BarrowsSpawns,
    private val random: GameRandom,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (brother in Brother.entries) {
            spadeDigging.register(brother.mound, radius = MOUND_RADIUS) { digInto(brother) }
            onOpLoc1(brother.stairs) { climbOut(brother) }
            onOpLoc1(brother.sarcophagus) { searchSarcophagus(brother) }
        }
    }

    private suspend fun ProtectedAccess.digInto(brother: Brother) {
        anim(DIG_SEQ)
        soundSynth(DIG_SOUND)
        delay(1)
        BarrowsRun.ensureStarted(player, random)
        telejump(brother.cryptLanding)
        mes("You've broken into a crypt!")
    }

    private fun ProtectedAccess.climbOut(brother: Brother) {
        spawns.dismissAll(player)
        telejump(brother.mound)
    }

    private suspend fun ProtectedAccess.searchSarcophagus(brother: Brother) {
        BarrowsRun.ensureStarted(player, random)
        if (BarrowsRun.tunnelBrother(player) == brother) {
            offerTunnel()
            return
        }
        if (BarrowsRun.isKilled(player, brother) || spawns.activeBrother(player) == brother) {
            mes("You search the sarcophagus.")
            mes("You don't find anything.")
            return
        }
        spawns.summonBrother(player, brother, "You dare disturb my rest!")
    }

    private suspend fun ProtectedAccess.offerTunnel() {
        mesbox("You've found a hidden tunnel, do you want to enter?")
        val enter = choice2("Yeah I'm fearless!", true, "No way, that looks scary!", false)
        if (!enter) {
            return
        }
        spawns.dismissAll(player)
        telejump(BarrowsCoords.CORNER_ENTRIES[random.of(BarrowsCoords.CORNER_ENTRIES.size)])
    }

    private companion object {
        const val MOUND_RADIUS = 2
        const val DIG_SEQ = "seq.human_dig"
        const val DIG_SOUND = "synth.digspade"
    }
}
