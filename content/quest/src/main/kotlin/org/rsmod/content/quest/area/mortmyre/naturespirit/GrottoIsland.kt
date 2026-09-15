package org.rsmod.content.quest.area.mortmyre.naturespirit

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc5
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpObj3
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.DRUIDIC_SPELL
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.FILLIMAN
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.FUNGUS
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.JOURNAL
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.MIRROR
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.STAGE_BLESSED
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.STAGE_ENTERED_SWAMP
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.STAGE_PUZZLE_SOLVED
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.STAGE_SHOWN_MIRROR
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.USED_SPELL
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.WASHING_BOWL
import org.rsmod.content.quest.area.mortmyre.naturespirit.npcs.Filliman
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.map.Direction
import org.rsmod.game.obj.Obj
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Filliman's island in the south of Mort Myre: the broken bridge onto it, his bench and washing
 * bowl, the grotto tree that hides his journal, the three symbol stones, and the grotto entrance
 * where his spirit appears to anyone who came in by the swamp gate.
 */
class GrottoIsland
@Inject
constructor(
    private val natureSpirit: NatureSpiritQuest,
    private val spirits: SpiritSpawns,
    private val filliman: Filliman,
    private val objRepo: ObjRepository,
    private val world: WorldRepository,
) : PluginScript() {

    private val washingBowlType =
        ServerCacheManager.getItem(WASHING_BOWL.asRSCM(RSCMType.OBJ)) ?: error("Missing obj: $WASHING_BOWL")

    override fun ScriptContext.startup() {
        onOpLoc1(BRIDGE) { jumpBridge(it.loc) }
        onOpObj3(washingBowlType) { takeWashingBowl(it.obj) }
        onOpLoc1(GROTTO_TREE) { mes("It looks like a tree on a large rock with roots trailing down to the ground.") }
        onOpLoc2(GROTTO_TREE) { searchGrottoTree() }
        onOpLoc1(GROTTO_DOOR) { enterGrotto() }
        onOpHeld1(JOURNAL) { readJournal() }

        onOpLoc5(NATURE_STONE) { searchStone("nature", "This stone seems complete in some way.") }
        onOpLoc5(FAITH_STONE) { searchStone("faith", "This stone seems to be complete somehow.") }
        onOpLoc5(SPIRIT_STONE) { searchStone("spirit", "This stone seems to be complete in some way.") }
        onOpLocU(NATURE_STONE) { useOnNatureStone(it.objType.internalName) }
        onOpLocU(SPIRIT_STONE) { useOnSpiritStone(it.objType.internalName) }
        onOpLocU(FAITH_STONE) { mes("Nothing interesting happens.") }
    }

    /** About 24% at level 1 to 99% at level 99; a fall still lands the player across, bruised. */
    private suspend fun ProtectedAccess.jumpBridge(bridge: BoundLocInfo) {
        arriveDelay()
        val northbound = coords.z < bridge.coords.z || coords.z <= MortMyreCoords.BRIDGE_SOUTH_Z
        val landing =
            CoordGrid(
                bridge.coords.x,
                if (northbound) MortMyreCoords.BRIDGE_NORTH_Z else MortMyreCoords.BRIDGE_SOUTH_Z,
                0,
            )
        faceSquare(landing)
        anim(JUMP_SEQ)
        soundSynth(JUMP_SOUND)
        delay(1)
        telejump(landing, TeleportType.Exempt)
        if (statRandom(AGILITY, JUMP_LOW, JUMP_HIGH, invisibleBoost = 0)) {
            soundSynth(LAND_SOUND)
            statAdvance(AGILITY, JUMP_XP)
        } else {
            soundSynth(SPLASH_SOUND)
            mes("You nearly drown in the disgusting swamp.")
            val damage = random.of(1, MAX_FALL_DAMAGE).coerceAtMost(player.hitpoints - 1)
            if (damage > 0) {
                queueHit(delay = 1, type = HitType.Typeless, damage = damage)
            }
            statAdvance(AGILITY, FAIL_XP)
        }
        if (northbound) {
            mes("The aura of Fillimans camp protects you from the swamp.")
        }
    }

    private suspend fun ProtectedAccess.takeWashingBowl(bowl: Obj) {
        if (inv.isFull()) {
            mes("You don't have enough inventory space.")
            return
        }
        if (coords != bowl.coords) {
            delay(1)
        }
        anim(TAKE_SEQ)
        soundSynth(TAKE_SOUND)
        if (!objRepo.del(bowl)) {
            return
        }
        invAdd(inv, WASHING_BOWL)
        if (MIRROR !in player.inv) {
            objRepo.add(MIRROR, bowl.coords, MIRROR_DURATION, receiver = player)
            mes("You find a small mirror under the washing bowl.")
        }
    }

    private suspend fun ProtectedAccess.searchGrottoTree() {
        arriveDelay()
        if (natureSpirit.stage(player) != STAGE_SHOWN_MIRROR || JOURNAL in player.inv) {
            mes("You find nothing interesting.")
            return
        }
        anim(SEARCH_SEQ)
        invAddOrDrop(objRepo, JOURNAL)
        objbox(
            JOURNAL,
            "You search the strange rock. You find a knot and inside of it you discover a small " +
                "tome. The words on the front are a bit vague, but you make out the words " +
                "'Tarlock' and 'journal'.",
        )
    }

    private suspend fun ProtectedAccess.readJournal() {
        mesbox(
            "Most of the writing is pretty uninteresting, but something inside refers to a nature " +
                "spirit. The requirements for which are \"something from nature\", \"something " +
                "with faith\", and \"something of the spirit-to-become freely given\". It's all " +
                "pretty vague.",
        )
    }

    private suspend fun ProtectedAccess.enterGrotto() {
        arriveDelay()
        val stage = natureSpirit.stage(player)
        mes("You prepare to enter the Druid's grotto.")
        when {
            natureSpirit.isComplete(player) -> {
                delay(1)
                telejump(MortMyreCoords.ALTAR_ENTRY, TeleportType.Exempt)
            }
            stage >= STAGE_PUZZLE_SOLVED -> {
                delay(1)
                telejump(MortMyreCoords.GROTTO_ENTRY, TeleportType.Exempt)
                mes("You see a beautifully tended small grotto area.")
            }
            stage >= STAGE_ENTERED_SWAMP -> {
                mesbox("A shifting apparition appears in front of you.")
                val spirit = spirits.summon(FILLIMAN, MortMyreCoords.FILLIMAN_OUTSIDE, Direction.North)
                spirit.facePlayer(player)
                startDialogue(spirit) { with(filliman) { talk(spirit) } }
            }
        }
    }

    private suspend fun ProtectedAccess.searchStone(symbol: String, complete: String) {
        arriveDelay()
        val suffix = if (natureSpirit.stage(player) >= STAGE_PUZZLE_SOLVED) " $complete" else ""
        mesbox("You search the stone and find that it has some sort of $symbol symbol scratched into it.$suffix")
    }

    private suspend fun ProtectedAccess.useOnNatureStone(obj: String) {
        arriveDelay()
        if (natureSpirit.stage(player) !in STAGE_BLESSED until STAGE_PUZZLE_SOLVED) {
            mes("Nothing interesting happens.")
            return
        }
        when {
            natureSpirit.fungusPlaced.get(player) -> mes("The stone seems to be complete already.")
            obj != FUNGUS -> mes("You try to place the item onto the stone, but it just moves off.")
            else -> {
                invDel(inv, FUNGUS)
                absorb(MortMyreCoords.NATURE_STONE)
                natureSpirit.fungusPlaced.set(player, true)
                mes("The stone seems to absorb the fungus.")
            }
        }
    }

    private suspend fun ProtectedAccess.useOnSpiritStone(obj: String) {
        arriveDelay()
        if (natureSpirit.stage(player) !in STAGE_BLESSED until STAGE_PUZZLE_SOLVED) {
            mes("Nothing interesting happens.")
            return
        }
        when {
            natureSpirit.spellPlaced.get(player) -> mes("The stone seems to be complete already.")
            obj != USED_SPELL && obj != DRUIDIC_SPELL -> mes("You try to place the item onto the stone, but it just moves off.")
            else -> {
                invDel(inv, obj)
                absorb(MortMyreCoords.SPIRIT_STONE)
                natureSpirit.spellPlaced.set(player, true)
                mes(if (obj == USED_SPELL) "The stone seems to absorb the used spell scroll." else "The stone seems to absorb the spell scroll.")
                val spirit = spirits.find(FILLIMAN) ?: return
                startDialogue(spirit) { chatNpc(happy, "Aha, yes, that seems right well done!") }
            }
        }
    }

    private fun ProtectedAccess.absorb(stone: CoordGrid) {
        anim(TAKE_SEQ)
        spotanimMap(world, ABSORB_SPOTANIM, stone)
    }

    private companion object {
        const val BRIDGE = "loc.druidjump_loc"
        const val GROTTO_TREE = "loc.grotto_druidicspirit"
        const val GROTTO_DOOR = "loc.grotto_door_druidicspirit"
        const val NATURE_STONE = "loc.stonedisc_ds_nature"
        const val FAITH_STONE = "loc.stonedisc_ds_faith"
        const val SPIRIT_STONE = "loc.stonedisc_ds_spirit"

        const val AGILITY = "stat.agility"
        const val JUMP_LOW = 60
        const val JUMP_HIGH = 252
        const val JUMP_XP = 15.0
        const val FAIL_XP = 2.0
        const val MAX_FALL_DAMAGE = 7

        const val JUMP_SEQ = "seq.human_longjump"
        const val TAKE_SEQ = "seq.human_pickuptable"
        const val SEARCH_SEQ = "seq.human_pickuptable"
        const val JUMP_SOUND = "synth.jump"
        const val LAND_SOUND = "synth.jump_land_bridge"
        const val SPLASH_SOUND = "synth.splash_and_river"
        const val TAKE_SOUND = "synth.pick2"
        const val ABSORB_SPOTANIM = "spotanim.druidicspirit_effect"
        const val MIRROR_DURATION = 200
    }
}

/**
 * Filliman's spirit and the nature spirit he becomes are not map spawns: they appear when a
 * player calls on them and fade after a while, one of each at a time.
 */
@Singleton
class SpiritSpawns @Inject constructor(private val npcRepo: NpcRepository) {
    private val active = HashMap<String, Npc>()

    fun summon(type: String, coords: CoordGrid, face: Direction): Npc {
        val existing = active[type]?.takeIf { it.isSlotAssigned && it.coords.level == coords.level }
        if (existing != null && existing.coords.chebyshevDistance(coords) <= LEASH) {
            return existing
        }
        existing?.let { npcRepo.del(it, Int.MAX_VALUE) }
        val npc = Npc(type, coords)
        npcRepo.add(npc, LIFETIME)
        npc.respawnDir = face
        active[type] = npc
        return npc
    }

    fun find(type: String): Npc? = active[type]?.takeIf { it.isSlotAssigned }

    fun dismiss(type: String) {
        val npc = active.remove(type) ?: return
        if (npc.isSlotAssigned) {
            npcRepo.del(npc, Int.MAX_VALUE)
        }
    }

    private companion object {
        /** Five minutes, long enough for the longest conversation. */
        const val LIFETIME = 500
        const val LEASH = 10
    }
}
