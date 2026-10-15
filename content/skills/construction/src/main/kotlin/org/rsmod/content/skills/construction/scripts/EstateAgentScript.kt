package org.rsmod.content.skills.construction.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.constructionLvl
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.content.skills.construction.Construction
import org.rsmod.content.skills.construction.data.HouseLocation
import org.rsmod.content.skills.construction.data.HouseStyle
import org.rsmod.content.skills.construction.house.HouseStore
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** The estate agents, who sell a house and then move or redecorate it. */
class EstateAgentScript @Inject constructor(private val store: HouseStore) : PluginScript() {
    override fun ScriptContext.startup() {
        for (agent in AGENTS) {
            onOpNpc1(agent) { talk(it.npc) }
            onOpNpc3(agent) { relocate(it.npc) }
            onOpNpc4(agent) { redecorate(it.npc) }
        }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc) {
        val state = store.state(player)
        if (!state.owned) {
            startDialogue(npc) {
                chatNpc(happy, "Good day! Looking for a home of your own?")
                chatPlayer(quiz, "How much would that cost?")
                chatNpc(
                    neutral,
                    "A plot in Rimmington is yours for ${Construction.HOUSE_COST} coins, and I'll " +
                        "throw in a garden and a portal to get you home.",
                )
            }
            val buy = choice2("Buy a house for ${Construction.HOUSE_COST} coins.", true, "Not today.", false)
            if (!buy) {
                return
            }
            if (!invTakeFee(Construction.HOUSE_COST)) {
                startDialogue(npc) { chatNpc(neutral, "You don't have enough coins.") }
                return
            }
            store.update(player) { it.createStarterHouse() }
            startDialogue(npc) {
                chatNpc(
                    happy,
                    "Congratulations! Your house is waiting for you through the portal in " +
                        "Rimmington.",
                )
            }
            return
        }

        startDialogue(npc) {
            chatNpc(happy, "Good day. What can I do with your house?")
            chatNpc(
                neutral,
                "Your house is in ${state.location.label}, done out in " +
                    "${state.style.label.lowercase()}.",
            )
        }
    }

    private suspend fun ProtectedAccess.relocate(npc: Npc) {
        val state = store.state(player)
        if (!state.owned) {
            startDialogue(npc) { chatNpc(neutral, "You don't own a house to move.") }
            return
        }
        val options = HouseLocation.entries.filter { it != state.location }
        val choice =
            menu(
                "Move your house where?",
                hotkeys = true,
                choices = options.map { "${it.label} - ${it.cost} coins (level ${it.level})" },
            )
        val destination = options.getOrNull(choice) ?: return
        if (player.constructionLvl < destination.level) {
            startDialogue(npc) {
                chatNpc(
                    neutral,
                    "You need ${destination.level} Construction before I can move you to " +
                        "${destination.label}.",
                )
            }
            return
        }
        if (!invTakeFee(destination.cost)) {
            startDialogue(npc) {
                chatNpc(neutral, "Moving there costs ${destination.cost} coins.")
            }
            return
        }
        store.update(player) { it.location = destination }
        startDialogue(npc) {
            chatNpc(happy, "Done. Your house now stands in ${destination.label}.")
        }
    }

    private suspend fun ProtectedAccess.redecorate(npc: Npc) {
        val state = store.state(player)
        if (!state.owned) {
            startDialogue(npc) { chatNpc(neutral, "You don't own a house to redecorate.") }
            return
        }
        val options = HouseStyle.entries.filter { it != state.style }
        val choice =
            menu(
                "Redecorate in which style?",
                hotkeys = true,
                choices = options.map { "${it.label} - ${it.cost} coins (level ${it.level})" },
            )
        val style = options.getOrNull(choice) ?: return
        if (player.constructionLvl < style.level) {
            startDialogue(npc) {
                chatNpc(neutral, "That style needs ${style.level} Construction.")
            }
            return
        }
        if (!invTakeFee(style.cost)) {
            startDialogue(npc) { chatNpc(neutral, "That costs ${style.cost} coins.") }
            return
        }
        store.update(player) { it.style = style }
        startDialogue(npc) {
            chatNpc(happy, "The builders are on their way. Enjoy your ${style.label.lowercase()}.")
        }
    }

    private companion object {
        val AGENTS =
            listOf("npc.poh_estate_agent", "npc.prif_estate_agent", "npc.fortis_estate_agent")
    }
}
