package org.rsmod.content.quest.area.varrock.shieldofarrav.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onAiTimer
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.COINS
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Benny Gutenberg, who prints and sells the Varrock Herald in Varrock Square - the paper Straven
 * tells would-be VTAM employees to read. He shouts his wares between customers.
 */
class Benny
@Inject
constructor(private val objRepo: ObjRepository, private val random: GameRandom) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(BENNY) { startDialogue(it.npc) { benny() } }
        onAiTimer(BENNY) { shout(npc) }
    }

    private suspend fun Dialogue.benny() {
        val topic =
            choice4(
                "Can I have a newspaper, please?",
                1,
                "How much does a paper cost?",
                2,
                "Varrock Herald? Never heard of it.",
                3,
                "Anything interesting in there?",
                4,
            )
        when (topic) {
            1 -> {
                chatPlayer(quiz, "Can I have a newspaper, please?")
                chatNpc(happy, "Certainly, guv. That'll be 50 coins, please.")
            }
            2 -> {
                chatPlayer(quiz, "How much does a paper cost?")
                chatNpc(happy, "Only 50 coins! A steal! Fancy one?")
            }
            3 -> {
                chatPlayer(confused, "Varrock Herald? Never heard of it.")
                chatNpc(
                    neutral,
                    "Then allow me to enlighten you. The Varrock Herald is a brand new newspaper, " +
                        "written, printed and sold by yours truly, Benny Gutenberg.",
                )
                chatNpc(
                    happy,
                    "Every edition's packed with gripping stories! Can I tempt you to one for a " +
                        "mere 50 coins?",
                )
            }
            else -> {
                chatPlayer(quiz, "Anything interesting in there?")
                chatNpc(
                    happy,
                    "You bet, mate. Sharp opinions, fiery interviews and the juiciest celebrity " +
                        "gossip! A cracking read for just 50 coins! Want one?",
                )
            }
        }
        sell()
    }

    private suspend fun Dialogue.sell() {
        if (access.inv.count(COINS) < PRICE) {
            chatPlayer(sad, "Maybe when I've got more money on me.")
            chatNpc(bored, "No cash, no paper. Enjoy your ignorance.")
            return
        }
        if (!choice2("Sure, here you go...", true, "No, thanks.", false)) {
            chatPlayer(neutral, "No, thanks.")
            chatNpc(neutral, "Suit yourself. Plenty more fish in the sea.")
            return
        }
        chatPlayer(happy, "Sure, here you go...")
        access.invDel(access.inv, COINS, PRICE)
        access.invAddOrDrop(objRepo, NEWSPAPER)
        access.soundSynth(COINS_SOUND)
        objbox(NEWSPAPER, "You buy a copy of the Varrock Herald.")
    }

    private fun shout(benny: Npc) {
        benny.say(SHOUTS[random.of(SHOUTS.size)])
        benny.aiTimer(random.of(SHOUT_MIN_TICKS, SHOUT_MAX_TICKS))
    }

    private companion object {
        const val BENNY = "npc.qip_soa_newspaperseller"
        const val NEWSPAPER = "obj.qip_soa_newspaper2"
        const val COINS_SOUND = "synth.coins_jingle_1"
        const val PRICE = 50
        const val SHOUT_MIN_TICKS = 15
        const val SHOUT_MAX_TICKS = 40

        val SHOUTS =
            listOf(
                "Read all about it!",
                "Extra! Extra! Read all about it!",
                "Get your Varrock Herald now!",
                "Varrock Herald, only 50 coins!",
                "Varrock Herald, on sale here!",
            )
    }
}
