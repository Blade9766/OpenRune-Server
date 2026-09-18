package org.rsmod.content.quest.area.desert.touristtrap.npcs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld5
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.desert.touristtrap.MiningCampSecurity
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapCoords
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.ANA_BARREL_HEAD
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.ANA_CARRIED
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.ANA_IN_A_BARREL
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.ANA_IN_MINE_NPC
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.EMPTY_BARREL
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.METAL_KEY
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_CAUGHT_ANA
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_GIVEN_PINEAPPLE
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_SAVED_ANA
import org.rsmod.content.quest.area.desert.touristtrap.ttMetAna
import org.rsmod.content.quest.area.desert.touristtrap.wearingSlaveRobes
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Ana, working in the far north-west of the mine, and Ana once she has been squeezed into a barrel.
 *
 * She is a multi-npc on `varbit.tourtrap_qip_ana_state`, so she vanishes from the mine for the
 * player carrying her; if anything goes wrong on the way out she is back at her rock. Careless
 * talk in front of a guard, or dropping her barrel anywhere in the camp, ends in a cell.
 */
class Ana
@Inject
constructor(
    private val quest: TouristTrapQuest,
    private val security: MiningCampSecurity,
    private val npcRepo: NpcRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(ANA_IN_MINE_NPC) { talk(it.npc) }
        onOpNpcU(ANA_IN_MINE_NPC) { event ->
            if (event.objType.internalName == EMPTY_BARREL) {
                startDialogue(event.npc) { intoTheBarrel() }
            } else {
                startDialogue(event.npc) { chatNpc(happy, "Thanks, but I don't need that... really!") }
            }
        }
        onOpHeld1(ANA_IN_A_BARREL) {
            startDialogue {
                chatNpcSpecific(
                    "Ana (in a Barrel)",
                    ANA_BARREL_HEAD,
                    angry,
                    "<col=000080>-- Ana looks pretty angry, she starts shouting at you. --</col> " +
                        "Get me out of here! Do you hear me! Get me OUT OF HERE I say!",
                )
            }
        }
        onOpHeld5(ANA_IN_A_BARREL) { dropBarrel() }
    }

    private suspend fun ProtectedAccess.talk(ana: Npc) {
        if (quest.stage(player) >= STAGE_SAVED_ANA || ANA_IN_A_BARREL in inv) {
            mes("This slave does not appear interested in talking to you.")
            return
        }
        if (!player.wearingSlaveRobes() && !security.isExempt(player)) {
            ana.say("Ooops... looks like a guard ")
            delay(2)
            ana.say("has seen your fancy clothes.")
            delay(2)
            val guard = security.guardNear(coords, OVERHEARD_RANGE)
            if (guard != null) {
                with(security) { caughtInFancyClothes(guard) }
                return
            }
        }
        startDialogue(ana) {
            if (player.ttMetAna) {
                helloAgain()
            } else {
                player.ttMetAna = true
                firstMeeting()
            }
        }
    }

    private suspend fun Dialogue.firstMeeting() {
        chatPlayer(happy, "Hello!")
        chatNpc(quiz, "Hello there, I don't think I've seen you before.")
        if (choice2("No, I'm new here!", true, "What's your name.", false)) {
            chatPlayer(neutral, "No, I'm new here!")
            chatNpc(
                happy,
                "I thought so you know! How do you like the hospitality down here? Not exactly Al " +
                    "Kharid town style is it? Well, I guess I'd better get back to work.",
            )
            chatNpc(sad, "Don't want to get into trouble with the guards again.")
            if (choice2("Do you get into trouble with guards often?", true, "I want to try and get you out of here.", false)) {
                troubleWithGuards()
            } else {
                getYouOut()
            }
        } else {
            chatPlayer(quiz, "What's your name.")
            whatsYourName()
        }
    }

    private suspend fun Dialogue.helloAgain() {
        chatPlayer(happy, "Hello again!")
        chatNpc(quiz, "Hello there, how's it going? Do you have a plan to get out of here yet?")
        val option =
            choice3(
                "Not yet, sorry, what's your name again?",
                1,
                "Well, I'm working on it, have you got any suggestions?",
                2,
                "Not yet, have you been in any more trouble with the guards?",
                3,
            )
        when (option) {
            1 -> {
                chatPlayer(quiz, "Not yet, sorry, what's your name again?")
                whatsYourName()
            }
            2 -> {
                chatPlayer(quiz, "Well, I'm working on it, have you got any suggestions?")
                suggestions()
            }
            else -> {
                chatPlayer(quiz, "Not yet, have you been in any more trouble with the guards?")
                chatNpc(
                    happy,
                    "No, not so far. I've been working very hard. Not like some people I know " +
                        "<col=000080>-- She cocks a smirk at you. --</col>",
                )
                if (choice2("Do you enjoy it down here?", true, "Okay, see ya!", false)) {
                    enjoyItDownHere()
                } else {
                    seeYa()
                }
            }
        }
    }

    private suspend fun Dialogue.whatsYourName() {
        chatNpc(
            happy,
            "My name? Oh, how sweet, my name is Ana. I come from Al Kharid though we've only " +
                "recently moved there. I was born, and did most of my growing up, in Varrock. I " +
                "thought the desert might be interesting.",
        )
        chatNpc(quiz, "What a surprise I got!")
        if (choice2("What kind of surprise did you get?", true, "Do you want to go back to Al Kharid?", false)) {
            chatPlayer(quiz, "What kind of surprise did you get?")
            chatNpc(
                angry,
                "Well, I was just touring the desert looking for the nomad tribe to the West. And " +
                    "I was set upon by these armoured men.",
            )
            chatNpc(
                quiz,
                "I think that the guards think I am an escaped prisoner. They didn't understand " +
                    "that I was exploring the desert as an adventurer.",
            )
        } else {
            chatPlayer(quiz, "Do you want to go back to Al Kharid?")
            chatNpc(
                happy,
                "Sure, I miss my Mum, her name is Irena and she is probably waiting for me. How do " +
                    "you propose we get out of here though?",
            )
            chatNpc(
                neutral,
                "I'm sure you've noticed the many square jawed guards around here. You look like " +
                    "you can handle yourself, but I have my doubts that you can take them all on!",
            )
        }
    }

    private suspend fun Dialogue.troubleWithGuards() {
        chatPlayer(quiz, "Do you get into trouble with guards often?")
        chatNpc(
            happy,
            "No, not really, because I'm usually working very hard. Come to think of it, I'd " +
                "better get back to work.",
        )
        if (choice2("Do you enjoy it down here?", true, "Okay, see ya!", false)) {
            enjoyItDownHere()
        } else {
            seeYa()
        }
    }

    private suspend fun Dialogue.seeYa() {
        chatPlayer(neutral, "Okay, see ya!")
        chatNpc(neutral, "Goodbye and good luck!")
    }

    private suspend fun Dialogue.enjoyItDownHere() {
        chatPlayer(quiz, "Do you enjoy it down here?")
        chatNpc(confused, "Of course not! I just don't have much choice about it at the moment.")
        if (choice2("I want to try and get you out of here.", true, "Do you have any ideas about how we can get out of here?", false)) {
            getYouOut()
            return
        }
        chatPlayer(quiz, "Do you have any ideas about how we can get out of here?")
        chatNpc(
            quiz,
            "Hmmm, not really, I would have tried them already if I did. The guards seem to live " +
                "in the compound. How did you get in there anyway?",
        )
        howDidYouGetIn()
    }

    private suspend fun Dialogue.getYouOut() {
        chatPlayer(neutral, "I want to try and get you out of here!")
        chatNpc(
            confused,
            "Wow! You're brave. How do you propose we do that? In case you hadn't noticed, this " +
                "place is quite well guarded.",
        )
        if (!choice2("We could try to sneak out.", true, "Have you got any suggestions?", false)) {
            chatPlayer(quiz, "Have you got any suggestions?")
            suggestions()
            return
        }
        chatPlayer(shifty, "We could try to sneak out.")
        chatNpc(
            laugh,
            "That doesn't sound very likely. How did you get in here anyway? Did you deliberately " +
                "hand yourself over to the guards? Ha, ha ha ha! Sorry, just kidding.",
        )
        howDidYouGetIn()
    }

    private suspend fun Dialogue.howDidYouGetIn() {
        if (!choice2("I managed to sneak past the guards.", true, "Huh, these guards are rubbish, it was easy to sneak past them!", false)) {
            chatPlayer(laugh, "Huh, these guards are rubbish, it was easy to sneak past them!")
            overheard("I heard that! So you managed to sneak in did you!", takeKey = false)
            return
        }
        chatPlayer(neutral, "I managed to sneak past the guards.")
        chatNpc(
            confused,
            "Hmm, impressive, but can you so easily sneak out again? How did you manage to get " +
                "through the gate?",
        )
        if (choice2("I have a key.", true, "It's a trade secret!", false)) {
            chatPlayer(neutral, "I have a key.")
            overheard("I heard that! So you used a key did you?!", takeKey = true)
            return
        }
        chatPlayer(happy, "It's a trade secret!")
        chatNpc(
            quiz,
            "Oh, right, well, I guess you know what you're doing. Anyway, I have to get back to " +
                "work. The guards will come along soon and give us some trouble else.",
        )
    }

    /** A guard in earshot takes an interest in how the new slave got in. */
    private suspend fun Dialogue.overheard(accusation: String, takeKey: Boolean) {
        val guard = security.guardNear(player.coords, OVERHEARD_RANGE) ?: return
        chatNpcSpecific("Guard", GUARD_HEAD, angry, accusation)
        if (takeKey && METAL_KEY in player.inv) {
            chatNpcSpecific("Guard", GUARD_HEAD, angry, "Right, we'll have that key off you!")
            access.invDel(access.inv, METAL_KEY)
        }
        access.ifClose()
        guard.say("Guards! Guards!")
        with(security) { access.throwInCell(guard) }
    }

    private suspend fun Dialogue.suggestions() {
        chatNpc(quiz, "Hmmm, let me think...")
        chatNpc(quiz, "Hmmm.")
        chatNpc(sad, "No, sorry...")
        chatNpc(quiz, "The only thing that gets out of here is the rock that we mine.")
        chatNpc(
            neutral,
            "Not even the dead get a decent funeral. Bodies are just thrown down disused mine " +
                "holes. It's very disrespectful...",
        )
        if (choice2("Okay, I'll check around for another way to try and get out.", true, "How does the rock get out?", false)) {
            chatPlayer(neutral, "Okay, I'll check around for another way to try and get out.")
            chatNpc(neutral, "Good luck!")
            return
        }
        chatPlayer(quiz, "How does the rock get out?")
        chatNpc(
            neutral,
            "Well, we mine it in this section, then someone scoops it into a barrel. The barrels " +
                "are loaded onto a mine cart. Then they're deposited near the surface lift.",
        )
        chatNpc(
            neutral,
            "I have no idea where they go from there. But that's not going to help us, is it?",
        )
        if (choice2("Maybe? I'll come back to you when I have a plan.", true, "Where would I get one of those barrels from?", false)) {
            chatPlayer(neutral, "Maybe? I'll come back to you when I have a plan.")
            chatNpc(
                happy,
                "Okay, well, I'm not going anywhere! <col=000080>-- Ana nods at a nearby guard! " +
                    "--</col> Unless he feels generous enough to let me go! <col=000080>-- The " +
                    "guard ignores the comment. --</col>",
            )
            chatNpc(neutral, "Oh well, I'd better get back to work, you take care!")
            return
        }
        chatPlayer(quiz, "Where would I get one of those barrels from?")
        chatNpc(
            quiz,
            "Well, you would get one from around by the lift area. But why would you want one of " +
                "those?",
        )
        if (choice2("Er no reason! Just wondering.", true, "I could try to sneak you out if you were in a barrel!", false)) {
            chatPlayer(shifty, "Er no reason! Just wondering.")
            chatNpc(
                angry,
                "Hmmm, just don't get any funny ideas... I am not going to get into one of those " +
                    "barrels! Okay, have you got that?",
            )
        } else {
            chatPlayer(happy, "I could try to sneak you out if you were in a barrel!")
            chatNpc(
                angry,
                "There is no way you are getting me into a barrel. No WAY! DO you understand?",
            )
        }
        if (choice2("Okay, yep, I've got that.", true, "Well, we'll see, it might be the only way.", false)) {
            chatPlayer(neutral, "Okay, yep, I've got that.")
            chatNpc(
                neutral,
                "Good, just make sure you keep it in mind. Anyway, I have to get back to work. The " +
                    "guards will come along soon and give us some trouble else.",
            )
        } else {
            chatPlayer(shifty, "Well, we'll see, it might be the only way.")
            chatNpc(
                angry,
                "No, there has to be a better way! Anyway, I have to get back to work. The guards " +
                    "will come along soon and give us some trouble else.",
            )
        }
    }

    private suspend fun Dialogue.intoTheBarrel() {
        val stage = quest.stage(player)
        if (stage >= STAGE_SAVED_ANA) {
            access.mes("You have already completed this quest.")
            chatNpc(neutral, "I think you might have me confused with someone else.")
            return
        }
        if (ANA_IN_A_BARREL in player.inv) {
            access.mes("You already have Ana in a barrel, you can't get two in there!")
            return
        }
        if (stage < STAGE_CAUGHT_ANA) {
            chatNpc(angry, "Hey, what do you think you're doing?")
        } else {
            chatNpc(
                angry,
                "Hey, what do you think you're doing? Leave me alone and let me get on with my " +
                    "work. Else we'll both be in trouble. Oh no, NOT AGAIN!",
            )
        }
        access.ifClose()
        if (access.invReplace(access.inv, EMPTY_BARREL, 1, ANA_IN_A_BARREL).failure) {
            return
        }
        access.anim(BARREL_SEQ)
        access.soundSynth(BARREL_SOUND)
        quest.moveAna(player, ANA_CARRIED)
        if (stage >= STAGE_GIVEN_PINEAPPLE) {
            quest.advanceTo(access, STAGE_CAUGHT_ANA)
        }
        player.say("Shush... It's for your own good!")
        chatNpcSpecific(
            "Ana (in a Barrel)",
            ANA_BARREL_HEAD,
            angry,
            "<col=0000ff>-- You manage to squeeze Ana into the barrel, -- -- despite her many " +
                "complaints. --</col> I djont fit in dis bawwel... Wet me out!!",
        )
    }

    /**
     * Ana climbs straight out of a dropped barrel and makes enough noise to bring every guard in
     * the camp running. Outside the camp she simply refuses to be left behind.
     */
    private suspend fun ProtectedAccess.dropBarrel() {
        val inCamp = TouristTrapCoords.inCamp(coords) || TouristTrapCoords.inMine(coords)
        if (!inCamp) {
            startDialogue {
                chatNpcSpecific(
                    "Ana (in a Barrel)",
                    ANA_BARREL_HEAD,
                    confused,
                    "You can't drop me here! I'll die in the desert on my own. Take me to the " +
                        "Shantay pass near Al Kharid.",
                )
            }
            return
        }
        startDialogue {
            chatNpcSpecific(
                "Ana (in a Barrel)",
                ANA_BARREL_HEAD,
                neutral,
                "You drop the barrel on the floor, Ana gets out.",
            )
        }
        if (inSurfaceCell()) {
            return
        }
        if (invReplace(inv, ANA_IN_A_BARREL, 1, EMPTY_BARREL).failure) {
            return
        }
        quest.moveAna(player, TouristTrapQuest.ANA_IN_MINE)
        val free = mapFindSquareLineOfWalk(coords, minRadius = 1, maxRadius = 1) ?: coords
        val ana = Npc(anaType, free)
        npcRepo.add(ana, ANA_OUTBURST_TICKS)
        ana.say("How dare you put me in that barrel you barbarian!")
        mes("Ana's outburst attracts the guards, they come running over.")
        delay(2)
        val guard = security.guardNear(coords, OVERHEARD_RANGE)
        guard?.say("Hey! What's going on here then?")
        mes("Guard: Hey! What's going on here then?")
        mes("The guards drag Ana away and then throw you into a cell.")
        delay(2)
        with(security) { throwInCell(guard) }
    }

    private fun ProtectedAccess.inSurfaceCell(): Boolean =
        coords.level == 0 && coords.x in CELL_MIN_X..TouristTrapCoords.CELL_DOOR_X &&
            coords.z in CELL_MIN_Z..CELL_MAX_Z

    private val anaType by lazy {
        ServerCacheManager.getNpc(ANA_NPC.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $ANA_NPC")
    }

    private companion object {
        const val ANA_NPC = "npc.ana"
        const val GUARD_HEAD = "npc.tourtrap_qip_desert_mining_guard_1"
        const val OVERHEARD_RANGE = 6
        const val ANA_OUTBURST_TICKS = 6

        const val BARREL_SEQ = "seq.human_pickupfloor"
        const val BARREL_SOUND = "synth.put_down"

        /* The surface cell, where OSRS lets a dropped Ana stay put in her barrel. */
        const val CELL_MIN_X = 3284
        const val CELL_MIN_Z = 3031
        const val CELL_MAX_Z = 3036
    }
}
