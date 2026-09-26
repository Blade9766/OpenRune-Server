package org.rsmod.content.quest.area.lumbridge.dorgeshuun

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.InstanceSpec
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.player.stat.thievingLvl
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.AGILITY_REQ
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.STAGE_HAM_HIDEOUT
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.STAGE_STOREROOMS
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.STAGE_ZANIK_DEAD
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.ZANIK_FOLLOWER_HAM
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.HamHideout.Companion.JAIL_WAKE_TILE
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.HamHideout.Companion.OUTSIDE_CELL
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.HamHideout.Companion.TRAPDOOR_BURIED
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.HamHideout.Companion.TRAPDOOR_TILE
import org.rsmod.content.quest.area.varrock.demonslayer.fadeFromBlack
import org.rsmod.content.quest.area.varrock.demonslayer.fadeToBlack
import org.rsmod.content.quest.manager.menu
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.map.Direction
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * The storerooms under the H.A.M. hideout, where the leaders meet behind a pair of double doors.
 *
 * During the quest each player gets a private copy of the storerooms (map square 40_81) with the
 * five quest guards in it. Zanik can't be seen by the guards, and she shoots a guard dead the moment
 * he turns on the player with his back to her. Everything else a guard sees ends with the player in
 * the hideout cell and every guard back on his post. The guards:
 *
 * 1. by the ladder, who only warns the player off the first time and keeps facing them;
 * 2. in the west corridor, looking south, who turns round when spoken to;
 * 3. patrolling the middle corridor - Zanik shoots him on the player's word when he walks away;
 * 4. in the east corridor, looking south, who chases the player past wherever Zanik is waiting;
 * 5. at the meeting-room doors, who turns to face anyone who shows themselves in the north hall.
 *
 * After the quest the trapdoor leads to the square itself, where the guards stand about to be
 * robbed.
 */
