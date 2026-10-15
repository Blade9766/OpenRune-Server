package org.rsmod.content.quest.area.gnomestronghold.grandtree.npcs

import dev.openrune.types.MesAnimType
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTree
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.CHARLIE_HEAD
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.GLOUGH
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.GNOME_GUARD
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.NARNODE
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.NARNODE_HEAD
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_CHARLIE_QUESTIONED
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_DEMON_SLAIN
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_ESCAPE_BY_GLIDER
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_GLOUGH_WARNED
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_HAS_LUMBER_ORDER
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_KING_DOUBTS
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_ON_KARAMJA
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_TRANSLATED
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_TRAPDOOR_OPEN
import org.rsmod.content.quest.area.gnomestronghold.grandtree.fadeTeleport
import org.rsmod.content.quest.area.gnomestronghold.grandtree.freeTileNear
import org.rsmod.content.quest.area.gnomestronghold.grandtree.landNear
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Glough, the head tree guardian, in his tree house south of the Grand Tree. `npc.grandtree_glough`
 * is a varp-multi on `varp.mm_main`, so the Talk-to arrives on the base type. Confronting him
 * with his journal has the guards throw the player into Charlie's cage, where Charlie gives up
 * the shipyard password and the King lets the player out.
 */
class Glough
@Inject
constructor(
    private val grandTree: GrandTreeQuest,
    private val npcRepo: NpcRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(GLOUGH) { startDialogue(it.npc) { glough(it.npc) } }
    }

    private suspend fun Dialogue.glough(npc: Npc) {
        when (val stage = grandTree.stage(player)) {
            in 0 until STAGE_TRANSLATED -> eating()
            STAGE_TRANSLATED -> deadlyPlot()
            in STAGE_GLOUGH_WARNED until STAGE_CHARLIE_QUESTIONED -> {
                chatPlayer(happy, "Hello Glough.")
                chatNpc(angry, "The human saboteur is in the cage where he belongs. Go back to the King, human, I've work to do.")
            }
            STAGE_CHARLIE_QUESTIONED -> if (grandTree.foundJournal.get(player)) suspicions(npc) else eating()
            STAGE_ESCAPE_BY_GLIDER -> {
                chatNpc(angry, "You again! Leave before I have you put back in the cage!")
            }
            STAGE_ON_KARAMJA, STAGE_HAS_LUMBER_ORDER -> knowWhatYoureUpTo()
            in STAGE_KING_DOUBTS until STAGE_TRAPDOOR_OPEN -> darnKey()
            STAGE_TRAPDOOR_OPEN -> {
                chatNpc(angry, "You're becoming quite annoying traveller! Leave human, before I have you put in the cage!")
            }
            in STAGE_DEMON_SLAIN..Int.MAX_VALUE -> postQuest()
            else -> error("Unhandled Grand Tree stage $stage")
        }
    }

    private suspend fun Dialogue.eating() {
        chatPlayer(happy, "Hello.")
        mesbox("The gnome is munching on a worm hole.")
        chatNpc(angry, "Can I help human? Can't you see I'm eating?!")
        mesbox("The gnome continues to eat.")
    }

    private suspend fun Dialogue.deadlyPlot() {
        chatPlayer(happy, "Hello.")
        mesbox("The gnome is munching on a worm hole.")
        chatNpc(angry, "Can I help human? Can't you see I'm eating?!")
        mesbox("The gnome continues to eat.")
        chatPlayer(neutral, "The King asked me to inform you that the Daconia rocks have been taken!")
        chatNpc(confused, "Surely not!")
        chatPlayer(neutral, "Apparently a human took them from Hazelmere. Hazelmere believed him; he had the King's seal!")
        chatNpc(angry, "I should've known! The humans are going to invade!")
        chatPlayer(angry, "Never!")
        chatNpc(angry, "Your type can't be trusted! I'll take care of this! Go back to the King.")
        grandTree.advanceTo(access, STAGE_GLOUGH_WARNED)
    }

    private suspend fun Dialogue.suspicions(npc: Npc) {
        chatPlayer(angry, "Glough! I don't know what you're up to but I know you paid Charlie to get those rocks!")
        chatNpc(angry, "You're a fool human! You have no idea what's going on.")
        chatPlayer(angry, "I know the Grand Tree's dying! And I think you're part of the reason.")
        chatNpc(angry, "How dare you accuse me! I'm the head tree guardian!")
        chatNpc(angry, "Guards! Guards!")
        access.imprisoned(npc)
    }

    private suspend fun ProtectedAccess.imprisoned(glough: Npc) {
        val guard = summon(GNOME_GUARD, glough.coords, GUARD_LINGER_TICKS)
        try {
            mesbox("A gnome guard appears.")
            startDialogue { guard(angry, "Come with me!") }
            mesbox("You are escorted by the guards and get locked up with Charlie.")
        } finally {
            npcRepo.del(guard, Int.MAX_VALUE)
        }
        fadeTeleport(GrandTree.CELL, exact = true)
        startDialogue {
            charlie(sad, "So they got you as well?")
            chatPlayer(angry, "It's Glough! He's trying to cover something up.")
            charlie(neutral, "I shouldn't tell you this adventurer. But if you want to get to the bottom of this you should go and talk to the Karamja Shipyard foreman.")
            chatPlayer(quiz, "Why?")
            charlie(neutral, "Glough sent me to Karamja to meet him. I delivered a large amount of gold. For what? I don't know. He may be able to tell you what Glough's up to. That's if you can get out of here. You'll find him")
            charlie(neutral, "in the Karamja Shipyard, east of Shilo Village. Be careful! If he discovers you're not working for Glough, there'll be trouble! The sea men use the password Ka-Lu-Min.")
            chatPlayer(happy, "Thanks Charlie!")
        }
        mesbox("King Narnode appears.")
        val king = summon(NARNODE, GrandTree.CELL_DOORSTEP, KING_LINGER_TICKS, exact = true)
        try {
            startDialogue {
                king(sad, "Traveller please accept my apologies! Glough had no right to arrest you! I just think he's scared of humans. Let me get you out of there.")
                mesbox("The King lets you out of the cell.")
                telejump(landNear(GrandTree.CELL_EXIT))
                chatPlayer(neutral, "I don't think you can trust Glough, your highness. He seems to have an unnatural hatred for humans.")
                king(neutral, "I know he can be a bit extreme at times. But he's the best tree guardian I have, he has made the gnomes paranoid about humans though.")
                king(worried, "I'm afraid Glough has placed guards on the front gate to stop you escaping! Let my glider pilot fly you away until things calm down around here.")
                chatPlayer(neutral, "Well, OK.")
                king(sad, "I'm sorry again Traveller!")
            }
        } finally {
            npcRepo.del(king, Int.MAX_VALUE)
        }
        grandTree.advanceTo(this, STAGE_ESCAPE_BY_GLIDER)
    }

    /** Spawns a cast member beside [near] (or exactly on it) that stays still and faces the player. */
    private fun ProtectedAccess.summon(type: String, near: CoordGrid, ticks: Int, exact: Boolean = false): Npc {
        val tile =
            if (exact) {
                near
            } else {
                freeTileNear(listOf(near.translateX(-1), near.translateZ(1), near.translateX(1), near.translateZ(-1)))
            }
        val npc = Npc(type, tile)
        npc.mode = NpcMode.None
        npcRepo.add(npc, ticks)
        npc.facePlayer(player)
        return npc
    }

    private suspend fun Dialogue.guard(mood: MesAnimType, text: String) {
        chatNpcSpecific(GrandTree.GUARD_NAME, GNOME_GUARD, mood, text)
    }

    private suspend fun Dialogue.charlie(mood: MesAnimType, text: String) {
        chatNpcSpecific("Charlie", CHARLIE_HEAD, mood, text)
    }

    private suspend fun Dialogue.king(mood: MesAnimType, text: String) {
        chatNpcSpecific(GrandTree.KING_NAME, NARNODE_HEAD, mood, text)
    }

    private suspend fun Dialogue.knowWhatYoureUpTo() {
        chatPlayer(angry, "I know what you're up to Glough!")
        chatNpc(angry, "You have no idea human!")
        chatPlayer(neutral, "You may be able to make a fleet but the tree gnomes will never follow you into battle against humans.")
        chatNpc(laugh, "So, you know more than I thought! The gnomes fear humanity more than any other race. I just need to give them a push in the right direction. There's nothing you can do traveller! Leave before it's too late!")
        chatPlayer(neutral, "King Narnode won't allow it!")
        chatNpc(angry, "The King's a fool and a coward! He'll bow to me! You'll soon be back in that cage!")
    }

    private suspend fun Dialogue.darnKey() {
        chatPlayer(angry, "I'm going to stop you, Glough!")
        chatNpc(angry, "You're becoming quite annoying traveller!")
        mesbox("Glough is searching his pockets.")
        chatNpc(confused, "Where are that darn key?")
        chatNpc(angry, "Leave human, before I have you put in the cage!")
    }

    private suspend fun Dialogue.postQuest() {
        chatPlayer(quiz, "Hello?")
        chatNpc(angry, "I warn you now, human: stay out of my affairs!")
        chatPlayer(angry, "I've stopped you before, Glough, and I'll stop you again!")
    }

    private companion object {
        const val GUARD_LINGER_TICKS = 50
        const val KING_LINGER_TICKS = 200
    }
}
