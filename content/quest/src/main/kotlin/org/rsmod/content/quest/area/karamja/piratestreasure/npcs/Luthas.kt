package org.rsmod.content.quest.area.karamja.piratestreasure.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.COINS
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.CRATE_CAPACITY
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.LUTHAS
import org.rsmod.content.quest.area.karamja.piratestreasure.PiratesTreasureQuest.Companion.LUTHAS_WAGE
import org.rsmod.content.quest.area.karamja.piratestreasure.ptCrateBananas
import org.rsmod.content.quest.area.karamja.piratestreasure.ptCrateRum
import org.rsmod.content.quest.area.karamja.piratestreasure.ptLuthasJob
import org.rsmod.content.quest.area.karamja.piratestreasure.ptRumShipped
import org.rsmod.content.quest.manager.menu
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Luthas runs the Musa Point banana plantation and pays [LUTHAS_WAGE] coins for each crate filled
 * with bananas. A full crate goes off to Wydin's store in Port Sarim when he pays for it, along with
 * anything hidden inside it.
 */
class Luthas @Inject constructor(private val objRepo: ObjRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(LUTHAS) { startDialogue(it.npc) { luthas() } }
    }

    private suspend fun Dialogue.luthas() {
        when {
            !player.ptLuthasJob -> stranger()
            player.ptCrateBananas >= CRATE_CAPACITY -> payday()
            else -> taskPending()
        }
    }

    private suspend fun Dialogue.stranger() {
        chatNpc(happy, "Hello I'm Luthas, I run the banana plantation here.")
        val topic =
            choice2(
                "Could you offer me employment on your plantation?",
                Topic.Employment,
                "That customs officer is annoying isn't she?",
                Topic.Customs,
            )
        when (topic) {
            Topic.Employment -> employment()
            else -> customsOfficer()
        }
    }

    private suspend fun Dialogue.employment() {
        chatPlayer(quiz, "Could you offer me employment on your plantation?")
        chatNpc(
            happy,
            "Yes, I can sort something out. There's a crate ready to be loaded onto the ship.",
        )
        player.ptLuthasJob = true
        chatNpc(
            neutral,
            "You wouldn't believe the demand for bananas from Wydin's shop over in Port Sarim. I " +
                "think this is the third crate I've shipped him this month..",
        )
        chatNpc(happy, "If you could fill it up with bananas, I'll pay you $LUTHAS_WAGE gold.")
    }

    private suspend fun Dialogue.taskPending() {
        chatNpc(quiz, "Have you completed your task yet?")
        val topic =
            menu(
                "What did I have to do again?" to Topic.Remind,
                "No, the crate isn't full yet." to Topic.NotFull,
                "So where are these bananas going to be delivered to?" to Topic.Destination,
                "That customs officer is annoying isn't she?" to Topic.Customs,
            )
        when (topic) {
            Topic.Remind -> {
                chatPlayer(quiz, "What did I have to do again?")
                chatNpc(
                    neutral,
                    "There's a crate ready to be loaded onto the ship. If you could fill it up " +
                        "with bananas, I'll pay you $LUTHAS_WAGE gold.",
                )
            }
            Topic.NotFull -> {
                chatPlayer(sad, "No, the crate isn't full yet...")
                chatNpc(neutral, "Well come back when it is.")
            }
            Topic.Destination -> destination()
            else -> customsOfficer()
        }
    }

    private suspend fun Dialogue.payday() {
        chatPlayer(happy, "I've filled a crate with bananas.")
        chatNpc(happy, "Well done, here's your payment.")
        shipCrate()
        access.invAddOrDrop(objRepo, COINS, LUTHAS_WAGE)
        access.mes("Luthas hands you $LUTHAS_WAGE coins.")
        val topic =
            menu(
                "Will you pay me for another crate full?" to Topic.Another,
                "Thank you, I'll be on my way" to Topic.Leave,
                "So where are these bananas going to be delivered to?" to Topic.Destination,
                "That customs officer is annoying isn't she?" to Topic.Customs,
            )
        when (topic) {
            Topic.Another -> {
                chatPlayer(quiz, "Will you pay me for another crate full?")
                chatNpc(happy, "Yes certainly.")
                chatNpc(
                    neutral,
                    "If you go outside you should see the old crate has been loaded on to the " +
                        "ship, and there is another empty crate in its place.",
                )
            }
            Topic.Leave -> chatPlayer(happy, "Thank you, I'll be on my way.")
            Topic.Destination -> destination()
            else -> customsOfficer()
        }
    }

    private fun Dialogue.shipCrate() {
        player.ptCrateBananas = 0
        if (player.ptCrateRum) {
            player.ptCrateRum = false
            player.ptRumShipped = true
        }
    }

    private suspend fun Dialogue.destination() {
        chatPlayer(quiz, "So where are these bananas going to be delivered to?")
        chatNpc(neutral, "I sell them to Wydin who runs a grocery store in Port Sarim.")
    }

    private suspend fun Dialogue.customsOfficer() {
        chatPlayer(quiz, "That customs officer is annoying isn't she?")
        chatNpc(neutral, "Well I know her pretty well. She doesn't cause me any trouble any more.")
        chatNpc(
            neutral,
            "She doesn't even search my export crates any more. She knows they only contain " +
                "bananas.",
        )
        chatPlayer(quiz, "Really? How interesting. Whereabouts do you send those to?")
        chatNpc(
            neutral,
            "There is a little shop over in Port Sarim that buys them up by the crate. I believe " +
                "it is run by a man called Wydin.",
        )
    }

    private enum class Topic {
        Employment,
        Customs,
        Remind,
        NotFull,
        Destination,
        Another,
        Leave,
    }
}
