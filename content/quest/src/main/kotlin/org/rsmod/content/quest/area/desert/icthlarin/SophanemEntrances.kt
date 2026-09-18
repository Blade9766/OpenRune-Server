package org.rsmod.content.quest.area.desert.icthlarin

import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_JAR_RETURNED
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The ways in and out of quarantined Sophanem. The city gates are barred by priests until the
 * High Priest makes an exception for the player (once the stolen jar is back); until then the
 * only way in is the tunnel under the rocks north-east of the Wanderer's tent, which opens from
 * the moment she hypnotises the player, and the only way out is the hole in the east wall.
 */
class SophanemEntrances
@Inject
constructor(
    private val quest: IcthlarinsLittleHelperQuest,
    private val passages: GenericPassageScript,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(TUNNEL_ROCK) { crawlIntoSophanem() }
        onOpLoc1(WALL_HOLE) { crawlOutOfSophanem() }
        for (gate in GATES) {
            onOpLoc1(gate) { openGate(it.loc, it.type) }
        }
    }

    private suspend fun ProtectedAccess.crawlIntoSophanem() {
        anim(CRAWL_SEQ)
        mes("You crawl into the tunnel...")
        delay(2)
        telejump(SophanemCoords.TUNNEL_EXIT, TeleportType.Exempt)
        mes("...and emerge inside the walls of Sophanem.")
    }

    private suspend fun ProtectedAccess.crawlOutOfSophanem() {
        anim(CRAWL_SEQ)
        mes("You squeeze through the hole in the wall.")
        delay(2)
        telejump(SophanemCoords.HOLE_OUTSIDE, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.openGate(gate: BoundLocInfo, type: ObjectServerType) {
        if (quest.stage(player) >= STAGE_JAR_RETURNED) {
            with(passages) { walkThrough(gate, type) }
            return
        }
        val inside = SophanemCoords.inSophanem(coords)
        startDialogue {
            chatNpcSpecific(
                "Priest",
                GATE_PRIEST,
                neutral,
                "Sorry, but Sophanem is under quarantine. No one may leave or enter.",
            )
            if (inside) {
                chatPlayer(angry, "But I'm not infected with anything!")
                chatNpcSpecific("Priest", GATE_PRIEST, neutral, "I'm afraid my orders are clear.")
            }
        }
    }

    private companion object {
        const val TUNNEL_ROCK = "loc.ics_little_entrance_multi"
        const val WALL_HOLE = "loc.ics_wall_crack"
        const val GATE_PRIEST = "npc.ics_little_priestdoorman"
        const val CRAWL_SEQ = "seq.human_crawling"

        val GATES = listOf("loc.sophanem_gate_left", "loc.sophanem_gate_right", "loc.sophanem_gate_small")
    }
}
