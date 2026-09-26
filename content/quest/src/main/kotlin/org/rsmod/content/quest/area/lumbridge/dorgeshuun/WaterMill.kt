package org.rsmod.content.quest.area.lumbridge.dorgeshuun

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.InstanceSpec
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.api.script.onNpcHit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpHeld5
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.quest.area.ardougne.undergroundpass.clearWalkStyle
import org.rsmod.content.quest.area.ardougne.undergroundpass.setWalkStyle
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.DARTOG
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.STAGE_MACHINE_SMASHED
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.STAGE_MILL
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.STAGE_REVIVED
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.STAGE_SHOWDOWN
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.STAGE_SIGMUND_FLED
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.STAGE_TEARS
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.ZANIK_CHATHEAD
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.ZANIK_CRATE
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.ZANIK_SHOWDOWN
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest
import org.rsmod.content.quest.manager.menu
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitType
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * The Lumbridge water mill, where the H.A.M. are putting together a dwarven drilling machine to
 * flood Dorgesh-Kaan.
 *
 * Up top: the delivery dwarf, the crates of machine parts and the empty crate Zanik hides in so the
 * player can carry her down the trapdoor. Below, for the fight, each player gets a private copy of
 * Jagex's pre-quest mill cellar (map square 31_79, drill intact): Sigmund and three guards, with
 * Zanik shooting at them. Sigmund prays against whatever hurts him until the guards are dead, when
 * Zanik's bolts make him keep praying against missiles; at his last hitpoint he vanishes with a
 * ring of life. Smashing the drill and walking out down the south tunnel ends the quest.
 *
 * After the quest the trapdoor leads to the real cellar (square 50_151) and its tunnel is open to
 * the Dorgeshuun mine, where Dartog stands guard.
 */
