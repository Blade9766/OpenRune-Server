package org.rsmod.content.quest.area.burthorpe.heroesquest.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpNpc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** The pirate guards of Scarface Pete's mansion, who answer a greeting with a random pirate cry. */
class PirateGuard @Inject constructor(private val random: GameRandom) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.pirate_guard") { startDialogue(it.npc) { greet() } }
    }

    private suspend fun Dialogue.greet() {
        chatPlayer(happy, "Hello!")
        when (val roll = random.of(CRIES.size + SPECIAL_CRIES)) {
            CRIES.size -> {
                chatNpc(angry, "Oooh arrh!")
                chatNpc(quiz, "No wait. That's a farmer.")
            }
            CRIES.size + 1 -> {
                chatNpc(angry, "Pieces of eight! Pieces of eight!")
                chatNpc(quiz, "Oh wait, that's the parrot's line.")
            }
            CRIES.size + 2 -> {
                chatNpc(angry, "Avast behind!")
                chatPlayer(angry, "I'm not that fat!")
            }
            else -> chatNpc(angry, CRIES[roll])
        }
    }

    private companion object {
        const val SPECIAL_CRIES = 3

        val CRIES =
            listOf(
                "Yo ho ho and bottle of alcopop!",
                "Good day to you my dear sir!",
                "Arrh be off with ye!",
                "3 days at port for resupply then out on the high sea!",
                "Arrh arrh!",
                "Arrh ye scurvy sea dog!",
                "A pox on ye!",
                "Arrh! I be in search of buried treasure!",
                "Ahoy there!",
                "Avast me hearties!",
                "Shiver me timbers!",
                "You'll be joining Davey Jones in his locker!",
                "Arrrh ye lily livered landlubber!",
                "Batten down the hatches, there's a storm brewin'!",
                "All hands on deck!",
            )
    }
}
