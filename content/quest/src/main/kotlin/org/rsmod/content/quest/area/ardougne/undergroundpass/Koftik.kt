package org.rsmod.content.quest.area.ardougne.undergroundpass

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.KOFTIK_BRIDGE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.KOFTIK_END
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.KOFTIK_GRID
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.KOFTIK_MAZE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.KOFTIK_OUTSIDE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.KOFTIK_TEMPLE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.OILY_CLOTH
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_BRIDGE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_ENTERED
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_IBAN_DEAD
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_UNICORN
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Koftik, King Lathas's scout, who guides the player into the pass and slowly loses his mind to
 * Iban's voices on the way down.
 *
 * He is the same npc six times over, spawned at six points along the route, each behind a varbit
 * of its own that the stage sets. Past the prison he no longer knows whose side he is on, and he
 * only comes back to himself once Iban is dead, at the last cave out of the pass.
 */
@Singleton
class Koftik
@Inject
constructor(private val quest: UndergroundPassQuest, private val npcRepo: NpcRepository) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(KOFTIK_OUTSIDE) { startDialogue(it.npc) { outside() } }
        onOpNpc1(KOFTIK_BRIDGE) { startDialogue(it.npc) { atTheBridge() } }
        onOpNpc1(KOFTIK_GRID) { startDialogue(it.npc) { atTheGrid() } }
        onOpNpc1(KOFTIK_MAZE) { startDialogue(it.npc) { goneOver() } }
        onOpNpc1(KOFTIK_TEMPLE) { startDialogue(it.npc) { goneOver() } }
        onOpNpc1(KOFTIK_END) { startDialogue(it.npc) { whereAmI() } }
        onOpLoc1(LAST_WAY_OUT) { leaveWithKoftik() }
    }

    /** The cave mouth sends a player who has not yet spoken to Koftik to him first. */
    suspend fun ProtectedAccess.meetOutside() {
        val koftik =
            npcRepo.findAll(ZoneKey.from(coords), KOFTIK_REACH).firstOrNull { it.isType(KOFTIK_OUTSIDE) }
                ?: return
        startDialogue(koftik) { firstMeeting() }
    }

    private suspend fun Dialogue.outside() {
        if (quest.stage(player) == STAGE_STARTED) {
            firstMeeting()
            return
        }
        when (quest.stage(player)) {
            STAGE_COMPLETE -> {
                chatPlayer(quiz, "Hello Koftik.")
                chatNpc(happy, "Hello Adventurer, how's things?")
                chatPlayer(happy, "Not bad, yourself?")
                chatNpc(neutral, "I'm good... just keeping an eye out.")
            }
            STAGE_IBAN_DEAD -> {
                chatPlayer(happy, "Thanks for getting me out Koftik.")
                chatNpc(happy, "Always a pleasure squire. Have you informed the king about Iban?")
                if (choice2("No, not yet.", 1, "Yes, I've told him.", 2) == 1) {
                    chatPlayer(neutral, "No, not yet.")
                    chatNpc(neutral, "Traveller, this is no time to linger. The King must know that Iban is dead. This is a truly historical moment for Ardougne.")
                } else {
                    chatPlayer(happy, "Yes, I've told him.")
                    chatNpc(happy, "Good to hear, the sooner we find King Tyras the better.")
                }
            }
            in STAGE_BRIDGE..STAGE_UNICORN -> {
                chatPlayer(neutral, "Hello Koftik.")
                chatNpc(shocked, "It scares me in there, ... The voices, don't you hear them?")
                chatPlayer(neutral, "You'll be okay Koftik...")
            }
            STAGE_ENTERED ->
                chatNpc(neutral, "I know it's scary in there, but you'll have to go in alone. I'll catch up as soon as I can.")
            else -> access.mes("Koftik doesn't seem interested in talking.")
        }
    }

    private suspend fun Dialogue.firstMeeting() {
        chatPlayer(happy, "Hello there, are you the King's scout?")
        chatNpc(happy, "That I am, brave adventurer. King Lathas informed me that you need to cross these mountains.")
        chatNpc(neutral, "I'm afraid you'll have to go through the ancient underground pass...")
        chatPlayer(happy, "That's OK, I've travelled through many a cave in my time.")
        chatNpc(shocked, "These caves are different... They're filled with the spirit of Zamorak!")
        chatNpc(neutral, "You can feel it as you wind your way round the stalagmites.. an icy chill that penetrates the very fabric of your being...")
        chatNpc(neutral, "Not so many travellers come down here these days, ...but there are some who are still foolhardy enough.")
        quest.advanceTo(access, STAGE_ENTERED)
        if (choice2("I'll take my chances.", 1, "Tell me more...", 2) == 1) {
            chatPlayer(bored, "I'll take my chances.")
            chatNpc(neutral, "Okay traveller, I'll catch up with you by the bridge.")
            return
        }
        chatPlayer(neutral, "Tell me more...")
        chatNpc(neutral, "I remember seeing one such warrior. Going by the name of Randas... ..he stood tall and proud like an Elven King...")
        chatNpc(neutral, "...That same pride made him vulnerable to Zamorak's calls. Randas' worthy desire to be a great and mighty warrior also made him corruptible to Zamorak's promises of glory.")
        chatNpc(neutral, "..Zamorak showed him a way to achieve his goals by appealing to that most base and dark nature ...that resides in all of us.")
        chatPlayer(shocked, "What happened to him?")
        chatNpc(sad, "No one knows...")
    }

    private suspend fun Dialogue.atTheBridge() {
        if (quest.stage(player) >= STAGE_IBAN_DEAD) {
            chatPlayer(happy, "Thanks for getting me out Koftik.")
            chatNpc(happy, "Always a pleasure squire.")
            offerCloth(firstTime = false)
            return
        }
        if (quest.stage(player) == STAGE_ENTERED) {
            chatPlayer(quiz, "Koftik, how can we cross the bridge?")
            chatNpc(confused, "I'm not sure, seems as if others were here before us though...")
            if (!access.inv.contains(OILY_CLOTH)) {
                chatNpc(neutral, "I found this cloth amongst the charred remains of some arrows.")
                chatPlayer(shocked, "Charred arrows? They must have been trying to burn something. Or someone!")
                if (access.invAdd(access.inv, OILY_CLOTH).success) {
                    access.mes("Koftik gives you a damp cloth...")
                }
            }
            chatPlayer(neutral, "Interesting... we better keep our eyes open.")
            chatNpc(neutral, "I have also found the remains of a book...")
            if (choice2("Not to worry, probably just litter.", 1, "What does it say?", 2) == 1) {
                chatPlayer(neutral, "Not to worry, probably just litter.")
                chatNpc(confused, "Well.. maybe?")
                return
            }
            chatPlayer(neutral, "What does it say?")
            chatNpc(neutral, "It seems to be written by the adventurer Randas. It reads...")
            access.randasDiary()
            return
        }
        chatPlayer(happy, "Hi Koftik.")
        offerCloth(firstTime = true)
    }

    private suspend fun Dialogue.offerCloth(firstTime: Boolean) {
        if (access.inv.contains(OILY_CLOTH)) {
            return
        }
        val opener = if (firstTime) "Hi - I" else "I"
        chatNpc(neutral, "$opener found this cloth amongst the charred remains of some arrows, perhaps you need it?")
        if (access.invAdd(access.inv, OILY_CLOTH).success) {
            access.objbox(OILY_CLOTH, "Koftik gives you a damp cloth.")
        }
    }

    private suspend fun ProtectedAccess.randasDiary() {
        mesbox(
            "<col=000080>The Diary of Randas</col><br>It began as a whisper in my ears. " +
                "Dismissing the sounds as the whistling of the wind, I steeled myself against " +
                "these forces, and continued on my way.",
        )
        mesbox(
            "But the whispers became moans... At once fearsome and enticing like the call of " +
                "some beautiful siren.",
        )
        mesbox(
            "<col=8B0000>Join us!</col><br>Our greatness lies within you, but only Zamorak can " +
                "unlock your potential...",
        )
    }

    private suspend fun Dialogue.atTheGrid() {
        chatPlayer(quiz, "Hello Koftik.")
        if (quest.stage(player) >= STAGE_IBAN_DEAD) {
            chatNpc(happy, "Hello, how's things?")
            chatPlayer(happy, "Not bad, yourself?")
            chatNpc(neutral, "I'm good... just keeping an eye out.")
            return
        }
        chatNpc(neutral, "Do you hear them? The voices tell me things.")
        chatPlayer(happy, "Are you OK?")
        chatNpc(neutral, "The path of the righteous man is beset on all sides by the iniquities of the selfish and the tyranny of evil men.")
        chatPlayer(happy, "Tyranny of the righteous? What?")
        chatNpc(neutral, "So many paths to choose... Here we must all take our own path.")
    }

    /** Past the prison Koftik belongs to Iban, and the second time he says so more plainly. */
    private suspend fun Dialogue.goneOver() {
        if (quest.stage(player) >= STAGE_IBAN_DEAD) {
            chatPlayer(quiz, "Hello Koftik.")
            chatNpc(happy, "Hello, how's things?")
            chatPlayer(happy, "Not bad, yourself?")
            chatNpc(neutral, "I'm good... just keeping an eye out.")
            return
        }
        if (player.koftikChat == 1) {
            chatNpc(happy, "Nice job killing those Paladins. You're much closer to his side now. Perhaps you could kill those dwarfs to the south...")
            return
        }
        UndergroundPassQuest.setVarBit(player, "varbit.upass_koftik_chat", 1)
        chatNpc(shocked, "Traveller is that you?.. my friend on a mission!")
        chatPlayer(quiz, "Koftik, you're still here, you should leave.")
        chatNpc(quiz, "Leave?...leave?..this is my home now. Home with my lord, he talks to me, he's my friend.")
        access.mesbox("Koftik seems to be in a weak state of mind.")
        chatPlayer(neutral, "Koftik you really should leave these caverns.")
        chatNpc(angry, "Not now, we're all the same down here. There's just you and those Dwarfs left to be converted.")
        chatPlayer(quiz, "Dwarfs?")
        chatNpc(angry, "Foolish Dwarfs, still believing that they can resist. No one resists Iban, go traveller")
        chatNpc(shocked, "The Dwarfs to the south, they're not safe in the south.")
        chatNpc(angry, "We'll show them, go slay them m'lord. He'll be so proud, that's all I want.")
        chatPlayer(sad, "I'll pray for you.")
    }

    private suspend fun Dialogue.whereAmI() {
        if (quest.isComplete(player)) {
            chatPlayer(quiz, "Hello Koftik.")
            chatNpc(happy, "Hello, how's things?")
            chatPlayer(happy, "Not bad, yourself?")
            chatNpc(neutral, "I'm good... just keeping an eye out.")
            return
        }
        chatNpc(confused, "Traveller, where am I? I can't remember a thing!")
        chatPlayer(sad, "We were losing you to Iban's influence...")
        chatNpc(confused, "What?..of course, the voices. ...But they've stopped. What happened?")
        chatPlayer(neutral, "Iban's dead, I destroyed him.")
        chatNpc(happy, "You've done well, now we must inform the King. He'll have to send in some high mages to resurrect the Well of Voyage. Follow me, I'll lead you out.")
        chatPlayer(happy, "At last! I've had enough of caves.")
        access.ifClose()
        access.mes("Koftik leads you back up through the winding caverns...")
        access.delay(2)
        access.telejump(UpassCoords.KOFTIK_LEADS_OUT)
        access.mes("...and back to the cave entrance.")
    }

    /** The last cave out only means anything with Koftik standing beside it to lead the way. */
    private suspend fun ProtectedAccess.leaveWithKoftik() {
        arriveDelay()
        val koftik =
            npcRepo.findAll(ZoneKey.from(coords), KOFTIK_REACH).firstOrNull { it.isType(KOFTIK_END) }
        if (koftik == null || quest.stage(player) < STAGE_IBAN_DEAD) {
            mes("The cave is dark and the passage beyond it twists out of sight.")
            return
        }
        startDialogue(koftik) { whereAmI() }
    }

    private companion object {
        const val LAST_WAY_OUT = "loc.upass_last_out"
        const val KOFTIK_REACH = 1
    }
}
