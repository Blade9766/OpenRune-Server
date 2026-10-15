package org.rsmod.content.quest.area.gnomestronghold.monkeymadness

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.refs.done.hitmark_groups
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.player.clearInteractionRoute
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onApLocU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.quest.area.gnomestronghold.grandtree.landNear
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.ABERAB
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.DENTURES
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.ENCHANTED_BAR
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.EYE_OF_GNOME
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.MOULD
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.SLEEPING_GUARD
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.STAGE_APE_ATOLL
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.TREFAJI
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.UNSTRUNG_AMULET
import org.rsmod.content.quest.area.varrock.demonslayer.fadeFromBlack
import org.rsmod.content.quest.area.varrock.demonslayer.fadeToBlack
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Marim, the monkey town on Ape Atoll, as a human sees it: the archers at the gate who put every
 * newcomer in jail, the jail itself with its patrolling guards, the warehouse crates and the
 * cavern under them, the temple trapdoor and its wall of flames, and the bamboo ladders, stairs,
 * doors and bridge that join it all together. A player in monkey form walks through all of it
 * untroubled; see [Greegree].
 */
@Singleton
class Marim
@Inject
constructor(
    private val monkeyMadness: MonkeyMadnessQuest,
    private val greegree: Greegree,
    private val launcher: ProtectedAccessLauncher,
    private val search: NpcSearch,
    private val locRepo: LocRepository,
) : PluginScript() {

    private val capturing = monkeyMadness.quest.attribute(name = "CAPTURING", default = false, temp = true)
    private val everJailed = monkeyMadness.quest.attribute(name = "EVER_JAILED", default = false)

    private val templeTrapdoorOpen = ServerCacheManager.getObject(TEMPLE_TRAPDOOR_OPEN.asRSCM(RSCMType.LOC)) ?: error("Missing loc: $TEMPLE_TRAPDOOR_OPEN")
    private val warehouseTrapdoorOpen = ServerCacheManager.getObject(WAREHOUSE_TRAPDOOR_OPEN.asRSCM(RSCMType.LOC)) ?: error("Missing loc: $WAREHOUSE_TRAPDOOR_OPEN")

    override fun ScriptContext.startup() {
        onPlayerCoordsChanged {
            if (player.coords != lastKnownCoords) {
                watchHuman(player)
            }
        }
        onPlayerSoftTimer(JAIL_TIMER) { launcher.launch(player) { jailTick() } }

        onOpLoc1(JAIL_DOOR) { pickJailLock(it.loc) }
        onOpLoc1(JAIL_DOOR_UNPICKABLE) { mes("This cell is empty and its lock is far too heavy to pick.") }
        onOpNpc1(TREFAJI) { startDialogue(it.npc) { jailGuard() } }
        onOpNpc1(ABERAB) { startDialogue(it.npc) { jailGuard() } }

        onOpLoc1(CRATE_OVER_HOLE) { crateOverHole() }
        onOpLoc1(DENTURE_CRATE) { itemCrate(DENTURES, "You find a set of magical monkey dentures wrapped in cloth.") }
        onOpLoc1(MOULD_CRATE) { itemCrate(MOULD, "You find a mould shaped like a monkey's face.") }
        onOpLoc1(EYE_OF_GNOME_CRATE) { itemCrate(EYE_OF_GNOME, "You find a strange glass eye.") }
        onOpLoc1(BANANA_CRATE) { itemCrate(MonkeyMadnessQuest.BANANA, "You find a banana.") }
        onOpLoc1(BANANA_CRATES_STACKED) { itemCrate(MonkeyMadnessQuest.BANANA, "You find a banana.") }
        onOpLoc1(TINDERBOX_CRATE) { itemCrate("obj.tinderbox", "You find a tinderbox.") }
        onOpLoc1(NEEDLE_CRATE) { itemCrate("obj.needle", "You find a needle.") }
        onOpLoc1(THREAD_CRATE) { itemCrate("obj.thread", "You find some thread.") }
        onOpLoc1(CHISEL_CRATE) { itemCrate("obj.chisel", "You find a chisel.") }
        onOpLoc1(HAMMER_CRATE) { itemCrate("obj.hammer", "You find a hammer.") }
        onOpLoc1(BRONZE_SCIMITAR_CRATE) { itemCrate("obj.bronze_scimitar", "You find a bronze scimitar.") }
        onOpLoc1(IRON_SCIMITAR_CRATE) { itemCrate("obj.iron_scimitar", "You find an iron scimitar.") }
        onOpLoc1(MONKEY_WRENCH_CRATE) { itemCrate(MonkeyMadnessQuest.MONKEY_WRENCH, "You find a monkey wrench.") }
        onOpLoc1(PLAIN_CRATE) { emptyCrate() }
        onOpNpc1(SLEEPING_GUARD) { wakeGuard(it.npc) }

        onOpLoc1(WAREHOUSE_TRAPDOOR) { openTrapdoor(it.loc, warehouseTrapdoorOpen) }
        onOpLoc1(WAREHOUSE_TRAPDOOR_OPEN) { climbDownTo(MonkeyMadness.WAREHOUSE_TRAPDOOR_LANDING) }
        onOpLoc2(WAREHOUSE_TRAPDOOR_OPEN) { closeTrapdoor(it.loc, WAREHOUSE_TRAPDOOR) }
        onOpLoc1(ROPE_EASTERN) { climbRope(MonkeyMadness.CAVERN_ROPE_TOP) }
        onOpLoc1(ROPE_WESTERN) { climbRope(MonkeyMadness.CRATE_HOLE.translateX(-1)) }

        onOpLoc1(TEMPLE_TRAPDOOR) { openTrapdoor(it.loc, templeTrapdoorOpen) }
        onOpLoc1(TEMPLE_TRAPDOOR_OPEN) { climbDownTo(MonkeyMadness.TEMPLE_UNDER_LANDING) }
        onOpLoc2(TEMPLE_TRAPDOOR_OPEN) { closeTrapdoor(it.loc, TEMPLE_TRAPDOOR) }
        onOpLoc1(ROPE_TEMPLE) { climbRope(MonkeyMadness.TEMPLE_ROPE_TOP) }
        onOpLoc1(GORILLA_STATUE) { prayAtStatue() }
        for (wall in FIREWALLS) {
            onOpLocU(wall, ENCHANTED_BAR) { smithAmulet() }
            onApLocU(wall, ENCHANTED_BAR) {
                if (isWithinApRange(it.loc, FIREWALL_RANGE)) {
                    smithAmulet()
                }
            }
        }

        onOpLoc1(STAIRS_BASE) { climbStairs(it.loc, up = true) }
        onOpLoc1(STAIRS_TOP) { climbStairs(it.loc, up = false) }
        for (ladder in LADDERS_UP) {
            onOpLoc1(ladder) { climbLadder(it.loc, 1) }
        }
        for (ladder in LADDERS_DOWN) {
            onOpLoc1(ladder) { climbLadder(it.loc, -1) }
        }
        for (ladder in WATCHTOWER_LADDERS_UP) {
            onOpLoc1(ladder) { climbLadderTo(it.loc, WATCHTOWER_TOP) }
        }
        for (ladder in WATCHTOWER_LADDERS_DOWN) {
            onOpLoc1(ladder) { climbLadderTo(it.loc, 0) }
        }
        onOpLoc1(BRIDGE_LADDER) { climbLadderTo(it.loc, BRIDGE_LEVEL) }
        onOpLoc1(BRIDGE_LADDER_TOP) { climbLadderTo(it.loc, 0) }
        onOpLoc1(JUMPING_SQUARE) { jumpOffBridge() }
        onOpLoc1(DUNGEON_LADDER_ENTRANCE) { climbLadderTo(it.loc, MonkeyMadness.DUNGEON_LADDER_BOTTOM) }
        onOpLoc1(DUNGEON_LADDER_EXIT) { climbLadderTo(it.loc, MonkeyMadness.DUNGEON_LADDER_TOP) }

        for (door in DOORS) {
            onOpLoc1(door) { passDoor(it.loc) }
        }
        for (gate in GATES) {
            onOpLoc1(gate) { passGate(it.loc) }
        }
    }

    /* Capture */

    private fun watchHuman(player: Player) {
        if (monkeyMadness.stage(player) < STAGE_APE_ATOLL || greegree.isMonkey(player) || capturing.get(player)) {
            return
        }
        val coords = player.coords
        if (coords.level != 0) {
            return
        }
        val nearGate = coords.x in GATE_APPROACH_X && coords.z in GATE_APPROACH_Z
        val creaky = coords in CREAKY_FLOORS && coords.chebyshevDistance(SLEEPING_GUARD_POST) <= GUARD_HEARING
        val inPlantation = coords.x in MonkeyMadness.PLANTATION_X && coords.z in MonkeyMadness.PLANTATION_Z
        val reason =
            when {
                nearGate && !everJailed.get(player) -> CaptureReason.GATE
                creaky -> CaptureReason.GUARD
                inPlantation && auntWatching(player) -> CaptureReason.AUNT
                else -> return
            }
        player.clearInteractionRoute()
        launcher.launch(player) { capture(reason) }
    }

    private fun auntWatching(player: Player): Boolean {
        val aunt = search.find(player.coords, MonkeyMadnessQuest.MONKEYS_AUNT, AUNT_SIGHT, HuntVis.LineOfSight) ?: return false
        return aunt.coords.chebyshevDistance(player.coords) <= AUNT_SIGHT
    }

    enum class CaptureReason { GATE, GUARD, AUNT, JAIL_GUARD }

    /** Arrows fly, the lights go out, and the player comes round in the jail. */
    suspend fun ProtectedAccess.capture(reason: CaptureReason) {
        if (capturing.get(player)) {
            return
        }
        capturing.set(player, true)
        try {
            when (reason) {
                CaptureReason.GATE -> {
                    mes("Monkey archers on the walls have spotted you!")
                    shootPlayer()
                    delay(3)
                    mes("A poisoned arrow catches you in the shoulder. Everything goes dark...")
                }
                CaptureReason.GUARD -> {
                    mes("The floor creaks loudly and the guard wakes up!")
                    delay(1)
                    mes("Before you can run, the guard clubs you over the head. Everything goes dark...")
                }
                CaptureReason.AUNT -> {
                    mes("The Monkey's Aunt sees you and screeches for the guards!")
                    soundSynth(MonkeyMadness.SOUND_MONKEY_CALLS)
                    delay(2)
                    mes("Monkey guards come running. Everything goes dark...")
                }
                CaptureReason.JAIL_GUARD -> {
                    soundSynth(MonkeyMadness.SOUND_GORILLA_PUNCH)
                    mes("The guard grabs you by the collar and throws you back into your cell.")
                }
            }
            anim(MonkeyMadness.UNCONSCIOUS_SEQ)
            delay(2)
            fadeToBlack()
            telejump(MonkeyMadness.JAIL_CELL, TeleportType.Exempt)
            resetAnim()
            delay(1)
            fadeFromBlack()
            closeFadeOverlay()
            everJailed.set(player, true)
            softTimer(JAIL_TIMER, 1)
            if (reason != CaptureReason.JAIL_GUARD) {
                mesbox("You wake up in a cell. The door is locked, and something large is snoring outside.")
            }
        } finally {
            capturing.set(player, false)
        }
    }

    private fun ProtectedAccess.shootPlayer() {
        val archers = npcFindAll(player.coords, ARCHER, ARCHER_RANGE, HuntVis.Off, search).take(ARCHER_COUNT)
        for (archer in archers) {
            archer.facePlayer(player)
            archer.anim(ARCHER_SHOOT_SEQ)
        }
        queueHit(player, delay = 1, type = HitType.Ranged, damage = ARROW_DAMAGE, hitmark = hitmark_groups.regular_damage)
    }

    /* Jail */

    private suspend fun ProtectedAccess.jailTick() {
        val coords = player.coords
        val inRoom = coords.level == 0 && coords.x in MonkeyMadness.JAIL_MIN_X..MonkeyMadness.JAIL_MAX_X && coords.z in MonkeyMadness.JAIL_MIN_Z..MonkeyMadness.JAIL_MAX_Z
        if (!inJail() && !inRoom) {
            clearSoftTimer(JAIL_TIMER)
            return
        }
        if (greegree.isMonkey(player) || capturing.get(player)) {
            return
        }
        val guard = nearestGuard(coords) ?: return
        val distance = guard.coords.chebyshevDistance(coords)
        if (inRoom && distance <= GUARD_CATCH_RANGE) {
            capture(CaptureReason.JAIL_GUARD)
        } else if (inJail() && coords.z == MonkeyMadness.JAIL_DOOR.z && distance <= 1) {
            guard.facePlayer(player)
            soundSynth(MonkeyMadness.SOUND_GORILLA_PUNCH)
            queueHit(guard, delay = 0, type = HitType.Melee, damage = random.of(PUNCH_MIN, PUNCH_MAX))
            mes("The guard punches you through the bars!")
        }
    }

    private fun nearestGuard(coords: CoordGrid): Npc? {
        val guards = listOf(TREFAJI, ABERAB).mapNotNull { search.find(coords, it, GUARD_SEARCH, HuntVis.Off) }
        return guards.minByOrNull { it.coords.chebyshevDistance(coords) }
    }

    private suspend fun ProtectedAccess.pickJailLock(door: BoundLocInfo) {
        if (!inJail()) {
            mes("The door is locked from the outside. You have no reason to break in.")
            return
        }
        val guard = nearestGuard(player.coords)
        anim(LOCKPICK_SEQ)
        delay(2)
        if (guard != null && guard.coords.chebyshevDistance(door.coords) <= GUARD_DOOR_WATCH) {
            mes("You can't pick the lock while the guard is standing right there.")
            return
        }
        val chance = if (player.inv.contains(LOCKPICK)) LOCKPICK_CHANCE else BARE_HANDS_CHANCE
        if (random.of(CHANCE_OUT_OF) >= chance) {
            mes("You fail to pick the lock.")
            return
        }
        soundSynth(MonkeyMadness.SOUND_UNLOCK)
        mes("You pick the lock and slip out of the cell.")
        telejump(MonkeyMadness.JAIL_DOORSTEP, TeleportType.Exempt)
    }

    private suspend fun Dialogue.jailGuard() {
        if (!greegree.isMonkey(player) && !player.worn.contains(MonkeyMadnessQuest.AMULET) && !player.inv.contains(MonkeyMadnessQuest.AMULET)) {
            chatNpc(angry, "Ook! Ook ook OOK!")
            chatPlayer(confused, "I have no idea what you're saying.")
            return
        }
        chatNpc(angry, "Back in your cell, prisoner, or I'll knock you into next week.")
        chatPlayer(quiz, "How long are you going to keep us here?")
        chatNpc(neutral, "Until the King says otherwise. Now get away from the bars.")
    }

    /* Warehouse and cavern */

    private suspend fun ProtectedAccess.crateOverHole() {
        anim(MonkeyMadness.SEARCH_SEQ)
        delay(1)
        mesbox("The crate has no bottom, and neither does the floor beneath it. You fall through into darkness.")
        val damage = (player.hitpoints * FALL_DAMAGE_PERCENT / 100).coerceAtLeast(1)
        soundSynth(MonkeyMadness.SOUND_JUMP_AND_FALL)
        telejump(landNear(MonkeyMadness.CAVERN_LANDING), TeleportType.Exempt)
        queueHit(player, delay = 0, type = HitType.Typeless, damage = damage)
        anim(MonkeyMadness.JUMP_SEQ)
        mes("You land heavily on the cavern floor.")
    }

    private suspend fun ProtectedAccess.itemCrate(obj: String, found: String) {
        anim(MonkeyMadness.SEARCH_SEQ)
        delay(1)
        if (player.inv.contains(obj)) {
            mes("You search the crate but find nothing else of use.")
            return
        }
        if (player.inv.freeSpace() < 1) {
            mes("You need a free inventory space to take anything from the crate.")
            return
        }
        invAdd(player.inv, obj)
        objbox(obj, found)
    }

    private suspend fun ProtectedAccess.emptyCrate() {
        anim(MonkeyMadness.SEARCH_SEQ)
        delay(1)
        mes("The crate is empty.")
    }

    private suspend fun ProtectedAccess.wakeGuard(npc: Npc) {
        if (greegree.isMonkey(player)) {
            startDialogue(npc) {
                chatNpc(neutral, "Zzz... ook... zzz...")
                chatPlayer(neutral, "Sleep well, brother.")
            }
            return
        }
        npc.facePlayer(player)
        capture(CaptureReason.GUARD)
    }

    /* Trapdoors, ropes, statue and the wall of flames */

    private suspend fun ProtectedAccess.openTrapdoor(trapdoor: BoundLocInfo, open: dev.openrune.types.ObjectServerType) {
        anim(MonkeyMadness.SEARCH_SEQ)
        delay(1)
        soundSynth(MonkeyMadness.SOUND_TRAPDOOR_OPEN)
        locRepo.change(trapdoor, open, TRAPDOOR_TICKS)
    }

    private suspend fun ProtectedAccess.closeTrapdoor(trapdoor: BoundLocInfo, closed: String) {
        soundSynth(MonkeyMadness.SOUND_TRAPDOOR_CLOSE)
        locRepo.change(trapdoor, closed, TRAPDOOR_TICKS)
    }

    private suspend fun ProtectedAccess.climbDownTo(dest: CoordGrid) {
        anim(MonkeyMadness.LADDER_SEQ)
        delay(1)
        telejump(landNear(dest), TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.climbRope(dest: CoordGrid) {
        anim(MonkeyMadness.LADDER_SEQ)
        soundSynth(MonkeyMadness.SOUND_ROPECLIMB)
        delay(2)
        telejump(landNear(dest), TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.prayAtStatue() {
        anim(PRAY_SEQ)
        delay(2)
        if (greegree.isMonkey(player)) {
            mes("You feel Marimbo's approval.")
        } else {
            mes("You feel very much like an outsider.")
        }
    }

    private suspend fun ProtectedAccess.smithAmulet() {
        if (!player.inv.contains(MOULD)) {
            mesbox("You need the m'amulet mould to shape the bar.")
            return
        }
        if (monkeyMadness.zooknockStage.get(player) < MonkeyMadnessQuest.ZOOKNOCK_BAR_GIVEN) {
            mesbox("The flames lick at the bar, but nothing happens. Zooknock's enchantment must come first.")
            return
        }
        anim(SMITH_SEQ)
        soundSynth(MonkeyMadness.SOUND_FIRE)
        delay(3)
        invDel(player.inv, ENCHANTED_BAR)
        invAdd(player.inv, UNSTRUNG_AMULET)
        monkeyMadness.zooknockStage.set(player, MonkeyMadnessQuest.ZOOKNOCK_AMULET_EXPLAINED)
        monkeyMadness.syncVars(player)
        objbox(UNSTRUNG_AMULET, "You hold the mould in the flames and the enchanted bar flows into it. Once it cools you have an amulet, though it needs stringing.")
    }

    /* Ladders, stairs, the bridge */

    private suspend fun ProtectedAccess.climbLadder(ladder: BoundLocInfo, translate: Int) {
        climbLadderTo(ladder, player.coords.level + translate)
    }

    private suspend fun ProtectedAccess.climbLadderTo(ladder: BoundLocInfo, level: Int) {
        climbLadderTo(ladder, CoordGrid(ladder.coords.x, ladder.coords.z, level))
    }

    private suspend fun ProtectedAccess.climbLadderTo(ladder: BoundLocInfo, dest: CoordGrid) {
        anim(MonkeyMadness.LADDER_SEQ)
        delay(1)
        telejump(landNear(dest), TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.climbStairs(stairs: BoundLocInfo, up: Boolean) {
        val level = stairs.coords.level + if (up) 1 else -1
        val landing = if (up) CoordGrid(stairs.coords.x, stairs.coords.z + STAIRS_UP_OFFSET, level) else CoordGrid(stairs.coords.x, stairs.coords.z - STAIRS_DOWN_OFFSET, level)
        delay(1)
        telejump(landNear(landing), TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.jumpOffBridge() {
        mesbox("You take a running jump off the end of the bridge and hit the water far below.")
        anim(MonkeyMadness.JUMP_SEQ)
        soundSynth(MonkeyMadness.SOUND_JUMP_AND_FALL)
        delay(2)
        telejump(landNear(BRIDGE_JUMP_LANDING), TeleportType.Exempt)
        resetAnim()
        mes("You swim to the shore.")
    }

    /* Doors and gates */

    /** Steps the player through a wall door onto the tile on its other side. */
    private suspend fun ProtectedAccess.passDoor(door: BoundLocInfo) {
        val doorTile = door.coords
        val across = tileAcross(doorTile, door.angle)
        val dest = if (player.coords == doorTile) across else doorTile
        if (player.coords != doorTile && player.coords != across) {
            playerWalk(across)
            return
        }
        soundSynth(DOOR_SOUND)
        playerWalk(dest)
    }

    private fun tileAcross(tile: CoordGrid, angle: LocAngle): CoordGrid =
        when (angle) {
            LocAngle.West -> tile.translateX(-1)
            LocAngle.North -> tile.translateZ(1)
            LocAngle.East -> tile.translateX(1)
            LocAngle.South -> tile.translateZ(-1)
        }

    private suspend fun ProtectedAccess.passGate(gate: BoundLocInfo) {
        if (!greegree.isMonkey(player)) {
            mes("The monkey guards slam the gate shut in your face.")
            if (monkeyMadness.stage(player) >= STAGE_APE_ATOLL && !everJailed.get(player)) {
                capture(CaptureReason.GATE)
            }
            return
        }
        val z = gate.coords.z
        val dest = if (player.coords.z <= z) CoordGrid(player.coords.x, z + 1, 0) else CoordGrid(player.coords.x, z - 1, 0)
        soundSynth(DOOR_SOUND)
        playerWalk(landNear(dest))
    }

    companion object {
        const val JAIL_TIMER = "timer.mm_jail"
        const val JAIL_DOOR = "loc.mm_jail_door"
        const val JAIL_DOOR_UNPICKABLE = "loc.mm_jail_door_unpickable"
        const val LOCKPICK = "obj.lockpick"
        const val LOCKPICK_SEQ = "seq.human_pickpocket"
        const val PRAY_SEQ = "seq.human_pray"
        const val SMITH_SEQ = "seq.human_smithing"
        const val ARCHER = "npc.mm_posted_archer"
        const val DOOR_SOUND = "synth.door_open"

        const val CRATE_OVER_HOLE = "loc.mm_crate_over_hole"
        const val DENTURE_CRATE = "loc.mm_denture_crate"
        const val MOULD_CRATE = "loc.mm_monkey_amulet_mould_crate"
        const val EYE_OF_GNOME_CRATE = "loc.mm_eye_of_gnome_crate"
        const val BANANA_CRATE = "loc.mm_banana_crate"
        const val BANANA_CRATES_STACKED = "loc.mm_banana_crates_stacked"
        const val TINDERBOX_CRATE = "loc.mm_tinderbox_crate"
        const val NEEDLE_CRATE = "loc.mm_needle_crate"
        const val THREAD_CRATE = "loc.mm_thread_crate"
        const val CHISEL_CRATE = "loc.mm_chisel_crate"
        const val HAMMER_CRATE = "loc.mm_hammer_crate"
        const val BRONZE_SCIMITAR_CRATE = "loc.mm_bronze_scimitar_crate"
        const val IRON_SCIMITAR_CRATE = "loc.mm_iron_scimitar_crate"
        const val MONKEY_WRENCH_CRATE = "loc.mm_monkey_wrench_crate"
        const val PLAIN_CRATE = "loc.mm_crate"

        const val WAREHOUSE_TRAPDOOR = "loc.mm_eastern_warehouse_trapdoor"
        const val WAREHOUSE_TRAPDOOR_OPEN = "loc.mm_eastern_warehouse_trapdoor_open"
        const val ROPE_EASTERN = "loc.mm_climbing_rope_bottom_eastern_warehouse"
        const val ROPE_WESTERN = "loc.mm_climbing_rope_bottom_western_warehouse"
        const val TEMPLE_TRAPDOOR = "loc.mm_temple_trapdoor"
        const val TEMPLE_TRAPDOOR_OPEN = "loc.mm_temple_trapdoor_open"
        const val ROPE_TEMPLE = "loc.mm_climbing_rope_bottom_temple"
        const val GORILLA_STATUE = "loc.mm_gorillastatue1"
        val FIREWALLS = listOf("loc.mm_iban_firewall_straight", "loc.mm_iban_firewall_diagonal")

        const val STAIRS_BASE = "loc.mm_stairs_base"
        const val STAIRS_TOP = "loc.mm_stairs_top"
        val LADDERS_UP = listOf("loc.mm_bamboo_ladder", "loc.mm_bamboo_ladder_reverse")
        val LADDERS_DOWN = listOf("loc.mm_bamboo_ladder_top", "loc.mm_bamboo_ladder_top_reverse")
        val WATCHTOWER_LADDERS_UP = listOf("loc.mm_bamboo_ladder_watchtower_east", "loc.mm_bamboo_ladder_watchtower_west")
        val WATCHTOWER_LADDERS_DOWN = listOf("loc.mm_bamboo_ladder_top_watchtower_east", "loc.mm_bamboo_ladder_top_watchtower_west")
        const val WATCHTOWER_TOP = 2
        const val BRIDGE_LADDER = "loc.mm_bridge_ladder"
        const val BRIDGE_LADDER_TOP = "loc.mm_bridge_ladder_top"
        const val BRIDGE_LEVEL = 2
        const val JUMPING_SQUARE = "loc.mm_jumping_square"
        val BRIDGE_JUMP_LANDING = CoordGrid(2804, 2722, 0)
        const val DUNGEON_LADDER_ENTRANCE = "loc.mm_bamboo_ladder_dungeon_entrance"
        const val DUNGEON_LADDER_EXIT = "loc.mm_bamboo_ladder_dungeon_exit"
        val DOORS = listOf("loc.mm_bamboo_door", "loc.mm_bamboo_door_secure", "loc.mm_door_unopenable")
        val GATES = listOf("loc.mm_bamboo_largedoor", "loc.mm_bamboo_largedoor_left")

        val GATE_APPROACH_X = 2710..2735
        val GATE_APPROACH_Z = 2758..2772
        val SLEEPING_GUARD_POST = CoordGrid(2760, 2771, 0)
        const val GUARD_HEARING = 12
        const val AUNT_SIGHT = 5
        const val ARCHER_RANGE = 14
        const val ARCHER_COUNT = 3
        const val ARROW_DAMAGE = 8
        const val ARCHER_SHOOT_SEQ = "seq.m_monkey_attack_bow"
        const val FIREWALL_RANGE = 1
        const val GUARD_SEARCH = 12
        const val GUARD_CATCH_RANGE = 2
        const val GUARD_DOOR_WATCH = 3
        const val PUNCH_MIN = 10
        const val PUNCH_MAX = 20
        const val LOCKPICK_CHANCE = 6
        const val BARE_HANDS_CHANCE = 3
        const val CHANCE_OUT_OF = 8
        const val FALL_DAMAGE_PERCENT = 33
        const val TRAPDOOR_TICKS = 100
        const val STAIRS_UP_OFFSET = 3
        const val STAIRS_DOWN_OFFSET = 1

        /** The creaky floorboards around Marim; a footstep on one near the sleeping guard wakes him. */
        val CREAKY_FLOORS =
            setOf(
                CoordGrid(2752, 2782, 0), CoordGrid(2754, 2778, 0), CoordGrid(2755, 2774, 0), CoordGrid(2756, 2789, 0),
                CoordGrid(2759, 2756, 0), CoordGrid(2761, 2778, 0), CoordGrid(2762, 2793, 0), CoordGrid(2765, 2779, 0),
                CoordGrid(2767, 2784, 0), CoordGrid(2769, 2754, 0), CoordGrid(2770, 2774, 0), CoordGrid(2772, 2781, 0),
                CoordGrid(2774, 2787, 0), CoordGrid(2775, 2756, 0), CoordGrid(2777, 2790, 0), CoordGrid(2780, 2773, 0),
                CoordGrid(2785, 2768, 0), CoordGrid(2789, 2754, 0), CoordGrid(2791, 2763, 0),
            )
    }
}
