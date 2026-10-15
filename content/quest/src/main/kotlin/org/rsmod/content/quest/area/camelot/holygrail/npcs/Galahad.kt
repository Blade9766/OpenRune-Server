package org.rsmod.content.quest.area.camelot.holygrail.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.GALAHAD
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.NAPKIN
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_MERLIN
import org.rsmod.content.quest.area.camelot.holygrail.napkinGiven
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Galahad, once of the Round Table, now a hermit west of McGrubor's Wood. He is the only knight
 * who has seen the Grail, and he kept a napkin from its table; anything that has touched the
 * Grail lets its bearer see what the Grail's realm has hidden in this one.
 *
 * The napkin always comes back to his table drawer, so a player who loses it can have it again.
 */
class Galahad @Inject constructor(private val quest: HolyGrailQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(GALAHAD) { startDialogue(it.npc) { galahad() } }
    }

    private suspend fun Dialogue.galahad() {
        chatNpc(happy, "Welcome to my home. It's rare that I have guests. Will you take some tea?")
        val stage = quest.stage(player)
        if (stage < STAGE_MERLIN || quest.isComplete(player)) {
            smallTalk()
            return
        }
        if (!player.napkinGiven) {
            encounter()
            return
        }
        if (NAPKIN !in player.inv && !access.bank.contains(NAPKIN)) {
            chatPlayer(sad, "I've lost the napkin you gave me.")
            chatNpc(
                neutral,
                "Have you? It has a way of finding its way back to my drawer. Yes, here it is " +
                    "again. Look after it this time.",
            )
            giveNapkin()
            return
        }
        chatNpc(quiz, "How goes your search? Did the napkin show you anything?")
        chatNpc(neutral, "Keep it with you. It sees more clearly than either of us.")
    }

    private suspend fun Dialogue.smallTalk() {
        chatPlayer(quiz, "Are you a monk? You have the look of a knight.")
        chatNpc(
            neutral,
            "I was a knight once, at Arthur's table. I gave up the sword for quieter things. Mint " +
                "tea, mostly.",
        )
        if (quest.isComplete(player)) {
            chatNpc(happy, "And now you have done what I could not. Camelot has its Grail at last.")
        }
    }

    private suspend fun Dialogue.encounter() {
        chatPlayer(neutral, "Merlin says you have seen the Holy Grail.")
        chatNpc(
            sad,
            "Merlin talks too much. But yes. Years ago I rode into a country I could not find " +
                "again, to a castle where a king sat fishing from his window.",
        )
        chatNpc(
            neutral,
            "There was a feast. Maidens carried in a cup that shone like the morning, and every " +
                "man at the table was given what he most hungered for.",
        )
        chatNpc(
            neutral,
            "I was too awed to ask what any of it meant. The next morning the castle was empty, " +
                "and I was sitting alone in a field.",
        )
        chatNpc(
            confused,
            "All I brought back was this: a napkin from that table. I never could bring myself to " +
                "use it.",
        )
        val ask =
            choice2(
                "Could I borrow it? It may help me find the way.",
                true,
                "Thank you for the story.",
                false,
            )
        if (!ask) {
            chatPlayer(neutral, "Thank you for the story.")
            chatNpc(neutral, "Come back if you think the napkin might help you. It may.")
            return
        }
        chatPlayer(quiz, "Could I borrow it? It may help me find the way.")
        chatNpc(
            neutral,
            "Take it. Things that have touched the Grail see a little of its world. Carry it where " +
                "that world is close, and you may notice what other people miss.",
        )
        giveNapkin()
    }

    private suspend fun Dialogue.giveNapkin() {
        if (player.inv.isFull()) {
            chatNpc(neutral, "Your pack is full. Clear some room and I'll fetch it from the drawer.")
            return
        }
        access.invAdd(player.inv, NAPKIN)
        player.napkinGiven = true
        objbox(NAPKIN, "Galahad hands you a holy table napkin. It smells faintly of incense.")
    }
}
