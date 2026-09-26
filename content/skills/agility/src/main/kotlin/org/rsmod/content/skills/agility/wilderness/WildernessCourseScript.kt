package org.rsmod.content.skills.agility.wilderness

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.config.Constants
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.other.pouches.lootingbag.LootingBags
import org.rsmod.content.other.pouches.lootingbag.hasOpenLootingBag
import org.rsmod.content.other.pouches.lootingbag.lootingBag
import org.rsmod.content.other.pouches.lootingbag.saveLootingBag
import org.rsmod.content.skills.agility.wilderness.WildernessLaps.Companion.inCourse
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Everything around the Wilderness course's laps: the entrance door and gates (52 Agility to go
 * in), the Agility dispenser, the ladder into the dungeon, and losing the dispenser deposit on
 * leaving the course. Logging out inside the course keeps the deposit but costs ten laps of
 * streak.
 */
class WildernessCourseScript
@Inject
constructor(
    private val laps: WildernessLaps,
    private val passages: GenericPassageScript,
    private val objRepo: ObjRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(DISPENSER) { tag() }
        onOpLoc2(DISPENSER) { pay() }
        onOpLoc3(DISPENSER) { redeem() }
        onOpLoc1(ENTRANCE_DOOR) { openEntrance(it.loc, it.type) }
        for (gate in GATES) {
            onOpLoc1(gate) { openEntrance(it.loc, it.type) }
        }
        onOpLoc1(DUNGEON_LADDER) { climbIntoDungeon() }
        onPlayerCoordsChanged {
            if (lastKnownCoords == player.coords) return@onPlayerCoordsChanged
            if (player.dispenserDeposit && !inCourse(player.coords)) {
                leftCourse(player)
            }
        }
        onPlayerLogin {
            if (!player.dispenserDeposit) return@onPlayerLogin
            if (!inCourse(player.coords)) {
                leftCourse(player)
                return@onPlayerLogin
            }
            player.lapStreak = (player.lapStreak - LOGOUT_STREAK_PENALTY).coerceAtLeast(0)
        }
    }

    private suspend fun ProtectedAccess.openEntrance(gate: BoundLocInfo, type: ObjectServerType) {
        arriveDelay()
        val entering = coords.z < gate.coords.z
        if (entering && player.agilityLvl < ENTRY_LEVEL) {
            mes("You need an Agility level of $ENTRY_LEVEL to enter the Wilderness Agility Course.")
            return
        }
        if (!entering && gate.coords.z == COURSE_GATE_Z && !confirmLeaving()) {
            return
        }
        with(passages) { walkThrough(gate, type) }
    }

    private suspend fun ProtectedAccess.confirmLeaving(): Boolean {
        if (!player.dispenserDeposit || player.skipExitWarning) {
            return true
        }
        val choice =
            choice3(
                "Leave the course.",
                LEAVE,
                "Stay on the course.",
                STAY,
                "Leave, and don't warn me again.",
                LEAVE_AND_SKIP,
                title = "Leaving the course loses your 150,000 coin deposit.",
            )
        if (choice == LEAVE_AND_SKIP) {
            player.skipExitWarning = true
        }
        return choice != STAY
    }

    private fun leftCourse(player: Player) {
        laps.forfeitDeposit(player)
        player.mes("You have left the course, so the dispenser has kept your deposit.")
    }

    private suspend fun ProtectedAccess.climbIntoDungeon() {
        arriveDelay()
        anim(CLIMB_DOWN_SEQ)
        delay(1)
        telejump(DUNGEON_LANDING, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.tag() {
        arriveDelay()
        faceSquare(DISPENSER_COORDS)
        player.runEnergy = Constants.run_max_energy
        UpdateRun.energy(player, player.runEnergy)
        if (!player.ticketWaiting && !player.lootWaiting) {
            mes("You tag the dispenser. It has nothing for you until you complete another lap.")
            return
        }
        if (player.ticketWaiting) {
            if (inv.count(TICKET) == 0 && inv.isFull()) {
                mes("You need a free inventory space to take your Wilderness agility ticket.")
                return
            }
            invAdd(inv, TICKET)
            player.ticketWaiting = false
            mes("The dispenser gives you a Wilderness agility ticket.")
        }
        if (player.lootWaiting && player.dispenserDeposit) {
            dispenseLoot()
        }
    }

    private fun ProtectedAccess.dispenseLoot() {
        if (!player.hasOpenLootingBag()) {
            mes("You need an open looting bag in your inventory to collect the dispenser's loot.")
            return
        }
        val bracket = DispenserLoot.bracket(player.lapStreak)
        val awarded = mutableListOf<String>()
        for (table in listOf(bracket.resources, bracket.armour)) {
            val drop = DispenserLoot.roll(random, table)
            val count = random.of(drop.count)
            player.invAddOrDrop(objRepo, drop.obj, count, inv = player.lootingBag)
            awarded += "<col=ef1020>$count x ${ocUncertName(drop.obj)}</col>"
        }
        player.saveLootingBag()

        var extra: String? = null
        var clue = false
        val clueDenominator = if (wearsImbuedRingOfWealth()) CLUE_CHANCE_RING else CLUE_CHANCE
        if (random.of(clueDenominator) == 0) {
            clue = true
            player.invAddOrDrop(objRepo, MEDIUM_CLUE)
        }
        if (!inv.isFull()) {
            val supply = DispenserLoot.roll(random, DispenserLoot.extraSupply)
            invAdd(inv, supply.obj)
            extra = ocUncertName(supply.obj)
        }
        player.lootWaiting = false

        val items = awarded.joinToString(" and ")
        val prefix = if (clue) "a clue scroll, " else ""
        val suffix = extra?.let { ", and an extra <col=ef1020>$it</col>" } ?: ""
        mes("You have been awarded $prefix$items$suffix from the Agility dispenser.")
    }

    private fun ProtectedAccess.ocUncertName(obj: String): String {
        val type = ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)) ?: return obj
        return ocUncert(type).name
    }

    private fun ProtectedAccess.wearsImbuedRingOfWealth(): Boolean {
        val ring = player.worn[Wearpos.Ring.slot] ?: return false
        return IMBUED_RINGS_OF_WEALTH.any { ring.isType(it) }
    }

    private suspend fun ProtectedAccess.pay() {
        arriveDelay()
        faceSquare(DISPENSER_COORDS)
        if (player.dispenserDeposit) {
            mes("You have already paid the dispenser. Complete laps to earn its rewards.")
            return
        }
        if (inv.count(COINS) < FEE) {
            mes("You need 150,000 coins in your inventory to pay the dispenser.")
            return
        }
        if (!player.skipPaymentWarning) {
            val choice =
                choice3(
                    "Pay 150,000 coins.",
                    PAY,
                    "Don't pay.",
                    DONT_PAY,
                    "Pay, and don't warn me again.",
                    PAY_AND_SKIP,
                    title = "The deposit is lost if you die or leave the course.",
                )
            if (choice == DONT_PAY) {
                return
            }
            if (choice == PAY_AND_SKIP) {
                player.skipPaymentWarning = true
            }
        }
        if (invDel(inv, COINS, FEE).failure) {
            return
        }
        player.dispenserDeposit = true
        player.lootWaiting = false
        player.lapStreak = 0
        mes("You pay 150,000 coins into the dispenser. Tag it after each lap to collect your loot.")
        if (!ownsLootingBag()) {
            player.invAddOrDrop(objRepo, LootingBags.CLOSED)
            mes("The dispenser gives you a looting bag to carry your loot in.")
        }
    }

    private fun ProtectedAccess.ownsLootingBag(): Boolean =
        LOOTING_BAGS.any { inv.count(it) > 0 || bank.contains(it) }

    private suspend fun ProtectedAccess.redeem() {
        arriveDelay()
        faceSquare(DISPENSER_COORDS)
        val tickets = inv.count(TICKET)
        if (tickets == 0) {
            mes("You don't have any Wilderness agility tickets to redeem.")
            return
        }
        val xpEach = ticketXp(tickets)
        if (invDel(inv, TICKET, tickets).failure) {
            return
        }
        statAdvance(AGILITY, (tickets * xpEach).toDouble())
        val plural = if (tickets == 1) "ticket" else "tickets"
        mes("You redeem $tickets Wilderness agility $plural for ${tickets * xpEach} Agility experience.")
    }

    companion object {
        const val DISPENSER = "loc.wildy_agility_pillar"
        const val ENTRANCE_DOOR = "loc.balancegate52a"
        const val DUNGEON_LADDER = "loc.wild_laddertop_dungeon"
        const val TICKET = "obj.wildy_agility_token"
        const val COINS = "obj.coins"
        const val MEDIUM_CLUE = "obj.trail_medium_emote_exp1"
        const val AGILITY = "stat.agility"
        const val CLIMB_DOWN_SEQ = "seq.human_pickupfloor"

        const val ENTRY_LEVEL = 52
        const val FEE = 150_000
        const val LOGOUT_STREAK_PENALTY = 10
        const val CLUE_CHANCE = 40
        const val CLUE_CHANCE_RING = 20
        const val COURSE_GATE_Z = 3931

        private const val LEAVE = 1
        private const val STAY = 2
        private const val LEAVE_AND_SKIP = 3
        private const val PAY = 1
        private const val DONT_PAY = 2
        private const val PAY_AND_SKIP = 3

        val GATES = listOf("loc.balancegate52b_left", "loc.balancegate52b_right")
        val LOOTING_BAGS = listOf(LootingBags.CLOSED, LootingBags.OPEN)
        val IMBUED_RINGS_OF_WEALTH =
            listOf(
                "obj.ring_of_wealth_i",
                "obj.ring_of_wealth_i1",
                "obj.ring_of_wealth_i2",
                "obj.ring_of_wealth_i3",
                "obj.ring_of_wealth_i4",
                "obj.ring_of_wealth_i5",
            )

        val DISPENSER_COORDS = CoordGrid(3005, 3936, 0)
        val DUNGEON_LANDING = CoordGrid(3005, 10362, 0)

        /** Experience per ticket: bigger batches earn a bonus on every ticket in the batch. */
        fun ticketXp(tickets: Int): Int =
            when {
                tickets > 100 -> 230
                tickets > 50 -> 220
                tickets > 10 -> 210
                else -> 200
            }
    }
}
