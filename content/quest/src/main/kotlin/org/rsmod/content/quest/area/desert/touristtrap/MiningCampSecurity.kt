package org.rsmod.content.quest.area.desert.touristtrap

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.content.quest.area.ardougne.fadeFromBlack
import org.rsmod.content.quest.area.ardougne.fadeToBlack
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.ANA_CARRIED
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.ANA_IN_A_BARREL
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.ANA_IN_MINE
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.CELL_BARS_VARBIT
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.CELL_DOOR_KEY
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.EMPTY_BARREL
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.METAL_KEY
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid

/**
 * What the Desert Mining Camp's guards do to people they catch: the search and the cell, and the
 * mercenaries' cart ride into the open desert.
 *
 * Nobody is punished once the quest is complete; by then the guards have given up on the player.
 */
@Singleton
class MiningCampSecurity
@Inject
constructor(private val quest: TouristTrapQuest, private val npcSearch: NpcSearch) {
    private val guardIds: Set<Int> by lazy {
        TouristTrapQuest.CAMP_GUARDS.mapTo(HashSet()) { it.asRSCM(RSCMType.NPC) }
    }

    private val mercenaryIds: Set<Int> by lazy {
        TouristTrapQuest.MERCENARIES.mapTo(HashSet()) { it.asRSCM(RSCMType.NPC) }
    }

    fun guardNear(coords: CoordGrid, distance: Int): Npc? =
        npcSearch.findAllAny(coords, distance, HuntVis.LineOfSight).firstOrNull { it.id in guardIds }

    fun mercenaryNear(coords: CoordGrid, distance: Int): Npc? =
        npcSearch.findAllAny(coords, distance, HuntVis.Off).firstOrNull { it.id in mercenaryIds }

    fun isGuard(npc: Npc): Boolean = npc.id in guardIds

    fun isExempt(player: Player): Boolean = quest.isComplete(player)

    /**
     * The guards' search and the cell: keys may be confiscated, Ana is dragged back to the mine
     * if she is in the player's pack, and the player ends up in the surface cell, or in the
     * punishment mine if they were caught underground past the cave guard.
     */
    suspend fun ProtectedAccess.throwInCell(guard: Npc? = null) {
        if (isExempt(player)) {
            return
        }
        ifClose()
        // The punishment pit opens beyond the pineapple guard, so nobody goes there before him.
        val underground =
            TouristTrapCoords.inMine(coords) &&
                quest.stage(player) >= TouristTrapQuest.STAGE_GIVEN_PINEAPPLE
        if (ANA_IN_A_BARREL in inv) {
            guard?.say("Hey, what's in this barrel?")
            delay(1)
            guard?.say("Right... we'll take that off your hands.")
            loseAna()
            delay(1)
            mes("The guards drag Ana off into the distance.")
            delay(2)
        }
        mes("The guards search you!")
        delay(1)
        if (CELL_DOOR_KEY in inv && random.of(CONFISCATE_ODDS) == 0) {
            invDel(inv, CELL_DOOR_KEY)
            mes("The guards find the cell door key and remove it!")
        }
        if (METAL_KEY in inv && random.of(CONFISCATE_ODDS) == 0) {
            invDel(inv, METAL_KEY)
            mes("The guards find the main gate key and remove it!")
        }
        delay(2)
        mes("You are roughed up by the guards and manhandled into a cell.")
        guard?.say("Into the cell you go! I hope this teaches you a lesson.")
        delay(1)
        setVarBit(player, CELL_BARS_VARBIT, 0)
        val cell = if (underground) TouristTrapCoords.UNDERGROUND_CELL else TouristTrapCoords.SURFACE_CELL
        telejump(cell, TeleportType.Exempt)
    }

    /** Ana goes back to her rock in the mine; the empty barrel she was in stays with the player. */
    fun ProtectedAccess.loseAna() {
        if (invDel(inv, ANA_IN_A_BARREL).success) {
            invAdd(inv, EMPTY_BARREL)
        }
        if (player.ttAnaLocation == ANA_CARRIED) {
            quest.moveAna(player, ANA_IN_MINE)
        }
    }

    /**
     * The guards catching someone in the camp or the mine who has no business there: the three
     * lines, then the search and the cell.
     */
    suspend fun ProtectedAccess.caughtInFancyClothes(guard: Npc?) {
        if (isExempt(player)) {
            return
        }
        ifClose()
        guard?.facePlayer(player)
        guardShouts(guard, "Hey, they're interesting clothes!")
        guardShouts(guard, "You're no slave.")
        guardShouts(guard, "What are you doing in here?")
        throwInCell(guard)
    }

    suspend fun ProtectedAccess.caughtArmed(guard: Npc?) {
        if (isExempt(player)) {
            return
        }
        val armed = player.wieldingWeapon()
        val armoured = player.wearingArmour()
        if (!armed && !armoured) {
            return
        }
        ifClose()
        guard?.facePlayer(player)
        val what =
            when {
                armed && armoured -> "weapon and armour"
                armed -> "weapon"
                else -> "armour on"
            }
        guardShouts(guard, "Oi You with the $what, what are you doing?")
        guard?.say("You don't belong in here!")
        mes("More guards come to arrest you.")
        delay(2)
        throwInCell(guard)
    }

    private suspend fun ProtectedAccess.guardShouts(guard: Npc?, text: String) {
        guard?.say(text)
        mes("Guard: $text")
        delay(GUARD_LINE_DELAY)
    }

    /**
     * The mercenaries' answer to trouble outside the gate: a blindfolded cart ride and a drop
     * somewhere in the dunes, minus any water the player was carrying.
     */
    suspend fun ProtectedAccess.dumpInDesert(guard: Npc?) {
        if (isExempt(player)) {
            return
        }
        ifClose()
        guard?.facePlayer(player)
        guard?.say("Guards!! Guards!")
        delay(2)
        guard?.say("Let's see how good you are with Desert Survival techniques.")
        delay(2)
        mes("You're bundled into the back of a cart and blindfolded...")
        rideIntoDesert("Sometime later you wake up in the desert.")
    }

    /** The captain's men run out of patience with someone who keeps coming back to insult him. */
    suspend fun ProtectedAccess.cartedOff(guard: Npc?) {
        if (isExempt(player)) {
            return
        }
        ifClose()
        mes("An angry guard approaches you and whips out his sword.")
        guard?.facePlayer(player)
        for (line in LOST_PATIENCE) {
            guard?.say(line)
            delay(2)
        }
        mes("The guards grab you and rough you up a bit.")
        roughUp(guard, hits = 2)
        mes("You're grabbed and manhandled onto a cart.")
        rideIntoDesert("Sometime later you're dumped in the middle of the desert.")
    }

    private suspend fun ProtectedAccess.rideIntoDesert(arrival: String) {
        fadeToBlack()
        telejump(TouristTrapCoords.DESERT_DUMPS.random(), TeleportType.Exempt)
        delay(1)
        fadeFromBlack()
        mes(arrival)
        takeWater()
        mes("The guards move off leaving you stranded in the desert.")
    }

    /**
     * The guard lays into the player a few times. The blows are the player's own damage, not an
     * attack by the guard, so auto-retaliate does not start a fight nobody could win.
     */
    suspend fun ProtectedAccess.roughUp(guard: Npc?, hits: Int) {
        if (guard == null) {
            return
        }
        guard.facePlayer(player)
        repeat(hits) {
            guard.anim(ROUGH_UP_SEQ)
            val damage = random.of(0, ROUGH_UP_MAX_HIT).coerceAtMost(player.hitpoints - 1)
            queueHit(player, delay = 1, type = HitType.Typeless, damage = damage.coerceAtLeast(0))
            delay(2)
        }
    }

    private fun ProtectedAccess.takeWater() {
        var tookAny = false
        for (skin in FULL_WATERSKINS) {
            val count = inv.count(skin)
            if (count > 0 && invDel(inv, skin, count).success) {
                tookAny = true
            }
        }
        if (!tookAny) {
            return
        }
        mes("Guard: You won't be needing that water anymore!")
        mes("The guards throw your water away.")
        if (EMPTY_WATERSKIN !in inv) {
            invAdd(inv, EMPTY_WATERSKIN)
        }
    }

    private companion object {
        /** One chance in this many that the guards find each key during a search. */
        const val CONFISCATE_ODDS = 5

        const val GUARD_LINE_DELAY = 3
        const val ROUGH_UP_SEQ = "seq.human_unarmedpunch"
        const val ROUGH_UP_MAX_HIT = 3

        val LOST_PATIENCE =
            listOf(
                "Okay, that does it!",
                "You're in serious trouble now!",
                "Okay men, we need to teach this person a lesson",
                "about desert survival.",
            )

        val FULL_WATERSKINS =
            listOf("obj.water_skin4", "obj.water_skin3", "obj.water_skin2", "obj.water_skin1")
        const val EMPTY_WATERSKIN = "obj.water_skin0"
    }
}