@Singleton
class HamStorerooms
@Inject
constructor(
    private val dttd: DeathToTheDorgeshuunQuest,
    private val follower: ZanikFollower,
    private val manager: InstanceManager,
    private val npcRepo: NpcRepository,
    private val locRepo: LocRepository,
    private val worldRepo: WorldRepository,
    private val collision: CollisionFlagMap,
    private val launcher: ProtectedAccessLauncher,
    private val random: GameRandom,
    private val mapClock: MapClock,
) : PluginScript() {

    private class Visit(val session: InstanceSession, val dx: Int, val dz: Int) {
        val guards = arrayOfNulls<Npc>(GUARD_COUNT)
        val facing = Array(GUARD_COUNT) { GUARD_POSTS[it].facing }
        var patrolEast = true
        var patrolPause = 0
        var chasing = false
        var ending = false

        fun at(world: CoordGrid): CoordGrid = CoordGrid(world.x + dx, world.z + dz, world.level)

        fun toWorld(coords: CoordGrid): CoordGrid = CoordGrid(coords.x - dx, coords.z - dz, coords.level)
    }

    private class Post(val tile: CoordGrid, val facing: Direction, val type: String, val dead: String, val warned: String?)

    private val visits = HashMap<PlayerUid, Visit>()
    private val rays by lazy { RayCastValidator(collision) }

    init {
        follower.onTick(::tick)
    }

    override fun ScriptContext.startup() {
        onOpLoc1(LADDER_UP) { climbUp() }
        onOpLoc1(CRACK) { squeezeThrough(it.loc.coords, it.loc.angle.id) }
        onOpLoc1(ROOM_DOOR) {
            arriveDelay()
            mes("The door is locked.")
            soundSynth(HamHideout.LOCKED_SOUND)
        }
        onOpLoc4(ROOM_DOOR) { pickRoomDoor(it.loc.coords, it.loc.angle.id) }
        onOpLoc1(LISTEN_DOOR_LEFT) { listenAtDoor() }
        onOpLoc1(LISTEN_DOOR_RIGHT) { listenAtDoor() }
        for ((index, post) in GUARD_POSTS.withIndex()) {
            onOpNpc1(post.type) { talkToGuard(index, it.npc) }
        }
    }

    fun isInside(player: Player): Boolean = visitFor(player) != null

    private fun visitFor(player: Player): Visit? {
        val visit = visits[player.uid] ?: return null
        if (manager.sessionForPlayer(player) !== visit.session) {
            visits.remove(player.uid)
            return null
        }
        return visit
    }

    /* Getting in and out */

    suspend fun ProtectedAccess.climbDown() {
        anim(CLIMB_DOWN_SEQ)
        delay(1)
        val stage = dttd.stage(player)
        if (dttd.isComplete(player) || stage !in STAGE_HAM_HIDEOUT..STAGE_STOREROOMS) {
            telejump(STOREROOM_LANDING)
            return
        }
        if (!follower.isFollowing(player)) {
            mes("You should bring Zanik with you before you go down there.")
            return
        }
        enterCopy()
    }

    private suspend fun ProtectedAccess.enterCopy() {
        if (manager.sessionForPlayer(player) != null) {
            mes("You are already inside an instance.")
            return
        }
        val area =
            InstanceArea.copyRegions(
                regionIds = listOf(STOREROOM_REGION),
                level = 0,
                enterCoord = RegionLocal(0, ANCHOR.mx, ANCHOR.mz, ANCHOR.lx, ANCHOR.lz),
                exitCoord = TRAPDOOR_EXIT,
            )
        val spec =
            InstanceSpec(
                fee = 0,
                maxPlayers = 1,
                reclaimTicks = RECLAIM_TICKS,
                graceTicks = GRACE_TICKS,
                destroyWhenEmpty = true,
                area = area,
                settingsRowId = -1,
                bossName = "",
            )
        val (session, enter) =
            when (val result = manager.create(player, KEY, spec, InstanceAccess.Private, mapClock)) {
                is InstanceManager.Result.Failed -> {
                    mes(result.reason)
                    return
                }
                is InstanceManager.Result.Created -> result.session to result.enter
                is InstanceManager.Result.Joined -> result.session to result.enter
            }
        telejump(enter, TeleportType.Exempt)
        if (player.coords != enter) {
            manager.leave(player, session, mapClock)
            return
        }
        manager.finalizeEntry(player, session, mapClock)
        val visit = Visit(session, enter.x - ANCHOR.x, enter.z - ANCHOR.z)
        visits[player.uid] = visit
        telejump(visit.at(STOREROOM_LANDING), TeleportType.Exempt)
        swapMeetingDoors(visit)
        spawnGuards(visit)
        follower.spawn(player, ZANIK_FOLLOWER_HAM, at = visit.at(ZANIK_LANDING))
        dttd.advanceTo(this, STAGE_STOREROOMS)
        val zanik = follower.following(player) ?: return
        startDialogue(zanik) {
            chatNpc(
                worried,
                "The room where the HAM members make their plans must be down here somewhere. We've got to find " +
                    "it without getting caught!",
            )
        }
    }

    private fun swapMeetingDoors(visit: Visit) {
        for ((from, into, tile) in MEETING_DOORS) {
            val fromType = ServerCacheManager.getObject(from.asRSCM(RSCMType.LOC)) ?: continue
            val intoType = ServerCacheManager.getObject(into.asRSCM(RSCMType.LOC)) ?: continue
            val loc = locRepo.findExact(visit.at(tile), fromType) ?: continue
            locRepo.change(loc, intoType, Int.MAX_VALUE)
        }
    }

    private fun spawnGuards(visit: Visit) {
        for ((index, post) in GUARD_POSTS.withIndex()) {
            if (visit.guards[index]?.isSlotAssigned == true) {
                continue
            }
            visit.facing[index] = post.facing
            val guard = Npc(post.type, visit.at(post.tile))
            guard.mode = NpcMode.None
            guard.moveRestrict = if (index == PATROL || index == CHASER) MoveRestrict.Normal else MoveRestrict.NoMove
            guard.respawnDir = post.facing
            npcRepo.add(guard, Int.MAX_VALUE)
            guard.respawns = false
            manager.attachNpc(visit.session.id, guard)
            guard.lockFacingDirection(post.facing)
            visit.guards[index] = guard
        }
        visit.patrolEast = true
        visit.patrolPause = 0
        visit.chasing = false
    }

    private suspend fun ProtectedAccess.climbUp() {
        arriveDelay()
        anim(CLIMB_UP_SEQ)
        delay(1)
        leaveCopy()
        telejump(TRAPDOOR_EXIT)
        follower.relocate(player)
    }

    private fun ProtectedAccess.leaveCopy() {
        val visit = visits.remove(player.uid) ?: return
        manager.leave(player, visit.session, mapClock)
    }

    /* Cracks and doors */

    private suspend fun ProtectedAccess.squeezeThrough(crack: CoordGrid, angle: Int) {
        arriveDelay()
        if (player.agilityLvl < AGILITY_REQ) {
            startDialogue { mesbox("You need an agility level of $AGILITY_REQ to squeeze through that crack.") }
            val zanik = follower.following(player) ?: return
            startDialogue(zanik) {
                chatNpc(quiz, "Can't you get through?")
                chatPlayer(sad, "No, I'm not agile enough.")
                chatNpc(sad, "I don't think I could get through either. You'd better get some agility training done.")
            }
            return
        }
        val beyond = crack.translate(SIDE_X[angle], SIDE_Z[angle])
        val dest = if (player.coords == crack) beyond else crack
        faceSquare(if (player.coords == crack) beyond else crack)
        anim(CRACK_SEQ)
        soundSynth(CRACK_SOUND)
        delay(CRACK_TICKS)
        telejump(dest, TeleportType.Exempt)
        resetAnim()
    }

    private suspend fun ProtectedAccess.pickRoomDoor(door: CoordGrid, angle: Int) {
        arriveDelay()
        mes("You attempt to pick the lock on the door.", ChatType.Spam)
        anim(HamHideout.PICK_LOCK_SEQ)
        soundSynth(HamHideout.PICK_LOCK_SOUND)
        delay(HamHideout.PICK_LOCK_TICKS)
        if (random.of(maxExclusive = 100) >= (HamHideout.PICK_LOCK_BASE + player.thievingLvl).coerceAtMost(HamHideout.PICK_LOCK_MAX)) {
            mes("You fail to pick the lock.")
            return
        }
        mes("You pick the lock on the door.")
        soundSynth(HamHideout.DOOR_OPEN_SOUND)
        val outside = door.translate(SIDE_X[angle], SIDE_Z[angle])
        telejump(if (player.coords == door) outside else door, TeleportType.Exempt)
    }

    /* The guards */

    private suspend fun ProtectedAccess.talkToGuard(index: Int, npc: Npc) {
        val visit = visitFor(player) ?: return
        if (visit.guards[index] !== npc || visit.ending) {
            return
        }
        when (index) {
            FIRST -> {
                faceGuardAt(visit, index, player.coords)
                if (zanikBehind(player, visit, index)) {
                    shoot(visit, index)
                } else {
                    startDialogue(npc) {
                        chatNpc(angry, "Ordinary members aren't allowed in this area. Get back to the cave above.")
                    }
                }
            }
            PATROL -> mes("Nothing interesting happens.")
            CHASER -> spotted(visit, index)
            else -> {
                faceGuardAt(visit, index, player.coords)
                spotted(visit, index)
            }
        }
    }

    private fun tick(player: Player, zanik: Npc) {
        val visit = visitFor(player) ?: return
        if (visit.ending) {
            return
        }
        stepPatrol(visit)
        if (player.isAccessProtected) {
            return
        }
        val coords = player.coords
        for (index in 0 until GUARD_COUNT) {
            val guard = visit.guards[index] ?: continue
            if (!guard.isSlotAssigned) {
                continue
            }
            when (index) {
                FIRST -> {
                    if (!guard.coords.isWithinDistance(coords, FIRST_AWARENESS) || !sees(guard.coords, coords)) {
                        continue
                    }
                    faceGuardAt(visit, index, coords)
                    if (player.vars[GUARD_POSTS[index].warned!!] == 0) {
                        VarPlayerIntMapSetter.set(player, GUARD_POSTS[index].warned!!, 1)
                        launcher.launch(player) { firstWarning(guard) }
                        return
                    }
                    if (guard.coords.isWithinDistance(coords, FIRST_REACH) && zanikBehind(player, visit, index)) {
                        launcher.launch(player) { shoot(visit, index) }
                        return
                    }
                }
                CHASER -> {
                    if (visit.chasing) {
                        chase(player, visit, guard, zanik)
                        return
                    }
                    if (inCone(guard.coords, visit.facing[index], coords, CONE_RANGE) && sees(guard.coords, coords)) {
                        visit.chasing = true
                        guard.say("You there! You aren't allowed in here!")
                        VarPlayerIntMapSetter.set(player, GUARD_POSTS[index].warned!!, 1)
                        return
                    }
                }
                LAST -> {
                    if (guard.coords.isWithinDistance(coords, LAST_AWARENESS) && sees(guard.coords, coords)) {
                        faceGuardAt(visit, index, coords)
                        guard.say("Hey! You there!")
                        VarPlayerIntMapSetter.set(player, GUARD_POSTS[index].warned!!, 1)
                        launcher.launch(player) { spotted(visit, index) }
                        return
                    }
                }
                else -> {
                    if (inCone(guard.coords, visit.facing[index], coords, CONE_RANGE) && sees(guard.coords, coords)) {
                        faceGuardAt(visit, index, coords)
                        launcher.launch(player) { spotted(visit, index) }
                        return
                    }
                }
            }
        }
    }

    /** The patrolling guard walks the middle corridor, pausing at either end. */
    private fun stepPatrol(visit: Visit) {
        val guard = visit.guards[PATROL] ?: return
        if (!guard.isSlotAssigned || visit.ending) {
            return
        }
        if (visit.patrolPause > 0) {
            visit.patrolPause--
            return
        }
        val world = visit.toWorld(guard.coords)
        val end = if (visit.patrolEast) PATROL_EAST_END else PATROL_WEST_END
        if (world.x == end) {
            visit.patrolEast = !visit.patrolEast
            visit.patrolPause = PATROL_PAUSE
            val facing = if (visit.patrolEast) Direction.East else Direction.West
            visit.facing[PATROL] = facing
            guard.lockFacingDirection(facing)
            return
        }
        val facing = if (visit.patrolEast) Direction.East else Direction.West
        visit.facing[PATROL] = facing
        guard.clearFacingLock()
        guard.walk(guard.coords.translate(facing.xOff, 0))
    }

    private fun chase(player: Player, visit: Visit, guard: Npc, zanik: Npc) {
        val step = stepToward(guard.coords, player.coords)
        visit.facing[CHASER] = step
        if (guard.coords.isWithinDistance(zanik.coords, CHASE_SHOT_RANGE) && !facingToward(guard.coords, step, zanik.coords)) {
            launcher.launch(player) { shoot(visit, CHASER) }
            return
        }
        if (guard.coords.isWithinDistance(player.coords, 1)) {
            launcher.launch(player) { caught(visit, guard, null) }
            return
        }
        guard.clearFacingLock()
        guard.walk(player.coords)
    }

    private suspend fun ProtectedAccess.firstWarning(guard: Npc) {
        startDialogue(guard) {
            chatNpc(angry, "Hey! Ordinary members aren't allowed here!")
            chatNpc(
                neutral,
                "Go back upstairs and I'll say nothing about it. But if any of the other guards see you you're in " +
                    "big trouble!",
            )
        }
        val zanik = follower.following(player) ?: return
        startDialogue(zanik) { chatNpc(shifty, "*whispers* Get him to face away from me...") }
    }

    /** A guard has the player in his sights: Zanik shoots him if she can, otherwise he raises the alarm. */
    private suspend fun ProtectedAccess.spotted(visit: Visit, index: Int) {
        val guard = visit.guards[index] ?: return
        if (zanikBehind(player, visit, index)) {
            shoot(visit, index)
            return
        }
        caught(visit, guard, SPOTTED_LINES[index])
    }

    private suspend fun ProtectedAccess.shoot(visit: Visit, index: Int) {
        val guard = visit.guards[index] ?: return
        val zanik = follower.following(player) ?: return
        if (visit.ending) {
            return
        }
        visit.ending = true
        try {
            when (index) {
                FIRST -> startDialogue(guard) { chatNpc(angry, "Hey! Where do you think you're going?") }
                CHASER -> {}
                else -> startDialogue(guard) { chatNpc(angry, "Hey! What are you--") }
            }
            zanik.faceNpc(guard)
            zanik.anim(ZANIK_SHOOT_SEQ)
            zanik.spotanim(ZANIK_SHOOT_SPOTANIM)
            soundSynth(CROSSBOW_SOUND)
            val projType = ServerCacheManager.getProjectile(BOLT_PROJANIM.asRSCM(RSCMType.PROJANIM))
            val flight =
                projType?.let {
                    ProjAnim.fromNpcToNpc(zanik, guard, BOLT_SPOTANIM.asRSCM(RSCMType.SPOTANIM), it)
                }
            flight?.let { worldRepo.projAnim(it) }
            delay(flight?.serverCycles?.coerceAtLeast(1) ?: 1)
            guard.anim(GUARD_DEATH_SEQ)
            soundSynth(GUARD_DEATH_SOUND)
            when (index) {
                FIRST -> startDialogue(guard) { chatNpc(shocked, "Uh!") }
                CHASER, LAST -> {}
                else -> startDialogue(guard) { chatNpc(shocked, "Unk!") }
            }
            delay(DEATH_TICKS)
            if (guard.isSlotAssigned) {
                npcRepo.del(guard, Int.MAX_VALUE)
            }
            visit.guards[index] = null
            VarPlayerIntMapSetter.set(player, GUARD_POSTS[index].dead, 1)
            if (index == CHASER) {
                visit.chasing = false
            }
            zanik.resetFaceEntity()
            if (!follower.isWaiting(player)) {
                follower.followAgain(player)
            }
            afterShot(index, zanik)
        } finally {
            visit.ending = false
        }
    }

    private suspend fun ProtectedAccess.afterShot(index: Int, zanik: Npc) {
        startDialogue(zanik) {
            when (index) {
                FIRST -> {
                    chatNpc(
                        happy,
                        "We can get through this if we work together, ${player.displayName}. But I can't get too " +
                            "close to the guards or they'll see I'm not human.",
                    )
                    chatNpc(
                        neutral,
                        "You've got to get them to turn their backs on me so I can shoot them without them seeing " +
                            "me draw my crossbow.",
                    )
                }
                SECOND -> {
                    chatNpc(happy, "Thanks, ${player.displayName}!")
                    chatNpc(quiz, "Is the coast clear to get past?")
                    chatPlayer(worried, "No, there's a guard patrolling the corridor.")
                    chatNpc(shifty, "I'll take care of him! Just tell me when his back is to us...")
                }
                PATROL -> chatNpc(happy, "We got him, ${player.displayName}!")
                CHASER -> chatNpc(happy, "Another one down!")
                LAST ->
                    chatNpc(happy, "That's the last of them! Now to listen to what's happening in the meeting room!")
            }
        }
    }

    /** The guards drag the player off to the hideout cell and Zanik comes to let them out. */
    private suspend fun ProtectedAccess.caught(visit: Visit, guard: Npc, line: String?) {
        if (visit.ending) {
            return
        }
        visit.ending = true
        if (line != null) {
            guard.say(line)
            startDialogue(guard) { chatNpc(angry, line) }
        }
        fadeToBlack()
        resetGuards()
        follower.remove(player)
        leaveCopy()
        telejump(JAIL_WAKE_TILE, TeleportType.Exempt)
        delay(1)
        fadeFromBlack()
        closeFadeOverlay()
        val zanik = follower.spawn(player, ZANIK_FOLLOWER_HAM, at = OUTSIDE_CELL)
        follower.waitHere(player)
        startDialogue(zanik) {
            chatNpc(shocked, "${player.displayName}! Wake up!")
            chatPlayer(confused, "Bwuh?")
            chatPlayer(sad, "Uuuurgh...")
            chatPlayer(confused, "Where am I?")
            chatNpc(worried, "The guards caught you but I managed to slip away. Here, I'll let you out!")
        }
        zanik.anim(ZANIK_OPEN_CELL_SEQ)
        soundSynth(HamHideout.PICK_LOCK_SOUND)
        delay(2)
        telejump(OUTSIDE_CELL.translate(-1, 0), TeleportType.Exempt)
        follower.followAgain(player)
        startDialogue(zanik) { chatNpc(happy, "Let's go!") }
    }

    private fun ProtectedAccess.resetGuards() {
        for (post in GUARD_POSTS) {
            VarPlayerIntMapSetter.set(player, post.dead, 0)
            post.warned?.let { VarPlayerIntMapSetter.set(player, it, 0) }
        }
    }

    /* Zanik */

    suspend fun ProtectedAccess.talkToZanik(npc: Npc) {
        val visit = visitFor(player) ?: return
        startDialogue(npc) {
            val dead = GUARD_POSTS.map { player.vars[it.dead] == 1 }
            when {
                !dead[FIRST] -> {
                    if (player.vars[GUARD_POSTS[FIRST].warned!!] == 1) {
                        chatNpc(
                            shifty,
                            "*whispers* Get that guard to turn his back on me. Maybe if you walk around him he'll " +
                                "keep facing you.",
                        )
                    } else {
                        chatNpc(
                            worried,
                            "The room where the HAM members make their plans must be down here somewhere. We've " +
                                "got to find it without getting caught!",
                        )
                    }
                    return@startDialogue
                }
                !dead[SECOND] ->
                    chatNpc(
                        neutral,
                        "If you can get to the other side of that guard you can make him turn around so I can " +
                            "shoot him. Make sure another guard doesn't catch you though!",
                    )
                !dead[PATROL] -> {
                    chatNpc(quiz, "Is the coast clear to get past?")
                    chatPlayer(worried, "No, there's a guard patrolling the corridor.")
                    chatNpc(shifty, "I'll take care of him! Just tell me when his back is to us...")
                    patrolOrders(visit)
                    return@startDialogue
                }
                !dead[CHASER] -> {
                    chatPlayer(neutral, "There are two more guards but I don't know how to get behind them.")
                    if (visit.toWorld(player.coords).x >= EAST_SIDE_X) {
                        chatNpc(
                            neutral,
                            "That guard in the east corridor... if I wait at the end of the corridor then you could " +
                                "lead him past me...",
                        )
                    } else {
                        chatNpc(neutral, "Then maybe you could lead one of them past me...")
                    }
                }
                !dead[LAST] -> {
                    chatPlayer(neutral, "There's just one guard left.")
                    chatNpc(
                        neutral,
                        "I don't think that guard's going to move away from the door. I think we need to approach " +
                            "him from different directions...",
                    )
                }
                else -> {
                    chatPlayer(happy, "There's no more guards! We've got them all!")
                    chatNpc(neutral, "I think I can hear something happening behind the double doors. We should listen.")
                }
            }
            orders()
        }
    }

    private suspend fun Dialogue.orders() {
        val waiting = follower.isWaiting(player)
        when (
            menu(
                "Okay." to 0,
                (if (waiting) "Follow me!" else "Wait here.") to 1,
                "Isn't shooting people in the back wrong?" to 2,
                "How did you kill that guard in one hit?" to 3,
            )
        ) {
            0 -> chatPlayer(neutral, "Okay.")
            1 -> waitOrFollow(waiting)
            2 -> backShooting()
            3 -> oneHit()
        }
    }

    private suspend fun Dialogue.patrolOrders(visit: Visit) {
        when (
            menu(
                "Now!" to 0,
                "Isn't shooting people in the back wrong?" to 2,
                "How did you kill that guard in one hit?" to 3,
            )
        ) {
            0 -> {
                chatPlayer(angry, "Now!")
                val guard = visit.guards[PATROL] ?: return
                if (patrolTurnedAway(player, visit)) {
                    access.shoot(visit, PATROL)
                } else {
                    chatNpc(shocked, "Noo! He's seen us!")
                    access.caught(visit, guard, "What are you doing here!?")
                }
            }
            2 -> backShooting()
            3 -> oneHit()
        }
    }

    private suspend fun Dialogue.waitOrFollow(waiting: Boolean) {
        if (waiting) {
            chatPlayer(neutral, "Follow me!")
            chatNpc(happy, "Okay, let's go!")
            follower.followAgain(player)
        } else {
            chatPlayer(neutral, "Wait here.")
            chatNpc(neutral, "Okay.")
            follower.waitHere(player)
        }
    }

    private suspend fun Dialogue.backShooting() {
        chatPlayer(quiz, "Isn't shooting people in the back wrong?")
        chatNpc(confused, "Why more so than shooting them in the front?")
    }

    private suspend fun Dialogue.oneHit() {
        chatPlayer(quiz, "How did you kill that guard in one hit? I want to learn how to do that!")
        chatNpc(
            happy,
            "It's a special technique with the bone crossbow. If we both get out of this alive I'll teach you, " +
                "${player.displayName}!",
        )
    }

    /* Listening at the meeting room */

    private suspend fun ProtectedAccess.listenAtDoor() {
        arriveDelay()
        val visit = visitFor(player)
        if (visit == null) {
            mes("You can't hear anything through the door.")
            return
        }
        if (GUARD_POSTS.any { player.vars[it.dead] == 0 }) {
            mes("You'd better deal with the guards before they catch you listening at the door.")
            return
        }
        val zanik = follower.following(player)
        visit.ending = true
        startDialogue { mesbox("You listen at the door...") }
        anim(LISTEN_BEND_SEQ)
        zanik?.let {
            follower.waitHere(player)
            it.anim(ZANIK_LISTEN_SEQ)
        }
        delay(1)
        anim(LISTEN_IDLE_SEQ)
        startDialogue { chatPlayer(neutral, "I can't hear anything.") }
        if (zanik != null) {
            startDialogue(zanik) { chatNpc(shifty, "Shh! I can hear them.") }
            startDialogue { mesbox("Zanik listens...") }
            startDialogue(zanik) {
                chatNpc(
                    worried,
                    "They're saying something about... they're planning something... something about a machine...",
                )
                chatNpc(shocked, "Oh! Oh no!")
                chatPlayer(quiz, "What are they saying?")
                chatNpc(shocked, "We've got to do something, ${player.displayName}! They're going to--")
            }
        }
        val captor = Npc(EXTRA_GUARD, visit.at(CAPTOR_TILE))
        captor.mode = NpcMode.None
        npcRepo.add(captor, CAPTOR_TICKS)
        captor.respawns = false
        manager.attachNpc(visit.session.id, captor)
        captor.facePlayer(player)
        captor.anim(GUARD_POINT_SEQ)
        resetAnim()
        startDialogue(captor) { chatNpc(angry, "Got you, you spy!") }
        fadeToBlack()
        resetGuards()
        follower.remove(player)
        leaveCopy()
        player.dttdHamTrapdoor = TRAPDOOR_BURIED
        player.dttdZanikCorpse = true
        dttd.advanceTo(this, STAGE_ZANIK_DEAD)
        telejump(JAIL_WAKE_TILE, TeleportType.Exempt)
        delay(1)
        fadeFromBlack()
        closeFadeOverlay()
        startDialogue {
            chatPlayer(sad, "Oww... my head...")
            chatPlayer(confused, "Zanik?")
            chatPlayer(worried, "Where's Zanik?")
        }
    }

    /* Sight lines */

    private fun sees(from: CoordGrid, to: CoordGrid): Boolean =
        rays.hasLineOfSight(from, to, extraFlag = CollisionFlag.BLOCK_PLAYERS)

    private fun zanikBehind(player: Player, visit: Visit, index: Int): Boolean {
        val guard = visit.guards[index] ?: return false
        val zanik = follower.following(player) ?: return false
        if (!guard.coords.isWithinDistance(zanik.coords, SHOT_RANGE)) {
            return false
        }
        val facing = visit.facing[index]
        val toZanikX = zanik.coords.x - guard.coords.x
        val toZanikZ = zanik.coords.z - guard.coords.z
        return facing.xOff * toZanikX + facing.zOff * toZanikZ < 0
    }

    private fun patrolTurnedAway(player: Player, visit: Visit): Boolean {
        val guard = visit.guards[PATROL] ?: return false
        val zanik = follower.following(player) ?: return false
        val facing = visit.facing[PATROL]
        val towardZanik =
            facing.xOff * (zanik.coords.x - guard.coords.x) + facing.zOff * (zanik.coords.z - guard.coords.z)
        return towardZanik < 0 &&
            sees(zanik.coords, guard.coords) &&
            guard.coords.isWithinDistance(zanik.coords, PATROL_SHOT_RANGE)
    }

    private fun faceGuardAt(visit: Visit, index: Int, target: CoordGrid) {
        val guard = visit.guards[index] ?: return
        val direction = stepToward(guard.coords, target)
        visit.facing[index] = direction
        guard.lockFacingDirection(direction)
    }

    /** Whether [target] stands inside the 90 degree view of a guard looking [facing]. */
    private fun inCone(from: CoordGrid, facing: Direction, target: CoordGrid, range: Int): Boolean {
        if (from.level != target.level || !from.isWithinDistance(target, range) || from == target) {
            return false
        }
        val dx = target.x - from.x
        val dz = target.z - from.z
        val dot = facing.xOff * dx + facing.zOff * dz
        if (dot <= 0) {
            return false
        }
        val facingLength = facing.xOff * facing.xOff + facing.zOff * facing.zOff
        return 2 * dot * dot >= (dx * dx + dz * dz) * facingLength
    }

    private fun facingToward(from: CoordGrid, facing: Direction, target: CoordGrid): Boolean =
        facing.xOff * (target.x - from.x) + facing.zOff * (target.z - from.z) > 0

    private fun stepToward(from: CoordGrid, to: CoordGrid): Direction {
        val dx = Integer.signum(to.x - from.x)
        val dz = Integer.signum(to.z - from.z)
        return Direction.entries.firstOrNull { it.xOff == dx && it.zOff == dz } ?: Direction.South
    }

    companion object {
        const val KEY = "dttd_storerooms"
        private const val RECLAIM_TICKS = 100
        private const val GRACE_TICKS = 50
        private const val STOREROOM_REGION = (40 shl 8) or 81

        const val LADDER_UP = "loc.dttd_ladder_up_out_of_ham_storerooms"
        const val CRACK = "loc.dttd_cave_walls_cracked"
        const val ROOM_DOOR = "loc.dttd_ham_door"
        const val LISTEN_DOOR_LEFT = "loc.dttd_doorl_listenat"
        const val LISTEN_DOOR_RIGHT = "loc.dttd_doorr_listenat"
        const val EXTRA_GUARD = "npc.dttd_ham_guard_extra"

        const val CLIMB_DOWN_SEQ = "seq.human_pickupfloor"
        const val CLIMB_UP_SEQ = "seq.human_reachforladder"
        const val CRACK_SEQ = "seq.dttd_player_through_crack"
        const val CRACK_SOUND = "synth.squeeze_thru_crack"
        const val LISTEN_BEND_SEQ = "seq.dttd_bend_to_listen_at_door"
        const val LISTEN_IDLE_SEQ = "seq.dttd_bent_to_listen_at_door_idle"
        const val ZANIK_LISTEN_SEQ = "seq.dttd_zanik_bend_to_listen_at_door"
        const val ZANIK_SHOOT_SEQ = "seq.dttd_zanik_draw_and_fire_crossbow"
        const val ZANIK_SHOOT_SPOTANIM = "spotanim.dttd_zaniks_crossbow_drawn_and_fired"
        const val ZANIK_OPEN_CELL_SEQ = "seq.dttd_zanik_open_cell"
        const val GUARD_DEATH_SEQ = "seq.dttd_ham_cut_scene_death"
        const val GUARD_POINT_SEQ = "seq.dttd_ham_guard_pointing"
        const val BOLT_SPOTANIM = "spotanim.dttd_bone_crossbowbolt_travel_sp_attack"
        const val BOLT_PROJANIM = "projanim.bolt"
        const val CROSSBOW_SOUND = "synth.dttd_bone_crossbow_sa"
        const val GUARD_DEATH_SOUND = "synth.human_death"

        const val CRACK_TICKS = 2
        const val DEATH_TICKS = 2
        const val CAPTOR_TICKS = 20

        const val GUARD_COUNT = 5
        const val FIRST = 0
        const val SECOND = 1
        const val PATROL = 2
        const val CHASER = 3
        const val LAST = 4

        const val FIRST_AWARENESS = 6
        const val FIRST_REACH = 3
        const val LAST_AWARENESS = 7
        const val CONE_RANGE = 7
        const val SHOT_RANGE = 8
        const val PATROL_SHOT_RANGE = 10
        const val CHASE_SHOT_RANGE = 1
        const val PATROL_PAUSE = 3
        const val PATROL_WEST_END = 2566
        const val PATROL_EAST_END = 2577
        const val EAST_SIDE_X = 2574

        /** One step across a wall edge, indexed by the wall's angle (west, north, east, south). */
        private val SIDE_X = intArrayOf(-1, 0, 1, 0)
        private val SIDE_Z = intArrayOf(0, 1, 0, -1)

        /** The room below the ladder, and the tile Zanik lands on beside it. */
        val STOREROOM_LANDING = CoordGrid(2568, 5185, 0)
        val ZANIK_LANDING = CoordGrid(2569, 5185, 0)
        private val ANCHOR = CoordGrid(2571, 5195, 0)

        /** Beside the hidden trapdoor in the hideout. */
        val TRAPDOOR_EXIT = TRAPDOOR_TILE.translate(0, 1)

        /** Where the guard who catches them listening steps out, behind the player. */
        val CAPTOR_TILE = CoordGrid(2571, 5201, 0)

        private val GUARD_POSTS =
            listOf(
                Post(CoordGrid(2570, 5189, 0), Direction.East, "npc.dttd_ham_guard_1", "varbit.dttd_guard_1_dead", "varbit.dttd_guard_1_warned"),
                Post(CoordGrid(2566, 5192, 0), Direction.South, "npc.dttd_ham_guard_2", "varbit.dttd_guard_2_dead", "varbit.dttd_guard_2_warned"),
                Post(CoordGrid(2567, 5195, 0), Direction.East, "npc.dttd_ham_guard_3", "varbit.dttd_guard_3_dead", null),
                Post(CoordGrid(2577, 5201, 0), Direction.South, "npc.dttd_ham_guard_4", "varbit.dttd_guard_4_dead", "varbit.dttd_guard_4_warned"),
                Post(CoordGrid(2571, 5202, 0), Direction.South, "npc.dttd_ham_guard_5", "varbit.dttd_guard_5_dead", "varbit.dttd_guard_5_warned"),
            )

        private val SPOTTED_LINES =
            listOf(
                "Hey! Ordinary members aren't allowed here!",
                "Hey! What are you doing here? Get out!",
                "What are you doing here!?",
                "You there! You aren't allowed in here!",
                "Hey! You there!",
            )

        private val MEETING_DOORS =
            listOf(
                Triple("loc.poordoor_double_inner", LISTEN_DOOR_LEFT, CoordGrid(2571, 5204, 0)),
                Triple("loc.poordoor_doubler_inner", LISTEN_DOOR_RIGHT, CoordGrid(2572, 5204, 0)),
            )
    }
}
