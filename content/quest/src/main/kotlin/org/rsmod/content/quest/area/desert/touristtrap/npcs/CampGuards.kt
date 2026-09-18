package org.rsmod.content.quest.area.desert.touristtrap.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onApNpc2
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc2
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.desert.touristtrap.MiningCampSecurity
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapCoords
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.ANA_BARREL_HEAD
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.ANA_IN_A_BARREL
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.ANA_ON_LIFT
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.CAMP_GUARDS
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.SOUND_EAT
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_ANA_ON_LIFT
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_ENTERED_MINE
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_FINDING_PINEAPPLE
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_GIVEN_PINEAPPLE
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_LEARNED_DARTS
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_RETRIEVED_ANA_MINE_CART
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.TENTI_PINEAPPLE
import org.rsmod.content.quest.area.desert.touristtrap.ttAnaLocation
import org.rsmod.content.quest.area.desert.touristtrap.wearingSlaveRobes
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The guards inside the Desert Mining Camp and down its mine.
 *
 * Anyone who is not dressed as a slave and talks to one of them is thrown in a cell. Two of them
 * matter to the quest: the pair at the cave passage, one of whom lets a slave through for a whole
 * Tenti pineapple, and the one working the lift, who helps load the heaviest barrel of his life.
 */
class CampGuards
@Inject
constructor(private val quest: TouristTrapQuest, private val security: MiningCampSecurity) :
    PluginScript() {

    override fun ScriptContext.startup() {
        for (guard in CAMP_GUARDS) {
            onOpNpc1(guard) { talk(it.npc) }
            onApNpc2(guard) { apRange(-1) }
            onOpNpc2(guard) { attackWarning(it.npc) }
            onOpNpcU(guard) { event -> itemOnGuard(event.npc, event.objType.internalName) }
        }
        onOpLoc1(WINCH_BUCKET) {
            mesbox(
                "This looks like a lift of some sort. You see barrels of rocks being placed on " +
                    "the lift and they're hauled up to the surface.",
            )
        }
        onOpLoc2(WINCH_BUCKET) { useLift() }
        onOpLocU(WINCH_BUCKET, ANA_IN_A_BARREL) { loadAnaOntoLift() }
    }

    private suspend fun ProtectedAccess.talk(guard: Npc) {
        if (security.isExempt(player)) {
            startDialogue(guard) { chatNpc(neutral, "Move along now...") }
            return
        }
        if (!player.wearingSlaveRobes()) {
            with(security) { caughtInFancyClothes(guard) }
            return
        }
        when {
            inCaveGuardPost(coords) -> startDialogue(guard) { caveGuard() }
            inLiftArea(coords) -> startDialogue(guard) { liftGuard() }
            else -> {
                mes("This guard looks as if he's been in the sun for a while.")
                startDialogue(guard) { chatNpc(neutral, "Move along now...") }
            }
        }
    }

    private suspend fun Dialogue.caveGuard() {
        access.mes("The guard looks as if he's been down here a while.")
        val stage = quest.stage(player)
        if (stage >= STAGE_GIVEN_PINEAPPLE) {
            chatNpc(
                happy,
                "That pineapple was just delicious, many thanks. I don't suppose you could get me " +
                    "another? <col=000080>-- The guard looks at you pleadingly.</col>",
            )
            chatPlayer(
                angry,
                "You must be joking! The last one I gave you cost me double shifts working " +
                    "copper ore! You should be grateful you got one at all.",
            )
            chatNpc(angry, "Alright, alright, I was only asking!")
            return
        }
        if (TENTI_PINEAPPLE in player.inv) {
            handOverPineapple()
            return
        }
        chatNpc(angry, "Yeah, what do you want?")
        if (!choice2("Er nothing really.", false, "I'd like to mine in a different area.", true)) {
            chatPlayer(neutral, "Er nothing really.")
            chatNpc(neutral, "Okay... so move along and get on with your work.")
            return
        }
        chatPlayer(quiz, "I'd like to mine in a different area.")
        chatNpc(
            happy,
            "Oh, you want to work in another area of the mine eh? <col=000080>--The guard seems " +
                "pleased with his rhetorical question.--</col> Well, I can understand that! A " +
                "change is as good as a rest they say.",
        )
        if (!choice2("Huh, fat chance of a rest for me.", false, "Yes sir, you're quite right sir.", true)) {
            chatPlayer(bored, "Huh, fat chance of a rest for me.")
            access.ifClose()
            access.mes("The guard cuffs you around the head.")
            npc?.anim(CUFF_SEQ)
            access.queueHit(player, delay = 0, type = HitType.Typeless, damage = 1)
            chatNpc(angry, "You miserable whelp! Get back to work!")
            return
        }
        chatPlayer(neutral, "Yes sir, you're quite right sir.")
        chatNpc(
            happy,
            "Of course I'm right. And what goes around comes around as they say. And it's been " +
                "absolutely ages since I've had anything different to eat.",
        )
        chatNpc(
            happy,
            "What I wouldn't give for some whole, fresh, ripe and juicy pineapple for a change. " +
                "And those Tenti's have the best pineapple in this entire area.",
        )
        mesbox("The guard winks at you.")
        chatNpc(shifty, "I'm sure you get my meaning...")
        quest.advanceFrom(access, STAGE_ENTERED_MINE, STAGE_FINDING_PINEAPPLE)
        pineappleHints()
    }

    private suspend fun Dialogue.pineappleHints() {
        val option =
            choice3(
                "How am I going to get some pineapples around here?",
                1,
                "Yes sir, we understand each other perfectly.",
                2,
                "What are the Tenti's?",
                3,
            )
        when (option) {
            1 -> howToGetPineapples()
            2 -> understandEachOther()
            else -> {
                chatPlayer(quiz, "What are the Tenti's?")
                chatNpc(
                    neutral,
                    "Well! You don't come from around here do you? The Tenti's are the nomadic " +
                        "people west of here. They live in tents, so we call them the Tenti's. " +
                        "They have great pineapples! I'm sure you get my meaning?",
                )
                if (choice2("How am I going to get some pineapples around here?", true, "Yes sir, we understand each other perfectly.", false)) {
                    howToGetPineapples()
                } else {
                    understandEachOther()
                }
            }
        }
    }

    private suspend fun Dialogue.howToGetPineapples() {
        chatPlayer(quiz, "How am I going to get some pineapples around here?")
        chatNpc(
            neutral,
            "Well, that's not my problem is it? Also, I know that you slaves trade your items " +
                "down here. I'm sure that if you're resourceful enough, you'll come up with the " +
                "goods.",
        )
        chatNpc(
            neutral,
            "Now, get along and do some work, before we're both in for it. And remember, I " +
                "prefer my pineapples whole, not chopped up with all the juice gone.",
        )
    }

    private suspend fun Dialogue.understandEachOther() {
        chatPlayer(shifty, "Yes sir, we understand each other perfectly.")
        chatNpc(
            neutral,
            "Okay, good then. And remember, I prefer my pineapples whole, not chopped up with all " +
                "the juice gone.",
        )
        mesbox("The guard moves back to his post and winks at you knowingly.")
    }

    private suspend fun Dialogue.handOverPineapple() {
        if (quest.stage(player) >= STAGE_GIVEN_PINEAPPLE) {
            chatNpc(angry, "Yeah, whaddya want... get out of here!")
            return
        }
        chatPlayer(shifty, "Hey... I have something for you!")
        objbox(TENTI_PINEAPPLE, "You show the Tenti pineapple to the guard.")
        if (access.invDel(access.inv, TENTI_PINEAPPLE).failure) {
            return
        }
        npc?.anim(EAT_SEQ)
        access.soundSynth(SOUND_EAT)
        quest.advanceFrom(access, STAGE_LEARNED_DARTS, STAGE_GIVEN_PINEAPPLE)
        chatNpc(
            happy,
            "Great! Just what I've been looking for! Mmmmmmm... Delicious!! This is soo nice! " +
                "Mmmmm, *SLURP* Yummmm... Oh yes, this is great.",
        )
        chatPlayer(quiz, "So, can I go through now please?")
        chatNpc(happy, "Yes, yes, of course... a deal's a deal!")
    }

    private suspend fun ProtectedAccess.itemOnGuard(guard: Npc, obj: String?) {
        if (!TouristTrapCoords.inMine(coords)) {
            mes("Nothing interesting happens.")
            return
        }
        when (obj) {
            TENTI_PINEAPPLE -> startDialogue(guard) { handOverPineapple() }
            in FAKE_PINEAPPLES -> startDialogue(guard) { spitOut(obj!!) }
            ANA_IN_A_BARREL ->
                if (inLiftArea(coords)) {
                    startDialogue(guard) { helpWithBarrel() }
                } else {
                    mes("Nothing interesting happens.")
                }
            else -> mes("Nothing interesting happens.")
        }
    }

    private suspend fun Dialogue.spitOut(pineapple: String) {
        if (access.invDel(access.inv, pineapple).failure) {
            return
        }
        chatNpc(happy, "Oh great!")
        objbox(
            pineapple,
            "The guard rolls his eyes in glee and takes a bite of the pineapple. His face turns " +
                "from pleasure to pain as he spits the mouthful of pineapple out.",
        )
        chatNpc(
            angry,
            "Yeuch! That's awful! That's not Tenti pineapple! It's got no juice! Get me some " +
                "Tenti pineapple if you know what's good for you. A whole one so I can see that " +
                "it's good!",
        )
    }

    private suspend fun Dialogue.liftGuard() {
        if (ANA_IN_A_BARREL in player.inv) {
            helpWithBarrel()
            return
        }
        chatNpc(neutral, "Yes, what do you want?")
        if (choice2("Nothing thanks - sorry for disturbing you.", true, "Your head on a stick.", false)) {
            chatPlayer(shifty, "Nothing thanks - sorry for disturbing you.")
            chatNpc(angry, "Well... I guess that's okay, get on your way though.")
            return
        }
        chatPlayer(angry, "Your head on a stick.")
        chatNpc(angry, "Why you ungrateful whelp.. I'll teach you some manners.")
        with(security) { access.throwInCell(npc) }
    }

    private suspend fun ProtectedAccess.useLift() {
        val guard = security.guardNear(coords, LIFT_GUARD_REACH)
        if (guard == null) {
            mes("Nothing interesting happens.")
            return
        }
        startDialogue(guard) {
            if (player.ttAnaLocation == ANA_ON_LIFT) {
                chatNpc(
                    neutral,
                    "Hey, you'd better go and operate that lift yourself if you want that big " +
                        "heavy barrel to go anywhere.",
                )
                return@startDialogue
            }
            if (ANA_IN_A_BARREL in player.inv) {
                helpWithBarrel()
                return@startDialogue
            }
            chatNpc(quiz, "Hey there, what do you want?")
            var asked = choice2("What is this thing?", 1, "Can I use this?", 2)
            while (true) {
                if (asked == 1) {
                    chatPlayer(quiz, "What is this thing?")
                    chatNpc(
                        bored,
                        "It is quite clearly a lift. Any fool can see that it's used to transport " +
                            "rock to the surface.",
                    )
                    asked = choice2("Can I use this?", 2, "Ok, thanks.", 0)
                } else if (asked == 2) {
                    chatPlayer(quiz, "Can I use this?")
                    chatNpc(
                        bored,
                        "Of course not, you'd be doing me out of a job. Anyway, you haven't got " +
                            "any barrels that need to go to the surface.",
                    )
                    chatNpc(
                        bored,
                        "Now, move along and get some work done before you get a good beating.",
                    )
                    asked = choice2("What is this thing?", 1, "Ok, thanks.", 0)
                } else {
                    chatPlayer(neutral, "Ok, thanks.")
                    return@startDialogue
                }
            }
        }
    }

    private suspend fun ProtectedAccess.loadAnaOntoLift() {
        val guard = security.guardNear(coords, LIFT_GUARD_REACH)
        if (guard == null) {
            mes("Nothing interesting happens.")
            return
        }
        startDialogue(guard) { helpWithBarrel() }
    }

    /** The guard lends a hand with the barrel, and Ana nearly gives the game away. */
    private suspend fun Dialogue.helpWithBarrel() {
        mesbox("The guard notices the barrel (with Ana in it) that you're carrying.")
        chatNpc(quiz, "Hey, that Barrel looks heavy, do you need a hand?")
        if (!choice2("Yes please.", true, "No thanks, I can manage.", false)) {
            chatPlayer(neutral, "No thanks, I can manage.")
            chatNpc(bored, "Ok, fair enough, I was only offering.")
            return
        }
        chatPlayer(neutral, "Yes please.")
        mesbox("The guard comes over and helps you. He takes one end of the barrel.")
        chatNpc(neutral, "Blimey! This is heavy!")
        chatNpcSpecific("Ana (in a Barrel)", ANA_BARREL_HEAD, angry, "Why you cheeky....!")
        chatNpc(
            shocked,
            "<col=0000ff>~ The guard looks around, surprised at Ana's outburst. ~</col> What was " +
                "that?",
        )
        chatPlayer(shifty, "Oh, it was nothing.")
        chatNpc(confused, "I could have sworn I heard something!")
        chatNpcSpecific("Ana (in a Barrel)", ANA_BARREL_HEAD, angry, "Yes you did you ignoramus.")
        chatNpc(angry, "What was that you said?")
        if (!choice2("I said you were very gregarious!", true, "Oh, nothing.", false)) {
            chatPlayer(shifty, "Oh, nothing.")
            chatNpc(angry, "I heard you say something, now spit it out!")
            return
        }
        chatPlayer(happy, "I said you were very gregarious!")
        chatNpcSpecific("Ana (in a Barrel)", ANA_BARREL_HEAD, angry, "You creep!")
        chatNpc(
            happy,
            "Oh, right, how very nice of you to say so. <col=000080>-- The guard seems " +
                "flattered. --</col>",
        )
        chatNpc(
            neutral,
            "Anyway, let's get this barrel up to the surface, plenty more work for you to do!",
        )
        if (access.invDel(access.inv, ANA_IN_A_BARREL).failure) {
            return
        }
        quest.moveAna(player, ANA_ON_LIFT)
        quest.advanceFrom(access, STAGE_RETRIEVED_ANA_MINE_CART, STAGE_ANA_ON_LIFT)
        mesbox("The guard places the barrel carefully on the lift platform.")
        chatNpc(
            neutral,
            "Oh, there's no one operating the lift up top, hope this barrel isn't urgent? You'd " +
                "better get back to work!",
        )
    }

    private suspend fun ProtectedAccess.attackWarning(guard: Npc) {
        startDialogue(guard) {
            mesbox("This mercenary looks very fierce. Are you sure you want to attack him?")
            val attack =
                choice2(
                    "Yes, I can take him on!",
                    true,
                    "Er, no thanks, I've had second thoughts about it.",
                    false,
                )
            if (!attack) {
                mesbox("You decide not to fight the Mercenary.")
                return@startDialogue
            }
            access.ifClose()
            guard.say("Oh, want a fight do you!")
            with(security) { access.throwInCell(guard) }
        }
    }

    private fun inCaveGuardPost(coords: CoordGrid): Boolean =
        coords.x in CAVE_POST_MIN_X..CAVE_POST_MAX_X && coords.z in CAVE_POST_MIN_Z..CAVE_POST_MAX_Z

    private fun inLiftArea(coords: CoordGrid): Boolean =
        coords.x in LIFT_MIN_X..LIFT_MAX_X && coords.z in LIFT_MIN_Z..LIFT_MAX_Z

    private companion object {
        const val WINCH_BUCKET = "loc.tourtrap_qip_ropepullthingy2"
        const val LIFT_GUARD_REACH = 5

        /* The guards either side of the cave passage. */
        const val CAVE_POST_MIN_X = 3274
        const val CAVE_POST_MAX_X = 3290
        const val CAVE_POST_MIN_Z = 9410
        const val CAVE_POST_MAX_Z = 9420

        /* The lift and the barrels around it. */
        const val LIFT_MIN_X = 3288
        const val LIFT_MAX_X = 3296
        const val LIFT_MIN_Z = 9419
        const val LIFT_MAX_Z = 9427

        const val CUFF_SEQ = "seq.human_unarmedpunch"
        const val EAT_SEQ = "seq.human_eat"

        val FAKE_PINEAPPLES = setOf("obj.pineapple", "obj.pineapple_ring", "obj.pineapple_chunks")
    }
}
