package org.rsmod.content.quest.area.tirannwn.rovingelves.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.quest.area.tirannwn.rovingelves.CrystalSinging
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.CRYSTAL_BOW
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.CRYSTAL_SHIELD
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.FULL_CHARGES
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.ILFEEN
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.ILFEEN_CHEAPEST
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.ILFEEN_ENCHANT
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.ILFEEN_TALK
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.WEAPON_SEED
import org.rsmod.content.quest.area.tirannwn.rovingelves.ilfeenChants
import org.rsmod.content.quest.manager.menu
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Ilfeen sings crystal weapon seeds (what a reverted crystal bow or shield turns back into) into a
 * new bow or shield at full charge. Her price drops each time she is paid, down to a floor after
 * [ILFEEN_CHEAPEST] enchantments; her count is the cache's `varbit.roving_ilfeen_chantcount`, and
 * reaching the floor unlocks her "Enchant" option. The crystal halberd she also sings needs the
 * Western Provinces diary, which this server does not have, so it is not offered.
 */
class Ilfeen
@Inject
constructor(private val roving: RovingElvesQuest, private val singing: CrystalSinging) : PluginScript() {

    override fun ScriptContext.startup() {
        for (type in listOf(ILFEEN, ILFEEN_TALK, ILFEEN_ENCHANT)) {
            onOpNpc1(type) { startDialogue(it.npc) { talk() } }
            onOpNpc3(type) { startDialogue(it.npc) { enchant() } }
        }
    }

    private suspend fun Dialogue.talk() {
        chatPlayer(neutral, "Hello there.")
        chatNpc(happy, "Greetings, traveller. I am Ilfeen. I sing crystal weapon seeds into bows and shields, if you have one that needs a new shape.")
        if (WEAPON_SEED !in player.inv) {
            chatNpc(neutral, "Bring me a crystal weapon seed and the coins, and I will sing it for you.")
            return
        }
        enchant()
    }

    private suspend fun Dialogue.enchant() {
        if (WEAPON_SEED !in player.inv) {
            chatNpc(neutral, "You will need to bring me a crystal weapon seed first.")
            return
        }
        val count = player.ilfeenChants
        val bowPrice = bowPrice(count)
        val shieldPrice = shieldPrice(count)
        val obj =
            menu(
                "A crystal bow (${CrystalSinging.formatCoins(bowPrice)} coins)." to CRYSTAL_BOW,
                "A crystal shield (${CrystalSinging.formatCoins(shieldPrice)} coins)." to CRYSTAL_SHIELD,
                "Nothing, thanks." to null,
            ) ?: return
        val price = if (obj == CRYSTAL_BOW) bowPrice else shieldPrice
        if (!singing.singSeed(access, WEAPON_SEED, obj, price, FULL_CHARGES)) {
            chatNpc(neutral, "You will need ${CrystalSinging.formatCoins(price)} coins for that.")
            return
        }
        player.ilfeenChants = (count + 1).coerceAtMost(MAX_COUNT)
        roving.syncVars(player)
        objbox(obj, 400, "Ilfeen sings to the seed, and it grows into shape in your hands.")
    }

    companion object {
        const val MAX_COUNT = 7
        const val BOW_FIRST = 900_000
        const val BOW_STEP = 180_000
        const val SHIELD_FIRST = 750_000
        const val SHIELD_STEP = 150_000

        fun bowPrice(count: Int): Int = BOW_FIRST - BOW_STEP * count.coerceAtMost(ILFEEN_CHEAPEST)

        fun shieldPrice(count: Int): Int = SHIELD_FIRST - SHIELD_STEP * count.coerceAtMost(ILFEEN_CHEAPEST)
    }
}
