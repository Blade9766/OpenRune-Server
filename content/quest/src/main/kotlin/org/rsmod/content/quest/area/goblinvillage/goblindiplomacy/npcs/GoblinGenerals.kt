package org.rsmod.content.quest.area.goblinvillage.goblindiplomacy.npcs

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MesAnimType
import dev.openrune.types.NpcMode
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.goblinvillage.goblindiplomacy.GoblinDiplomacyQuest
import org.rsmod.content.quest.area.goblinvillage.goblindiplomacy.GoblinDiplomacyQuest.Companion.BLUE_MAIL
import org.rsmod.content.quest.area.goblinvillage.goblindiplomacy.GoblinDiplomacyQuest.Companion.GOBLIN_MAIL
import org.rsmod.content.quest.area.goblinvillage.goblindiplomacy.GoblinDiplomacyQuest.Companion.GRUBFOOT_BLUE
import org.rsmod.content.quest.area.goblinvillage.goblindiplomacy.GoblinDiplomacyQuest.Companion.GRUBFOOT_BROWN
import org.rsmod.content.quest.area.goblinvillage.goblindiplomacy.GoblinDiplomacyQuest.Companion.GRUBFOOT_HIDDEN
import org.rsmod.content.quest.area.goblinvillage.goblindiplomacy.GoblinDiplomacyQuest.Companion.GRUBFOOT_ORANGE
import org.rsmod.content.quest.area.goblinvillage.goblindiplomacy.GoblinDiplomacyQuest.Companion.ORANGE_MAIL
import org.rsmod.content.quest.area.goblinvillage.goblindiplomacy.GoblinDiplomacyQuest.Companion.colourName
import org.rsmod.content.quest.area.varrock.demonslayer.fadeFromBlack
import org.rsmod.content.quest.area.varrock.demonslayer.fadeToBlack
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest
import org.rsmod.game.entity.Npc
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Generals Wartface and Bentnoze in their hut in Goblin Village. Talking to either gets both,
 * since they never stop arguing long enough for one to answer alone. Using goblin mail on
 * either has Grubfoot model it in the changing room while the camera watches.
 */
