package org.rsmod.content.quest.area.digsite.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.PANNING_GUIDE
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.TEA_CUPS
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.TEA_VARBIT
import org.rsmod.content.quest.area.digsite.heldTea
import org.rsmod.content.quest.area.digsite.isNettleTea
import org.rsmod.content.quest.area.digsite.setVarBit
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The panning guide on the south-eastern shore. Nobody pans the river without his say-so, and his
 * say-so costs one cup of tea - nettle tea included, with a grimace.
 */
class PanningGuide : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(PANNING_GUIDE) { startDialogue(it.npc) { guide() } }
        onOpNpcU(PANNING_GUIDE) { useOnGuide(it.npc, it.objType.internalName) }
    }

    private suspend fun ProtectedAccess.useOnGuide(npc: Npc, obj: String) {
        if (obj !in TEA_CUPS) {
            mes("Nothing interesting happens.")
            return
        }
        startDialogue(npc) { acceptTea(obj) }
    }

    private suspend fun Dialogue.guide() {
        if (access.player.vars[TEA_VARBIT] != 0) {
            chatNpc(happy, "Help yourself to the river, friend. Mind the current.")
            return
        }
        chatPlayer(quiz, "Hello, who are you?")
        chatNpc(
            neutral,
            "Hello, I am the panning guide. I teach students how to pan in these waters. They're " +
                "not permitted to do so until after they've had training and, of course, they " +
                "must be invited to pan here too.",
        )
        chatPlayer(quiz, "So how do I become invited then?")
        chatNpc(
            neutral,
            "I'm not supposed to let people pan here unless they have permission from the " +
                "authorities first. Mind you, I could let you have a go if you're willing to do " +
                "me a favour.",
        )
        chatPlayer(quiz, "What's that?")
        chatNpc(happy, "Well, to be honest, what I would really like is... a nice cup of tea!")
        val tea = access.heldTea()
        if (tea == null) {
            chatPlayer(shocked, "Tea?!")
            chatNpc(happy, "Absolutely, I'm parched!")
            chatNpc(
                neutral,
                "If you could bring me one of those, I would be more than willing to let you pan " +
                    "here. I usually get some from Varrock but I'm busy at the moment.",
            )
            return
        }
        acceptTea(tea)
    }

    private suspend fun Dialogue.acceptTea(tea: String) {
        if (access.player.vars[TEA_VARBIT] != 0) {
            chatNpc(happy, "One cup was quite enough, thank you.")
            return
        }
        if (!access.player.inv.contains(tea)) {
            return
        }
        chatPlayer(happy, "I've some here that you can have.")
        if (isNettleTea(tea)) {
            chatNpc(bored, "Urg! Is that what you call tea? Oh well, I suppose it will do; you can pan all you want.")
        } else {
            chatNpc(happy, "Ah! Lovely! You can't beat a good cuppa... You're free to pan all you want.")
        }
        access.invDel(access.inv, tea)
        setVarBit(access.player, TEA_VARBIT, 1)
    }
}
