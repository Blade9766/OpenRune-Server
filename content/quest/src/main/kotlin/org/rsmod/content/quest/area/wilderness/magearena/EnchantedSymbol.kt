package org.rsmod.content.quest.area.wilderness.magearena

import dev.openrune.ServerCacheManager
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import org.rsmod.api.area.checker.isInWildernessBasic
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Kolodion's enchanted symbol. Activating it in the surface Wilderness costs some blood and
 * tells the player how close they are to the chosen follower, hot-and-cold style; on top of the
 * spot the follower rises out of the ground and attacks.
 */
class EnchantedSymbol
@Inject
constructor(
    private val mageArena2: MageArena2Quest,
    private val spawns: FollowerSpawns,
    private val search: NpcSearch,
    private val npcRepo: NpcRepository,
    private val aiInteractions: AiPlayerInteractions,
    private val collision: CollisionFlagMap,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeld1(SYMBOL) { activate() }
    }

    private suspend fun ProtectedAccess.activate() {
        if (mageArena2.stage(player) == 0) {
            mes("The symbol is cold and lifeless in your hand.")
            return
        }
        if (!player.coords.isInWildernessBasic()) {
            mes("The symbol doesn't seem to work here, I should try using it in the Wilderness.")
            return
        }
        if (spawns.ensureRotation(player)) {
            mesbox("It appears the creatures have moved locations, the symbol glows slightly as it locates their new positions.")
        }
        var chosen: God? = null
        startDialogue {
            chosen =
                when (
                    choice4(
                        "Saradomin", 1,
                        "Guthix", 2,
                        "Zamorak", 3,
                        "Cancel", 4,
                        title = "Which follower should the symbol seek?",
                    )
                ) {
                    1 -> God.SARADOMIN
                    2 -> God.GUTHIX
                    3 -> God.ZAMORAK
                    else -> null
                }
        }
        val god = chosen ?: return
        if (mageArena2.isKilled(player, god) && !mageArena2.quest.isQuestCompleted(player)) {
            mes("The symbol no longer reacts to ${god.followerName}; you have already taken his remains.")
            return
        }
        sacrifice()
        val target = spawns.spawnTile(player, god)
        val distance = player.coords.chebyshevDistance(target)
        if (distance <= SHAKING_DISTANCE) {
            mes("<col=ef1020>The symbol is visibly shaking!</col>")
            summonFollower(god, target)
            return
        }
        val temperature = TEMPERATURES.first { distance >= it.first }.second
        mes("The symbol is $temperature. You sense ${god.followerName} to the ${compassDirection(player.coords, target)}.")
    }

    /** "The symbol will take a blood sacrifice every time it's used." */
    private fun ProtectedAccess.sacrifice() {
        val damage = random.of(1..SACRIFICE_MAX)
        queueHit(delay = 0, type = HitType.Typeless, damage = damage)
    }

    private suspend fun ProtectedAccess.summonFollower(god: God, target: CoordGrid) {
        val existing =
            search.find(target, god.follower, FOLLOWER_SEARCH_RADIUS, HuntVis.Off)
                ?: search.find(target, god.followerSpawning, FOLLOWER_SEARCH_RADIUS, HuntVis.Off)
        if (existing != null) {
            mes("${god.followerName} is already here!")
            if (existing.id == god.followerId) {
                existing.opPlayer2(player, aiInteractions)
            }
            return
        }
        val type = ServerCacheManager.getNpc(god.followerId) ?: return
        val tile = collision.freeFootprint(target, type.size) ?: target
        val rising = Npc(god.followerSpawning, tile)
        npcRepo.add(rising, SPAWN_TICKS + 1)
        rising.anim(god.followerSpawnAnim)
        rising.facePlayer(player)
        mes("<col=ef1020>${god.followerName} rises from the ground!</col>")
        delay(SPAWN_TICKS)
        if (rising.isSlotAssigned) {
            npcRepo.del(rising, Int.MAX_VALUE)
        }
        val follower = Npc(type, tile)
        npcRepo.add(follower, FOLLOWER_LIFETIME)
        follower.facePlayer(player)
        follower.opPlayer2(player, aiInteractions)
    }

    private companion object {
        const val SYMBOL = "obj.ma2_symbol"
        const val SACRIFICE_MAX = 16
        const val SHAKING_DISTANCE = 3
        const val SPAWN_TICKS = 4
        const val FOLLOWER_SEARCH_RADIUS = 15

        /** Ten minutes before an un-killed follower sinks back into the ground. */
        const val FOLLOWER_LIFETIME = 1000

        /** Distance thresholds (checked in order) and the temperature reported at or beyond them. */
        val TEMPERATURES =
            listOf(
                300 to "ice cold",
                200 to "very cold",
                120 to "cold",
                70 to "warm",
                40 to "hot",
                15 to "very hot",
                0 to "incredibly hot",
            )
    }
}