@Singleton
class WaterMill
@Inject
constructor(
    private val dttd: DeathToTheDorgeshuunQuest,
    private val follower: ZanikFollower,
    private val scenes: DttdScenes,
    private val lostTribe: LostTribeQuest,
    private val manager: InstanceManager,
    private val npcRepo: NpcRepository,
    private val locRepo: LocRepository,
    private val worldRepo: WorldRepository,
    private val collision: CollisionFlagMap,
    private val playerList: PlayerList,
    private val aiInteractions: AiPlayerInteractions,
    private val hitModifier: NpcHitModifier,
    private val death: NpcDeath,
    private val launcher: ProtectedAccessLauncher,
    private val random: GameRandom,
    private val mapClock: MapClock,
) : PluginScript() {

    private class Fight(val session: InstanceSession, val owner: PlayerUid, val dx: Int, val dz: Int) {
        var sigmund: Npc? = null
        val guards = mutableListOf<Npc>()
        var prayer: HitType? = null
        var zanikCooldown = 0
        var leaving = false
        var fleeing: Npc? = null

        fun at(world: CoordGrid): CoordGrid = CoordGrid(world.x + dx, world.z + dz, world.level)

        fun toWorld(coords: CoordGrid): CoordGrid = CoordGrid(coords.x - dx, coords.z - dz, coords.level)
    }

    private val fights = HashMap<PlayerUid, Fight>()
    private val rays by lazy { RayCastValidator(collision) }

    init {
        follower.onTick(::tick)
    }

    override fun ScriptContext.startup() {
        onOpNpc1(DWARF) { startDialogue(it.npc) { dwarf() } }
        onOpNpc1(MILL_MAN_CRATE) { startDialogue(it.npc) { millMan() } }
        onOpNpc1(MILL_MAN_NO_CRATE) { startDialogue(it.npc) { millMan() } }
        onOpLoc1(PARTS_CRATE) { searchPartsCrate() }
        onOpLoc1(EMPTY_CRATE) { searchEmptyCrate() }
        onOpLoc1(MILL_TRAPDOOR) { goDown() }
        onOpLoc1(CELLAR_LADDER) { climbUp() }
        onOpLoc1(DRILL) { smashDrill(it.loc.coords) }
        onOpLoc1(TUNNEL_MILLSIDE) { millTunnel() }
        onOpLoc1(TUNNEL_MINESIDE) { mineTunnel() }
        onOpHeld5(ZANIK_CRATE) { letZanikOut("Zanik climbs out of the crate.") }
        onPlayerSoftTimer(CRATE_TIMER) { crateTick(player) }
        onPlayerLogin { crateOnLogin(player) }

        onOpNpc1(DARTOG) { startDialogue(it.npc) { dartog() } }
        onOpNpc3(DARTOG) { lostTribe.run { guideThroughTunnels("Dartog", LostTribeQuest.MINES_ARRIVAL) } }
        onOpNpc4(DARTOG) { lostTribe.run { guideThroughTunnels("Dartog", LostTribeQuest.CELLAR_ARRIVAL) } }

        val sigmundType = npcType(SIGMUND)
        onModifyNpcHit(sigmundType) { modifySigmundHit(npc, hit) }
        onNpcHit(sigmundType) { if (npc.hitpoints <= 1) sigmundFlees(npc) }
        onNpcQueue(npcType(MILL_GUARD), DEATH_QUEUE) { guardDied(this) }
    }

    private fun npcType(name: String) =
        ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $name")

    private fun fightFor(player: Player): Fight? {
        val fight = fights[player.uid] ?: return null
        if (manager.sessionForPlayer(player) !== fight.session) {
            fights.remove(player.uid)
            return null
        }
        return fight
    }

    private fun owningFight(npc: Npc): Pair<Player, Fight>? {
        for ((uid, fight) in fights) {
            if (fight.sigmund === npc || npc in fight.guards) {
                val player = uid.resolve(playerList) ?: return null
                return player to fight
            }
        }
        return null
    }

    fun inMill(player: Player): Boolean = fightFor(player) != null

    private fun nearMill(coords: CoordGrid): Boolean = coords.level == 0 && coords.isWithinDistance(TRAPDOOR, MILL_RANGE)

    private suspend fun Dialogue.zanik(mood: dev.openrune.types.MesAnimType, text: String) =
        chatNpcSpecific("Zanik", ZANIK_CHATHEAD, mood, text)

    /* Up top */

    private suspend fun Dialogue.dwarf() {
        val disguised = dttd.wearsHamSet(player)
        if (disguised) {
            chatNpc(
                neutral,
                "Oh, it's another one of you funny people. Okay, grab one of the crates and take it downstairs. " +
                    "I told your boss how to put the parts together.",
            )
        } else {
            chatNpc(angry, "What do you want?")
        }
        while (true) {
            val options = buildList {
                add("What's in these crates?" to 1)
                add("Where's Sigmund?" to 2)
                add("Why are you helping the HAM people?" to 3)
                if (disguised) {
                    add("Okay." to 0)
                    add("There's no way I'm helping!" to 4)
                } else {
                    add("Thanks." to 0)
                }
            }
            when (menu(options)) {
                1 -> {
                    chatPlayer(quiz, "What's in these crates?")
                    if (disguised) {
                        chatNpc(
                            neutral,
                            "Didn't your boss tell you? It's a digging machine. Very special. Digs straight up, and " +
                                "can work without supervision when it's turned on.",
                        )
                    } else {
                        chatNpc(angry, "None of your business! Those funny pink-robed people told me to keep it secret!")
                    }
                }
                2 -> {
                    chatPlayer(quiz, "Where's Sigmund?")
                    if (disguised) {
                        chatNpc(
                            angry,
                            "What, your boss? He's downstairs waiting for you slackers to carry the rest of the " +
                                "crates down. So get a move on!",
                        )
                    } else {
                        chatNpc(neutral, "The guy who ordered all this? I don't see that's any of your business.")
                    }
                }
                3 -> {
                    chatPlayer(quiz, "Why are you helping the HAM people?")
                    if (disguised) {
                        chatNpc(
                            neutral,
                            "Is that what you people call yourselves? Well, it's certainly not out of the goodness " +
                                "of my heart. Your boss paid good money for this machine, plus my fee for " +
                                "transporting it all this way.",
                        )
                        chatNpc(angry, "So get to work and get it downstairs!")
                    } else {
                        chatNpc(neutral, "What, these odd pink-robed people? Oh, the normal reason. Because they paid me.")
                        chatPlayer(quiz, "But don't you know what they're planning to do?")
                        chatNpc(angry, "No, and I don't want to. Now go away!")
                    }
                    return
                }
                4 -> {
                    chatPlayer(angry, "There's no way I'm helping!")
                    chatNpc(angry, "Oh, honestly! If you're not here to help then get out of the way!")
                    return
                }
                else -> {
                    chatPlayer(neutral, if (disguised) "Okay." else "Thanks.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.millMan() {
        if (dttd.wearsHamSet(player)) {
            chatNpc(neutral, "These crates are heavy! I could do with some help if that's what you're here for.")
        } else {
            chatNpc(angry, "*pant* Go away, outsider! *pant*")
        }
    }

    private suspend fun ProtectedAccess.searchPartsCrate() {
        arriveDelay()
        val zanik = follower.following(player)
        if (zanik == null || dttd.stage(player) != STAGE_MILL) {
            mes("The crate is full of machine parts.")
            return
        }
        startDialogue(zanik) {
            chatNpc(angry, "Those must be the parts for the machine they're going to use to flood my city!")
            chatPlayer(quiz, "Hey... if I carried one of these crates I could slip downstairs and stop them!")
            chatNpc(
                angry,
                "Don't you dare go down there without me, ${player.displayName}! Find some way to get us both down " +
                    "there so I can face Sigmund myself!",
            )
        }
    }

    private suspend fun ProtectedAccess.searchEmptyCrate() {
        arriveDelay()
        val zanik = follower.following(player)
        if (zanik == null || dttd.stage(player) != STAGE_MILL || !nearMill(player.coords)) {
            mes("The crate is empty.")
            return
        }
        var hide = false
        startDialogue(zanik) {
            chatNpc(happy, "Are you thinking what I'm thinking, ${player.displayName}?")
            if (!choice2("I don't know, what are you thinking?", true, "I doubt it.", false)) {
                chatPlayer(neutral, "I doubt it.")
                return@startDialogue
            }
            chatPlayer(quiz, "I don't know, what are you thinking?")
            chatNpc(
                happy,
                "I could hide in one of those crates, and then you could carry me downstairs as if you were " +
                    "carrying some of the machinery.",
            )
            if (!choice2("Good idea.", true, "Not now.", false)) {
                chatPlayer(neutral, "Not now.")
                return@startDialogue
            }
            chatPlayer(happy, "Good idea.")
            if (!dttd.handsFree(player)) {
                chatNpc(
                    neutral,
                    "You ought to free both your hands so you can carry the crate. I'm afraid I might be heavy!",
                )
                return@startDialogue
            }
            hide = true
        }
        if (hide) {
            hideInCrate(zanik)
        }
    }

    private suspend fun ProtectedAccess.hideInCrate(zanik: Npc) {
        follower.waitHere(player)
        zanik.anim(ZANIK_JUMP_SEQ)
        soundSynth(CRATE_SOUND)
        delay(ZANIK_JUMP_TICKS)
        spotanim(LID_SPOTANIM)
        anim(LID_SEQ)
        follower.remove(player)
        if (invAdd(inv, ZANIK_CRATE).failure) {
            follower.spawn(player)
            mes("You need a free inventory space to carry the crate.")
            return
        }
        val slot = inv.indexOfFirst { it?.id == ZANIK_CRATE.asRSCM(RSCMType.OBJ) }
        if (slot >= 0) {
            invEquip(slot)
        }
        carryCrate()
        mes("Zanik climbs into the crate and you pick it up.")
    }

    private fun ProtectedAccess.carryCrate() {
        setWalkStyle(CRATE_WALK_SEQ, CRATE_READY_SEQ)
        player.softTimer(CRATE_TIMER, 1)
    }

    private fun carrying(player: Player): Boolean = ZANIK_CRATE in player.worn || ZANIK_CRATE in player.inv

    private fun ProtectedAccess.dropCrate(): Boolean {
        player.clearSoftTimer(CRATE_TIMER)
        clearWalkStyle()
        var removed = false
        for (inventory in listOf(player.worn, player.inv)) {
            while (ZANIK_CRATE in inventory && invDel(inventory, ZANIK_CRATE).success) {
                removed = true
            }
        }
        rebuildAppearance()
        return removed
    }

    private suspend fun ProtectedAccess.letZanikOut(message: String? = null, line: String? = null) {
        if (!dropCrate()) {
            return
        }
        val zanik = follower.spawn(player, ZANIK_SHOWDOWN)
        message?.let { mes(it) }
        if (line != null) {
            startDialogue(zanik) { chatNpc(angry, line) }
        }
    }

    private fun crateTick(player: Player) {
        if (!carrying(player)) {
            player.clearSoftTimer(CRATE_TIMER)
            return
        }
        player.softTimer(CRATE_TIMER, 1)
        if (nearMill(player.coords) || inMill(player) || player.isAccessProtected) {
            return
        }
        launcher.launch(player) {
            letZanikOut(line = "Why are we going away from the mill? We should get down there and save my city!")
        }
    }

    private fun crateOnLogin(player: Player) {
        if (!carrying(player)) {
            return
        }
        launcher.launch(player) {
            dropCrate()
            follower.returnToCellarIfDue(player)
        }
    }

    private suspend fun ProtectedAccess.goDown() {
        arriveDelay()
        val stage = dttd.stage(player)
        when {
            dttd.isComplete(player) -> {
                anim(CLIMB_DOWN_SEQ)
                delay(1)
                telejump(REAL_CELLAR_LANDING)
            }
            stage < STAGE_TEARS -> {
                mes("The trapdoor is locked.")
                soundSynth(LOCKED_SOUND)
            }
            stage in STAGE_SHOWDOWN..STAGE_MACHINE_SMASHED -> enterFight(returning = true)
            !dttd.wearsHamSet(player) || stage < STAGE_MILL ->
                dwarfSays("Where do you think you're going? Only people helping assemble the machine are allowed down there!")
            follower.isFollowing(player) -> dwarfSays("Hey! No goblins allowed down there!")
            !carrying(player) ->
                dwarfSays("Hey! What do you think you're doing going down there empty-handed? Get a crate and carry it down!")
            else -> enterFight(returning = false)
        }
    }

    private suspend fun ProtectedAccess.dwarfSays(text: String) {
        val dwarfType = DWARF.asRSCM(RSCMType.NPC)
        val dwarf =
            npcRepo.findAll(ZoneKey.from(player.coords), 2).firstOrNull {
                it.id == dwarfType && it.coords.isWithinDistance(player.coords, DWARF_RANGE)
            }
        if (dwarf == null) {
            mes(text)
            return
        }
        startDialogue(dwarf) { chatNpc(angry, text) }
    }

    /* The fight below */

    private suspend fun ProtectedAccess.enterFight(returning: Boolean) {
        if (manager.sessionForPlayer(player) != null) {
            mes("You are already inside an instance.")
            return
        }
        anim(CLIMB_DOWN_SEQ)
        delay(1)
        val area =
            InstanceArea.copyRegions(
                regionIds = listOf(MILL_REGION),
                level = 0,
                enterCoord = RegionLocal(0, ANCHOR.mx, ANCHOR.mz, ANCHOR.lx, ANCHOR.lz),
                exitCoord = SURFACE_EXIT,
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
        val fight = Fight(session, player.uid, enter.x - ANCHOR.x, enter.z - ANCHOR.z)
        fights[player.uid] = fight
        telejump(fight.at(CELLAR_LANDING), TeleportType.Exempt)
        if (dttd.stage(player) >= STAGE_SIGMUND_FLED) {
            breakDrill(fight)
        }
        dropCrate()
        val zanik = follower.spawn(player, ZANIK_SHOWDOWN, at = fight.at(ZANIK_LANDING))
        if (!returning) {
            zanik.anim(ZANIK_JUMP_SEQ)
        }
        spawnFoes(fight)
        dttd.advanceTo(this, STAGE_SHOWDOWN)
        val sigmund = fight.sigmund
        when {
            returning ->
                startDialogue(zanik) {
                    chatNpc(
                        happy,
                        "${player.displayName}! Thank goodness you're all right! Now let's finish off Sigmund and his " +
                            "cronies before they flood the city!",
                    )
                }
            sigmund != null -> {
                startDialogue(sigmund) {
                    chatNpc(
                        angry,
                        "${player.displayName}! You and your goblin friend have interfered for the last time! Guards, " +
                            "kill them!",
                    )
                }
                startDialogue(zanik) { chatNpc(angry, "I won't let you destroy my home, Sigmund!") }
            }
        }
        for (guard in fight.guards) {
            guard.opPlayer2(player, aiInteractions)
        }
    }

    private fun ProtectedAccess.spawnFoes(fight: Fight) {
        if (dttd.stage(player) >= STAGE_SIGMUND_FLED) {
            return
        }
        val remaining = GUARDS_TOTAL - player.dttdMillGuardsDead
        for (tile in GUARD_TILES.take(remaining.coerceAtLeast(0))) {
            fight.guards += spawn(fight, MILL_GUARD, tile)
        }
        fight.sigmund = spawn(fight, SIGMUND, SIGMUND_TILE)
        fight.prayer = if (fight.guards.isEmpty()) HitType.Ranged else null
        updateHeadIcon(fight)
    }

    private fun spawn(fight: Fight, type: String, tile: CoordGrid): Npc {
        val npc = Npc(type, fight.at(tile))
        npcRepo.add(npc, Int.MAX_VALUE)
        npc.respawns = false
        manager.attachNpc(fight.session.id, npc)
        return npc
    }

    private fun tick(player: Player, zanik: Npc) {
        val fight = fightFor(player) ?: return
        if (fight.leaving) {
            return
        }
        val fleeing = fight.fleeing
        if (fleeing != null) {
            if (launcher.launch(player) { sigmundVanishes(fleeing) }) {
                fight.fleeing = null
            }
            return
        }
        val stage = dttd.stage(player)
        if (stage == STAGE_SHOWDOWN) {
            zanikFights(player, fight, zanik)
            val sigmund = fight.sigmund
            if (sigmund != null && sigmund.isSlotAssigned && sigmund.mode != NpcMode.OpPlayer2 && !player.isAccessProtected) {
                sigmund.opPlayer2(player, aiInteractions)
            }
        }
        val world = fight.toWorld(player.coords)
        if (world.z <= TUNNEL_EXIT_Z && world.x in TUNNEL_EXIT_X && !player.isAccessProtected) {
            when {
                stage >= STAGE_MACHINE_SMASHED -> {
                    fight.leaving = true
                    launcher.launch(player) { leaveThroughTunnel(fight, zanik) }
                }
                stage == STAGE_SIGMUND_FLED ->
                    launcher.launch(player) {
                        startDialogue(zanik) {
                            chatNpc(
                                angry,
                                "We can't just leave that machine there for someone else to complete! We should " +
                                    "smash it beyond repair before returning home!",
                            )
                        }
                        walk(fight.at(TUNNEL_TURN_BACK))
                    }
            }
        }
    }

    /** Zanik picks off the guards, then keeps Sigmund praying against her bolts. */
    private fun zanikFights(player: Player, fight: Fight, zanik: Npc) {
        if (fight.zanikCooldown > 0) {
            fight.zanikCooldown--
            return
        }
        fight.guards.removeAll { !it.isSlotAssigned || it.hitpoints <= 0 }
        val sigmund = fight.sigmund?.takeIf { it.isSlotAssigned }
        val target =
            fight.guards
                .filter { it.coords.isWithinDistance(zanik.coords, ZANIK_RANGE) && sees(zanik.coords, it.coords) }
                .minByOrNull { it.coords.chebyshevDistance(zanik.coords) }
                ?: sigmund?.takeIf { it.coords.isWithinDistance(zanik.coords, ZANIK_RANGE) && sees(zanik.coords, it.coords) }
                ?: return
        fight.zanikCooldown = ZANIK_ATTACK_TICKS
        zanik.faceNpc(target)
        zanik.anim(ZANIK_FIRE_SEQ)
        worldRepo.soundArea(zanik, ZANIK_ATTACK_SOUND, radius = SOUND_RADIUS)
        val projType = ServerCacheManager.getProjectile(BOLT_PROJANIM.asRSCM(RSCMType.PROJANIM)) ?: return
        val flight = ProjAnim.fromNpcToNpc(zanik, target, BOLT_SPOTANIM.asRSCM(RSCMType.SPOTANIM), projType)
        worldRepo.projAnim(flight)
        if (target === sigmund) {
            if (fight.prayer != HitType.Ranged) {
                fight.prayer = HitType.Ranged
                updateHeadIcon(fight)
                target.say("Saradomin protect me from this foul goblin!")
            }
            target.queueHit(zanik, flight.serverCycles, HitType.Ranged, 0, hitModifier)
            return
        }
        target.queueHit(zanik, flight.serverCycles, HitType.Ranged, random.of(0..ZANIK_MAX_HIT), hitModifier)
    }

    private fun sees(from: CoordGrid, to: CoordGrid): Boolean =
        rays.hasLineOfSight(from, to, extraFlag = CollisionFlag.BLOCK_PLAYERS)

    private suspend fun guardDied(access: org.rsmod.api.npc.access.StandardNpcAccess) {
        val guard = access.npc
        val owner = owningFight(guard)
        death.deathNoDrops(access)
        val (player, fight) = owner ?: return
        fight.guards.remove(guard)
        if (player.dttdMillGuardsDead < GUARDS_TOTAL) {
            player.dttdMillGuardsDead = player.dttdMillGuardsDead + 1
        }
    }

    private fun modifySigmundHit(sigmund: Npc, hit: org.rsmod.game.hit.HitBuilder) {
        val (_, fight) = owningFight(sigmund) ?: return
        if (!hit.isFromPlayer) {
            return
        }
        val style = hit.type
        if (style == fight.prayer) {
            hit.damage = 0
        }
        if (hit.damage >= sigmund.hitpoints) {
            hit.damage = (sigmund.hitpoints - 1).coerceAtLeast(0)
        }
        val phaseTwo = fight.guards.none { it.isSlotAssigned }
        if (!phaseTwo && style != fight.prayer && style in PRAYER_LINES) {
            fight.prayer = style
            updateHeadIcon(fight)
            sigmund.say(PRAYER_LINES.getValue(style))
        }
    }

    private fun updateHeadIcon(fight: Fight) {
        val sigmund = fight.sigmund ?: return
        val index =
            when (fight.prayer) {
                HitType.Melee -> 0
                HitType.Ranged -> 1
                HitType.Magic -> 2
                else -> null
            }
        if (index == null) {
            sigmund.clearHeadIcon(HEADICON_SLOT)
        } else {
            sigmund.setHeadIcon(HEADICON_SLOT, HEADICON_GRAPHIC, index)
        }
    }

    private fun sigmundFlees(sigmund: Npc) {
        val (player, fight) = owningFight(sigmund) ?: return
        if (fight.sigmund !== sigmund || dttd.stage(player) != STAGE_SHOWDOWN) {
            return
        }
        fight.sigmund = null
        fight.fleeing = sigmund
        sigmund.clearHeadIcon(HEADICON_SLOT)
        sigmund.mode = NpcMode.None
    }

    private suspend fun ProtectedAccess.sigmundVanishes(sigmund: Npc) {
        startDialogue(sigmund) {
            chatNpc(
                angry,
                "Curse you, ${player.displayName}, and your goblin friend! You win this time, but we will meet again!",
            )
        }
        sigmund.anim(SIGMUND_VANISH_SEQ)
        sigmund.spotanim(SIGMUND_VANISH_SPOTANIM)
        soundSynth(VANISH_SOUND)
        delay(VANISH_TICKS)
        if (sigmund.isSlotAssigned) {
            npcRepo.del(sigmund, Int.MAX_VALUE)
        }
        dttd.advanceTo(this, STAGE_SIGMUND_FLED)
        val zanik = follower.following(player) ?: return
        startDialogue(zanik) {
            chatNpc(shocked, "What happened? He vanished!")
            chatPlayer(
                neutral,
                "He must have had a ring of life! It looks like he got away from us this time, Zanik.",
            )
            chatNpc(
                happy,
                "The important thing is we stopped him! Now let's smash that machine and then get back to " +
                    "Dorgesh-Kaan!",
            )
        }
    }

    private suspend fun ProtectedAccess.smashDrill(drill: CoordGrid) {
        arriveDelay()
        val fight = fightFor(player) ?: return
        when (dttd.stage(player)) {
            STAGE_SHOWDOWN -> startDialogue { mesbox("You should deal with Sigmund first.") }
            STAGE_SIGMUND_FLED -> {
                faceSquare(drill)
                anim(SMASH_SEQ)
                soundSynth(SMASH_SOUND)
                val type = ServerCacheManager.getObject(DRILL.asRSCM(RSCMType.LOC)) ?: return
                val loc = locRepo.findExact(drill, type) ?: return
                val breaking = ServerCacheManager.getObject(DRILL_BREAKING.asRSCM(RSCMType.LOC)) ?: return
                locRepo.change(loc, breaking, Int.MAX_VALUE)
                spotanimMap(worldRepo, SMOKE_SPOTANIM, drill.translate(1, 1))
                delay(SMASH_TICKS)
                breakDrill(fight)
                dttd.advanceTo(this, STAGE_MACHINE_SMASHED)
                val zanik = follower.following(player) ?: return
                startDialogue(zanik) {
                    chatNpc(
                        happy,
                        "Now let's get back to Dorgesh-Kaan to tell them what happened! We can go south along the " +
                            "tunnel Sigmund has dug.",
                    )
                }
            }
        }
    }

    private fun breakDrill(fight: Fight) {
        val broken = ServerCacheManager.getObject(DRILL_BROKEN.asRSCM(RSCMType.LOC)) ?: return
        for (name in listOf(DRILL, DRILL_BREAKING)) {
            val type = ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC)) ?: continue
            val loc = locRepo.findExact(fight.at(DRILL_TILE), type) ?: continue
            locRepo.change(loc, broken, Int.MAX_VALUE)
        }
    }

    private suspend fun ProtectedAccess.climbUp() {
        arriveDelay()
        val fight = fightFor(player)
        if (fight == null) {
            anim(CLIMB_UP_SEQ)
            delay(1)
            telejump(SURFACE_EXIT)
            return
        }
        val zanik = follower.following(player)
        val stage = dttd.stage(player)
        if (zanik != null && stage in STAGE_SHOWDOWN..STAGE_MACHINE_SMASHED) {
            var leave = false
            startDialogue(zanik) {
                if (stage == STAGE_SHOWDOWN) {
                    chatNpc(worried, "Don't leave me, ${player.displayName}! I can't defeat Sigmund without you!")
                } else {
                    chatNpc(
                        neutral,
                        "There's no need to go that way, ${player.displayName}! We can go south along the tunnel to " +
                            "the Dorgeshuun mine.",
                    )
                }
                if (choice2("Okay, I'll stay!", false, "Don't worry, I'll be back!", true)) {
                    chatPlayer(neutral, "Don't worry, I'll be back!")
                    chatNpc(worried, "Hurry, ${player.displayName}!")
                    leave = true
                } else {
                    chatPlayer(neutral, "Okay, I'll stay!")
                }
            }
            if (!leave) {
                return
            }
        }
        anim(CLIMB_UP_SEQ)
        delay(1)
        follower.remove(player)
        leaveFight()
        telejump(SURFACE_EXIT)
    }

    private fun ProtectedAccess.leaveFight() {
        val fight = fights.remove(player.uid) ?: return
        manager.leave(player, fight.session, mapClock)
    }

    private suspend fun ProtectedAccess.leaveThroughTunnel(fight: Fight, zanik: Npc) {
        startDialogue(zanik) {
            chatNpc(happy, "I'll show you the way through to the city. Wait till we tell Mistag about what happened!")
        }
        follower.remove(player)
        leaveFight()
        with(scenes) { homecoming() }
    }

    private suspend fun ProtectedAccess.millTunnel() {
        arriveDelay()
        if (dttd.isComplete(player)) {
            squeeze(MINESIDE_LANDING)
            return
        }
        val zanik = follower.following(player) ?: return
        startDialogue(zanik) {
            chatNpc(
                angry,
                "This must lead to the Dorgeshuun mines! Sigmund must mean the water to run through here and flood " +
                    "the city!",
            )
            chatNpc(angry, "We've got to stop him, ${player.displayName}!")
        }
    }

    private suspend fun ProtectedAccess.mineTunnel() {
        arriveDelay()
        if (dttd.isComplete(player)) {
            squeeze(MILLSIDE_LANDING)
        }
    }

    private suspend fun ProtectedAccess.squeeze(dest: CoordGrid) {
        anim(CRAWL_SEQ)
        soundSynth(SQUEEZE_SOUND)
        delay(CRAWL_TICKS)
        telejump(dest)
    }

    /* Zanik */

    suspend fun ProtectedAccess.talkToZanik(npc: Npc) {
        val stage = dttd.stage(player)
        startDialogue(npc) {
            when {
                stage == STAGE_SHOWDOWN -> chatNpc(angry, "Let's get them, ${player.displayName}!")
                stage == STAGE_SIGMUND_FLED ->
                    chatNpc(
                        angry,
                        "Quick, ${player.displayName}, let's smash that drilling machine before Sigmund returns! " +
                            "Then we can return to Dorgesh- Kaan for a hero's welcome!",
                    )
                stage >= STAGE_MACHINE_SMASHED && inMill(player) ->
                    chatNpc(
                        happy,
                        "We did it, ${player.displayName}! My city is safe! Now we can follow that tunnel south back " +
                            "to the mines!",
                    )
                stage >= STAGE_MACHINE_SMASHED ->
                    chatNpc(neutral, "Where did you go? We should follow the tunnel south to Dorgesh-Kaan!")
                nearMill(player.coords) -> {
                    chatNpc(
                        neutral,
                        "We're not too late! They're still assembling the machine. Now we've got to find some way to " +
                            "get down there and stop them.",
                    )
                    beforeTheMill(atMill = true)
                }
                else -> {
                    chatNpc(
                        angry,
                        "Let's go, ${player.displayName}! We've got to get to the Lumbridge water mill so we can foil " +
                            "Sigmund's plan!",
                    )
                    beforeTheMill(atMill = false)
                }
            }
        }
    }

    private suspend fun Dialogue.beforeTheMill(atMill: Boolean) {
        val options = buildList {
            add((if (atMill) "How can we get down there?" else "Let's go!") to 0)
            add("I need to do something else for a bit." to 1)
            add("What did Sigmund do to you?" to 2)
            add("What's it like being dead?" to 3)
        }
        when (menu(options)) {
            0 ->
                if (atMill) {
                    chatPlayer(quiz, "How can we get down there?")
                    chatNpc(neutral, "I don't know. But there must be something around here we can use...")
                } else {
                    chatPlayer(happy, "Let's go!")
                }
            1 -> {
                chatPlayer(neutral, "I need to do something else for a bit.")
                chatNpc(
                    worried,
                    "Do it quickly, ${player.displayName}! We've got to stop Sigmund from destroying my city!",
                )
                follower.sendHome(player)
            }
            2 -> {
                chatPlayer(quiz, "What did Sigmund do to you?")
                chatNpc(sad, "It was horrible, ${player.displayName}!")
                chatNpc(
                    sad,
                    "They didn't just k-kill me quickly. They were enjoying themselves. The light was so bright I " +
                        "couldn't see but I could hear them laughing...",
                )
                chatNpc(angry, "Let's get them, ${player.displayName}! I need to save my city and I want to get my revenge!")
            }
            3 -> {
                chatPlayer(quiz, "What's it like being dead?")
                chatNpc(neutral, "It was... it wasn't like anything.")
                chatNpc(sad, "One moment Sigmund and his thugs were... you know...")
                chatNpc(neutral, "And then suddenly I was down in the cave and you were here. There was nothing in between.")
                chatNpc(
                    neutral,
                    "We Dorgeshuun don't believe in an afterlife. At least, if there is one we don't claim to know about " +
                        "it. And if anything happened to me while I was dead then I don't remember it.",
                )
                chatNpc(confused, "And now my life's a mystery too. What was that destiny Juna talked about?")
                chatNpc(angry, "But there's no time for that now. Let's get to the water mill!")
            }
        }
    }

    /** Kazgar's greeting while Zanik is in a hurry to reach the mill; true when it replaced his own. */
    suspend fun Dialogue.kazgarInterrupted(): Boolean {
        if (dttd.stage(player) !in STAGE_REVIVED..STAGE_MILL || !follower.isFollowing(player)) {
            return false
        }
        chatNpc(happy, "Hello, Zanik! Hello, surface-dweller! Would you like me to lead you to the mines?")
        zanik(
            angry,
            "We've not got time to talk now, Kazgar! ${player.displayName}, you've got to take me to the Lumbridge " +
                "water mill at once!",
        )
        return true
    }

    private suspend fun Dialogue.dartog() {
        chatNpc(neutral, "Hello, surface-dweller. Can I help you?")
        when (
            menu(
                "Who are you?" to 1,
                "Can you show me the way to the mines?" to 2,
                "Can you show me the way to Lumbridge Castle cellar?" to 3,
                "I'm fine, thanks." to 0,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "Who are you?")
                chatNpc(
                    happy,
                    "The council posted me here to guard this new tunnel. I can also give you directions through the " +
                        "tunnels. A hero like you is always welcome in our mines!",
                )
            }
            2 -> {
                chatPlayer(quiz, "Can you show me the way to the mines?")
                chatNpc(happy, "Of course! You're always welcome in our mines!")
                lostTribe.run { access.guideThroughTunnels("Dartog", LostTribeQuest.MINES_ARRIVAL) }
            }
            3 -> {
                chatPlayer(quiz, "Can you show me the way to Lumbridge Castle cellar?")
                chatNpc(happy, "Of course!")
                lostTribe.run { access.guideThroughTunnels("Dartog", LostTribeQuest.CELLAR_ARRIVAL) }
            }
            else -> chatPlayer(neutral, "I'm fine, thanks.")
        }
    }

    companion object {
        const val KEY = "dttd_water_mill"
        private const val RECLAIM_TICKS = 100
        private const val GRACE_TICKS = 50
        private const val MILL_REGION = (31 shl 8) or 79

        const val DWARF = "npc.dttd_delivery_dwarf"
        const val MILL_MAN_CRATE = "npc.dttd_mill_man_crate"
        const val MILL_MAN_NO_CRATE = "npc.dttd_mill_man_nocrate"
        const val SIGMUND = "npc.dttd_sigmund_melee"
        const val MILL_GUARD = "npc.dttd_ham_guard_mill"

        const val PARTS_CRATE = "loc.dttd_machine_parts_crate_small_full_multi"
        const val EMPTY_CRATE = "loc.dttd_machine_crate_open_with_lid"
        const val MILL_TRAPDOOR = "loc.dttd_mill_trapdoor"
        const val CELLAR_LADDER = "loc.dttd_ladder_up_out_of_mill_basement"
        const val DRILL = "loc.dttd_drilling_machine"
        const val DRILL_BREAKING = "loc.dttd_anim_drill_machine_being_broken"
        const val DRILL_BROKEN = "loc.dttd_drilling_machine_broken"
        const val TUNNEL_MILLSIDE = "loc.dttd_tunnel_millside"
        const val TUNNEL_MINESIDE = "loc.dttd_tunnel_mineside"

        const val CRATE_TIMER = "timer.dttd_crate"
        const val DEATH_QUEUE = "queue.death"

        const val CRATE_READY_SEQ = "seq.dttd_carrying_crate_ready"
        const val CRATE_WALK_SEQ = "seq.dttd_carrying_crate_walk"
        const val ZANIK_JUMP_SEQ = "seq.dttd_zanik_jump_into_box"
        const val LID_SEQ = "seq.dttd_arm_puts_lid_on_box"
        const val LID_SPOTANIM = "spotanim.dttd_zanik_arm_puts_lid_on_box"
        const val ZANIK_FIRE_SEQ = "seq.dttd_zanik_firing_crossbow"
        const val SIGMUND_VANISH_SEQ = "seq.dttd_sigmund_vanishing"
        const val SIGMUND_VANISH_SPOTANIM = "spotanim.dttd_sigmund_vanish"
        const val SMASH_SEQ = "seq.dttd_machine_smashing"
        const val SMOKE_SPOTANIM = "spotanim.dttd_drill_machine_smashing_smoke"
        const val CLIMB_DOWN_SEQ = "seq.human_pickupfloor"
        const val CLIMB_UP_SEQ = "seq.human_reachforladder"
        const val CRAWL_SEQ = "seq.human_crawling"
        const val BOLT_SPOTANIM = "spotanim.dttd_bone_crossbowbolt_travel"
        const val BOLT_PROJANIM = "projanim.bolt"

        const val CRATE_SOUND = "synth.dttd_zanik_gets_in_crate"
        const val ZANIK_ATTACK_SOUND = "synth.dttd_zanik_attack"
        const val SMASH_SOUND = "synth.dttd_smash_machine"
        const val VANISH_SOUND = "synth.teleport_all"
        const val LOCKED_SOUND = "synth.locked"
        const val SQUEEZE_SOUND = "synth.squeeze_through_rocks"

        const val HEADICON_SLOT = 0
        const val HEADICON_GRAPHIC = 440
        const val SOUND_RADIUS = 10

        const val MILL_RANGE = 12
        const val DWARF_RANGE = 12
        const val ZANIK_JUMP_TICKS = 2
        const val ZANIK_ATTACK_TICKS = 5
        const val ZANIK_RANGE = 10
        const val ZANIK_MAX_HIT = 8
        const val GUARDS_TOTAL = 3
        const val VANISH_TICKS = 3
        const val SMASH_TICKS = 3
        const val CRAWL_TICKS = 2

        val PRAYER_LINES =
            mapOf(
                HitType.Melee to "Saradomin protect me from melee!",
                HitType.Magic to "Saradomin protect me from magic!",
                HitType.Ranged to "Saradomin protect me from ranged!",
            )

        /** The mill trapdoor, in the yard of the farm east of the river. */
        val TRAPDOOR = CoordGrid(3230, 3286, 0)
        val SURFACE_EXIT = CoordGrid(3230, 3287, 0)

        /** The pre-quest copy of the mill cellar (square 31_79): beside its ladder and the drill. */
        private val ANCHOR = CoordGrid(2010, 5085, 0)
        val CELLAR_LANDING = CoordGrid(2023, 5087, 0)
        val ZANIK_LANDING = CoordGrid(2022, 5088, 0)
        val DRILL_TILE = CoordGrid(1997, 5087, 0)
        val SIGMUND_TILE = CoordGrid(2002, 5089, 0)
        val GUARD_TILES = listOf(CoordGrid(2003, 5091, 0), CoordGrid(2004, 5087, 0), CoordGrid(2001, 5085, 0))

        /** The south end of the copy's tunnel, and a step back up it. */
        const val TUNNEL_EXIT_Z = 5060
        val TUNNEL_EXIT_X = 2010..2035
        val TUNNEL_TURN_BACK = CoordGrid(2017, 5068, 0)

        /** The real cellar after the quest, and either end of the tunnel to the mine. */
        val REAL_CELLAR_LANDING = CoordGrid(3239, 9695, 0)
        val MILLSIDE_LANDING = CoordGrid(3244, 9663, 0)
        val MINESIDE_LANDING = CoordGrid(3245, 9648, 0)
    }
}
