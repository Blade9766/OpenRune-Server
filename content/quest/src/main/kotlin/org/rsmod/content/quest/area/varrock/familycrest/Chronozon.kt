package org.rsmod.content.quest.area.varrock.familycrest

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.heal
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.api.script.onNpcHit
import org.rsmod.content.quest.area.varrock.familycrest.FamilyCrestQuest.Companion.CHRONOZON
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The blood demon holding Johnathon's piece of the crest, by the earth obelisk in Edgeville
 * Dungeon.
 *
 * Chronozon is bound to all four elements at once. Until wind, water, earth and fire blast have
 * each drawn blood from him he cannot be killed: the blow that should finish him knits him back
 * together at full health instead. The elements he has already been weakened by stay weakened, so
 * a fight that runs him down early only costs time. Only the blast spells count, and only when
 * they land - a splash does nothing.
 *
 * The binding is Johnathon's problem, not the demon's: once the player has taken the crest part
 * from him he dies like anything else. The weakness itself lives on a temporary varbit, so it is
 * also lost on logout.
 */
class Chronozon @Inject constructor(private val playerList: PlayerList) : PluginScript() {

    override fun ScriptContext.startup() {
        val type = ServerCacheManager.getNpc(CHRONOZON.asRSCM(RSCMType.NPC))
        checkNotNull(type) { "Missing npc: $CHRONOZON" }

        onModifyNpcHit(type) {
            if (!hit.isFromPlayer || hit.damage <= 0) {
                return@onModifyNpcHit
            }
            val uid = hit.sourceUid ?: return@onModifyNpcHit
            val source = PlayerUid(uid).resolve(playerList) ?: return@onModifyNpcHit
            recordBlast(source, this)
            if (hit.damage >= npc.hitpoints && isBound(source)) {
                hit.damage = npc.hitpoints - 1
            }
        }

        onNpcHit(type) {
            if (npc.hitpoints > 1 || !hit.isFromPlayer) {
                return@onNpcHit
            }
            val source = hit.resolvePlayerSource(playerList) ?: return@onNpcHit
            if (!isBound(source)) {
                return@onNpcHit
            }
            regenerate(npc, source)
        }
    }

    /** True while the demon still needs all four elements before this player can kill him. */
    private fun isBound(source: Player): Boolean {
        if (!source.johnathonAsked || source.johnathonDone) {
            return false
        }
        return source.chronozonWeakness and ALL_ELEMENTS != ALL_ELEMENTS
    }

    /** Marks the element, if this hit was a blast, and tells the player it bit. */
    private fun recordBlast(source: Player, event: NpcHitEvents.Modify) {
        if (event.hit.type != HitType.Magic) {
            return
        }
        if (!source.johnathonAsked || source.johnathonDone) {
            return
        }
        val flag =
            BLASTS.entries.firstOrNull { (spell, _) -> event.hit.isSecondaryObj(spell) }?.value
                ?: return
        if (source.chronozonWeakness and flag != 0) {
            return
        }
        source.chronozonWeakness = source.chronozonWeakness or flag
        source.mes("Chronozon weakens....")
    }

    /**
     * Puts the demon back to full health. Hits are modified when they are queued rather than when
     * they land, so a hit capped in [onModifyNpcHit] can still finish him off when an earlier one
     * lands first; clearing the death queue undoes that.
     */
    private fun regenerate(npc: Npc, source: Player) {
        npc.clearQueue(DEATH_QUEUE)
        npc.heal(npc.baseHitpointsLvl - npc.hitpoints)
        npc.spotanim(REGENERATE_SPOTANIM)
        source.mes("Chronozon knits himself back together. Only all four elements will hold him.")
    }

    private companion object {
        const val REGENERATE_SPOTANIM = "spotanim.weaken_impact"
        const val DEATH_QUEUE = "queue.death"

        const val ALL_ELEMENTS = 0xF

        /** The four blast spells and the bit each sets in `varbit.famcrest_chronozon_weaken`. */
        val BLASTS: Map<ItemServerType, Int> by lazy {
            mapOf(
                spell("obj.41_wind_blast") to 0x1,
                spell("obj.47_water_blast") to 0x2,
                spell("obj.53_earth_blast") to 0x4,
                spell("obj.59_fire_blast") to 0x8,
            )
        }

        private fun spell(internal: String): ItemServerType =
            checkNotNull(ServerCacheManager.getItem(internal.asRSCM(RSCMType.OBJ))) {
                "Missing spell obj: $internal"
            }
    }
}

/**
 * Remembers that the player has taken Johnathon's piece from the demon. The part itself is a
 * guaranteed quest drop from `chronozon.toml`.
 */
class ChronozonKillHook
@Inject
constructor(
    private val familyCrest: FamilyCrestQuest,
    private val launcher: ProtectedAccessLauncher,
) : NpcDeathKillHook {
    private val demonId: Int by lazy { CHRONOZON.asRSCM(RSCMType.NPC) }

    override fun onKill(context: NpcDeathKillContext) {
        if (context.npc.id != demonId) {
            return
        }
        val hero = context.hero
        if (!hero.johnathonAsked || hero.johnathonDone) {
            return
        }
        hero.johnathonDone = true
        hero.chronozonWeakness = 0
        launcher.launch(hero) { familyCrest.syncStage(this) }
    }
}
