package org.rsmod.content.quest.area.gnomestronghold.grandtree.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.BARK_SAMPLE
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.HAZELMERE
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.SCROLL
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_HAS_SCROLL
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_STARTED
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Hazelmere, the last of the mages who grew the tree, upstairs on his island east of Yanille. */
class Hazelmere
@Inject
constructor(
    private val grandTree: GrandTreeQuest,
    private val objRepo: ObjRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(HAZELMERE) { startDialogue(it.npc) { hazelmere() } }
    }

    private suspend fun Dialogue.hazelmere() {
        val stage = grandTree.stage(player)
        when {
            stage == STAGE_STARTED && player.inv.contains(BARK_SAMPLE) -> examineBark()
            stage == STAGE_STARTED -> {
                mesbox("The mage starts to speak but all you hear is:")
                chatNpc(neutral, "Blah. Blah, blah, blah, blah...blah!")
                mesbox("Hazelmere seems to be waiting for something. Perhaps the King's bark sample would help.")
            }
            stage >= STAGE_HAS_SCROLL -> {
                chatNpc(neutral, "Blah, blah...Daconia...blah, blah.")
                if (stage == STAGE_HAS_SCROLL && !player.inv.contains(SCROLL)) {
                    mesbox("You make a writing motion. The mage scribbles something down on a scroll.")
                    access.invAddOrDrop(objRepo, SCROLL)
                    objbox(SCROLL, "Hazelmere has given you the scroll.")
                    return
                }
                mesbox("You still can't understand Hazelmere. The mage wrote it down for you on a scroll.")
            }
            else -> {
                mesbox("The mage starts to speak but all you hear is:")
                chatNpc(neutral, "Blah. Blah, blah, blah, blah...blah!")
            }
        }
    }

    private suspend fun Dialogue.examineBark() {
        mesbox("The mage starts to speak but all you hear is:")
        chatNpc(neutral, "Blah. Blah, blah, blah, blah...blah!")
        if (access.invDel(access.inv, BARK_SAMPLE).failure) {
            return
        }
        mesbox("You give the bark sample to Hazelmere.")
        mesbox("The mage carefully examines the sample.")
        chatNpc(worried, "Blah, blah...Daconia...blah, blah.")
        chatPlayer(quiz, "Can you write this down and I'll try and translate it?")
        chatNpc(quiz, "Blah, blah?")
        mesbox("You make a writing motion. The mage scribbles something down on a scroll.")
        access.invAddOrDrop(objRepo, SCROLL)
        grandTree.advanceTo(access, STAGE_HAS_SCROLL)
        objbox(SCROLL, "Hazelmere has given you the scroll.")
    }
}
