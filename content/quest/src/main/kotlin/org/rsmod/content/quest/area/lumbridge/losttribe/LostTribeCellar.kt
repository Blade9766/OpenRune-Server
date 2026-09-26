package org.rsmod.content.quest.area.lumbridge.losttribe

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.player.stat.miningLvl
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.AGILITY_REQ
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.BROOCH
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.MINING_REQ
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_ASKING
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_PERMISSION
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_TUNNEL_DUG
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.getInvObj
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The collapsed east wall of the Lumbridge Castle cellar and the tunnels behind it.
 *
 * `loc.lost_tribe_cellar_wall` (cellar side) and `loc.lost_tribe_cellar_wall_back` (tunnel side)
 * are multilocs on the quest stage: plain shelves before the quest, rubble while the player looks
 * into the collapse, and a hole to squeeze through once the rubble is dug out. The brooch Mistag
 * dropped lies a few steps inside the tunnel; it is a private spawn so every player finds their own.
 * `loc.lost_tribe_hole_2` links the south end of the tunnels to the Lumbridge Swamp Caves, where the
 * collapsing floors of the maze drop careless explorers.
 */
class LostTribeCellar
@Inject
constructor(private val lostTribe: LostTribeQuest, private val objRepo: ObjRepository) :
    PluginScript() {

    private val broochId by lazy { BROOCH.asRSCM(RSCMType.OBJ) }

    override fun ScriptContext.startup() {
        val pickaxes =
            ServerCacheManager.getItems().values.filter {
                it.isContentType(PICKAXE_CONTENT) && it.name.isNotEmpty()
            }
        for (pickaxe in pickaxes) {
            onOpLocU(CELLAR_WALL, pickaxe) { digRubble(it.vis) }
            onOpLocU(RUBBLE, pickaxe) { digRubble(it.vis) }
        }
        onOpLoc1(CELLAR_WALL) { squeeze(CELLAR_SIDE, TUNNEL_SIDE, intoTunnel = true) }
        onOpLoc1(CELLAR_WALL_BACK) { squeeze(TUNNEL_SIDE, CELLAR_SIDE, intoTunnel = false) }
        onOpLoc1(SWAMP_HOLE) { squeezeSwampHole(it.loc) }
        onOpHeld1(BROOCH) {
            objbox(BROOCH, "It's a stone brooch with a strange symbol carved on it.")
        }
    }

    private suspend fun ProtectedAccess.digRubble(rubble: BoundLocInfo) {
        arriveDelay()
        val stage = lostTribe.stage(player)
        if (stage < STAGE_ASKING || stage >= STAGE_TUNNEL_DUG) {
            mes(constants.dm_default)
            return
        }
        if (stage < STAGE_PERMISSION) {
            mes("You should ask the Duke's permission before you start digging up his cellar.")
            return
        }
        if (player.miningLvl < MINING_REQ) {
            mes("You need a Mining level of $MINING_REQ to dig through the rubble.")
            return
        }
        val pickaxe = bestPickaxe()
        if (pickaxe == null) {
            mes("You need a pickaxe that you have the Mining level to use.")
            return
        }
        faceLoc(rubble)
        anim(pickaxeAnim(pickaxe))
        soundSynth(MINE_SOUND)
        delay(DIG_TICKS)
        resetAnim()
        lostTribe.advanceTo(this, STAGE_TUNNEL_DUG)
        mesbox("You dig a narrow tunnel through the rocks.")
    }

    private suspend fun ProtectedAccess.squeeze(from: CoordGrid, to: CoordGrid, intoTunnel: Boolean) {
        arriveDelay()
        if (lostTribe.stage(player) < STAGE_TUNNEL_DUG) {
            mes(constants.dm_default)
            return
        }
        if (player.agilityLvl < AGILITY_REQ) {
            mes("You need an Agility level of $AGILITY_REQ to squeeze through the hole.")
            return
        }
        if (intoTunnel && !hasLight()) {
            mes("It's too dark in there. You'll need a light source before you go in.")
            return
        }
        crawl(from, to)
        if (intoTunnel) {
            spawnBrooch()
        }
    }

    private suspend fun ProtectedAccess.squeezeSwampHole(hole: BoundLocInfo) {
        arriveDelay()
        if (player.agilityLvl < AGILITY_REQ) {
            mes("You need an Agility level of $AGILITY_REQ to squeeze through the hole.")
            return
        }
        val fromTunnel = coords.z > hole.coords.z
        if (fromTunnel) {
            crawl(coords, SWAMP_HOLE_SOUTH)
        } else {
            crawl(coords, SWAMP_HOLE_NORTH)
        }
        player.lostTribeHole2Dug = true
    }

    private suspend fun ProtectedAccess.crawl(from: CoordGrid, to: CoordGrid) {
        if (coords != from) {
            telejump(from, TeleportType.Exempt)
            delay(1)
        }
        val dir =
            when {
                to.x > from.x -> constants.em_face_east
                to.x < from.x -> constants.em_face_west
                to.z > from.z -> constants.em_face_north
                else -> constants.em_face_south
            }
        mes("You squeeze through the hole.")
        anim(CRAWL_SEQ)
        soundSynth(SQUEEZE_SOUND)
        exactMove(
            start = from,
            end = to,
            delay1 = 0,
            delay2 = CRAWL_TICKS * CLIENT_CYCLES_PER_TICK,
            dir = dir,
            teleportType = TeleportType.Exempt,
        )
        delay(CRAWL_TICKS)
        resetAnim()
    }

    /** The brooch waits a few steps inside the tunnel until the player has handed it to Mistag. */
    private fun ProtectedAccess.spawnBrooch() {
        if (player.lostTribeBroochReturned || lostTribe.ownsItem(this, BROOCH)) {
            return
        }
        val alreadyThere = objRepo.findAll(BROOCH_TILE).any { it.type == broochId && it.isPrivate }
        if (!alreadyThere) {
            objRepo.add(BROOCH, BROOCH_TILE, BROOCH_TICKS, receiver = player, reveal = BROOCH_TICKS)
        }
    }

    private fun ProtectedAccess.hasLight(): Boolean =
        LIGHT_SOURCES.any { it in player.inv || it in player.worn }

    private fun ProtectedAccess.bestPickaxe(): ItemServerType? {
        val carried = inv.filterNotNull { true } + listOfNotNull(player.righthand)
        return carried
            .map { getInvObj(it) }
            .filter {
                it.isContentType(PICKAXE_CONTENT) &&
                    player.miningLvl >= (it.paramOrNull(params.levelrequire) ?: 1)
            }
            .maxByOrNull { it.paramOrNull(params.levelrequire) ?: 1 }
    }

    private fun pickaxeAnim(pickaxe: ItemServerType): String {
        val seq = pickaxe.paramOrNull(params.skill_anim) ?: return DEFAULT_MINE_SEQ
        return RSCM.getReverseMapping(RSCMType.SEQ, seq.id)
    }

    companion object {
        const val CELLAR_WALL = "loc.lost_tribe_cellar_wall"
        const val CELLAR_WALL_BACK = "loc.lost_tribe_cellar_wall_back"
        const val RUBBLE = "loc.lost_tribe_cellar_hole_blocking"
        const val SWAMP_HOLE = "loc.lost_tribe_hole_2"

        const val PICKAXE_CONTENT = "content.mining_pickaxe"
        const val DEFAULT_MINE_SEQ = "seq.human_mining_bronze_pickaxe"
        const val CRAWL_SEQ = "seq.human_crawling"
        const val MINE_SOUND = "synth.mine_quick"
        const val SQUEEZE_SOUND = "synth.squeeze_through_rocks"

        const val DIG_TICKS = 4
        const val CRAWL_TICKS = 2
        const val CLIENT_CYCLES_PER_TICK = 30
        const val BROOCH_TICKS = 200

        val CELLAR_SIDE = CoordGrid(3219, 9618, 0)
        val TUNNEL_SIDE = CoordGrid(3221, 9618, 0)
        val BROOCH_TILE = CoordGrid(3230, 9610, 0)
        val SWAMP_HOLE_NORTH = CoordGrid(3224, 9604, 0)
        val SWAMP_HOLE_SOUTH = CoordGrid(3224, 9600, 0)

        val LIGHT_SOURCES =
            listOf(
                "obj.lit_candle",
                "obj.lit_black_candle",
                "obj.torch_lit",
                "obj.candle_lantern_lit",
                "obj.candle_lantern_black_lit",
                "obj.oil_lamp_lit",
                "obj.oil_lantern_lit",
                "obj.bullseye_lantern_lit",
                "obj.tog_sapphire_lantern_lit",
                "obj.cave_goblin_mining_helmet_lit",
            )
    }
}
