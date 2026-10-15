package org.rsmod.content.quest.area.burghderott.inaid

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.AMBUSH_ROUTE
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.IVAN_BODY
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.IVAN_FOOD
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.IVAN_FOOD_HEAL
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.IVAN_HELM
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.IVAN_LEGS
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.IVAN_SICKLE
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_IVAN_DELIVERED
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_PARTY_TOLD
import org.rsmod.content.quest.area.paterdomus.priestinperil.PaterdomusDoors
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitType
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Escorting Ivan Strom to Paterdomus.
 *
 * Talking to Ivan once Polmafi or Radigad has been told offers the two routes; whichever is taken
 * ends in an ambush in a private copy of a Temple Trekking swamp clearing: two level 75 vampyre
 * juvinates on the short route through Canifis, four level 50s on the long route through the
 * swamp (OSRS wiki). Ivan follows the player. Every juvinate goes for Ivan until the player hits
 * it, after which it fights the player instead.
 *
 * Ivan has 40 hitpoints. Each piece of steel armour he was given makes a juvinate's blow less
 * likely to land, the silver sickle lets him strike back, and he eats a portion of his food
 * whenever he drops to half health. With no food left and a fifth of his health, he teleports back
 * to the old hideout and the attempt has failed: dealing with the juvinates then achieves nothing,
 * and the player must take the escape path. Leaving, dying or logging out ends the attempt too;
 * Ivan keeps his armour, sickle and whatever food he has not eaten for the next one.
 *
 * The quest moves on only when the last juvinate is dealt with while Ivan is still there.
 */
