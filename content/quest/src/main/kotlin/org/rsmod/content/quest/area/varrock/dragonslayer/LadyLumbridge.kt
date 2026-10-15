package org.rsmod.content.quest.area.varrock.dragonslayer

import dev.openrune.types.MesAnimType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.COINS
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.NAILS_PER_PLANK
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.REPAIR_PLANKS
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.SHIP_PRICE
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.STAGE_BRIEFED
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.STAGE_NED_ABOARD
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.STAGE_ON_CRANDOR
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.STAGE_SHIP_BOUGHT
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.STAGE_SHIP_REPAIRED
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Lady Lumbridge at the south end of Port Sarim's docks: Klarense who sells her, Cabin Boy
 * Jenkins who comes with her, the gangplank and hold ladder, and the hole in the hull that must
 * be patched with three planks, ninety steel nails and a hammer before she can sail.
 *
 * The hull hole is a multiloc on `varbit.dragonslayer_ship_fullyfixed`, which
 * [DragonSlayerQuest.syncVars] keeps in step with the repair count.
 */
class LadyLumbridge
@Inject
constructor(
    private val dragonSlayer: DragonSlayerQuest,
    private val random: GameRandom,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(KLARENSE) { startDialogue(it.npc) { klarense() } }
        onOpNpc1(JENKINS) { startDialogue(it.npc) { jenkins() } }

        onOpLoc1(GANGPLANK_ON) { board() }
        onOpLoc1(GANGPLANK_OFF) { disembark() }
        onOpLoc1(DECK_LADDER) { climbLadder(HOLD_ARRIVAL) }
        onOpLoc1(HOLD_LADDER) { climbLadder(DECK_ARRIVAL) }

        for (hole in listOf(HOLE, HOLE_MULTI)) {
            onOpLoc1(hole) { repair() }
            onOpLocU(hole, PLANK) { repair() }
        }
    }

    /* Boarding */

    private suspend fun ProtectedAccess.board() {
        arriveDelay()
        if (!dragonSlayer.shipBought(player)) {
            startDialogue { chatNpcSpecific("Klarense", KLARENSE, angry, "Hey, stay off my ship! That's private property!") }
            return
        }
        delay(1)
        telejump(DECK_ARRIVAL)
    }

    private suspend fun ProtectedAccess.disembark() {
        arriveDelay()
        delay(1)
        telejump(DOCK_ARRIVAL)
    }

    private suspend fun ProtectedAccess.climbLadder(dest: CoordGrid) {
        arriveDelay()
        anim(CLIMB_SEQ)
        delay(1)
        telejump(dest)
    }

    /* Repairs */

    private suspend fun ProtectedAccess.repair() {
        if (!dragonSlayer.shipBought(player)) {
            mesbox("This isn't your ship. Klarense wouldn't thank you for hammering planks into her.")
            return
        }
        val planks = dragonSlayer.repairStage.get(player)
        if (planks >= REPAIR_PLANKS) {
            mes("You have already patched the hole in the ship.")
            return
        }
        if (!inv.contains(PLANK)) {
            mesbox("You'll need to use wooden planks on this hole to patch it up.")
            return
        }
        if (inv.count(NAILS) < NAILS_PER_PLANK) {
            mesbox("You need $NAILS_PER_PLANK steel nails to attach the plank with.")
            return
        }
        if (!inv.contains(HAMMER)) {
            mesbox("You need a hammer to force the nails in with.")
            return
        }
        anim(HAMMER_SEQ)
        soundSynth(HAMMER_SOUND)
        delay(2)
        invDel(inv, PLANK, 1)
        invDel(inv, NAILS, NAILS_PER_PLANK)
        val done = planks + 1
        dragonSlayer.repairStage.set(player, done)
        dragonSlayer.syncVars(player)
        when (done) {
            1 -> mesbox("You nail a plank over the hole, but you still need more planks to close the hole completely.")
            2 -> mesbox("You nail a plank over the hole, but you still need one more plank to close the hole completely.")
            else -> {
                mesbox("You nail a final plank over the hole. You have successfully patched the hole in the ship.")
                dragonSlayer.setStage(this, STAGE_SHIP_REPAIRED)
            }
        }
    }

    /* Klarense */

    private suspend fun Dialogue.klarense() {
        val stage = dragonSlayer.stage(player)
        when {
            stage < STAGE_BRIEFED -> {
                chatNpc(happy, "Hello there! Interested in buying this fine ship? I'm sure I can offer her to you for a much better price than any of those shipwrights ask for!")
                chatPlayer(neutral, "I'm not really in the market for a ship right now.")
                chatNpc(neutral, "Well, if you ever are, you know where to find me!")
            }
            stage < STAGE_SHIP_BOUGHT -> sellingPitch()
            else -> captain(stage)
        }
    }

    private suspend fun Dialogue.sellingPitch() {
        chatNpc(happy, "Hello there! Interested in buying this fine ship? I'm sure I can offer her to you for a much better price than any of those shipwrights ask for!")
        chatPlayer(quiz, "A much better price? What's the catch?")
        chatNpc(worried, "Well, she's not quite seaworthy right now...")
        chatPlayer(quiz, "Not seaworthy? Why would I want to buy her, then?")
        chatNpc(happy, "Because this isn't just any ship! This is the Lady Lumbridge, one of the last Crandorian ships! Despite her size, she's one of the most agile ships around!")
        chatPlayer(neutral, "Except she's not seaworthy...")
        chatNpc(happy, "No, but a bit of work will quickly sort that out!")
        while (true) {
            when (
                choice3(
                    "I'd like to buy her.", 1,
                    "Why is she damaged?", 2,
                    "I think I'll pass.", 3,
                )
            ) {
                1 -> {
                    buyShip()
                    return
                }
                2 -> {
                    chatPlayer(quiz, "Why is she damaged?")
                    chatNpc(neutral, "Oh, there was no particular accident, just years of wear and tear. It's been a long time since Crandor was destroyed and she's had several owners since then. Unfortunately, not all of them looked after her.")
                }
                3 -> {
                    chatPlayer(neutral, "I think I'll pass.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.buyShip() {
        chatPlayer(happy, "I'd like to buy her.")
        chatNpc(happy, "Of course! I'm sure the work needed on her wouldn't be too expensive.")
        chatNpc(happy, "How does $SHIP_PRICE gold sound? I'll even throw in my cabin boy, Jenkins, for free! He'll swab the decks and splice the mainsails for you!")
        when (
            choice2(
                "Yep, sounds good.", 1,
                "I'm not paying that much for a broken boat!", 2,
            )
        ) {
            1 -> {
                chatPlayer(happy, "Yep, sounds good.")
                if (player.inv.count(COINS) < SHIP_PRICE) {
                    chatPlayer(sad, "...except I don't have that kind of money on me...")
                    chatNpc(neutral, "Then she'll be waiting here when you do.")
                    return
                }
                access.invDel(access.inv, COINS, SHIP_PRICE)
                dragonSlayer.setStage(access, STAGE_SHIP_BOUGHT)
                chatNpc(happy, "Okey-dokey! She's all yours!")
            }
            2 -> {
                chatPlayer(angry, "I'm not paying that much for a broken boat!")
                chatNpc(neutral, "That's fair enough, I suppose.")
            }
        }
    }

    private suspend fun Dialogue.captain(stage: Int) {
        if (stage >= STAGE_ON_CRANDOR) {
            chatNpc(shocked, "Wow! You sure are lucky! Seems the Lady Lumbridge just washed right back into the dock by herself!")
            chatNpc(worried, "She's pretty badly damaged, though...")
        } else {
            chatNpc(happy, "Hello, captain! Here to inspect your new ship? Just a little work and she'll be seaworthy again.")
        }
        while (true) {
            when (
                choice4(
                    "So, what needs fixing on the ship?", 1,
                    "Would you sail this ship to Crandor for me?", 2,
                    "What are you going to do now you don't have a ship?", 3,
                    "Can I board the ship now?", 4,
                )
            ) {
                1 -> {
                    chatPlayer(quiz, "So, what needs fixing on the ship?")
                    if (dragonSlayer.shipRepaired(player)) {
                        chatNpc(neutral, "I dunno. It looks fine to me.")
                        chatPlayer(confused, "But you said it needed fix-")
                        chatNpc(happy, "Yes, but that was before you fixed it!")
                    } else {
                        chatNpc(neutral, "Well, the big gaping hole in the hold is the problem. You'll need a few wooden planks hammered in with steel nails to fix it.")
                    }
                }
                2 -> {
                    chatPlayer(quiz, "So, would you like to sail this ship to Crandor for me?")
                    chatNpc(shocked, "Crandor? You're joking, right?")
                    when (
                        choice2(
                            "Yes. Ha ha ha!", 1,
                            "No. I want to go to Crandor.", 2,
                        )
                    ) {
                        1 -> {
                            chatPlayer(laugh, "Yes. Ha ha ha!")
                            chatNpc(angry, "Crandor's not something we sailors joke about. You can't sail from here to Catherby, or Entrana, or Ardougne without passing that accursed island.")
                            chatNpc(worried, "You can't get close because of the reefs, but you can always see it. Sometimes there's a dark shape circling in the sky above it. That's when you sail on as quick as you can and pray it isn't hungry.")
                        }
                        2 -> {
                            chatPlayer(neutral, "No. I want to go to Crandor.")
                            chatNpc(shocked, "Then you must be crazy.")
                            chatNpc(worried, "That island is surrounded by reefs that would rip this ship to shreds. Even with a map you'd need an experienced captain to stand a chance of getting through.")
                            chatNpc(worried, "And even if I could get you there, I'm not going any closer to that dragon than I have to. They say it can bite a ship in half.")
                        }
                    }
                }
                3 -> {
                    chatPlayer(quiz, "What are you going to do now you don't have a ship?")
                    chatNpc(happy, "Oh, I'll be fine. I've got work as Port Sarim's first lifeguard!")
                }
                4 -> {
                    chatPlayer(quiz, "Can I board the ship now?")
                    chatNpc(happy, "Sure thing, she's all yours.")
                    return
                }
            }
        }
    }

    /* Cabin Boy Jenkins */

    private suspend fun Dialogue.jenkins() {
        val stage = dragonSlayer.stage(player)
        if (stage < STAGE_SHIP_BOUGHT) {
            chatNpc(happy, "Ahoy! This 'ere's the Lady Lumbridge. If ye're wanting to buy 'er, Klarense is the man to see, down on the dock.")
            return
        }
        if (stage == STAGE_NED_ABOARD) {
            captainAndCabinBoy()
            return
        }
        chatNpc(happy, "Ahoy! What d'ye think of yer ship, then?")
        when (
            choice3(
                "I'm ready to go back to shore.", 1,
                "I'd like to inspect her some more.", 2,
                "Can you sail this ship to Crandor?", 3,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "I'm ready to go back to shore.")
                mesbox("You disembark from the ship.")
                access.telejump(DOCK_ARRIVAL)
            }
            2 -> {
                chatPlayer(neutral, "I'd like to inspect her some more.")
                chatNpc(happy, "Aye.")
            }
            3 -> {
                chatPlayer(quiz, "Can you sail this ship to Crandor?")
                chatNpc(worried, "Not me, sir! I'm just an 'umble cabin boy. You'll need a proper cap'n.")
                chatPlayer(quiz, "Where can I find a captain?")
                chatNpc(neutral, "The cap'ns round 'ere seem to be a mite scared of Crandor. I ask 'em why and they just say it was afore my time.")
                chatNpc(happy, "But there is one cap'n I reckon might 'elp. I 'eard there's a retired 'un in Draynor Village so desperate to sail again 'e'd take any job.")
                chatNpc(neutral, "I can't remember 'is name, but 'e lives in Draynor Village an' makes rope.")
            }
        }
    }

    private suspend fun Dialogue.captainAndCabinBoy() {
        when (random.of(1..5)) {
            1 -> {
                ned(happy, "Arr, Jim lad!")
                chatNpc(neutral, "It's Jenkins, cap'n!")
                ned(happy, "Arr, Jenkins lad!")
            }
            2 -> {
                ned(happy, "Shiver me timbers!")
                chatNpc(happy, "Aye aye, cap'n!")
            }
            3 -> {
                ned(happy, "Splice the mainsail!")
                chatNpc(happy, "Aye aye, cap'n!")
            }
            4 -> {
                ned(happy, "Swab the deck!")
                chatNpc(happy, "Aye aye, cap'n!")
            }
            else -> {
                ned(happy, "Weigh the anchor!")
                chatNpc(sad, "It's too heavy, cap'n!")
            }
        }
    }

    private suspend fun Dialogue.ned(mood: MesAnimType, text: String) =
        chatNpcSpecific("Captain Ned", NED_CAPTAIN, mood, text)

    companion object {
        const val KLARENSE = "npc.klarense"
        const val JENKINS = "npc.cabin_boy_jenkins"
        const val NED_CAPTAIN = "npc.ned_1op"

        const val GANGPLANK_ON = "loc.dragonshipgangplank_on"
        const val GANGPLANK_OFF = "loc.dragonshipgangplank_off"
        const val DECK_LADDER = "loc.dragonshipladdertop"
        const val HOLD_LADDER = "loc.dragonshipladder"
        const val HOLE = "loc.shiphole"
        const val HOLE_MULTI = "loc.dragonslayer_shiphole"

        const val PLANK = "obj.woodplank"
        const val NAILS = "obj.nails"
        const val HAMMER = "obj.hammer"

        const val CLIMB_SEQ = "seq.human_pickupfloor"
        const val HAMMER_SEQ = "seq.human_hammer_hit"
        const val HAMMER_SOUND = "synth.hammer_and_build"

        /** The dock end of the gangplank; the docks are bridged down to ground level. */
        val DOCK_ARRIVAL = CoordGrid(3047, 3204, 0)

        /** The deck is a level above the dock. */
        val DECK_ARRIVAL = CoordGrid(3047, 3207, 1)

        /** The hold is the single row of tiles under the deck, beside its ladder. */
        val HOLD_ARRIVAL = CoordGrid(3048, 3208, 0)

        /** Where the deck ladder comes up. */
        val DECK_LADDER_TILE = CoordGrid(3048, 3208, 1)
    }
}
