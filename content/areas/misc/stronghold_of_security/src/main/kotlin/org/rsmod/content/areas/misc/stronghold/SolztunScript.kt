package org.rsmod.content.areas.misc.stronghold

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.game.inv.Inventory
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Solztun, the barbarian spirit beside the Cradle of Life. He imbues a skull sceptre so it no
 * longer crumbles when its charges run out, for anyone carrying the boots from the cradle.
 */
class SolztunScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(Stronghold.SOLZTUN) { talk(it.npc) }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc) {
        startDialogue(npc) {
            chatNpc(
                angry,
                "I'm no ghost! I'm a spirit. I'm injecting my thoughts into your head so we can " +
                    "talk without any of that amulet nonsense.",
            )
            when {
                hasSceptre(SkullSceptreScript.IMBUED_SCEPTRE) -> alreadyImbued()
                hasSceptre(SkullSceptreScript.SKULL_SCEPTRE) && hasBoots() -> offerImbue()
                hasSceptre(SkullSceptreScript.SKULL_SCEPTRE) -> needBoots()
                else -> explain()
            }
        }
    }

    private suspend fun Dialogue.alreadyImbued() {
        chatNpc(happy, "That sceptre of yours already carries my blessing. It will never crumble now.")
    }

    private suspend fun Dialogue.offerImbue() {
        chatNpc(
            neutral,
            "I see you carry a skull sceptre, and the boots of one who has walked every level of " +
                "this stronghold. Would you like me to imbue the sceptre so it never crumbles?",
        )
        val imbue = choice2("Yes please.", true, "No thanks.", false)
        if (!imbue) {
            chatNpc(neutral, "As you wish. Come back if you change your mind.")
            return
        }
        chatPlayer(happy, "Yes please.")
        val inventory = if (invContains(access.inv, SkullSceptreScript.SKULL_SCEPTRE)) access.inv else access.worn
        access.invReplace(inventory, SkullSceptreScript.SKULL_SCEPTRE, 1, SkullSceptreScript.IMBUED_SCEPTRE)
        if (inventory === access.worn) {
            access.rebuildAppearance()
        }
        vars[Stronghold.SCEPTRE_IMBUED] = 1
        access.mes("Solztun imbues your skull sceptre with his spirit.")
        chatNpc(happy, "There. My spirit is bound to it now; spend its charges as freely as you like.")
    }

    private suspend fun Dialogue.needBoots() {
        chatNpc(
            neutral,
            "A skull sceptre! I could imbue that for you, but only for someone who has proven " +
                "themselves here. Show me the boots from the Cradle of Life and we'll talk.",
        )
    }

    private suspend fun Dialogue.explain() {
        chatNpc(
            neutral,
            "The creatures of this stronghold carry the pieces of a skull sceptre. Bring me the " +
                "assembled sceptre, along with the boots from the Cradle of Life, and I will " +
                "imbue it so it never crumbles.",
        )
    }

    private fun Dialogue.hasSceptre(obj: String): Boolean = invContains(access.inv, obj) || invContains(access.worn, obj)

    private fun Dialogue.hasBoots(): Boolean = BOOTS.any { invContains(access.inv, it) || invContains(access.worn, it) }

    private fun Dialogue.invContains(inventory: Inventory, obj: String): Boolean = access.invTotal(inventory, obj) > 0

    private companion object {
        val BOOTS = listOf(Stronghold.FANCY_BOOTS, Stronghold.FIGHTING_BOOTS, Stronghold.FANCIER_BOOTS)
    }
}
