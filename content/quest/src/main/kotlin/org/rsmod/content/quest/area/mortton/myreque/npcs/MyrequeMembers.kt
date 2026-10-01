package org.rsmod.content.quest.area.mortton.myreque.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.mortton.myreque.Betrayal
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STAGE_BETRAYED
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STAGE_GUARD_PASSED
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STAGE_HOUND_SLAIN
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STAGE_MET_MEMBERS
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STAGE_MET_VELIAF
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STAGE_ROUTE_REVEALED
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STAGE_WEAPONS_DELIVERED
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.VELIAF
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.WEAPONS
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Member
import org.rsmod.content.quest.area.mortton.myreque.missingWeapons
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Veliaf Hurtz and the five Myreque members in the hideout under the Hollows.
 *
 * Veliaf must be met first; then each of the others, in any order. Meeting the last of them
 * moves the quest on and sends the player back to Veliaf, who takes the weapons - only when all
 * of them are in the pack - and the betrayal plays the moment the dialogue closes.
 */
class MyrequeMembers
@Inject
constructor(
    private val myq: InSearchOfTheMyrequeQuest,
    private val betrayal: Betrayal,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(VELIAF) { talkToVeliaf(it.npc) }
        for (member in Member.entries) {
            onOpNpc1(member.npc) { startDialogue(it.npc) { memberChat(member) } }
        }
    }

    private suspend fun ProtectedAccess.talkToVeliaf(npc: Npc) {
        if (betrayal.isInScene(player)) {
            return
        }
        when (myq.stage(player)) {
            STAGE_WEAPONS_DELIVERED -> {
                startDialogue(npc) { chatNpc(worried, "Wait... do you feel that? The air's gone cold all of a sudden.") }
                with(betrayal) { playScene() }
                return
            }
            STAGE_BETRAYED -> {
                startDialogue(npc) {
                    chatNpc(angry, "Don't stand there talking, kill that hound! Keep the bed or the barrels between you and it if you need to.")
                }
                with(betrayal) { releaseHound() }
                return
            }
        }
        var delivered = false
        startDialogue(npc) { delivered = veliaf() }
        if (delivered) {
            with(betrayal) { playScene() }
        }
    }

    /** Returns true once the weapons have changed hands. */
    private suspend fun Dialogue.veliaf(): Boolean {
        val stage = myq.stage(player)
        when {
            stage < STAGE_GUARD_PASSED -> chatNpc(angry, "Who let you in here? Curpile will hear about this.")
            stage < STAGE_MET_VELIAF -> firstMeeting()
            stage < STAGE_MET_MEMBERS -> introductions()
            stage < STAGE_WEAPONS_DELIVERED -> return handOver()
            stage < STAGE_ROUTE_REVEALED -> aftermath()
            myq.isComplete(player) -> afterQuest()
            else -> routeReminder()
        }
        return false
    }

    private suspend fun Dialogue.firstMeeting() {
        chatNpc(quiz, "Curpile let you through, so you must have something to say for yourself. I'm Veliaf Hurtz. What brings you to the bottom of a swamp?")
        chatPlayer(neutral, "Weapons. A man in Canifis called Vanstrom Klause paid for them and asked me to bring them to you.")
        chatNpc(confused, "Vanstrom Klause... I don't know the name. But a gift of steel is a rare thing in Morytania, and I'm not too proud to take it.")
        chatNpc(neutral, "Before anything changes hands, though, introduce yourself to the others. Down here we live or die by knowing who stands beside us.")
        myq.advanceTo(access, STAGE_MET_VELIAF)
    }

    private suspend fun Dialogue.introductions() {
        val unmet = Member.entries.filterNot { myq.hasMet(player, it) }
        chatNpc(neutral, "Have you spoken to everyone yet? You've still to meet ${unmet.joinToString { it.displayName }}.")
    }

    private suspend fun Dialogue.handOver(): Boolean {
        chatNpc(quiz, "So you've met us all. Now, about these weapons?")
        val missing = missingWeapons(player.inv)
        if (missing.isNotEmpty()) {
            chatPlayer(worried, "I'm still short of ${missing.joinToString()}.")
            chatNpc(neutral, "Then fetch them, and keep them in your pack. A sword on your belt is your sword, not ours.")
            return false
        }
        chatPlayer(happy, "Here they are: a longsword, two swords, a mace, a warhammer and a dagger, all good steel.")
        for ((weapon, count) in WEAPONS) {
            access.invDel(access.inv, weapon, count)
        }
        myq.advanceTo(access, STAGE_WEAPONS_DELIVERED)
        mesbox("You hand the bundle of steel weapons to Veliaf.")
        chatNpc(happy, "Fine work. With these we can finally strike back properly. You have our thanks, friend.")
        return true
    }

    private suspend fun Dialogue.aftermath() {
        chatNpc(sad, "Sani. Harold. Gone, just like that.")
        chatPlayer(sad, "I'm so sorry. I had no idea what he was.")
        chatNpc(neutral, "No. You couldn't have. He's fooled cleverer people than either of us. Vanstrom Klause... we'll remember that name.")
        chatNpc(neutral, "This place isn't safe any more, but we've nowhere else to go yet. And you need a way out that doesn't lead past the boat.")
        chatNpc(neutral, "Squeeze back out past the stalagmites and follow the tunnel north-west to its end. Search the wall there; it opens into a cellar under Canifis, with a ladder up behind the tavern.")
        chatNpc(neutral, "Use it whenever you need to reach us. But tell no one.")
        myq.advanceTo(access, STAGE_ROUTE_REVEALED)
    }

    private suspend fun Dialogue.routeReminder() {
        chatNpc(neutral, "The way out is at the north-western end of the tunnels. Search the wall there and climb the ladder to Canifis.")
        chatNpc(neutral, "And if you see Vanstrom Klause again... don't trust a word he says.")
    }

    private suspend fun Dialogue.afterQuest() {
        chatNpc(neutral, "We'll mourn our friends and keep fighting. It's what they would have wanted. Thank you for what you did with that hound.")
    }

    private suspend fun Dialogue.memberChat(member: Member) {
        val stage = myq.stage(player)
        when {
            stage < STAGE_MET_VELIAF -> chatNpc(neutral, "Talk to Veliaf first. He decides who's welcome here.")
            stage >= STAGE_BETRAYED -> mourn(member, stage)
            myq.hasMet(player, member) -> again(member)
            else -> {
                introduce(member)
                myq.markMet(player, member)
                if (myq.hasMetEveryone(player)) {
                    myq.advanceTo(access, STAGE_MET_MEMBERS)
                    access.mes("You have now met all of the Myreque. You should speak to Veliaf.")
                }
            }
        }
    }

    private suspend fun Dialogue.introduce(member: Member) {
        when (member) {
            Member.Sani -> {
                chatPlayer(neutral, "Hello. I'm here with weapons from Canifis.")
                chatNpc(neutral, "Sani Piliu. I'm the one who notices things, so don't take it personally if I keep an eye on you.")
                chatNpc(neutral, "I can put an arrow through a vyrewatch's wing at sixty paces. The swords are welcome, but it's ammunition we're really short of.")
            }
            Member.Harold -> {
                chatPlayer(neutral, "Hello, I'm new.")
                chatNpc(angry, "Harold Evans. Twenty years I soldiered for kings who never knew my name, and now I fight for nobody's pay at all.")
                chatNpc(neutral, "Don't get underfoot, don't touch my kit, and if it comes to blood, stand behind me. That's the whole of my advice.")
            }
            Member.Ivan -> {
                chatPlayer(neutral, "Hello there.")
                chatNpc(happy, "Hello! I'm Ivan Strom. Did you really come all the way through the swamp? Past the ghasts and everything?")
                chatNpc(happy, "The others say I'm too young for this, but somebody has to free Morytania. Might as well start early!")
            }
            Member.Polmafi -> {
                chatPlayer(neutral, "Hello. I'm here to help.")
                chatNpc(neutral, "Polmafi Ferdygris, formerly of the scriptorium, currently of this cave. I used to catalogue histories of the Drakans; now I'm trying to end them.")
                chatNpc(neutral, "Did you know the vampyres keep meticulous records? Bloodlines, tithes, every village. Terrifying, really. Also quite useful.")
            }
            Member.Radigad -> {
                chatPlayer(neutral, "Hello. Something smells good.")
                chatNpc(happy, "That'll be the stew. Radigad Ponfit, cook, quartermaster and occasional hero, when nobody else volunteers.")
                chatNpc(neutral, "You'll stay for a bowl. It's mostly mushroom and optimism, but it's hot.")
            }
        }
    }

    private suspend fun Dialogue.again(member: Member) {
        when (member) {
            Member.Sani -> chatNpc(neutral, "Still here? Go and see Veliaf, he's the one who wants those weapons.")
            Member.Harold -> chatNpc(neutral, "What? Speak to Veliaf, not me.")
            Member.Ivan -> chatNpc(happy, "Have you shown Veliaf the weapons yet? I can't wait to see them!")
            Member.Polmafi -> chatNpc(neutral, "Fascinating as this conversation is, Veliaf is the man you want.")
            Member.Radigad -> chatNpc(happy, "Stew's not ready yet. Go and talk to Veliaf.")
        }
    }

    private suspend fun Dialogue.mourn(member: Member, stage: Int) {
        val fighting = stage < STAGE_HOUND_SLAIN
        when (member) {
            Member.Ivan ->
                chatNpc(sad, if (fighting) "Kill it! Please, before it gets anyone else!" else "They were my friends. I'll make him pay for this. I swear it.")
            Member.Polmafi ->
                chatNpc(sad, if (fighting) "Its bones are cursed, not invulnerable! Strike it!" else "I read about creatures like him for years. Reading did not prepare me for this.")
            Member.Radigad ->
                chatNpc(sad, if (fighting) "Mind its jaws!" else "I'll cook for four now, not six. Doesn't seem right.")
            else -> Unit
        }
    }
}
