package org.rsmod.content.skills.runecrafting.essence

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The npcs who know the incantation. [portal] is the `varbit.essencemine_portal` value the mine's
 * exit portals transform on, and also tells the portal where to send the player back to; every
 * value used here shows a portal (values 2, 5 and 6 hide it).
 */
enum class EssenceMineTeleporter(val portal: Int, val returnCoord: CoordGrid) {
    Sedridor(portal = 0, returnCoord = CoordGrid(3106, 9572)),
    Aubury(portal = 1, returnCoord = CoordGrid(3253, 3401)),
    Cromperty(portal = 3, returnCoord = CoordGrid(2685, 3324)),
    Brimstail(portal = 4, returnCoord = CoordGrid(2409, 9816)),
    Distentor(portal = 7, returnCoord = CoordGrid(2593, 3088)),
    ;

    companion object {
        fun fromPortal(value: Int): EssenceMineTeleporter =
            entries.firstOrNull { it.portal == value } ?: Sedridor
    }
}

/** Runs once a teleporter has actually put the player in the mine; never for a cancelled cast. */
fun interface EssenceMineArrivalHook {
    fun onArrival(access: ProtectedAccess, teleporter: EssenceMineTeleporter)
}

@Singleton
class RuneEssenceTeleports
@Inject
constructor(
    private val worldRepo: WorldRepository,
    private val arrivalHooks: Set<@JvmSuppressWildcards EssenceMineArrivalHook> = emptySet(),
) {

    suspend fun teleportToMine(
        access: ProtectedAccess,
        npc: Npc,
        teleporter: EssenceMineTeleporter,
    ) {
        castTeleport(access, npc, INCANTATION)
        val landing = MINE_LANDINGS.random()
        val dest = access.mapFindSquareLineOfSight(landing, 0, 1) ?: landing
        access.telejump(dest)
        if (access.player.coords != dest) {
            return
        }
        access.vars[PORTAL_VARBIT] = teleporter.portal
        for (hook in arrivalHooks) {
            hook.onArrival(access, teleporter)
        }
    }

    /**
     * The incantation and curse-style projectile every npc teleporter casts at the player, up to
     * the moment the player should be moved.
     */
    suspend fun castTeleport(access: ProtectedAccess, npc: Npc, incantation: String) {
        val player = access.player
        access.ifClose()
        npc.facePlayer(player)
        npc.say(incantation)
        npc.spotanim(CASTING, height = CASTING_HEIGHT)
        worldRepo.soundArea(npc, CAST_SOUND)

        val travel =
            ProjAnim(
                spotanim = TRAVEL.asRSCM(RSCMType.SPOTANIM),
                startHeight = 31,
                endHeight = 31,
                startTime = 61,
                endTime = IMPACT_CYCLES,
                angle = 16,
                progress = 128,
                sourceIndex = npc.slotId + 1,
                targetIndex = -(player.slotId + 1),
                startCoord = npc.coords,
                endCoord = player.coords,
            )
        worldRepo.projAnim(travel)
        access.spotanim(IMPACT, delay = IMPACT_CYCLES, height = IMPACT_HEIGHT)
        player.soundSynth(HIT_SOUND, delay = IMPACT_CYCLES)

        access.delay(TELEPORT_DELAY)
        npc.resetFaceEntity()
    }

    suspend fun exitMine(access: ProtectedAccess, portal: BoundLocInfo) {
        val player = access.player
        val travel =
            ProjAnim(
                spotanim = TRAVEL.asRSCM(RSCMType.SPOTANIM),
                startHeight = 15,
                endHeight = 31,
                startTime = 0,
                endTime = PORTAL_IMPACT_CYCLES,
                angle = 16,
                progress = 128,
                sourceIndex = 0,
                targetIndex = -(player.slotId + 1),
                startCoord = portal.coords,
                endCoord = player.coords,
            )
        worldRepo.projAnim(travel)
        access.spotanim(IMPACT, delay = PORTAL_IMPACT_CYCLES, height = IMPACT_HEIGHT)
        player.soundSynth(PORTAL_SOUND)
        access.delay(1)
        val dest = EssenceMineTeleporter.fromPortal(access.vars[PORTAL_VARBIT]).returnCoord
        access.telejump(access.mapFindSquareLineOfWalk(dest, 0, 2) ?: dest)
    }

    companion object {
        const val INCANTATION = "Senventior Disthine Molenko!"
        const val PORTAL_VARBIT = "varbit.essencemine_portal"

        private const val CASTING = "spotanim.curse_casting"
        private const val TRAVEL = "spotanim.curse_travel"
        private const val IMPACT = "spotanim.curse_impact"
        private const val CAST_SOUND = "synth.curse_cast_and_fire"
        private const val HIT_SOUND = "synth.curse_hit"
        private const val PORTAL_SOUND = "synth.teleport_all"

        private const val CASTING_HEIGHT = 92
        private const val IMPACT_HEIGHT = 124
        private const val IMPACT_CYCLES = 100
        private const val PORTAL_IMPACT_CYCLES = 30
        private const val TELEPORT_DELAY = 4

        val MINE_LANDINGS =
            listOf(
                CoordGrid(2912, 4833),
                CoordGrid(2912, 4831),
                CoordGrid(2913, 4826),
                CoordGrid(2910, 4824),
                CoordGrid(2906, 4826),
                CoordGrid(2908, 4835),
                CoordGrid(2905, 4837),
                CoordGrid(2912, 4838),
                CoordGrid(2923, 4846),
                CoordGrid(2931, 4842),
                CoordGrid(2921, 4856),
                CoordGrid(2935, 4846),
                CoordGrid(2894, 4839),
                CoordGrid(2896, 4845),
                CoordGrid(2899, 4858),
                CoordGrid(2893, 4826),
                CoordGrid(2899, 4821),
                CoordGrid(2896, 4809),
                CoordGrid(2921, 4820),
                CoordGrid(2925, 4818),
                CoordGrid(2921, 4811),
                CoordGrid(2934, 4821),
            )
    }
}

class EssenceMinePortals @Inject constructor(private val teleports: RuneEssenceTeleports) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.blankrunestone_exit_portal") { teleports.exitMine(this, it.loc) }
    }
}
