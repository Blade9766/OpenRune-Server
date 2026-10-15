package org.rsmod.content.quest.area.seers.elementalworkshop

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.refs.params
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.seers.elementalworkshop.ElementalWorkshopQuest.Companion.ORE
import org.rsmod.content.quest.area.seers.elementalworkshop.ElementalWorkshopQuest.Companion.REQUIRED_LEVEL
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.type.getInvObj
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The elemental rocks of the workshop's western cavern. Swinging a pick at one wakes it: the rock
 * sinks away and a level-35 earth elemental rises in its place, bound to the player who struck it.
 * Only that awakened elemental gives up elemental ore, and only to its owner; the earth elementals
 * already roaming the cavern drop nothing of the sort.
 *
 * Losing the fight costs nothing permanent: the elemental wanders off after [ELEMENTAL_TICKS] and
 * the rock is back after [ROCK_RESPAWN_TICKS], ready to be struck again.
 */
@Singleton
class ElementalRocks
@Inject
constructor(
    private val ew: ElementalWorkshopQuest,
    private val npcRepo: NpcRepository,
    private val objRepo: ObjRepository,
    private val death: NpcDeath,
    private val playerList: PlayerList,
    private val worldQueues: WorldQueueList,
    private val aiInteractions: AiPlayerInteractions,
) : PluginScript() {

    private val owners = HashMap<Npc, PlayerUid>()

    override fun ScriptContext.startup() {
        onEvent<NpcStateEvents.Delete> { owners.remove(npc) }
        onOpNpc1(ROCK) { mine(it.npc) }
        onNpcQueue(npcType(AWAKENED), "queue.death") { elementalDied() }
    }

    fun ownerOf(npc: Npc): PlayerUid? = owners[npc]

    private fun npcType(name: String) =
        ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $name")

    private suspend fun ProtectedAccess.mine(rock: Npc) {
        if (hasElementalNearby()) {
            mes("You already have an angry rock to deal with!")
            return
        }
        val pickaxe = findPickaxe(this)
        if (pickaxe == null) {
            mes("You need a pickaxe to mine this rock.")
            return
        }
        if (stat(MINING) < REQUIRED_LEVEL) {
            mes("You need a Mining level of $REQUIRED_LEVEL to mine this rock.")
            return
        }
        faceSquare(rock.coords)
        val seq = pickaxe.paramOrNull(params.skill_anim)?.let { RSCM.getReverseMapping(RSCMType.SEQ, it.id) }
        anim(seq ?: DEFAULT_MINE_SEQ)
        mes("You swing your pick at the rock.")
        delay(2)
        if (!rock.isSlotAssigned || !rock.isVisible || hasElementalNearby()) {
            return
        }
        resetAnim()
        awaken(rock)
    }

    /**
     * Whether the player's own awakened elemental is still close by. One left behind after the
     * player died or walked away is sent back into the ground so it never blocks another try.
     */
    private fun ProtectedAccess.hasElementalNearby(): Boolean {
        val existing = owners.entries.firstOrNull { it.value == player.uid }?.key ?: return false
        val close =
            existing.coords.level == coords.level &&
                existing.coords.chebyshevDistance(coords) <= LEASH_TILES
        if (existing.isSlotAssigned && close) {
            return true
        }
        owners.remove(existing)
        if (existing.isSlotAssigned) {
            npcRepo.del(existing, Int.MAX_VALUE)
        }
        return false
    }

    private fun ProtectedAccess.awaken(rock: Npc) {
        val type = ServerCacheManager.getNpc(AWAKENED.asRSCM(RSCMType.NPC)) ?: return
        val coords = rock.coords
        npcRepo.hide(rock, ROCK_RESPAWN_TICKS)
        val elemental = Npc(type, coords)
        npcRepo.add(elemental, ELEMENTAL_TICKS)
        owners[elemental] = player.uid
        elemental.anim(EMERGE_SEQ)
        elemental.facePlayer(player)
        mes("The rock shudders and heaves itself up. It's alive!")
        val uid = player.uid
        worldQueues.add(EMERGE_TICKS) {
            val target = uid.resolve(playerList) ?: return@add
            if (elemental.isSlotAssigned) {
                elemental.opPlayer2(target, aiInteractions)
            }
        }
    }

    /**
     * Takes over the awakened elemental's death so it leaves ore rather than the generic bones.
     * The owner is read before the death sequence, which deletes the npc and so unbinds it.
     */
    private suspend fun StandardNpcAccess.elementalDied() {
        val owner = owners[npc]
        val hero = findHero(playerList)
        val dropAt = npc.coords
        death.deathNoDrops(this)
        rewardOre(owner, hero, dropAt)
    }

    internal fun rewardOre(owner: PlayerUid?, hero: Player?, dropAt: CoordGrid) {
        if (hero == null || owner != hero.uid) {
            return
        }
        objRepo.add(ORE, dropAt, ORE_TICKS, hero)
        ew.markOreFound(hero)
        hero.mes("As the elemental crumbles, a lump of glittering ore falls from it.")
    }

    internal companion object {
        const val ROCK = "npc.elem1_qip_earth_elemental_rock_version_rock"
        const val AWAKENED = "npc.elem1_qip_earth_elemental_rock_version"
        const val ROAMING = "npc.elemental_earth"
        const val MINING = "stat.mining"
        const val MINING_PICKAXE = "content.mining_pickaxe"
        const val DEFAULT_MINE_SEQ = "seq.human_mining_bronze_pickaxe"
        const val EMERGE_SEQ = "seq.earth_elemental_rework_emerge"

        const val EMERGE_TICKS = 2
        const val LEASH_TILES = 15
        const val ELEMENTAL_TICKS = 300
        const val ROCK_RESPAWN_TICKS = 50
        const val ORE_TICKS = 200

        fun findPickaxe(access: ProtectedAccess): ItemServerType? =
            (access.player.worn.objs.asSequence() + access.inv.objs.asSequence())
                .filterNotNull()
                .map(::getInvObj)
                .firstOrNull { it.isContentType(MINING_PICKAXE) }
    }
}