@Singleton
class IvanEscort
@Inject
constructor(
    private val iaom: InAidOfTheMyrequeQuest,
    private val copies: BurghCopies,
    private val fights: VampyreFights,
    private val doors: PaterdomusDoors,
    private val aiInteractions: AiPlayerInteractions,
    private val npcHitModifier: NpcHitModifier,
    private val random: GameRandom,
    private val playerList: PlayerList,
    private val clock: MapClock,
    private val routeFactory: RouteFactory,
) : PluginScript() {

    enum class Route(val varValue: Int, val juvinate: String, val count: Int, val maxHit: Int, val leaving: String) {
        Short(0, "npc.burgh_ivan_temple_vampire_juve_1", 2, 10, "You decide to take the route through Canifis."),
        Long(1, "npc.burgh_ivan_temple_vampire_juve_2", 4, 7, "You decide to take the long route through the swamp."),
    }

    internal class Escort(val owner: PlayerUid, val copy: BurghCopies.Copy, val route: Route, val ivan: Npc) {
        val juvinates = ArrayList<Npc>()
        val engaged = HashSet<Npc>()
        val swingTicks = HashMap<Npc, Int>()
        var ivanTicks = 0
        var escaped = false
        var finished = false
    }

    private val escorts = HashMap<PlayerUid, Escort>()

    override fun ScriptContext.startup() {
        onPlayerSoftTimer(ESCORT_TIMER) { tick(player) }
        onPlayerLogout { endAttempt(player) }
        for (ivan in IVAN_AMBUSH) {
            onOpNpc1(ivan) {
                startDialogue(it.npc) {
                    if (random.of(0, 1) == 0) {
                        chatNpc(worried, "Let's get out of this place... I'm scared!")
                    } else {
                        chatNpc(confused, "Did those vampyres say they wanted to 'eat' me?")
                    }
                }
            }
            onOpNpcU(ivan) {
                startDialogue(it.npc) { chatNpc(worried, "Food is the very last thing on my mind right now... let's get out of here!") }
            }
            onNpcQueue(npcType(ivan), "queue.death") { escortOf(npc)?.let { escape(it) } }
        }
        onOpLoc1(ESCAPE_PATH) { escapePath() }
        onOpLoc1(CONTINUE_PATH) { continuePath() }
    }

    internal fun escortOf(player: Player): Escort? = escorts[player.uid]

    private fun escortOf(ivan: Npc): Escort? = escorts.values.firstOrNull { it.ivan === ivan }

    suspend fun Dialogue.setOff() {
        chatNpc(happy, "Hey there. I hear you're going to be taking me to the temple.")
        chatPlayer(neutral, "That's right. We should get going soon.")
        chatNpc(happy, "Very well. I'm ready, lead the way.")
        val route =
            choice2(
                "Route 1: the short route, through Canifis.",
                Route.Short,
                "Route 2: the long route, through the swamp.",
                Route.Long,
                title = "Which way will you take Ivan to Paterdomus?",
            )
        access.begin(route)
    }

    private suspend fun ProtectedAccess.begin(route: Route) {
        if (iaom.stage(player) != STAGE_PARTY_TOLD || escorts.containsKey(player.uid)) {
            return
        }
        VarPlayerIntMapSetter.set(player, AMBUSH_ROUTE, route.varValue)
        mes(route.leaving)
        fadeOverlay(startColour = 0, startTransparency = 255, endColour = 0, endTransparency = 0, clientDuration = FADE_DURATION)
        delay(FADE_TICKS)
        val copy = with(copies) { enter(INSTANCE_KEY, TREK_START, ESCAPE_EXIT) }
        closeFadeOverlay()
        copy ?: return
        val ivanType = if (player.vars[IVAN_SICKLE] == 1) IVAN_WITH_SICKLE else IVAN_PLAIN
        val ivan = copies.spawn(copy, ivanType, IVAN_START, Direction.North)
        ivan.assignSpawnOwner(player, clock.cycle)
        ivan.facePlayer(player)
        ivan.mode = NpcMode.PlayerFollow
        val escort = Escort(player.uid, copy, route, ivan)
        escorts[player.uid] = escort
        val listener =
            object : VampyreFights.Listener {
                override fun dispatched(npc: Npc, owner: PlayerUid) = juvinateGone(npc, owner)

                override fun hitByPlayer(npc: Npc) {
                    escort.engaged += npc
                }
            }
        for (tile in JUVINATE_SPAWNS.take(route.count)) {
            val juvinate = copies.spawn(copy, route.juvinate, tile, Direction.South)
            fights.own(juvinate, player, listener)
            escort.juvinates += juvinate
        }
        escort.juvinates.first().say("There's that tasty morsel Ivan! Time for some food...")
        softTimer(ESCORT_TIMER, 1)
    }

    internal fun tick(player: Player) {
        val escort = escorts[player.uid] ?: return
        if (escort.finished) {
            return
        }
        if (player.coords.level != escort.copy.at(TREK_START).level || player.coords.chebyshevDistance(escort.copy.at(TREK_START)) > LEASH) {
            endAttempt(player)
            doors.launchWhenFree(player.uid) { with(copies) { leave() } }
            return
        }
        for (juvinate in escort.juvinates.toList()) {
            if (!juvinate.isSlotAssigned) continue
            if (escort.escaped || juvinate in escort.engaged) {
                if (juvinate.mode != NpcMode.OpPlayer2) juvinate.opPlayer2(player, aiInteractions)
                continue
            }
            attackIvan(escort, juvinate)
        }
        if (!escort.escaped) {
            ivanActs(player, escort)
        }
        player.softTimer(ESCORT_TIMER, 1)
    }

    private fun attackIvan(escort: Escort, juvinate: Npc) {
        val ivan = escort.ivan
        if (juvinate.coords.chebyshevDistance(ivan.coords) > 1) {
            // A straight walk snags on the clearing's scenery from most spawns, so they path round it.
            val route = routeFactory.create(juvinate.avatar, ivan.avatar).map { CoordGrid(it.x, it.z, it.level) }
            if (route.isNotEmpty()) juvinate.walk(route) else juvinate.walk(ivan.coords)
            return
        }
        juvinate.faceNpc(ivan)
        val ticks = (escort.swingTicks[juvinate] ?: 0) + 1
        if (ticks < SWING_TICKS) {
            escort.swingTicks[juvinate] = ticks
            return
        }
        escort.swingTicks[juvinate] = 0
        juvinate.anim(JUVINATE_ATTACK_SEQ)
        val owner = escort.owner.resolve(playerList)
        val damage = if (owner != null && lands(owner)) random.of(1, escort.route.maxHit) else 0
        ivan.queueHit(juvinate, HIT_DELAY, HitType.Melee, damage, npcHitModifier)
    }

    /** Steel armour makes a blow on Ivan less likely to land: [HIT_CHANCE]% less per piece. */
    private fun lands(player: Player): Boolean {
        val pieces = listOf(IVAN_HELM, IVAN_BODY, IVAN_LEGS).count { player.vars[it] == 1 }
        val chance = BASE_HIT_CHANCE - pieces * HIT_CHANCE
        return random.of(1, 100) <= chance
    }

    private fun ivanActs(player: Player, escort: Escort) {
        val ivan = escort.ivan
        if (!ivan.isSlotAssigned) {
            return
        }
        val food = player.vars[IVAN_FOOD]
        if (ivan.hitpoints * 2 <= ivan.baseHitpointsLvl && food > 0) {
            val heal = player.vars[IVAN_FOOD_HEAL].coerceAtLeast(1)
            ivan.hitpoints = minOf(ivan.baseHitpointsLvl, ivan.hitpoints + heal)
            VarPlayerIntMapSetter.set(player, IVAN_FOOD, food - 1)
            player.mes("Ivan : Urgh, just had to eat some food, I only have ${food - 1} left.")
        } else if (ivan.hitpoints * ESCAPE_FRACTION <= ivan.baseHitpointsLvl && food == 0) {
            escape(escort)
            return
        }
        if (player.vars[IVAN_SICKLE] != 1) {
            return
        }
        val target = escort.juvinates.firstOrNull { it.isSlotAssigned && it.coords.chebyshevDistance(ivan.coords) <= 1 } ?: return
        if (++escort.ivanTicks < SWING_TICKS) {
            return
        }
        escort.ivanTicks = 0
        ivan.faceNpc(target)
        ivan.anim(IVAN_ATTACK_SEQ)
        target.queueHit(ivan, HIT_DELAY, HitType.Melee, random.of(0, IVAN_MAX_HIT), npcHitModifier)
    }

    private fun escape(escort: Escort) {
        if (escort.escaped || escort.finished) {
            return
        }
        escort.escaped = true
        val ivan = escort.ivan
        ivan.say("I'm getting badly beaten up here, I'm going to die if I stay... see you back at the old base.")
        ivan.spotanim(ESCAPE_SPOT)
        copies.remove(ivan)
        escort.owner.resolve(playerList)?.mes("Ivan has fled back to the Myreque's old hideout. Take the escape path out of here and try again.")
    }

    private fun juvinateGone(npc: Npc, owner: PlayerUid) {
        val escort = escorts[owner] ?: return
        escort.juvinates.remove(npc)
        escort.engaged.remove(npc)
        if (escort.juvinates.isNotEmpty() || escort.finished) {
            return
        }
        val player = owner.resolve(playerList) ?: return
        if (escort.escaped) {
            player.mes("You've seen off the juvinates, but with Ivan gone there's no point going on. Take the escape path back.")
            return
        }
        escort.finished = true
        iaom.jumpForward(player, STAGE_IVAN_DELIVERED)
        doors.launchWhenFree(owner) { arrive(escort) }
    }

    internal suspend fun ProtectedAccess.arrive(escort: Escort) {
        try {
            mesbox("You manage to slay the rest of the vampyre juvinates and continue on your journey.")
            startDialogue {
                chatNpcSpecific(IVAN_NAME, IVAN_PLAIN, happy, "Many thanks for bringing me safely to the temple. I'm going to rush on ahead, I'm eager to get out of these muddy clothes.")
                chatPlayer(neutral, "Not a problem. I need to chat to Drezel about some things. I'll see you later.")
            }
        } finally {
            endAttempt(player)
            if (with(copies) { isInside() }) {
                with(copies) { leave() }
            }
            telejump(BurghCoords.PATERDOMUS_ARRIVAL, TeleportType.Exempt)
        }
    }

    private suspend fun ProtectedAccess.escapePath() {
        arriveDelay()
        val escort = escorts[player.uid]
        if (escort == null) {
            mes("Nothing interesting happens.")
            return
        }
        val ivanStayed = !escort.escaped
        endAttempt(player)
        with(copies) { leave() }
        telejump(ESCAPE_EXIT, TeleportType.Exempt)
        if (ivanStayed) {
            mes("You flee the ambush. Ivan makes his own way back to the old hideout.")
        }
    }

    private suspend fun ProtectedAccess.continuePath() {
        arriveDelay()
        val escort = escorts[player.uid]
        if (escort == null) {
            mes("Nothing interesting happens.")
            return
        }
        mes(if (escort.escaped) "There's no point going on without Ivan." else "You can't go on while the vampyres are still about.")
    }

    /** Clears the attempt's npcs and timer; whatever Ivan has left stays on his varbits. */
    fun endAttempt(player: Player) {
        val escort = escorts.remove(player.uid) ?: return
        player.clearSoftTimer(ESCORT_TIMER)
        for (npc in escort.juvinates + escort.ivan) {
            copies.remove(npc)
        }
    }

    private fun npcType(name: String) =
        ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $name")

    internal companion object {
        const val INSTANCE_KEY = "burgh_ivan_escort"
        const val ESCORT_TIMER = "timer.burgh_escort"
        const val IVAN_PLAIN = "npc.burgh_ivan_strom_temple_ambush"
        const val IVAN_WITH_SICKLE = "npc.burgh_ivan_strom_temple_ambush_sickle"
        val IVAN_AMBUSH = listOf(IVAN_PLAIN, IVAN_WITH_SICKLE)
        const val IVAN_NAME = "Ivan Strom"
        const val ESCAPE_PATH = "loc.templetrek_swamp_fail_path"
        const val CONTINUE_PATH = "loc.templetrek_swamp_success_path"

        /** A Temple Trekking swamp clearing: escape path to the south, the way on to the north. */
        val TREK_START = CoordGrid(1999, 5030, 0)
        val IVAN_START = CoordGrid(1999, 5029, 0)
        val JUVINATE_SPAWNS =
            listOf(
                CoordGrid(1995, 5043, 0),
                CoordGrid(2004, 5043, 0),
                CoordGrid(1992, 5042, 0),
                CoordGrid(2007, 5042, 0),
            )

        /** Escaping, or failing, leaves the player beside the Canifis tavern's trapdoor. */
        val ESCAPE_EXIT = CoordGrid(3495, 3465, 0)
        const val LEASH = 40

        const val JUVINATE_ATTACK_SEQ = "seq.human_unarmedpunch"
        const val IVAN_ATTACK_SEQ = "seq.human_sword_slash"
        const val ESCAPE_SPOT = "spotanim.misty"
        const val SWING_TICKS = 4
        const val HIT_DELAY = 1
        const val IVAN_MAX_HIT = 3
        const val BASE_HIT_CHANCE = 80
        const val HIT_CHANCE = 15
        const val ESCAPE_FRACTION = 5
        const val FADE_DURATION = 30
        const val FADE_TICKS = 1
    }
}
