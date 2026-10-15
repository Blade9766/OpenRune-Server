package org.rsmod.content.skills.hunter.goatpit

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import kotlin.math.sign
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.npc.isAliveInWorld
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onApNpcT
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcT
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.other.pets.PetRewards
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.skills.hunter.rumours.RumourTracker
import org.rsmod.content.skills.hunter.traps.TrapManager
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType
import org.rsmod.game.map.collision.add
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Goat hunting on Wyrmscraig. The goat pit is a per-player multiloc on `varbit.goat_pit_state`
 * (empty, spiked, one goat, some goats, full) with the goat count in `varbit.goat_pit_inpit`.
 * Prodding a goat with a cattleprod drives it a few tiles straight away from the player; if that
 * line crosses a spiked pit with room, the goat falls in. Clearing the pit harvests each goat
 * and breaks the spikes.
 */
class GoatPitScript
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val collision: CollisionFlagMap,
    private val worldQueues: WorldQueueList,
    private val worldRepo: WorldRepository,
    private val spells: MagicSpellRegistry,
    private val runes: MagicRuneManager,
    private val random: GameRandom,
    private val xpMods: XpModifiers,
    private val rumours: RumourTracker,
    private val pets: PetRewards,
) : PluginScript() {
    override fun ScriptContext.startup() {
        blockPitForNpcs()
        onOpNpc1(GOAT) { prod(it.npc) }
        val goatType = requireNotNull(ServerCacheManager.getNpc(GOAT.asRSCM(RSCMType.NPC)))
        for (lure in LureSpell.entries) {
            val spell = spells.allSpells().firstOrNull { it.component.packed == lure.component.asRSCM(RSCMType.COMPONENT) } ?: continue
            onApNpcT(goatType, spell.component) { lure(it.npc, spell, lure) }
            onOpNpcT(goatType, lure.component) { lure(it.npc, spell, lure) }
        }
        onOpNpc1(GEOFF) { talkToGeoff(it.npc) }
        onOpLoc1(PROD_SUPPLY) { takeProd() }
        onOpLoc1(SPIKES_SUPPLY) { takeSpikes(1) }
        onOpLoc2(SPIKES_SUPPLY) { takeSpikes(5) }
        onOpLoc3(SPIKES_SUPPLY) { takeSpikes(10) }
        onOpLoc4(SPIKES_SUPPLY) { takeSpikes(countDialog()) }
        onOpLoc1(PIT_EMPTY) { line() }
        onOpLoc2(PIT_EMPTY) { inspect() }
        onOpLoc1(PIT_SPIKED) { inspect() }
        onOpLoc2(PIT_SPIKED) { inspect() }
        for (pit in PIT_WITH_GOATS) {
            onOpLoc1(pit) { clear() }
            onOpLoc2(pit) { inspect() }
        }
    }

    /** Goats would otherwise wander across the pit, which the map does not flag as blocked. */
    private fun blockPitForNpcs() {
        for (dx in 0 until PIT_SIZE) {
            for (dz in 0 until PIT_SIZE) {
                collision.add(CoordGrid(PIT_X + dx, PIT_Z + dz, 0), CollisionFlag.BLOCK_NPCS)
            }
        }
    }

    private suspend fun ProtectedAccess.talkToGeoff(npc: Npc) =
        startDialogue(npc) {
            chatNpc(
                happy,
                "Line the goat pit with some wooden spikes, then give the goats a prod with a " +
                    "cattleprod to send them in. Help yourself to the supplies here.",
            )
            chatNpc(
                neutral,
                "Once the pit's full, clear it out. The spikes won't survive, so you'll need to " +
                    "line it again after.",
            )
        }

    private fun ProtectedAccess.canHunt(): Boolean {
        if (player.statBase(TrapManager.STAT) < LEVEL) {
            mes("You need a Hunter level of $LEVEL to hunt goats.")
            return false
        }
        if (!QuestRequirements.hasCompleted(player, SHEEP_HERDER)) {
            mes("You need to complete Sheep Herder to hunt goats.")
            return false
        }
        return true
    }

    private fun ProtectedAccess.takeProd() {
        if (CATTLEPROD in player.inv || player.righthand?.isType(CATTLEPROD) == true) {
            mes("You already have a cattleprod.")
            return
        }
        if (inv.isFull()) {
            mes("You don't have enough inventory space.")
            return
        }
        invAdd(inv, CATTLEPROD)
        mes("You take a cattleprod from the supply.")
    }

    private fun ProtectedAccess.takeSpikes(count: Int) {
        if (count <= 0) {
            return
        }
        if (inv.freeSpace() == 0 && SPIKES !in player.inv) {
            mes("You don't have enough inventory space.")
            return
        }
        invAdd(inv, SPIKES, count)
    }

    private suspend fun ProtectedAccess.line() {
        if (!canHunt()) {
            return
        }
        if (SPIKES !in player.inv) {
            mes("You need some wooden spikes to line the pit.")
            return
        }
        anim(LINE_SEQ)
        delay(1)
        if (player.vars[STATE] != State.Empty.value) {
            return
        }
        invDel(inv, SPIKES)
        setState(player, 0)
        mes("You line the pit with wooden spikes.")
    }

    private fun ProtectedAccess.inspect() {
        val count = player.vars[IN_PIT]
        if (player.vars[STATE] == State.Empty.value) {
            mes("The pit is empty. It needs to be lined with wooden spikes before it can catch goats.")
            return
        }
        mes(
            "The pit contains $count goat remains, out of ${capacity(player)}. The spikes are " +
                "still sharp."
        )
    }

    private suspend fun ProtectedAccess.clear() {
        while (player.vars[IN_PIT] > 0) {
            if (inv.isFull()) {
                mes("You don't have enough inventory space.")
                return
            }
            anim(CLEAR_SEQ)
            delay(1)
            val remaining = player.vars[IN_PIT]
            if (remaining <= 0) {
                return
            }
            invAdd(inv, if (random.of(LOOT_ROLL) < HORN_WEIGHT) HORN else FUR)
            statAdvance(TrapManager.STAT, harvestXp() * xpMods.get(player, TrapManager.STAT))
            rumours.onCatch(player, RUMOUR_KEY)
            pets.rollSkillingPet(player, PET, TrapManager.STAT, PET_CHANCE)
            if (remaining == 1) {
                VarPlayerIntMapSetter.set(player, IN_PIT, 0)
                VarPlayerIntMapSetter.set(player, STATE, State.Empty.value)
                mes("You clear the last goat out of the pit, breaking the spikes.")
            } else {
                setState(player, remaining - 1)
            }
        }
    }

    private suspend fun ProtectedAccess.prod(goat: Npc) {
        if (!canHunt()) {
            return
        }
        val weapon = player.righthand
        if (weapon == null) {
            mes("You should use a cattleprod if you want to prod the goats.")
            return
        }
        if (!weapon.isType(CATTLEPROD)) {
            mes("You don't think your current weapon would work as a cattleprod.")
            return
        }
        faceEntitySquare(goat)
        anim(PROD_SEQ)
        delay(1)
        val dx = (goat.coords.x - player.coords.x).sign
        val dz = (goat.coords.z - player.coords.z).sign
        drive(goat, dx, dz, PROD_DISTANCE, PROD_XP)
    }

    /**
     * Telekinetic Grab and Dark Lure pull a goat straight towards the caster, so the caster stands
     * on the far side of the pit to drop it in.
     */
    private suspend fun ProtectedAccess.lure(goat: Npc, spell: MagicSpell, lure: LureSpell) {
        if (!canHunt()) {
            return
        }
        if (!runes.canCastSpell(player, spell) || runes.attemptCast(player, spell).isFailure()) {
            return
        }
        faceEntitySquare(goat)
        anim(lure.castSeq)
        spotanim(lure.castSpot, height = CAST_HEIGHT)
        worldRepo.projAnimSourced(player, goat, SpotanimType(lure.travelSpot.asRSCM(RSCMType.SPOTANIM)), LURE_PROJANIM)
        statAdvance(MAGIC, spell.castXp)
        delay(LURE_TRAVEL_TICKS)
        if (!goat.isAliveInWorld()) {
            return
        }
        goat.spotanim(lure.impactSpot)
        val dx = (player.coords.x - goat.coords.x).sign
        val dz = (player.coords.z - goat.coords.z).sign
        val reach = maxOf(kotlin.math.abs(player.coords.x - goat.coords.x), kotlin.math.abs(player.coords.z - goat.coords.z)) - 1
        drive(goat, dx, dz, reach.coerceIn(0, LURE_DISTANCE), LURE_XP)
    }

    private fun ProtectedAccess.drive(goat: Npc, dx: Int, dz: Int, distance: Int, xp: Double) {
        if ((dx == 0 && dz == 0) || distance <= 0) {
            return
        }
        val path = (1..distance).map { goat.coords.translate(dx * it, dz * it) }
        val pitTile = path.firstOrNull { it.inPit() }
        val open = player.vars[STATE] != State.Empty.value && player.vars[IN_PIT] < capacity(player)
        val edge = path.takeWhile { !it.inPit() }
        goat.noneMode()
        edge.lastOrNull()?.let(goat::walk)
        if (pitTile == null || !open) {
            worldQueues.add(edge.size + 1) { if (goat.isAliveInWorld()) goat.defaultMode() }
            return
        }
        val hunter = player
        worldQueues.add(edge.size + 1) { fallIn(hunter, goat, xp) }
    }

    private fun fallIn(player: Player, goat: Npc, xp: Double) {
        if (!goat.isAliveInWorld()) {
            return
        }
        goat.defaultMode()
        val count = player.vars[IN_PIT]
        if (player.vars[STATE] == State.Empty.value || count >= capacity(player)) {
            return
        }
        npcRepo.despawn(goat, GOAT_RESPAWN)
        setState(player, count + 1)
        player.statAdvance(TrapManager.STAT, xp * xpMods.get(player, TrapManager.STAT))
        if (count + 1 >= capacity(player)) {
            player.mes("The pit is now filled with goats.")
        }
    }

    private fun setState(player: Player, count: Int) {
        VarPlayerIntMapSetter.set(player, IN_PIT, count)
        val state =
            when {
                count == 0 -> State.Spiked
                count >= capacity(player) -> State.Full
                count == 1 -> State.One
                else -> State.Some
            }
        VarPlayerIntMapSetter.set(player, STATE, state.value)
    }

    private fun capacity(player: Player): Int {
        val level = player.statBase(TrapManager.STAT)
        return CAPACITIES.last { level >= it.first }.second
    }

    private fun ProtectedAccess.harvestXp(): Double {
        val level = player.statBase(TrapManager.STAT)
        return if (level < XP_STEP_LEVEL) {
            BASE_XP + LOW_XP_PER_LEVEL * (level - LEVEL)
        } else {
            STEP_XP + (level - XP_STEP_LEVEL)
        }
    }

    private fun CoordGrid.inPit(): Boolean =
        x in PIT_X until PIT_X + PIT_SIZE && z in PIT_Z until PIT_Z + PIT_SIZE

    private enum class LureSpell(
        val component: String,
        val castSeq: String,
        val castSpot: String,
        val travelSpot: String,
        val impactSpot: String,
    ) {
        TelekineticGrab(
            "component.magic_spellbook:telegrab",
            "seq.human_casttelegrab",
            "spotanim.telegrab_casting",
            "spotanim.telegrab_travel",
            "spotanim.telegrab_impact",
        ),
        DarkLure(
            "component.magic_spellbook:dark_lure",
            "seq.human_castentangle",
            "spotanim.dark_lure_cast_spotanim",
            "spotanim.dark_lure_travel_projanim",
            "spotanim.dark_lure_hit_spotanim",
        ),
    }

    private enum class State(val value: Int) {
        Empty(0),
        Spiked(1),
        One(2),
        Some(3),
        Full(4),
    }

    private companion object {
        const val GOAT = "npc.goat_pit_goat"
        const val GEOFF = "npc.goat_pit_helper"
        const val PROD_SUPPLY = "loc.goat_pit_prod"
        const val SPIKES_SUPPLY = "loc.goat_pit_spikes"
        const val PIT_EMPTY = "loc.goat_pit_multi_empty"
        const val PIT_SPIKED = "loc.goat_pit_multi_spikes"
        const val STATE = "varbit.goat_pit_state"
        const val IN_PIT = "varbit.goat_pit_inpit"
        const val CATTLEPROD = "obj.cattleprod"
        const val SPIKES = "obj.goat_pit_spikes"
        const val HORN = "obj.desert_goat_horn"
        const val FUR = "obj.goat_pit_fur"
        const val PET = "obj.goatpitpet"
        const val RUMOUR_KEY = "WyrmscraigGoat"
        const val PROD_SEQ = "seq.cattleprod"
        const val LINE_SEQ = "seq.human_pickupfloor"
        const val CLEAR_SEQ = "seq.human_pickupfloor"

        const val LEVEL = 60
        const val SHEEP_HERDER = "quest_sheepherder"
        const val PIT_X = 2572
        const val PIT_Z = 2195
        const val PIT_SIZE = 3
        const val PROD_DISTANCE = 6
        const val GOAT_RESPAWN = 15
        const val PROD_XP = 20.0
        const val LURE_XP = 10.0
        const val LURE_DISTANCE = 10
        const val LURE_TRAVEL_TICKS = 2
        const val CAST_HEIGHT = 92
        const val LURE_PROJANIM = "projanim.magic_spell"
        const val MAGIC = "stat.magic"
        const val BASE_XP = 100.0
        const val LOW_XP_PER_LEVEL = 3.0
        const val XP_STEP_LEVEL = 80
        const val STEP_XP = 160.0
        const val LOOT_ROLL = 4
        const val HORN_WEIGHT = 3
        const val PET_CHANCE = 40_000

        val PIT_WITH_GOATS =
            listOf("loc.goat_pit_multi_one", "loc.goat_pit_multi_some", "loc.goat_pit_multi_full")

        val CAPACITIES = listOf(60 to 16, 69 to 18, 77 to 20, 85 to 22, 93 to 24)
    }
}
