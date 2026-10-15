package org.rsmod.content.skills.farming.scripts

import dev.openrune.types.ItemServerType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.skills.farming.data.Crop
import org.rsmod.content.skills.farming.data.Crops
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The tool leprechauns standing beside the patches.
 *
 * Produce used on one comes back as a bank note, which is what the walk between patches depends on.
 * His "Exchange" and "Deposit-all" options belong to [FarmingToolStoreScript], which owns the store
 * interface and the event masks it needs.
 */
class ToolLeprechaun : PluginScript() {
    private val produce: Set<String> = Crops.ALL.mapTo(HashSet(), Crop::produce)

    override fun ScriptContext.startup() {
        for (leprechaun in LEPRECHAUNS) {
            onOpNpc1(leprechaun) { talk(it.npc) }
            onOpNpcU(leprechaun) { note(it.npc, it.objType, it.invSlot) }
        }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc) {
        startDialogue(npc) {
            chatNpc(happy, "Well now, what can I be doing for you?")
            chatNpc(
                neutral,
                "Hand me anything you've grown and I'll swap it for a bank note, so you can " +
                    "carry a good deal more of it.",
            )
        }
    }

    private suspend fun ProtectedAccess.note(npc: Npc, type: ItemServerType, slot: Int) {
        if (type.internalName !in produce) {
            startDialogue(npc) {
                chatNpc(neutral, "That's not something you grew. I only note a farmer's own crop.")
            }
            return
        }
        val held = inv[slot] ?: return
        val note = runCatching { ocCert(type.internalName) }.getOrNull()
        if (note == null) {
            startDialogue(npc) {
                chatNpc(sad, "Sorry, I can't be making a note out of that one.")
            }
            return
        }
        if (invReplace(inv, slot, held.count, note).failure) {
            return
        }
        mes("The leprechaun exchanges your ${type.name.lowercase()} for a bank note.")
    }

    private companion object {
        /** Every leprechaun that keeps a farmer's tools, including the quest and island ones. */
        val LEPRECHAUNS =
            listOf(
                "npc.farming_tools_leprechaun",
                "npc.farming_tools_leprechaun_draynor",
                "npc.farming_tools_leprechaun_varlamore",
                "npc.myarm_leprechaun",
                "npc.fossil_leprechaun_underwater",
            )
    }
}
