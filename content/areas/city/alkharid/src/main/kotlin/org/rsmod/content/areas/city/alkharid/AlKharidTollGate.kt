package org.rsmod.content.areas.city.alkharid

import dev.openrune.types.MesAnimType
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc4
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The toll gate between Lumbridge and Al Kharid. Crossing either way costs 10 coins, paid to the
 * Border Guard on the player's side of the gate, until Prince Ali has been rescued; from then on
 * the gate just opens.
 */
class AlKharidTollGate @Inject constructor(private val passages: GenericPassageScript) :
    PluginScript() {

    override fun ScriptContext.startup() {
        for (gate in GATES) {
            onOpLoc1(gate) { open(it.loc, it.type) }
            onOpLoc4(gate) { payToll(it.loc, it.type) }
        }
    }

    private suspend fun ProtectedAccess.open(gate: BoundLocInfo, type: ObjectServerType) {
        if (QuestRequirements.hasCompleted(player, PRINCE_ALI_RESCUE)) {
            with(passages) { walkThrough(gate, type) }
            return
        }
        startDialogue {
            chatPlayer(quiz, "Can I come through this gate?")
            guard(gate, neutral, "You must pay a toll of 10 gold coins to pass.")
            when (
                choice3(
                    "No thank you, I'll walk around.", WALK_AROUND,
                    "Who does my money go to?", WHO_GETS_MONEY,
                    "Yes, ok.", PAY,
                )
            ) {
                WALK_AROUND -> {
                    chatPlayer(neutral, "No, thank you. I'll walk around.")
                    guard(gate, neutral, "Ok suit yourself.")
                }
                WHO_GETS_MONEY -> guard(gate, neutral, "The money goes to the city of Al-Kharid.")
                PAY -> pay(gate, type)
            }
        }
    }

    private suspend fun ProtectedAccess.payToll(gate: BoundLocInfo, type: ObjectServerType) {
        if (QuestRequirements.hasCompleted(player, PRINCE_ALI_RESCUE)) {
            with(passages) { walkThrough(gate, type) }
            return
        }
        startDialogue { pay(gate, type) }
    }

    private suspend fun Dialogue.pay(gate: BoundLocInfo, type: ObjectServerType) {
        if (!access.invTakeFee(TOLL)) {
            chatPlayer(sad, "Oh dear, I don't actually seem to have enough money.")
            return
        }
        mesbox("You pay the guard.")
        with(passages) { access.walkThrough(gate, type) }
    }

    private suspend fun Dialogue.guard(gate: BoundLocInfo, mesanim: MesAnimType, text: String) {
        val guard = if (access.coords.x < gate.coords.x) LUMBRIDGE_GUARD else AL_KHARID_GUARD
        chatNpcSpecific("Border Guard", guard, mesanim, text)
    }

    private companion object {
        const val PRINCE_ALI_RESCUE = "quest_princealirescue"
        const val TOLL = 10

        const val LUMBRIDGE_GUARD = "npc.borderguard1"
        const val AL_KHARID_GUARD = "npc.borderguard2"

        const val WALK_AROUND = 1
        const val WHO_GETS_MONEY = 2
        const val PAY = 3

        val GATES =
            listOf("loc.kharidmetalgateclosedl", "loc.kharidmetalgateclosedr")
    }
}
