package org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFlag
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.GUNNJORN
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.KEY
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.STAGE_STARTED
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Gunnjorn runs the Barbarian Outpost agility course and keeps Larrissa's spare lighthouse key. */
class Gunnjorn @Inject constructor(private val horror: HorrorFromTheDeepQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(GUNNJORN) { startDialogue(it.npc) { gunnjorn() } }
    }

    private suspend fun Dialogue.gunnjorn() {
        val needsKey = horror.stage(player) == STAGE_STARTED && !player.inv.contains(KEY)
        if (!needsKey) {
            chatNpc(laugh, "Ha! Welcome to my obstacle course. Enjoy yourself, but mind your step. This is no playground, and it has claimed lives before.")
            return
        }
        if (horror[player, HorrorFlag.GotKey]) {
            chatNpc(laugh, "Welcome back to my obstacle course. Mind your step out there.")
            chatPlayer(sad, "Hello again, Gunnjorn. I'm afraid I've lost the key you gave me.")
            handOverKey(again = true)
            return
        }
        chatNpc(laugh, "Ha! Welcome to my obstacle course. Enjoy yourself, but mind your step. This is no playground, and it has claimed lives before.")
        chatPlayer(quiz, "Hello, are you Gunnjorn?")
        chatNpc(happy, "That I am. This course is mine, and a dangerous one it is!")
        chatPlayer(neutral, "Very impressive. I believe your cousin Larrissa left a key with you?")
        chatNpc(confused, "She did! How would you know about that? She said she'd likely never need it, but asked me to look after it all the same.")
        chatPlayer(worried, "Something has happened at the lighthouse and she's been locked out. She needs that key.")
        handOverKey(again = false)
    }

    private suspend fun Dialogue.handOverKey(again: Boolean) {
        if (player.inv.freeSpace() == 0) {
            chatNpc(neutral, if (again) "I'd give you another, but you've no room to carry it." else "I'd hand it over, but you've no room to carry it.")
            return
        }
        chatNpc(happy, if (again) "No trouble. Here's another." else "Of course. Here you are.")
        access.invAdd(access.inv, KEY)
        horror.set(player, HorrorFlag.GotKey)
        objbox(KEY, "Gunnjorn hands you the lighthouse key.")
    }
}
