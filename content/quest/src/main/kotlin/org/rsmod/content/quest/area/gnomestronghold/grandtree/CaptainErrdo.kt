package org.rsmod.content.quest.area.gnomestronghold.grandtree

import dev.openrune.types.MesAnimType
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.content.quest.area.gnomestronghold.gliders.GnomeGliders
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.JOGRE
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.PILOT
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_ESCAPE_BY_GLIDER
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_ON_KARAMJA
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Captain Errdo, the King's glider pilot on the top floor of the Grand Tree. `npc.pilot_grand_tree`
 * is a varbit-multi on the pilot's last destination, so every op arrives on the base type. The
 * same type stands beside the wreck on Karamja after the escape flight, which is told apart by
 * its coordinates. Once the quest is done he flies the whole glider network.
 */
class CaptainErrdo
@Inject
constructor(
    private val grandTree: GrandTreeQuest,
    private val gliders: GnomeGliders,
    private val search: NpcSearch,
    private val aiInteractions: AiPlayerInteractions,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(PILOT) { glider(it.npc) }
        onOpNpc3(PILOT) { startDialogue(it.npc) { talk(it.npc) } }
        onOpNpc4(PILOT) { lastDestination(it.npc) }
    }

    private fun Npc.onKaramja(): Boolean = coords.z < GrandTree.KARAMJA_MAX_Z

    private suspend fun ProtectedAccess.glider(npc: Npc) {
        when {
            npc.onKaramja() -> mes("The glider is wrecked. Captain Errdo won't be flying it anywhere.")
            grandTree.quest.isQuestCompleted(player) -> with(gliders) { glider(npc, GnomeGliders.Destination.TA_QUIR_PRIW) }
            grandTree.stage(player) == STAGE_ESCAPE_BY_GLIDER -> startDialogue(npc) { fleeing() }
            else -> mes("The pilot only flies friends of the gnome people.")
        }
    }

    private suspend fun ProtectedAccess.lastDestination(npc: Npc) {
        when {
            npc.onKaramja() -> mes("The glider is wrecked. Captain Errdo won't be flying it anywhere.")
            grandTree.quest.isQuestCompleted(player) -> with(gliders) { flyToLast(npc, GnomeGliders.Destination.TA_QUIR_PRIW) }
            else -> mes("The pilot only flies friends of the gnome people.")
        }
    }

    private suspend fun Dialogue.talk(npc: Npc) {
        when {
            npc.onKaramja() -> {
                chatPlayer(quiz, "Where's the shipyard from here?")
                chatNpc(neutral, "It's east of here. I think I saw some buildings on the coast while we were crashing...")
            }
            grandTree.quest.isQuestCompleted(player) -> with(gliders) { pilotTalk(npc, GnomeGliders.Destination.TA_QUIR_PRIW) }
            grandTree.stage(player) == STAGE_ESCAPE_BY_GLIDER -> fleeing()
            else -> {
                chatPlayer(happy, "Hello there.")
                chatNpc(happy, "Hello! I'm Captain Errdo, the King's glider pilot.")
                chatPlayer(quiz, "Can I have a go?")
                chatNpc(neutral, "Sorry, the glider's not for outsiders. King's orders.")
            }
        }
    }

    private suspend fun Dialogue.fleeing() {
        chatNpc(quiz, "Hi, the King said that you need to leave?")
        chatPlayer(neutral, "Apparently humans are invading!")
        chatNpc(confused, "I find that hard to believe! I have lots of human friends.")
        chatPlayer(neutral, "I don't understand it either!")
        chatNpc(quiz, "So where to?")
        when (choice2("Take me to Karamja please!", 1, "Not anywhere for now!", 2)) {
            1 -> {
                chatPlayer(neutral, "Take me to Karamja please.")
                chatNpc(happy, "Okay, you're the boss! Hold on tight, it'll be a rough ride.")
                access.flyToKaramja()
            }
            2 -> {
                chatPlayer(neutral, "Not anywhere for now!")
                chatNpc(neutral, "Okay. I'll be here for when you're ready.")
            }
        }
    }

    /** The escape flight ends in the jungle west of the shipyard, with a jogre for company. */
    private suspend fun ProtectedAccess.flyToKaramja() {
        mesbox("You fly on the glider.")
        fadeTeleport(GrandTree.CRASH_LANDING)
        grandTree.advanceTo(this, STAGE_ON_KARAMJA)
        npcFind(GrandTree.CRASH_ERRDO, PILOT, ERRDO_RADIUS, HuntVis.Off, search)?.facePlayer(player)
        startDialogue {
            errdo(sad, "Sorry about that.")
            errdo(neutral, "That turbulence over the Karamja Volcano was a bit unexpected, and the area round here isn't well suited for emergency landing.")
            errdo(happy, "Still! we're still alive that's the main thing. Are you okay?")
            chatPlayer(neutral, "I'm fine, I can't say the same for your glider!")
            errdo(sad, "I don't think I can fix this. Looks like we'll be heading back by foot. I might see if I can find Penwie while I'm here, I believe he's charting the area.")
            chatPlayer(quiz, "Where's the shipyard from here?")
            errdo(neutral, "I think I saw some buildings on the coast east of here while we were crashing. I'd have a look there.")
            errdo(happy, "Take care adventurer!")
            chatPlayer(happy, "Take care little man.")
        }
        val jogre = npcFind(player.coords, JOGRE, JOGRE_RADIUS, HuntVis.Off, search) ?: return
        jogre.say("Ug!")
        jogre.opPlayer2(player, aiInteractions)
    }

    private suspend fun Dialogue.errdo(mood: MesAnimType, text: String) {
        chatNpcSpecific("Captain Errdo", PILOT_HEAD, mood, text)
    }

    private companion object {
        const val PILOT_HEAD = "npc.pilot_grand_tree_base"
        const val ERRDO_RADIUS = 6
        const val JOGRE_RADIUS = 12
    }
}
