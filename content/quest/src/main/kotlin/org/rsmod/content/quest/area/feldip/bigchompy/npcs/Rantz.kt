package org.rsmod.content.quest.area.feldip.bigchompy.npcs

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.Camera
import org.rsmod.api.player.output.HintArrows
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onAiTimer
import org.rsmod.api.script.onNpcQueueWithArgs
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.ARROWS_WANTED
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.ARROW_LAUNCH_SPOT
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.ARROW_TRAVEL_SPOT
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.BAIT_CAMERA
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.BAIT_CLEARING
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.BLOATED_TOAD
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.CHOMPY_DISPLAY
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.COINS
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.COOKED_CHOMPY
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.FLAVOUR_ONION
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.FLAVOUR_POTATO
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.OGRE_ARROW
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.OGRE_ARROW_STACK
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.OGRE_BOW
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.OGRE_BOW_SEQ
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.OGRE_BOW_SOUND
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.RANTZ
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.RAW_CHOMPY
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.SEASONED_CHOMPY
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.STAGE_ASKED_ABOUT_TOADS
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.STAGE_CHOMPY_ATE_TOAD
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.STAGE_COOKED
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.STAGE_DROPPED_TOAD
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.STAGE_GAVE_ARROWS
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.STAGE_GOT_BOW
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.STAGE_KILLED_CHOMPY
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.STAGE_OPENED_CHEST
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.STAGE_RANTZ_MISSED
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.STAGE_SHOWN_TOAD
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.STAGE_TOLD_TO_COOK
import org.rsmod.content.quest.area.feldip.bigchompy.ChompyHunt
import org.rsmod.content.quest.area.feldip.bigchompy.ChompyRanks
import org.rsmod.content.quest.area.feldip.bigchompy.chompyKills
import org.rsmod.content.quest.area.feldip.bigchompy.chompyMadeArrows
import org.rsmod.content.quest.area.feldip.bigchompy.rantzFlavour
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Rantz, the ogre on the south-east coast of the Feldip Hills who starts the quest and does the
 * hunting with the player throughout it.
 *
 * `npc.rantz` is a multinpc keyed on `varp.chompybird`, so the player always sees
 * `npc.rantz_pre_quest` or `npc.rantz_post_quest` while every op arrives on the base type; the
 * handlers below are registered there. His AI timer is what makes the middle of the quest happen:
 * whenever he spots a chompy that came down for the bait he takes a shot, and misses, until the
 * player talks him into handing the bow over.
 */
