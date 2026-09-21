package org.rsmod.content.quest.area.feldip.bigchompy

import jakarta.inject.Inject
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.HintArrows
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onNpcQueueWithArgs
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpHeld5
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.BELLOWS_EMPTY
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.BELLOWS_FULL
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.BELLOWS_ONE
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.BELLOWS_SEQ
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.BELLOWS_SOUND
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.BELLOWS_SPOT
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.BELLOWS_SUCK_SOUND
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.BELLOWS_TWO
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.BLOATED_TOAD
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.MAX_BLOATED_TOADS
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.PICK_UP_SEQ
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.STAGE_DROPPED_TOAD
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.STAGE_SHOWN_TOAD
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.SWAMP_BUBBLES
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.SWAMP_BUBBLES_DARK
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.SWAMP_TOAD
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.TOAD_BURST_SOUND
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.TOAD_BURST_SPOT
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.TOAD_HISS_SOUND
import org.rsmod.content.quest.area.feldip.bigchompy.BigChompyBirdHuntingQuest.Companion.TOAD_INFLATE_SEQ
import org.rsmod.content.quest.area.feldip.bigchompy.ChompyHunt.Companion.BAIT_ROLL_CHANCE
import org.rsmod.content.quest.area.feldip.bigchompy.ChompyHunt.Companion.BAIT_ROLL_CYCLES
import org.rsmod.content.quest.area.feldip.bigchompy.ChompyHunt.Companion.BURST_ROLL
import org.rsmod.content.quest.area.feldip.bigchompy.ChompyHunt.Companion.BURST_ROLL_LATE
import org.rsmod.content.quest.area.feldip.bigchompy.ChompyHunt.Companion.CHOMPY_DRAWN
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Inflating swamp toads with the ogre bellows and using the results as chompy bait.
 *
 * A placed toad rolls once every [BAIT_ROLL_CYCLES] cycles. Until one of those rolls draws a bird
 * down the toad is only swelling, and the third roll bursts it over anyone standing close; once a
 * chompy has come the roll count carries [CHOMPY_DRAWN], so the same toad cannot call a second bird
 * and its burst is put off long enough for the player to take the shot.
 */
