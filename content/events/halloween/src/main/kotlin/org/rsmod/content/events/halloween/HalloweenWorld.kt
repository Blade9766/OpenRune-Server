package org.rsmod.content.events.halloween

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.enums.HalloweenEnums
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.table.HalloweenDecorationsRow
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid

/**
 * Owns the event's world presence while the season is active: the Great cauldron and costumed
 * children outside Varrock west bank, the town decorations, and the Generous Treater that pops up
 * around Varrock for a few minutes at a time.
 */
@Singleton
class HalloweenWorld
@Inject
constructor(
    private val season: HalloweenSeason,
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
    private val clock: MapClock,
    private val random: GameRandom,
) {
    private var cauldron: LocInfo? = null
    private val children = mutableListOf<Npc>()
    private val decorations = mutableListOf<LocInfo>()

    private var treater: Npc? = null
    private var treaterLeavesAt = 0
    private var nextTreaterAt = 0
    private var nextTreaterChatAt = 0
    private var departureLine: String? = null

    var treaterVisit = 0
        private set

    fun tick() {
        if (season.isActive) {
            if (cauldron == null) spawnDecorations()
            tickGenerousTreater()
        } else if (cauldron != null) {
            despawnAll()
        }
    }

    fun isCurrentTreater(npc: Npc): Boolean = npc === treater

    fun cheerDeposit() {
        children.lastOrNull()?.say("Yay, more treats!")
    }

    private fun spawnDecorations() {
        cauldron =
            locRepo.add(
                GREAT_CAULDRON_COORDS,
                GREAT_CAULDRON,
                Int.MAX_VALUE,
                LocAngle.West,
                LocShape.CentrepieceStraight,
            )
        for ((type, coords) in CHILDREN) {
            spawnNpc(type, coords)?.let(children::add)
        }
        for (row in HalloweenDecorationsRow.all()) {
            decorations +=
                locRepo.add(
                    row.coords,
                    row.loc.internalName,
                    Int.MAX_VALUE,
                    LocAngle[row.angle],
                    LocShape.CentrepieceStraight,
                )
        }
        nextTreaterAt = clock.cycle + random.of(TREATER_MIN_GAP, TREATER_MAX_GAP)
    }

    private fun despawnAll() {
        cauldron?.let { locRepo.del(it, Int.MAX_VALUE) }
        cauldron = null
        children.forEach { npcRepo.del(it, Int.MAX_VALUE) }
        children.clear()
        decorations.forEach { locRepo.del(it, Int.MAX_VALUE) }
        decorations.clear()
        removeTreater()
    }

    private fun tickGenerousTreater() {
        val npc = treater
        val now = clock.cycle
        if (npc == null) {
            if (now >= nextTreaterAt) spawnTreater()
            return
        }
        when {
            now >= treaterLeavesAt -> {
                departureLine?.let(npc::say)
                removeTreater()
                nextTreaterAt = now + random.of(TREATER_MIN_GAP, TREATER_MAX_GAP)
            }
            now >= treaterLeavesAt - DEPARTURE_WARNING && departureLine == null -> {
                val (warning, farewell) = random.pick(DEPARTURES)
                npc.say(warning)
                departureLine = farewell
            }
            now >= nextTreaterChatAt -> {
                npc.say(random.pick(IDLE_LINES))
                nextTreaterChatAt = now + TREATER_CHAT_INTERVAL
            }
        }
    }

    private fun spawnTreater() {
        val spots = HalloweenEnums.halloween_generous_treater_spots.mapNotNull { it.value }
        val spot = random.pick(spots)
        val npc = spawnNpc(random.pick(TREATERS), spot) ?: return
        treater = npc
        treaterVisit = treaterVisit % MAX_VISIT_ID + 1
        treaterLeavesAt = clock.cycle + TREATER_STAY
        nextTreaterChatAt = clock.cycle + TREATER_CHAT_INTERVAL
        departureLine = null
        npc.say(random.pick(ARRIVAL_LINES))
    }

    private fun removeTreater() {
        treater?.let { npcRepo.del(it, Int.MAX_VALUE) }
        treater = null
    }

    private fun spawnNpc(internal: String, coords: CoordGrid): Npc? {
        val type = ServerCacheManager.getNpc(internal.asRSCM(RSCMType.NPC)) ?: return null
        val npc = Npc(type, coords)
        npcRepo.add(npc, Int.MAX_VALUE)
        npc.respawns = false
        return npc
    }

    private companion object {
        const val GREAT_CAULDRON = "loc.hw22_communitygoal_0"
        val GREAT_CAULDRON_COORDS = CoordGrid(3195, 3433, 0)

        val CHILDREN =
            listOf(
                CAVE_HORROR to CoordGrid(3199, 3432, 0),
                SPOOKY_OUTFIT to CoordGrid(3199, 3434, 0),
                JACK_LANTERN to CoordGrid(3198, 3436, 0),
            )

        val TREATERS =
            listOf(
                "npc.hw22_generous_treater_1",
                "npc.hw22_generous_treater_2",
                "npc.hw22_generous_treater_3",
                "npc.hw22_generous_treater_4",
            )

        const val TREATER_STAY = 300
        const val TREATER_MIN_GAP = 300
        const val TREATER_MAX_GAP = 700
        const val TREATER_CHAT_INTERVAL = 40
        const val DEPARTURE_WARNING = 10
        const val MAX_VISIT_ID = 255

        val ARRIVAL_LINES =
            listOf(
                "I can't wait to see some spooky costumes!",
                "Time to be spooked!",
                "I've got plenty of treats to go around!",
                "Where are these Trick-or-Treaters I've heard so much about?",
            )
        val IDLE_LINES =
            listOf(
                "What a spooktacular time of year!",
                "Nothing beats Trick-or-Treat!",
                "Treats for everyone!",
                "I love seeing all these costumes!",
            )
        val DEPARTURES =
            listOf(
                "I'll be off to seek new costumes soon!" to "Time to seek new costumes!",
                "Uh-oh, I'm almost out of treats!" to "Better get more treats!",
                "Any more Trick-or-Treaters around?" to "Time to find some more Trick-or-Treaters!",
            )
    }
}
