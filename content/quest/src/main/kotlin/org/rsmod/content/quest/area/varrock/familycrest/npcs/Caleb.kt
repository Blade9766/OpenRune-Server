package org.rsmod.content.quest.area.varrock.familycrest.npcs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.varrock.familycrest.FamilyCrestQuest
import org.rsmod.content.quest.area.varrock.familycrest.FamilyCrestQuest.Companion.CALEB
import org.rsmod.content.quest.area.varrock.familycrest.FamilyCrestQuest.Companion.CALEB_CREST
import org.rsmod.content.quest.area.varrock.familycrest.FamilyCrestQuest.Companion.SALAD_FISH
import org.rsmod.content.quest.area.varrock.familycrest.calebAsked
import org.rsmod.content.quest.area.varrock.familycrest.calebDone
import org.rsmod.content.quest.area.varrock.familycrest.knowsBrothers
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The eldest Fitzharmon, cooking in the house south of the Catherby farming shop. He trades his
 * piece of the crest for the five cooked fish his salad wants, and only then will he say where
 * his brothers went.
 */
class Caleb @Inject constructor(private val familyCrest: FamilyCrestQuest) : PluginScript() {

    private val fishNames: List<String> by lazy {
        SALAD_FISH.map { ServerCacheManager.getItem(it.asRSCM(RSCMType.OBJ))?.name ?: it }
    }

    override fun ScriptContext.startup() {
        onOpNpc1(CALEB) { startDialogue(it.npc) { caleb() } }
    }

    private suspend fun Dialogue.caleb() {
        when {
            !familyCrest.isStarted(player) -> chatNpc(neutral, "Sorry, the kitchen's closed to visitors.")
            !player.calebDone -> salad()
            !player.knowsBrothers -> askAboutBrothers()
            else -> chatNpc(happy, "Say hello to the old man for me, would you?")
        }
    }

    private suspend fun Dialogue.salad() {
        if (!player.calebAsked) {
            chatPlayer(quiz, "Are you Caleb Fitzharmon?")
            chatNpc(
                neutral,
                "I was, before I took up the knife. What does an old name matter next to a " +
                    "properly dressed salad?",
            )
            chatPlayer(neutral, "Your father wants his family crest back.")
            chatNpc(
                bored,
                "Does he now. Well, my third of it is in a drawer somewhere, and I'm far too " +
                    "busy to go digging for it.",
            )
            chatNpc(
                happy,
                "Tell you what. Bring me the fish for my seafood salad and I'll fetch it out " +
                    "for you.",
            )
            listFish()
            player.calebAsked = true
            familyCrest.syncStage(access)
            return
        }
        val missing = SALAD_FISH.filterNot { access.inv.contains(it) }
        if (missing.isNotEmpty()) {
            chatNpc(quiz, "Have you got my fish yet?")
            chatPlayer(sad, "Not all of them.")
            listFish()
            return
        }
        chatPlayer(happy, "I have all five of your fish.")
        for (fish in SALAD_FISH) {
            if (access.invDel(access.inv, fish).failure) {
                return
            }
        }
        chatNpc(happy, "Perfect. Sit down and I'll cut you a plate of it one day.")
        access.invAdd(access.inv, CALEB_CREST)
        objbox(CALEB_CREST, "Caleb digs his piece of the family crest out of a drawer.")
        player.calebDone = true
        familyCrest.syncStage(access)
        askAboutBrothers()
    }

    private suspend fun Dialogue.askAboutBrothers() {
        chatPlayer(quiz, "What happened to the rest of the crest?")
        chatNpc(
            neutral,
            "Avan took a third and went off prospecting. Last I heard he was picking over the " +
                "mine outside Al Kharid, and he's grown very fond of his own company.",
        )
        chatNpc(
            bored,
            "Johnathon took the last third and a great deal of father's wine. You'll find him " +
                "at the Jolly Boar Inn, north-east of Varrock, if he's still upright.",
        )
        player.knowsBrothers = true
        familyCrest.syncStage(access)
    }

    private suspend fun Dialogue.listFish() {
        mesbox("Caleb wants: ${fishNames.joinToString(", ")}. All of them cooked.")
    }
}
