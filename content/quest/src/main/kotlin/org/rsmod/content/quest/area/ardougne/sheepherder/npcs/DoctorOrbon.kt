package org.rsmod.content.quest.area.ardougne.sheepherder.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.CLOTHING_PRICE
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.COINS
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.JACKET
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.ORBON
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.ardougne.sheepherder.SheepHerderQuest.Companion.TROUSERS
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Doctor Orbon, inside the East Ardougne church. Sells the plague jacket and trousers as one set
 * for 100 coins, and replaces any piece a paying customer has since lost at no further charge.
 */
class DoctorOrbon @Inject constructor(private val sheep: SheepHerderQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(ORBON) { startDialogue(it.npc) { orbon() } }
    }

    private suspend fun Dialogue.orbon() {
        val stage = sheep.stage(player)
        when {
            stage == 0 -> {
                chatNpc(neutral, "Good day. If you've come about a cough, I'm afraid I'm rather busy with the council's sheep business.")
                chatPlayer(quiz, "Sheep business?")
                chatNpc(worried, "Councillor Halgrive, outside, can tell you more. I just supply the protective clothing.")
            }
            stage > STAGE_STARTED -> {
                chatNpc(happy, "I hear those sheep have been dealt with. You can keep the clothing. I wouldn't want it back.")
            }
            !sheep.hasBoughtClothing(player) -> sell()
            else -> replace()
        }
    }

    private suspend fun Dialogue.sell() {
        chatPlayer(neutral, "Councillor Halgrive sent me. I'm to deal with the plague sheep.")
        chatNpc(worried, "Then you'll need protection. A plague jacket and plague trousers: wear both whenever you're near those animals or their remains.")
        chatNpc(neutral, "The set costs $CLOTHING_PRICE coins. The council will pay you back once the job's done.")
        if (!choice2("Buy the set for $CLOTHING_PRICE coins.", true, "Not right now.", false, title = "Buy protective clothing?")) {
            chatPlayer(neutral, "Not right now.")
            chatNpc(neutral, "Don't take too long. Those sheep aren't getting any healthier.")
            return
        }
        chatPlayer(neutral, "I'll take the set.")
        when {
            sheep.hasBoughtClothing(player) -> return
            access.inv.count(COINS) < CLOTHING_PRICE -> {
                chatNpc(sad, "That's $CLOTHING_PRICE coins, and you haven't got enough. Come back when you have.")
                return
            }
            !hasRoomForSet() -> {
                chatNpc(neutral, "You'll need two free spaces in your pack to carry the clothing.")
                return
            }
        }
        if (access.invDel(access.inv, COINS, CLOTHING_PRICE).failure) {
            return
        }
        access.invAdd(access.inv, JACKET, 1)
        access.invAdd(access.inv, TROUSERS, 1)
        sheep.markClothingBought(player)
        doubleobjbox(JACKET, TROUSERS, "You hand over $CLOTHING_PRICE coins. Doctor Orbon gives you a plague jacket and plague trousers.")
        chatNpc(neutral, "Remember, they only protect you if you're wearing them, both of them.")
    }

    private suspend fun Dialogue.replace() {
        val missing = listOf(JACKET, TROUSERS).filter { !owns(it) }
        if (missing.isEmpty()) {
            if (sheep.protectionMissing(player).isEmpty()) {
                chatNpc(happy, "Fully protected, I see. Good. Keep it that way until those sheep are ash.")
            } else {
                chatNpc(neutral, "You have the clothing, but it's no use in your pack. Wear both pieces whenever you handle the sheep.")
            }
            return
        }
        chatPlayer(sad, "I've lost some of the clothing you sold me.")
        if (access.inv.freeSpace() < missing.size) {
            chatNpc(neutral, "I've spares, but you've no room for them. Make some space in your pack.")
            return
        }
        chatNpc(neutral, "Careless, but you've paid already. Here, take these spares.")
        for (obj in missing) {
            access.invAdd(access.inv, obj, 1)
        }
        if (missing.size == 2) {
            doubleobjbox(JACKET, TROUSERS, "Doctor Orbon gives you a replacement plague jacket and plague trousers.")
        } else {
            objbox(missing.single(), zoom = 400, "Doctor Orbon gives you a replacement.")
        }
    }

    private fun Dialogue.owns(obj: String): Boolean =
        obj in access.inv || obj in access.worn || obj in access.bank

    /** Paying exactly the stack frees its slot, so it counts towards the two spaces. */
    private fun Dialogue.hasRoomForSet(): Boolean {
        val freed = if (access.inv.count(COINS) == CLOTHING_PRICE) 1 else 0
        return access.inv.freeSpace() + freed >= 2
    }
}