class Rantz
@Inject
constructor(
    private val quest: BigChompyBirdHuntingQuest,
    private val hunt: ChompyHunt,
    private val objRepo: ObjRepository,
    private val worldRepo: WorldRepository,
    private val random: GameRandom,
) : PluginScript() {
    private val arrowSpotanim by lazy { SpotanimType(ARROW_TRAVEL_SPOT.asRSCM(RSCMType.SPOTANIM)) }

    override fun ScriptContext.startup() {
        onOpNpc1(RANTZ) { startDialogue(it.npc) { rantz() } }
        onOpNpc3(RANTZ) { mes("Rantz only spares arrows for hunters who have earned his respect.") }
        onOpNpc4(RANTZ) { startDialogue(it.npc) { checkHats() } }

        onOpNpcU(RANTZ) { showItem(it.npc, it.objType.internalName) }

        onAiTimer(RANTZ) { lookForChompy(npc) }
        onNpcQueueWithArgs<PlayerUid>(LOOSE_ARROW_QUEUE) { looseArrow(it.args) }
        onNpcQueueWithArgs<PlayerUid>(ARROW_LANDS_QUEUE) { arrowMissed(it.args) }
    }

    /* Rantz's own hunting. */

    private fun lookForChompy(rantz: Npc) {
        if (random.of(maxExclusive = SPOT_ODDS) != 0) {
            return
        }
        val chompy = hunt.chompyNear(rantz.coords, SPOT_RANGE) ?: return
        val hunter = hunt.ownerOf(chompy) ?: return
        if (quest.stage(hunter) !in STAGE_CHOMPY_ATE_TOAD until STAGE_GOT_BOW) {
            return
        }
        rantz.faceSquare(chompy.coords)
        rantz.resetMode()
        rantz.say("Hey, dere's da chompy, I's gonna shoot it.")
        hunter.mes("Rantz: Hey, dere's da chompy, I's gonna shoot it.")
        rantz.queue(LOOSE_ARROW_QUEUE, AIM_CYCLES, hunter.uid)
    }

    private fun StandardNpcAccess.looseArrow(hunter: PlayerUid) {
        val chompy = hunt.chompyNear(npc.coords, SPOT_RANGE)
        if (chompy == null) {
            npc.defaultMode()
            return
        }
        npc.faceSquare(chompy.coords)
        npc.anim(OGRE_BOW_SEQ)
        npc.spotanim(ARROW_LAUNCH_SPOT, height = LAUNCH_HEIGHT)
        hunt.resolve(hunter)?.soundSynth(OGRE_BOW_SOUND)
        worldRepo.projAnim(npc, chompy, arrowSpotanim, ARROW_PROJANIM)
        npc.queue(ARROW_LANDS_QUEUE, FLIGHT_CYCLES, hunter)
    }

    private fun StandardNpcAccess.arrowMissed(uid: PlayerUid) {
        npc.say("Grrr...de'ese arrows are rubbish.")
        npc.defaultMode()
        val hunter = hunt.resolve(uid) ?: return
        if (quest.stage(hunter) !in STAGE_CHOMPY_ATE_TOAD until STAGE_GOT_BOW) {
            return
        }
        hunter.mes("Rantz keeps missing the chompy bird...")
        hunter.mes("Rantz: Grrr...de'ese arrows are rubbish.")
        hunt.withAccess(hunter) { quest.advanceTo(this, STAGE_RANTZ_MISSED) }
    }

    /* Dialogue. */

    private suspend fun ProtectedAccess.showItem(rantz: Npc, used: String) =
        startDialogue(rantz) {
            when (used) {
                OGRE_ARROW -> showArrows()
                BLOATED_TOAD -> showToad()
                RAW_CHOMPY -> showRawChompy()
                SEASONED_CHOMPY -> showSeasonedChompy()
                COOKED_CHOMPY -> giveCookedChompy()
                OGRE_BOW ->
                    chatNpc(
                        neutral,
                        "Yeah, it's a goodly stabbie chucker, but you's is too weedy to use it " +
                            "like Rantz.",
                    )
                else -> access.mes("Nothing interesting happens.")
            }
        }

    private suspend fun Dialogue.rantz() {
        if (quest.stage(player) >= STAGE_GOT_BOW && !carriesOgreBow()) {
            replaceBow()
        }
        when (quest.stage(player)) {
            0 -> questStart()
            STAGE_STARTED -> askedForArrows()
            STAGE_GAVE_ARROWS, STAGE_ASKED_ABOUT_TOADS, STAGE_OPENED_CHEST -> askedForToad()
            STAGE_SHOWN_TOAD -> pointAtClearing()
            STAGE_DROPPED_TOAD, STAGE_CHOMPY_ATE_TOAD -> baitPlaced()
            STAGE_RANTZ_MISSED -> offerToShoot()
            STAGE_GOT_BOW, STAGE_KILLED_CHOMPY -> askedForChompy()
            STAGE_TOLD_TO_COOK -> tellSeasonings()
            STAGE_COOKED -> handInChompy()
            else -> chatNpc(neutral, "Hey deyr, t'anks for da chompy, it was scrumbly!")
        }
    }

    private suspend fun Dialogue.questStart() {
        chatNpc(neutral, "Hey you creature! Make me some stabbers! I wanna hunt da chompy?")
        var topic: Topic? = openingChoice(explained = false)
        while (topic != null) {
            topic =
                when (topic) {
                    Topic.WhatAreStabbers -> whatAreStabbers()
                    Topic.WhatIsAChompy -> whatIsAChompy()
                    Topic.HowToMake -> howToMake()
                    Topic.Accept -> accept()
                    Topic.Refuse -> refuse()
                }
        }
    }

    private suspend fun Dialogue.openingChoice(explained: Boolean): Topic =
        choice4(
            if (explained) "How do I make the 'stabbers'?" else "What are 'stabbers'?",
            if (explained) Topic.HowToMake else Topic.WhatAreStabbers,
            "What's a 'chompy'?",
            Topic.WhatIsAChompy,
            "Ok, I'll make you some 'stabbers'.",
            Topic.Accept,
            "Er, make your own 'stabbers'!",
            Topic.Refuse,
        )

    private suspend fun Dialogue.whatAreStabbers(): Topic {
        chatPlayer(neutral, "What are stabbers?")
        chatNpc(
            neutral,
            "For da stabbie chucker, I's wanna hunt da chompy! Creature knows what Rantz wants... " +
                "...flyin' to stabbie da chompy!",
        )
        objbox(
            OGRE_BOW,
            OBJBOX_ZOOM,
            "The ogre shows you a huge but crude bow and then starts to nod energetically in an " +
                "effort to help you understand.",
        )
        chatPlayer(neutral, "I think I understand. You want me to make some arrows for you?")
        chatNpc(neutral, "Yeah, is what Rantz sayed, make da stabbers for da stabby chucker!")
        return openingChoice(explained = true)
    }

    private suspend fun Dialogue.whatIsAChompy(): Topic {
        chatPlayer(neutral, "What's a 'chompy'?")
        chatNpc(
            neutral,
            "Da chompy is der bestest yummies for Rantz, Fycie and Bugs! We's looking for da " +
                "yummies all da time. Da chompy is a big flapper, Rantz want's stabbers to sneaky, " +
                "sneaky, stick da chompy.",
        )
        chatPlayer(quiz, "Ah, so 'da chompy' is some kind of bird?")
        chatNpc(
            neutral,
            "Yeah, is what Rantz sayed, Da chompy is da big flapper and is bestest yummies. But " +
                "Rantz needs stabbers to stick da chompy... Will creatures make dem stabbers for us?",
        )
        return openingChoice(explained = false)
    }

    private suspend fun Dialogue.howToMake(): Topic {
        chatPlayer(neutral, "How do I make the 'stabbers'?")
        chatNpc(
            neutral,
            "Ahhh, da creature wants to know Rantz secret for stabbers? Rantz not say till da " +
                "creature says will make da stabbers for us else you Creature steals da chompy from us!",
        )
        return choice3(
            "What's a 'chompy'?",
            Topic.WhatIsAChompy,
            "OK, I'll make you some 'stabbers'.",
            Topic.Accept,
            "Er, make your own 'stabbers'!",
            Topic.Refuse,
        )
    }

    private suspend fun Dialogue.accept(): Topic? {
        chatPlayer(neutral, "OK, I'll make you some 'stabbers'.")
        chatNpc(neutral, "Good you creature, you need sticksies from achey tree and stabbies from dog bones.")
        quest.advanceTo(access, STAGE_STARTED)
        return null
    }

    private suspend fun Dialogue.refuse(): Topic? {
        chatPlayer(neutral, "Er, make your own 'stabbers'!")
        chatNpc(angry, "When I make 'stabbers', I pretend you chompy and practice on you!")
        return null
    }

    private suspend fun Dialogue.askedForArrows() {
        chatNpc(neutral, "Hey you creature... Have you made me da stabbers? I wanna stick da chompy?")
        if (access.invTotal(access.inv, OGRE_ARROW) > 0) {
            handOverArrows()
            return
        }
        chatPlayer(neutral, "Er not exactly?")
        chatNpc(
            angry,
            "You do stabbers quick quick! Or Rantz make stabbers for Rantz and then practice for " +
                "chompy sticking on creature!",
        )
        arrowRecipeChoice()
    }

    private suspend fun Dialogue.arrowRecipeChoice() {
        val remind =
            choice2(
                "How do I make the 'stabbers' again?",
                true,
                "Ok, I'll make the 'stabbers' for you.",
                false,
            )
        if (!remind) {
            chatPlayer(neutral, "Ok, I'll make the 'stabbers' for you.")
            chatNpc(neutral, "Good creature, quickly, hurry bring da stabbers!")
            return
        }
        chatPlayer(neutral, "How do I make the 'stabbers' again?")
        chatNpc(
            neutral,
            "Grrr creature... you's no good stabber maker! You's make da stabbie bit from da dog " +
                "bones, and get da sticksies from da Achey tree... simple see? Oh and da flufsies " +
                "from da flappers as well!",
        )
        chatPlayer(quiz, "Oh, so I need logs from the achey tree, bones from a canine... and feathers?")
        chatNpc(happy, "Is just what Rantz sayed!|@blu@~ The hulking ogre nods excitedly. ~")
    }

    private suspend fun Dialogue.handOverArrows() {
        chatPlayer(angry, "Well, yes actually, as you asked so nicely. Here you go! Here's your 'stabbers'.")
        if (!player.chompyMadeArrows) {
            chatNpc(
                angry,
                "Hey, 'dee'se stabbers no good! Did you make dem you's self? YOU go make dem " +
                    "arrows for ME creature!",
            )
            return
        }
        if (access.invTotal(access.inv, OGRE_ARROW) < ARROWS_WANTED) {
            chatNpc(
                neutral,
                "Dat's not enough, me's an good shot, but need some for practice. Bring more than " +
                    "fingers on hand...",
            )
            return
        }
        if (access.invDel(access.inv, OGRE_ARROW, ARROWS_WANTED).failure) {
            return
        }
        quest.advanceTo(access, STAGE_GAVE_ARROWS)
        objbox(OGRE_ARROW_STACK, OBJBOX_ZOOM, "Rantz takes six ogre arrows off you.")
        chatNpc(happy, "Ahh, der creature has dem... goodly, goodly. Now us can stick der chompy bird...")
        chatNpc(
            sad,
            "But da chompy not coming without da fatsy toadies... Godda get der fatsy toadies to " +
                "get da chompys. Den we put it over de're and sneaky, sneaky stick da chompy.",
        )
        toadQuestions()
    }

    private suspend fun Dialogue.askedForToad() {
        chatNpc(quiz, "Hey you creature, you still here?")
        chatNpc(
            quiz,
            "Da chompy still not coming! We need da fatsy toady to get da chompy, do you got it? " +
                "Do you got da fatsy toady? Then we can sneaky, sneaky stick da chompy.",
        )
        if (access.invTotal(access.inv, BLOATED_TOAD) == 0) {
            chatPlayer(angry, "No I haven't got the 'fatsy toady' yet!")
            chatNpc(sad, "Dat's a pidy... but maybe Rantz can help da creature?")
            toadQuestions()
            return
        }
        chatPlayer(neutral, "Yes, I have a 'fatsy toady' for you, here look!")
        approveToad()
    }

    private suspend fun Dialogue.toadQuestions() {
        while (true) {
            val topic =
                choice5(
                    "How do we make the chompys come?",
                    Toads.HowToLure,
                    "What are 'fatsy toadies'?",
                    Toads.WhatAreThey,
                    "Where do we put the 'fatsy toadies'?",
                    Toads.WhereToPut,
                    "What do you mean 'sneaky..sneaky, stick da chompy?'",
                    Toads.SneakySneaky,
                    "Ok, thanks.",
                    Toads.Done,
                )
            when (topic) {
                Toads.HowToLure -> {
                    chatPlayer(neutral, "How do we make the chompys come?")
                    chatNpc(
                        neutral,
                        "Chompys love da fatsy toadies. Toadies get big on der swamp gas and der " +
                            "chompys are licking der lips for em as me is licking lips for da chompy. " +
                            "Da chompys don't like da smaller toadies from nearby swampy.",
                    )
                    quest.advanceTo(access, STAGE_ASKED_ABOUT_TOADS)
                    chatNpc(
                        happy,
                        "Dey's fussie eaters just like Rantz. Fycie an' Bugs play with toadies and " +
                            "blower dey's all times making fatsy toadies.",
                    )
                }
                Toads.WhatAreThey -> {
                    chatPlayer(neutral, "What are 'fatsy toadies'?")
                    chatNpc(
                        neutral,
                        "Fatsy toadies are da chompy burds bestest yumms. But da toadies here are " +
                            "too small for da chompy. You've godda make da toadies big and round!",
                    )
                }
                Toads.WhereToPut -> {
                    chatPlayer(neutral, "Where do we put the 'fatsy toadies'?")
                    chatNpc(angry, "Over der!")
                    mesbox("The ogre points to a small clearing to da south.")
                    chatNpc(angry, "Ok creature? You got dat? Over dere by der no tree's place.")
                }
                Toads.SneakySneaky -> {
                    chatPlayer(neutral, "What do you mean 'sneaky..sneaky, stick da chompy?'")
                    chatNpc(
                        quiz,
                        "Duh! You creature is a bit stoopid yes? Us needs to sneaky, sneaky and " +
                            "stick da chompy! Den we can eat da chompy!",
                    )
                }
                Toads.Done -> {
                    chatPlayer(neutral, "Ok, thanks.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.approveToad() {
        objbox(BLOATED_TOAD, OBJBOX_ZOOM, "You show the bloated toad to Rantz. He nods with approval.")
        quest.advanceTo(access, STAGE_SHOWN_TOAD)
        chatNpc(neutral, "Dat's a good fatsy toady, now we's need to put it for da chompy to come.")
        pointAtClearing()
    }

    private suspend fun Dialogue.pointAtClearing() {
        chatPlayer(neutral, "Where should I put the 'fatsy toadies'?")
        Camera.camLookAt(player, BAIT_CLEARING, CAMERA_HEIGHT, CAMERA_RATE, CAMERA_RATE)
        Camera.camMoveTo(player, BAIT_CAMERA, CAMERA_DISTANCE, CAMERA_MOVE_RATE, CAMERA_MOVE_RATE)
        HintArrows.hintCoord(player, BAIT_CLEARING)
        chatNpc(
            neutral,
            "Over 'dere creature, put da toadies over der!|@blu@~ The ogre points to a clearing " +
                "to the south. ~",
        )
        Camera.camReset(player)
    }

    private suspend fun Dialogue.baitPlaced() {
        chatPlayer(neutral, "There you go, I've placed the bait.")
        chatNpc(happy, "Goodz, me now waits for da chompy! It shouldn't be long now. Sneaky... sneaky...")
        chatPlayer(neutral, "Yes, I know... stick da chompy!")
        chatNpc(
            neutral,
            "Hey, you's creature, is da fatsy toady still dere? Go get more fatsy toadies if dey all gone!",
        )
        chatPlayer(
            angry,
            "What? I have to get more bait if there's none there? Does this Chompy Bird even " +
                "exist I wonder?",
        )
    }

    private suspend fun Dialogue.offerToShoot() {
        chatPlayer(neutral, "Hey there, you keep missing the chompy bird.")
        chatNpc(angry, "I knows, I keeps missing... because your stabbers are|worserer at flying than a dead dog.")
        val push =
            choice2(
                "Oh, keep trying then... you might hit one through pure luck.",
                false,
                "Come on, let me have a go...",
                true,
            )
        if (!push) {
            chatPlayer(angry, "Oh, keep trying then... you might hit one through pure luck.")
            chatNpc(angry, "Grrrr... You lookin' like a chompy!")
            return
        }
        chatPlayer(angry, "Come on, let me have a go...")
        chatNpc(angry, "No, is Rantz stabby thrower... you too weedy...")
        val insist =
            choice2(
                "I'm actually quite strong... please let me try.",
                true,
                "Oh suit yourself, you'll just have to go hungry.",
                false,
            )
        if (!insist) {
            chatPlayer(angry, "Oh suit yourself, you'll just have to go hungry.")
            chatNpc(happy, "Or I eat you instead!")
            return
        }
        chatPlayer(sad, "I'm actually quite strong... please let me try.")
        chatNpc(
            angry,
            "Oh, ok...I lend you other stabby thrower... but creature don't better cry when it " +
                "hurts itself.",
        )
        access.invAddOrDrop(objRepo, OGRE_BOW)
        quest.advanceTo(access, STAGE_GOT_BOW)
        objbox(
            OGRE_BOW,
            OBJBOX_ZOOM,
            "Rantz hands over an ogre bow. It's huge! You can|barely drawn back the string!",
        )
    }

    private suspend fun Dialogue.askedForChompy() {
        chatNpc(angry, "Hey You! Got da chompy yet?")
        if (access.invTotal(access.inv, RAW_CHOMPY) > 0) {
            chatPlayer(neutral, "Yep, here's your chompy Bird!")
            showChompyCarcass()
            return
        }
        chatPlayer(neutral, "Not yet!")
        chatNpc(angry, "Well hurry up and get some, we is hungry!")
    }

    private suspend fun Dialogue.showChompyCarcass() {
        objbox(RAW_CHOMPY, OBJBOX_ZOOM, "You show Rantz the freshly plucked chompy carcass.")
        if (quest.stage(player) < STAGE_KILLED_CHOMPY) {
            chatNpc(
                angry,
                "Dat's a rotten chompy, did you shoot that creature? Is stinking bad, I's thinks " +
                    "it's too old. Go shoot your own chompy bird.",
            )
            return
        }
        chatNpc(angry, "Dat's a great chompy, you musta got a lucky shot wiv|da stabbie chucker.")
        quest.advanceTo(access, STAGE_TOLD_TO_COOK)
        player.rantzFlavour = if (random.randomBoolean()) FLAVOUR_ONION else FLAVOUR_POTATO
        tellSeasonings()
    }

    private suspend fun Dialogue.tellSeasonings() {
        chatNpc(
            angry,
            "Okay's now you's needs to cook da chompy! Slurp!|You's can cook it's over der!|" +
                "@blu@~ Rantz points to a nearby spit roast. ~",
        )
        val seasoning = if (player.rantzFlavour == FLAVOUR_ONION) "Onion." else "Potato"
        chatNpc(
            angry,
            "But's we's particular about our chompy yumms. Me's|wants $seasoning wiv mine! Fycie " +
                "and Bugs want|something wiv der's as well, go and ask 'em wat dey|want.",
        )
        chatPlayer(angry, "What! Now I've got the chompy bird, you expect me to|cook it as well?")
        chatNpc(
            angry,
            "Yep, da spit's over der! Last time Rantz did yummies,|got very bad, did bad things " +
                "to food... and belly.",
        )
        chatPlayer(angry, "HUH!")
    }

    private suspend fun Dialogue.handInChompy() {
        chatNpc(
            angry,
            "Hey creature, did you's get da cooked chompy yet?|I smelled something cooking and it " +
                "mades me 'ungry.|Hand over da chompy if ya know what's good for ya.",
        )
        if (access.invTotal(access.inv, SEASONED_CHOMPY) < 1) {
            chatPlayer(neutral, "Well, erm, I don't have one at the moment.")
            chatNpc(angry, "Well, 'urry up... else you's creature is looking tasty to me soon!")
            return
        }
        chatPlayer(angry, "Yes, here you go, here's your cooked chompy bird.")
        objbox(SEASONED_CHOMPY, OBJBOX_ZOOM, "You hand over the cooked chompy bird to Rantz.")
        chatNpc(happy, "Hey hey! We got da delicious chompy bird - yay!|This looks really tasty as well!")
        chatNpc(
            happy,
            "Tank's very much for da chompy...|Fycie an Bugs like very much da chompy yumms!|" +
                "@blu@~ The family of ogres sit down together ~|@blu@~ and enjoy your well cooked " +
                "chompy bird ~",
        )
        chatPlayer(laugh, "It's my pleasure!")
        if (access.invDel(access.inv, SEASONED_CHOMPY, 1).failure) {
            return
        }
        quest.complete(access)
    }

    private suspend fun Dialogue.replaceBow() {
        chatPlayer(neutral, "Hey, Rantz, I've lost the 'stabbie chucker', do you have another one please?")
        val cost = random.of(REPLACEMENT_BOW_MIN, REPLACEMENT_BOW_MAX)
        chatNpc(
            neutral,
            "Mee's has, but is not for freeness, dis one cost $cost of da gold coinses. Does you wants it?",
        )
        val buy = choice2("Yes, I'll buy the bow.", true, "No thanks...", false)
        if (!buy) {
            chatPlayer(neutral, "No thanks...")
            return
        }
        chatPlayer(neutral, "Yes, I'll buy the bow.")
        if (access.invTotal(access.inv, COINS) < cost) {
            chatNpc(neutral, "Hey you don't got da coins... come back when you have.")
            return
        }
        if (access.invDel(access.inv, COINS, cost).failure) {
            return
        }
        access.invAddOrDrop(objRepo, OGRE_BOW)
        doubleobjbox(OGRE_BOW, COINS, "You hand over the coins, and Rantz hands over the bow.")
        chatPlayer(neutral, "Thanks.")
    }

    private fun Dialogue.carriesOgreBow(): Boolean =
        access.invTotal(access.inv, OGRE_BOW) > 0 ||
            access.invTotal(access.bank, OGRE_BOW) > 0 ||
            OGRE_BOW in player.worn

    private suspend fun Dialogue.showArrows() {
        objbox(OGRE_ARROW_STACK, OBJBOX_ZOOM, "You show Rantz the ogre arrow.")
        val stage = quest.stage(player)
        if (stage == 0) {
            chatNpc(angry, "Don't creature threaten Rantz with pointy stabbers!")
            return
        }
        if (stage == STAGE_GAVE_ARROWS) {
            chatNpc(neutral, "Creature already given Rantz stabbers, Dey's worser at flying dan a dead dog.")
            return
        }
        chatNpc(neutral, "Hey you creature..you made da stabbers|Dat's good creature!")
        if (stage != STAGE_STARTED) {
            return
        }
        if (!player.chompyMadeArrows) {
            chatNpc(
                angry,
                "Hey, deese stabbers no good... did you make dem you's self? You go make dem " +
                    "stabbers for me creature! Don't get old ones or from other creatures.",
            )
            arrowRecipeChoice()
            return
        }
        handOverArrows()
    }

    private suspend fun Dialogue.showToad() {
        if (quest.stage(player) >= STAGE_SHOWN_TOAD) {
            objbox(BLOATED_TOAD, OBJBOX_ZOOM, "You show the bloated toad to Rantz.")
            chatNpc(neutral, "Hey creature got da fatsy toady Is you's gonna stick da chompy?")
            return
        }
        approveToad()
    }

    private suspend fun Dialogue.showRawChompy() {
        when (quest.stage(player)) {
            STAGE_KILLED_CHOMPY -> showChompyCarcass()
            STAGE_TOLD_TO_COOK -> {
                objbox(RAW_CHOMPY, OBJBOX_ZOOM, "You show Rantz the freshly plucked chompy carcass.")
                tellSeasonings()
            }
            in 0 until STAGE_KILLED_CHOMPY -> {
                chatNpc(angry, "Erk! Da's a yukky chompy, I's wants a yummie chompy!")
                rantz()
            }
            else ->
                chatNpc(neutral, "Erk! You's creature can cook da chompy on da spit! Rantz not do it for you!")
        }
    }

    private suspend fun Dialogue.showSeasonedChompy() {
        objbox(SEASONED_CHOMPY, OBJBOX_ZOOM, "You show the seasoned chompy to Rantz.")
        if (quest.stage(player) == STAGE_COOKED) {
            handInChompy()
            return
        }
        chatNpc(neutral, "Erhhhh! Dat doesn't look like somfin I'd wanna eat. Wher'dya find it?")
    }

    private suspend fun Dialogue.giveCookedChompy() {
        objbox(COOKED_CHOMPY, OBJBOX_ZOOM, "You offer Rantz the cooked chompy Bird.")
        chatNpc(happy, "Erm.. Da's very kindly of you creature!")
        access.invDel(access.inv, COOKED_CHOMPY, 1)
    }

    private suspend fun Dialogue.checkHats() {
        if (!quest.isComplete(player)) {
            chatNpc(angry, "You's not even stuck a chompy yet creature!")
            return
        }
        val kills = player.chompyKills
        chatNpc(neutral, "So creature, how many chompies has you sticked?")
        objbox(
            CHOMPY_DISPLAY,
            OBJBOX_ZOOM,
            "You've killed a total of @blu@$kills @bla@chompy birds so far!||" +
                "@blu@~ You're ${ChompyRanks.withArticle(kills)}! ~",
        )
        chatNpc(neutral, "Rantz not got no hatsies to spare right now creature. Keep sticking da chompy!")
    }

    private enum class Topic {
        WhatAreStabbers,
        WhatIsAChompy,
        HowToMake,
        Accept,
        Refuse,
    }

    private enum class Toads {
        HowToLure,
        WhatAreThey,
        WhereToPut,
        SneakySneaky,
        Done,
    }

    private companion object {
        const val LOOSE_ARROW_QUEUE = "queue.rantz_loose_arrow"
        const val ARROW_LANDS_QUEUE = "queue.rantz_arrow_lands"

        const val ARROW_PROJANIM = "projanim.arrow"

        /** One AI tick in this many makes Rantz notice a chompy. */
        const val SPOT_ODDS = 20
        const val SPOT_RANGE = 12

        const val AIM_CYCLES = 2
        const val FLIGHT_CYCLES = 3
        const val LAUNCH_HEIGHT = 50

        const val CAMERA_HEIGHT = 25
        const val CAMERA_RATE = 7
        const val CAMERA_DISTANCE = 900
        const val CAMERA_MOVE_RATE = 2

        const val OBJBOX_ZOOM = 250

        const val REPLACEMENT_BOW_MIN = 500
        const val REPLACEMENT_BOW_MAX = 550
    }
}
