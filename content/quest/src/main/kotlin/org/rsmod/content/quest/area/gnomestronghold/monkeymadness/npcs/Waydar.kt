package org.rsmod.content.quest.area.gnomestronghold.monkeymadness.npcs

import dev.openrune.types.MesAnimType
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadness
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.STAGE_CRASH_ISLAND
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.STAGE_HANGAR
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.WAYDAR
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.WAYDAR_HEAD
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.mmFadeTeleport
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Flight Commander Waydar. `npc.mm_waydar` is a varbit-multi that grows a Travel option once the
 * gliders are reinitialised; one spawn waits in the hangar, the other guards the wrecks on Crash
 * Island, told apart by their coordinates.
 */
class Waydar @Inject constructor(private val monkeyMadness: MonkeyMadnessQuest, private val search: NpcSearch) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(WAYDAR) { startDialogue(it.npc) { talk(it.npc) } }
        onOpNpc3(WAYDAR) { travel(it.npc) }
    }

    private fun Npc.inHangar(): Boolean = coords.z >= HANGAR_MIN_Z

    private suspend fun Dialogue.talk(npc: Npc) {
        if (npc.inHangar()) hangar() else crashIsland()
    }

    private suspend fun Dialogue.hangar() {
        val stage = monkeyMadness.stage(player)
        when {
            stage < STAGE_HANGAR -> chatNpc(neutral, "I don't know how you got in here, but you can find your own way out.")
            !monkeyMadness.glidersReady.get(player) -> {
                chatPlayer(neutral, "Daero says you're flying me south.")
                chatNpc(neutral, "I would be, if I could get one of these gliders off the ground. They fold themselves flat until the power is restored, and that needs the reinitialisation code.")
                chatPlayer(quiz, "Can't you enter it?")
                chatNpc(sad, "I've tried for days. The code is Glough's work, and he is the only one who ever understood it. He's banned from this hangar for life, of course.")
                chatNpc(neutral, "The panel is on the east wall. If you can make sense of it, be my guest. There's a crate of spare controls beside it that might help.")
            }
            stage == STAGE_HANGAR -> {
                chatNpc(happy, "You did it! I never thought I'd see these wings open again.")
                chatNpc(neutral, "Now, I only carry supplies for myself, so bring your own food. And watch out for the wildlife down there: some of it bites, hard.")
                chatNpc(quiz, "Ready to fly south?")
                when (choice2("Yes, let's go.", 1, "Not yet.", 2)) {
                    1 -> {
                        chatPlayer(happy, "Yes, let's go.")
                        access.flyToCrashIsland()
                    }
                    2 -> chatPlayer(neutral, "Not yet.")
                }
            }
            else -> {
                chatNpc(neutral, "Ready to go back to Crash Island?")
                when (choice2("Yes.", 1, "No.", 2)) {
                    1 -> access.flyToCrashIsland()
                    2 -> chatPlayer(neutral, "No.")
                }
            }
        }
    }

    private suspend fun Dialogue.crashIsland() {
        when {
            !monkeyMadness.lumdoRefused.get(player) -> {
                chatNpc(neutral, "Look at this place. Every one of the squad's gliders, smashed to pieces in the trees.")
                chatPlayer(quiz, "So this is where they came down?")
                chatNpc(neutral, "Blown all the way here from Karamja. Whatever Caranock says, that was no ordinary wind.")
                chatNpc(neutral, "There's a gnome by the boats. He must be one of Garkor's. Go and find out where the rest of them went.")
            }
            !monkeyMadness.lumdoOrdered.get(player) -> {
                chatPlayer(neutral, "Lumdo won't take me across. He says Garkor ordered him to stay with the gliders.")
                chatNpc(neutral, "Did he now. Well, Garkor isn't here, and I outrank him.")
                mesbox("Waydar marches over to Lumdo and gives him a very loud order.")
                access.orderLumdo()
                chatNpc(neutral, "He'll row you over. He won't like it, but he'll do it. I'm staying with the gliders; someone has to.")
                monkeyMadness.lumdoOrdered.set(player, true)
                monkeyMadness.syncVars(player)
            }
            else -> {
                chatNpc(neutral, "Lumdo will take you across to the atoll whenever you're ready. Good luck over there.")
                chatPlayer(neutral, "Thanks.")
            }
        }
    }

    private suspend fun ProtectedAccess.orderLumdo() {
        val lumdo = npcFind(MonkeyMadness.CRASH_ISLAND_LUMDO, MonkeyMadnessQuest.LUMDO, SEARCH_RADIUS, HuntVis.Off, search)
        lumdo?.say("Yes, sir. But the Sergeant won't like it, sir.")
    }

    private suspend fun ProtectedAccess.travel(npc: Npc) {
        when {
            !monkeyMadness.glidersReady.get(player) -> mes("Waydar can't fly anywhere until the gliders are reinitialised.")
            npc.inHangar() -> flyToCrashIsland()
            else -> flyToHangar()
        }
    }

    private suspend fun ProtectedAccess.flyToCrashIsland() {
        mesbox("You climb into the glider behind Waydar. The hangar roof opens and the ground falls away beneath you.")
        val first = monkeyMadness.stage(player) < STAGE_CRASH_ISLAND
        mmFadeTeleport(MonkeyMadness.CRASH_ISLAND_LANDING)
        monkeyMadness.advanceTo(this, STAGE_CRASH_ISLAND)
        monkeyMadness.syncVars(player)
        val waydar = npcFind(MonkeyMadness.CRASH_ISLAND_WAYDAR, WAYDAR, SEARCH_RADIUS, HuntVis.Off, search)
        waydar?.facePlayer(player)
        if (first) {
            startDialogue {
                waydar(neutral, "You can let go of the strut now. We're down.")
                chatPlayer(neutral, "That was further than I expected.")
                waydar(neutral, "Further than any gnome has ever flown. Look around you: this is where the squad came down. Those wrecks in the trees are their gliders.")
                waydar(neutral, "And that big island to the west must be the atoll from the charts. Talk to that gnome by the boats; he'll know where Garkor went.")
            }
        } else {
            mes("Waydar brings the glider down between the wrecks on Crash Island.")
        }
    }

    private suspend fun ProtectedAccess.flyToHangar() {
        mesbox("Waydar flies you back to the hangar under the Grand Tree.")
        mmFadeTeleport(MonkeyMadness.HANGAR_ARRIVAL)
    }

    private suspend fun Dialogue.waydar(mood: MesAnimType, text: String) {
        chatNpcSpecific("Waydar", WAYDAR_HEAD, mood, text)
    }

    private companion object {
        const val HANGAR_MIN_Z = 9000
        const val SEARCH_RADIUS = 8
    }
}