class BloatedToads
@Inject
constructor(
    private val quest: BigChompyBirdHuntingQuest,
    private val hunt: ChompyHunt,
    private val chompies: ChompyBirds,
    private val playerList: PlayerList,
    private val random: GameRandom,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLocU(SWAMP_BUBBLES) { fillBellows(it.objType.internalName) }
        onOpLocU(SWAMP_BUBBLES_DARK) { fillBellows(it.objType.internalName) }

        onOpNpc1(SWAMP_TOAD) { inflateWithCarriedBellows(it.npc) }
        onOpNpcU(SWAMP_TOAD) { useOnToad(it.npc, it.objType.internalName) }

        onOpHeld1(BLOATED_TOAD) { placeBait() }
        onOpHeld4(BLOATED_TOAD) { releaseAll() }
        onOpHeld5(BLOATED_TOAD) { releaseOne(it.slot) }

        onNpcQueueWithArgs<Int>(BAIT_ROLL_QUEUE) { rollForChompy(it.args ?: 0) }
    }

    private suspend fun ProtectedAccess.fillBellows(used: String) {
        arriveDelay()
        if (used == BELLOWS_FULL) {
            mes("These bellows are already full of swamp gas!")
            return
        }
        if (used !in EMPTYING_BELLOWS) {
            mes("Nothing interesting happens.")
            return
        }
        anim(BELLOWS_SEQ)
        soundSynth(BELLOWS_SUCK_SOUND)
        delay(1)
        if (invDel(inv, used).failure) {
            return
        }
        invAdd(inv, BELLOWS_FULL)
        mes("You collect some gas from the swamp.")
    }

    private suspend fun ProtectedAccess.useOnToad(toad: Npc, used: String) {
        when (used) {
            in FILLED_BELLOWS -> inflate(toad, used)
            BELLOWS_EMPTY -> blowAir(toad)
            else -> mes("Nothing interesting happens.")
        }
    }

    private suspend fun ProtectedAccess.inflateWithCarriedBellows(toad: Npc) {
        val bellows = FILLED_BELLOWS.firstOrNull { invTotal(inv, it) > 0 }
        if (bellows != null) {
            inflate(toad, bellows)
            return
        }
        if (invTotal(inv, BELLOWS_EMPTY) > 0) {
            blowAir(toad)
            return
        }
        mes("You need a pair of ogre bellows full of swamp gas to inflate this toad.")
    }

    private suspend fun ProtectedAccess.blowAir(toad: Npc) {
        arriveDelay()
        pumpBellows(toad)
        delay(1)
        toad.say("Hissss....")
        soundSynth(TOAD_HISS_SOUND)
        delay(1)
        mes("The air seems too thin to stay in the toad.")
        mes("Perhaps you need something thicker than air.")
    }

    private suspend fun ProtectedAccess.inflate(toad: Npc, bellows: String) {
        arriveDelay()
        if (inv.freeSpace() < 1) {
            mes("You don't have space to carry that.")
            return
        }
        if (invTotal(inv, BLOATED_TOAD) >= MAX_BLOATED_TOADS) {
            mes("One of your bloated toads manages to escape.")
            hunt.releaseSwampToad(player, coords)?.say("Ribbitt!")
            invDel(inv, BLOATED_TOAD, 1)
            delay(1)
        }
        mes("You manage to catch the toad and inflate it with the swamp gas.")
        pumpBellows(toad)
        delay(3)
        if (!drainBellows(bellows)) {
            return
        }
        mes("You add the bloated toad to your inventory.")
        invAdd(inv, BLOATED_TOAD)
        hunt.remove(toad)
    }

    private fun ProtectedAccess.pumpBellows(toad: Npc) {
        say("Come here toady!")
        toad.facePlayer(player)
        anim(BELLOWS_SEQ)
        soundSynth(BELLOWS_SOUND)
        spotanim(BELLOWS_SPOT, height = BELLOWS_SPOT_HEIGHT)
        toad.anim(TOAD_INFLATE_SEQ)
    }

    /** Swaps a filled pair of bellows for the next one down; false if the player lost them. */
    private fun ProtectedAccess.drainBellows(bellows: String): Boolean {
        val next =
            when (bellows) {
                BELLOWS_FULL -> BELLOWS_TWO
                BELLOWS_TWO -> BELLOWS_ONE
                else -> BELLOWS_EMPTY
            }
        if (invDel(inv, bellows).failure) {
            return false
        }
        invAdd(inv, next)
        return true
    }

    private suspend fun ProtectedAccess.placeBait() {
        if (quest.stage(player) < STAGE_SHOWN_TOAD) {
            objbox(
                BLOATED_TOAD,
                OBJBOX_ZOOM,
                "You're not sure where Rantz told you to place the bloated toad. You decide to " +
                    "wait and ask him where to place it.",
            )
            return
        }
        if (!canBaitHere()) {
            return
        }
        if (hunt.baitNear(coords, radius = 0) != null) {
            mes("There is a bloated toad already placed at this location.")
            return
        }
        anim(PICK_UP_SEQ)
        if (invDel(inv, BLOATED_TOAD).failure) {
            return
        }
        quest.advanceTo(this, STAGE_DROPPED_TOAD)
        val toad = hunt.placeBait(player, coords) ?: return
        toad.queue(BAIT_ROLL_QUEUE, BAIT_ROLL_CYCLES, 0)
        mes("You carefully place the bloated toad bait.")
        HintArrows.hintStop(player)
    }

    private fun ProtectedAccess.canBaitHere(): Boolean {
        val inClearing = hunt.inRantzClearing(coords)
        if (quest.stage(player) < STAGE_COMPLETE) {
            if (!inClearing) {
                mes("This is too far away for Rantz to shoot the chompy bird.")
                return false
            }
            return true
        }
        if (inClearing) {
            mes("Rantz doesn't like it when you chompy hunt on his turf.")
            mes("But you can hunt for chompys in the rest of the ogre area.")
            return false
        }
        if (!inHuntingGrounds()) {
            mes("You won't attract a chompy bird this far away from the ogre area.")
            return false
        }
        return true
    }

    private fun ProtectedAccess.inHuntingGrounds(): Boolean =
        coords.level == 0 &&
            coords.x in HUNTING_GROUND_WEST..HUNTING_GROUND_EAST &&
            coords.z in HUNTING_GROUND_SOUTH..HUNTING_GROUND_NORTH

    private suspend fun ProtectedAccess.releaseAll() {
        val count = invTotal(inv, BLOATED_TOAD)
        if (invDel(inv, BLOATED_TOAD, count).failure) {
            return
        }
        repeat(count) { hop() }
        if (random.randomBoolean(SAY_CHANCE)) {
            say(if (count == 1) "Free the fatsy toady!" else "Free the fatsy toadies!")
        }
        if (count == 1) {
            mes("You release the toad and see it hop off into the distance.")
        } else {
            mes("You release the toads and see them hop off into the distance.")
        }
    }

    private suspend fun ProtectedAccess.releaseOne(slot: Int) {
        if (invDel(inv, BLOATED_TOAD, 1, slot = slot).failure) {
            return
        }
        hop()
        if (random.randomBoolean(SAY_CHANCE)) {
            say("Free the fatsy toady!")
        }
        mes("You release the toad and see it hop off into the distance.")
    }

    private fun ProtectedAccess.hop() {
        val toad = hunt.releaseSwampToad(player, coords) ?: return
        when (random.of(maxExclusive = 3)) {
            0 -> toad.say("Croak!")
            1 -> toad.say("Ribbet!")
        }
    }

    private fun StandardNpcAccess.rollForChompy(rolls: Int) {
        val owner = hunt.ownerOf(npc)
        if (owner == null) {
            hunt.remove(npc)
            return
        }
        var next = rolls + 1
        if (rolls < CHOMPY_DRAWN && random.of(maxExclusive = BAIT_ROLL_CHANCE) == 0) {
            next += CHOMPY_DRAWN
            chompies.land(owner, npc)
        }
        if (rolls == BURST_ROLL || rolls == BURST_ROLL_LATE) {
            burst(npc)
            return
        }
        npc.queue(BAIT_ROLL_QUEUE, BAIT_ROLL_CYCLES, next)
    }

    /** A toad left to swell too long pops, spattering everyone on or beside its tile. */
    private fun burst(toad: Npc) {
        val coords = toad.coords
        toad.spotanim(TOAD_BURST_SPOT)
        hunt.remove(toad)
        for (player in nearbyPlayers(coords)) {
            player.mes("You're hit by bits of exploding toad!")
            player.soundSynth(TOAD_BURST_SOUND)
            player.queueHit(
                delay = 0,
                type = HitType.Typeless,
                damage = random.of(BURST_DAMAGE_MIN, BURST_DAMAGE_MAX),
                modifier = NoopPlayerHitModifier,
            )
        }
    }

    private fun nearbyPlayers(coords: CoordGrid): List<Player> =
        playerList.filter { it.coords.chebyshevDistance(coords) <= BURST_RADIUS }

    private companion object {
        const val BAIT_ROLL_QUEUE = "queue.chompy_bait_roll"

        val FILLED_BELLOWS = listOf(BELLOWS_FULL, BELLOWS_TWO, BELLOWS_ONE)
        val EMPTYING_BELLOWS = listOf(BELLOWS_EMPTY, BELLOWS_ONE, BELLOWS_TWO)

        const val BELLOWS_SPOT_HEIGHT = 64
        const val OBJBOX_ZOOM = 250

        /** One in this many releases makes the player shout about it. */
        const val SAY_CHANCE = 6

        const val BURST_RADIUS = 1
        const val BURST_DAMAGE_MIN = 1
        const val BURST_DAMAGE_MAX = 2

        /** The three Feldip map squares a chompy hunter may bait once the quest is over. */
        const val HUNTING_GROUND_WEST = 2432
        const val HUNTING_GROUND_EAST = 2687
        const val HUNTING_GROUND_SOUTH = 2880
        const val HUNTING_GROUND_NORTH = 2943
    }
}