class GoblinGenerals
@Inject
constructor(
    private val goblinDiplomacy: GoblinDiplomacyQuest,
    private val dragonSlayer: DragonSlayerQuest,
    private val random: GameRandom,
    private val search: NpcSearch,
    private val collision: CollisionFlagMap,
) : PluginScript() {

    private val quest
        get() = goblinDiplomacy.quest

    private val mailById = TRY_ON_MAIL.associateBy { it.asRSCM(RSCMType.OBJ) }
    private val wartfaceId = WARTFACE.asRSCM(RSCMType.NPC)

    override fun ScriptContext.startup() {
        for (general in listOf(WARTFACE, BENTNOZE)) {
            onOpNpc1(general) { startDialogue(it.npc) { generals() } }
            onOpNpcU(general) { showArmour(it.npc, mailById[it.objType.id]) }
        }
    }

    /* Speakers */

    private suspend fun Dialogue.wart(mood: MesAnimType, text: String) =
        chatNpcSpecific("General Wartface", WARTFACE, mood, text)

    private suspend fun Dialogue.bent(mood: MesAnimType, text: String) =
        chatNpcSpecific("General Bentnoze", BENTNOZE, mood, text)

    private suspend fun Dialogue.grub(mood: MesAnimType, text: String) =
        chatNpcSpecific("Grubfoot", Grubfoot.GRUBFOOT_HEAD, mood, text)

    /* Talk-to */

    private suspend fun Dialogue.generals() {
        if (seekingLozarsMap() && askAboutMap()) {
            return
        }
        if (quest.isQuestCompleted(player)) {
            afterQuest()
            return
        }
        when (random.of(1..5)) {
            1 -> {
                bent(angry, "All goblins should wear red armour!")
                wart(angry, "Not red! Red armour make you look fat.")
                bent(angry, "Everything make YOU look fat!")
                wart(angry, "Shut up!")
                bent(laugh, "Fatty!")
                wart(angry, "SHUT UP!")
                bent(laugh, "Even this human think you look fat! Don't you, human?")
                chatPlayer(confused, "Um...")
                when (
                    choice3(
                        "Yes, he looks fat.", 1,
                        "No, he doesn't look fat.", 2,
                        "I'll leave you to it.", 3,
                    )
                ) {
                    1 -> {
                        chatPlayer(laugh, "Yes, he looks fat!")
                        bent(laugh, "Ha ha! See, fatty? Even human think you fat!")
                        wart(angry, "Me not care what human think! Human ugly!")
                    }
                    2 -> {
                        chatPlayer(neutral, "No, he doesn't look fat.")
                        bent(angry, "Shut up human! Wartface fat and human stupid!")
                        wart(angry, "Shut up Bentnoze!")
                    }
                    3 -> {
                        chatPlayer(neutral, "I'll leave you to it.")
                        return
                    }
                }
            }
            2 -> {
                wart(angry, "I tell all goblins in village to wear green armour now!")
                bent(angry, "They not listen to you! I already tell them wear red armour!")
                wart(angry, "They listen to me not you! They know me bigger general!")
                bent(angry, "Me bigger general! They listen to me!")
                wart(quiz, "Human! What colour armour they wearing out there?")
                chatPlayer(neutral, "Half of them are wearing red and half of them green.")
                wart(angry, "Shut up human! They wearing green armour really! Human lying because he scared of you!")
                bent(happy, "Human scared of me not you? Then you think me bigger general!")
                wart(confused, "What? Me mean...")
                wart(angry, "Shut up! Me bigger general!")
            }
            3 -> {
                wart(angry, "We should wear green armour!")
                bent(angry, "Green armour? Are you stupid?")
                wart(angry, "You stupid! Only stupid goblins think red armour better!")
                bent(angry, "No they don't! Me think red armour better!")
                wart(angry, "That because you stupid!")
                bent(angry, "Me not stupid!")
                wart(angry, "Then why you not like green armour?")
                bent(angry, "Because red armour better!")
                wart(angry, "Only stupid goblins think that! You stupid!")
            }
            4 -> {
                wart(angry, "Green armour best.")
                bent(angry, "No no red every time.")
                wart(bored, "Go away human, we busy.")
            }
            else -> {
                bent(angry, "Red armour best.")
                wart(angry, "No it has to be green!")
                bent(bored, "Go away human, we busy.")
            }
        }
        options()
    }

    /* Dragon Slayer: Lozar's map piece */

    private fun Dialogue.seekingLozarsMap(): Boolean =
        dragonSlayer.stage(player) in DragonSlayerQuest.STAGE_BRIEFED..DragonSlayerQuest.STAGE_SHIP_REPAIRED &&
            !dragonSlayer.hasMapPiece(player, DragonSlayerQuest.MAP_PART_LOZAR)

    /** Returns true when the player asked about the map and the conversation is over. */
    private suspend fun Dialogue.askAboutMap(): Boolean {
        val asked =
            choice2(
                "I've heard that one of your number has got hold of part of a map.", 1,
                "So how is life for the goblins?", 2,
            )
        if (asked != 1) {
            chatPlayer(quiz, "So how is life for the goblins?")
            return false
        }
        chatPlayer(quiz, "I've heard that one of your number has got hold of part of a map.")
        bent(neutral, "Aha, that'd be Wormbrain.")
        chatPlayer(quiz, "Where would he be?")
        bent(laugh, "Wormbrain steals too much. He got caught. Now he lives in Port Sarim town jail.")
        dragonSlayer.askedGenerals.set(player, true)
        dragonSlayer.syncVars(player)
        return true
    }

    /** The main menu; every branch that says "previous" comes back here. */
    private suspend fun Dialogue.options() {
        while (true) {
            when (
                choice4(
                    "Why are you arguing about the colour of your armour?", 1,
                    "Wouldn't you prefer peace?", 2,
                    "Do you want me to pick an armour colour for you?", 3,
                    "I'll leave you to it.", 4,
                )
            ) {
                1 -> {
                    whyArguing()
                    return
                }
                2 -> peace()
                3 -> {
                    pickColour()
                    return
                }
                4 -> {
                    chatPlayer(neutral, "I'll leave you to it.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.whyArguing() {
        chatPlayer(quiz, "Why are you arguing about the colour of your armour?")
        bent(neutral, "We decide to celebrate goblin new century by changing colour of our armour. Brown get boring after a bit. We want change.")
        wart(neutral, "Problem is, we can't agree on new colour. We think maybe we use old goblin tribe colours, but Bentnoze from rubbish Thorobshuun tribe and me from much better Garagorshuun tribe.")
        bent(angry, "Thorobshuun tribe colour is red! Way better than silly green of Garagorshuun tribe.")
        wart(angry, "Shut up!")
        while (true) {
            when (
                choice4(
                    "What is the goblin new century?", 1,
                    "Wouldn't you prefer peace?", 2,
                    "Do you want me to pick an armour colour for you?", 3,
                    "I'll leave you to it.", 4,
                )
            ) {
                1 -> {
                    chatPlayer(quiz, "What is the goblin new century?")
                    bent(neutral, "Goblin century mark year of battle on Plain of Mud. That when Big High War God give goblin commandments.")
                    chatPlayer(quiz, "Big High War God?")
                    wart(neutral, "Big High War God is god for goblins. He take us and make us strong, not stupid like humans.")
                    bent(happy, "Me bet Big High War God would pick red armour.")
                    wart(angry, "Shut up! Big High War God would hate you!")
                }
                2 -> peace()
                3 -> {
                    pickColour()
                    return
                }
                4 -> {
                    chatPlayer(neutral, "I'll leave you to it.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.peace() {
        chatPlayer(quiz, "Wouldn't you prefer peace?")
        wart(neutral, "Goblins not so good at peace. Maybe peace be okay though, as long as it peace wearing green armour.")
        bent(angry, "But green too much like skin. Nearly make you look naked!")
    }

    private suspend fun Dialogue.pickColour() {
        chatPlayer(quiz, "Do you want me to pick an armour colour for you?")
        wart(happy, "Yes, as long as you pick green.")
        bent(angry, "No you have to pick red!")
        while (true) {
            when (
                choice4(
                    "You should wear red.", 1,
                    "You should wear green.", 2,
                    "What about a different colour?", 3,
                    "I'll leave you to it.", 4,
                )
            ) {
                1 -> {
                    chatPlayer(neutral, "You should wear red.")
                    bent(happy, "See? Even stupid human think red best. Now we all wear red!")
                    wart(angry, "Human not know anything! If we wear red then whole village be ugly like YOU!")
                    bent(angry, "Go away human. You not helping.")
                }
                2 -> {
                    chatPlayer(neutral, "You should wear green.")
                    wart(happy, "Green! We all wear green now, human has decided!")
                    bent(angry, "Why we have to do what human say? He not boss of us!")
                    wart(angry, "No but he agree with me!")
                    bent(angry, "That prove you a filthy human-lover!")
                    wart(angry, "Me hate humans! This human just happen to be right!")
                    bent(angry, "Go away human. You not know anything.")
                }
                3 -> {
                    differentColour()
                    return
                }
                4 -> {
                    chatPlayer(neutral, "I'll leave you to it.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.differentColour() {
        chatPlayer(quiz, "What about a different colour?")
        bent(confused, "That would mean me wrong... but at least Wartface not right!")
        wart(neutral, "Well Bentnoze never been right in his life. Still, maybe new colour good, but will have to see armour before decide.")
        bent(happy, "Human! You bring us armour in new colour!")
        if (quest.isQuestInProgress(player)) {
            val wanted = goblinDiplomacy.wantedMail(player) ?: return
            wart(neutral, "Yep, bring us ${colourName(wanted)} armour.")
            armourHelp()
            return
        }
        when (
            choice2(
                "Yes.", 1,
                "No.", 2,
                title = "Start the Goblin Diplomacy quest?",
            )
        ) {
            1 -> {
                chatPlayer(happy, "I can do that.")
                wart(quiz, "What colour we try?")
                bent(neutral, "Orange armour might be good.")
                wart(neutral, "Yep, bring us orange armour.")
                quest.advanceQuestStage(access)
                goblinDiplomacy.syncVars(player)
                chatPlayer(quiz, "How am I meant to get orange armour?")
                bent(neutral, "Well first you get goblin armour...")
                wart(neutral, "...and then you dye it orange!")
                bent(laugh, "Even human should be able to work that out!")
                armourHelp()
            }
            2 -> chatPlayer(neutral, "Actually, I think I'll leave you to it.")
        }
    }

    private suspend fun Dialogue.armourHelp() {
        while (true) {
            when (
                choice3(
                    "Where do I get goblin armour?", 1,
                    "Where do I get dye?", 2,
                    "Okay, I'll be back soon.", 3,
                )
            ) {
                1 -> {
                    chatPlayer(quiz, "Where do I get goblin armour?")
                    wart(neutral, "There some spare armour around village somewhere. You can take that.")
                    bent(confused, "It in crates somewhere. Can't remember which crates now.")
                    goblinDiplomacy.knowAboutArmour.set(player, true)
                    goblinDiplomacy.syncVars(player)
                }
                2 -> {
                    chatPlayer(quiz, "Where do I get dye?")
                    bent(happy, "You go north of here into wilderness. There you find many ways to die!")
                    chatPlayer(neutral, "No, D-Y-E, not D-I-E.")
                    wart(laugh, "Stupid Bentnoze, you not know how to spell!")
                    bent(angry, "Shut up Wartface!")
                    chatPlayer(quiz, "Do you know where I can get dye?")
                    bent(confused, "Me not know where dye come from.")
                    chatPlayer(quiz, "Well where did you get your red and green dye from?")
                    wart(neutral, "Some goblin or other, he steal it. Say he steal it from old witch in Draynor Village.")
                    bent(neutral, "Maybe you can get more dye from her?")
                    goblinDiplomacy.knowAboutDye.set(player, true)
                    goblinDiplomacy.syncVars(player)
                }
                3 -> {
                    chatPlayer(happy, "Okay, I'll be back soon.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.afterQuest() {
        when (random.of(1..5)) {
            1 -> {
                bent(angry, "Shut up Grubfoot!")
                grub(confused, "But me not say anything!")
                wart(angry, "Shut up Grubfoot!")
                bent(angry, "Shut up Wartface!")
                wart(angry, "Shut up Bentnoze!")
                grub(angry, "Shut up generals!")
                bent(angry, "Shut up Grubfoot!")
                wart(angry, "Shut up Grubfoot!")
                chatPlayer(angry, "Shut up goblins!")
            }
            2 -> {
                bent(quiz, "What we do now Wartface?")
                wart(confused, "Me dunno... maybe we change armour colour again.")
                bent(quiz, "Human, what colour you think best?")
                chatPlayer(neutral, "What about brown?")
                wart(happy, "Yeah brown is nice.")
                bent(happy, "Thanks human.")
            }
            3 -> {
                bent(neutral, "Our soldiers need better weapons. We give them all warhammers!")
                wart(angry, "No no give them battleaxes!")
                bent(angry, "Axes stupid! They things you cut wood with!")
                wart(angry, "But hammers things you put nails in wood with! At least axes sharp!")
                bent(angry, "You not need sharp when you have strong goblin swinging big lump of iron!")
                chatPlayer(quiz, "Do you want me to...")
                wart(angry, "Shut up human, we sort this one out ourselves!")
            }
            4 -> {
                wart(neutral, "Me tell Mudknuckles make worm stew for dinner.")
                bent(angry, "Me not like worm stew! Tell him make beetle pie!")
                wart(angry, "Beetle pie give you wind! You smell bad enough already!")
            }
            else -> {
                wart(neutral, "Now you've solved our argument, we gotta think of something else to do.")
                bent(bored, "Yep, we bored now.")
            }
        }
    }

    /* Use armour on a general */

    private suspend fun ProtectedAccess.showArmour(general: Npc, mail: String?) {
        arriveDelay()
        npcPlayerFaceClose(general)
        val wanted = goblinDiplomacy.wantedMail(player)
        if (wanted == null) {
            startDialogue(general) {
                chatPlayer(quiz, "What do you think of this colour?")
                bent(bored, "Go away human, we busy.")
            }
            return
        }
        if (mail != wanted) {
            startDialogue(general) {
                chatPlayer(quiz, "What do you think of this colour?")
                bent(angry, "That wrong colour.")
                wart(angry, "We tell you get ${colourName(wanted)} armour.")
            }
            return
        }
        startDialogue(general) { chatPlayer(happy, "I have some ${colourName(mail)} armour here.") }
        tryOn(general, mail)
    }

    /**
     * The changing-room scene, staged the way the original frames it: the generals in the
     * foreground with their backs to the camera, Grubfoot in front of the table, the player off
     * to the right. Grubfoot is a multi-npc driven by a varbit, so his outfit change is a varbit
     * flip while he is behind the curtain; the walk to and from it is real movement.
     *
     * Any click cancels a script, so the armour is only taken once the generals have delivered
     * their verdict, and the `finally` puts the hut back exactly as it was if the scene is
     * interrupted: the player simply shows the armour again.
     */
    private suspend fun ProtectedAccess.tryOn(general: Npc, mail: String) {
        val wartface = if (general.id == wartfaceId) general else find(general, WARTFACE)
        val bentnoze = if (general.id == wartfaceId) find(general, BENTNOZE) else general
        val grubfoot = find(general, Grubfoot.GRUBFOOT)
        val cast = listOfNotNull(wartface, bentnoze, grubfoot)
        val homes = cast.associateWith { it.coords }
        val colour =
            when (mail) {
                ORANGE_MAIL -> GRUBFOOT_ORANGE
                BLUE_MAIL -> GRUBFOOT_BLUE
                else -> GRUBFOOT_BROWN
            }
        var finished = false

        fadeToBlack()
        try {
            hideEntityOps()
            minimapHideMap()
            for (npc in cast) {
                npc.mode = NpcMode.None
            }
            // Everyone takes their mark while the screen is dark.
            wartface?.place(WARTFACE_MARK, Direction.North)
            bentnoze?.place(BENTNOZE_MARK, Direction.North)
            grubfoot?.place(GRUBFOOT_MARK, Direction.South)
            telejump(PLAYER_MARK)
            faceDirection(Direction.West)
            camMoveTo(CAMERA_FROM, height = CAMERA_HEIGHT, rate = 100, rate2 = 100)
            camLookAt(CAMERA_AT, height = CAMERA_LOOK_HEIGHT, rate = 100, rate2 = 100)
            delay(1)
            fadeFromBlack()
            closeFadeOverlay()

            startDialogue {
                wart(angry, "Grubfoot!")
                grub(happy, "Yes General Wartface?")
                wart(angry, "Put on this armour!")
            }
            if (grubfoot != null) {
                grubfoot.clearFacingLock()
                grubfoot.walk(CURTAIN_FRONT)
                delay(WALK_TICKS)
                goblinDiplomacy.showGrubfoot(player, GRUBFOOT_HIDDEN)
                delay(CHANGE_TICKS)
                goblinDiplomacy.showGrubfoot(player, colour)
                grubfoot.walk(GRUBFOOT_MARK)
                delay(WALK_TICKS)
                grubfoot.lockFacingDirection(Direction.South)
            } else {
                goblinDiplomacy.showGrubfoot(player, colour)
            }
            startDialogue { verdict(mail) }
            finished = true
        } finally {
            camReset()
            showEntityOps()
            minimapReset()
            for (npc in cast) {
                npc.clearFacingLock()
                homes[npc]?.let { npc.teleport(collision, it) }
                npc.mode = npc.defaultMode
            }
            if (!finished) {
                // Interrupted mid-scene: Grubfoot goes back to whatever he was wearing.
                goblinDiplomacy.syncVars(player)
            }
        }

        if (invDel(inv, mail).failure) {
            return
        }
        goblinDiplomacy.grubfootColour.set(player, colour)
        if (mail == GOBLIN_MAIL) {
            val remaining = quest.maxSteps - goblinDiplomacy.stage(player)
            quest.advanceQuestStage(this, remaining)
        } else {
            quest.advanceQuestStage(this)
        }
        goblinDiplomacy.syncVars(player)
    }

    private fun Npc.place(mark: CoordGrid, facing: Direction) {
        teleport(collision, mark)
        lockFacingDirection(facing)
    }

    private suspend fun Dialogue.verdict(mail: String) {
        grub(quiz, "What do you think?")
        when (mail) {
            ORANGE_MAIL -> {
                wart(bored, "No I don't like that much.")
                bent(bored, "It clashes with skin colour.")
                wart(neutral, "We need darker colour, like blue.")
                bent(neutral, "Yeah blue might be good.")
                wart(angry, "Human! Get us blue armour!")
            }
            BLUE_MAIL -> {
                bent(bored, "That not right. Not goblin colour at all.")
                wart(neutral, "Goblins wear dark earthy colours like brown.")
                bent(neutral, "Yeah brown might be good.")
                wart(angry, "Human! Get us brown armour!")
                chatPlayer(confused, "But I thought brown was the armour you were changing from...")
                chatPlayer(neutral, "Never mind, anything is worth a try.")
            }
            else -> {
                wart(happy, "That colour quite nice. Me can see myself wearing that.")
                bent(happy, "It a deal then. Brown armour it is.")
                wart(happy, "Thank you for sorting out argument, human. You have reward now.")
                chatPlayer(confused, "Thanks... I think...")
            }
        }
    }

    private fun find(near: Npc, type: String): Npc? =
        search.find(near.coords, type, SEARCH_RADIUS, HuntVis.Off)

    companion object {
        const val WARTFACE = "npc.general_wartface_green"
        const val BENTNOZE = "npc.general_bentnoze_red"

        /** The mails the generals will pass judgement on. */
        val TRY_ON_MAIL =
            listOf(
                GOBLIN_MAIL,
                ORANGE_MAIL,
                BLUE_MAIL,
                "obj.goblin_armour_red",
                "obj.goblin_armour_yellow",
                "obj.goblin_armour_green",
                "obj.goblin_armour_purple",
            )

        private const val SEARCH_RADIUS = 12

        /* Marks for the scene: generals in front, Grubfoot before the table, player to the right. */
        private val WARTFACE_MARK = CoordGrid(2956, 3510, 0)
        private val BENTNOZE_MARK = CoordGrid(2957, 3510, 0)
        private val GRUBFOOT_MARK = CoordGrid(2958, 3512, 0)
        private val PLAYER_MARK = CoordGrid(2960, 3510, 0)

        /** The tile in front of the changing curtain on the hut's west wall. */
        private val CURTAIN_FRONT = CoordGrid(2954, 3511, 0)

        /** Looking north over the generals' shoulders from the south side of the hut. */
        private val CAMERA_FROM = CoordGrid(2958, 3506, 0)
        private val CAMERA_AT = CoordGrid(2958, 3511, 0)
        private const val CAMERA_HEIGHT = 500
        private const val CAMERA_LOOK_HEIGHT = 100

        private const val WALK_TICKS = 5
        private const val CHANGE_TICKS = 3
    }
}
