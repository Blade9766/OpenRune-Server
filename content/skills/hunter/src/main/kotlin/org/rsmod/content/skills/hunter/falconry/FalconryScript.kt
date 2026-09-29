package org.rsmod.content.skills.hunter.falconry

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import kotlin.math.abs
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.npc.isAliveInWorld
import org.rsmod.api.player.output.HintArrows
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hunterLvl
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.worn.WornUnequipResult
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.stats.levelmod.InvisibleLevels
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.utils.skills.SkillingSuccessRate
import org.rsmod.content.skills.hunter.rumours.RumourTracker
import org.rsmod.content.skills.hunter.traps.TrapManager
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.isType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

enum class FalconKebbit(
    val npc: String,
    val falconNpc: String,
    val displayName: String,
    val level: Int,
    val xp: Double,
    val low: Int,
    val high: Int,
    val loot: List<String>,
) {
    SpottedKebbit(
        "npc.huntingbeast_speedy",
        "npc.hunting_falcon_onspeedy",
        "spotted kebbit",
        43,
        104.0,
        26,
        310,
        listOf("obj.bones", "obj.huntingbeast_speedy_fur"),
    ),
    DarkKebbit(
        "npc.huntingbeast_silent",
        "npc.hunting_falcon_onsilent",
        "dark kebbit",
        57,
        132.0,
        0,
        253,
        listOf("obj.bones", "obj.huntingbeast_silent_fur"),
    ),
    DashingKebbit(
        "npc.huntingbeast_speedy2",
        "npc.hunting_falcon_onspeedy2",
        "dashing kebbit",
        69,
        156.0,
        0,
        205,
        listOf("obj.bones", "obj.huntingbeast_speedy2_fur", "obj.huntingbeast_speedy2_meat"),
    ),
}

/**
 * Falconry at the Piscatoris falconry area. Matthias rents a gyr falcon on a falconer's glove
 * (worn in the weapon slot); sending it at a kebbit swaps the glove to its empty variant, and a
 * successful catch leaves a `Gyr Falcon` npc on the kebbit's tile for the owner to retrieve.
 * Falcons don't leave the area: walking out, logging out or abandoning a catch sends the bird
 * back to Matthias.
 */
class FalconryScript
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val worldRepo: WorldRepository,
    private val playerList: PlayerList,
    private val clock: MapClock,
    private val random: GameRandom,
    private val xpMods: XpModifiers,
    private val invisibleLevels: InvisibleLevels,
    private val rumours: RumourTracker,
) : PluginScript() {
    private class Catch(val kebbit: FalconKebbit, val falcon: Npc, val expires: Int)

    private val catches = HashMap<PlayerUid, Catch>()

    override fun ScriptContext.startup() {
        onOpNpc1(MATTHIAS) { talkToMatthias(it.npc) }
        onOpNpc3(MATTHIAS) { quickFalcon() }
        for (kebbit in FalconKebbit.entries) {
            onOpNpc1(kebbit.npc) { sendFalcon(it.npc, kebbit) }
            onOpNpc1(kebbit.falconNpc) { retrieve(it.npc) }
        }
        onEvent<GameLifecycle.LateCycle> { tick() }
        onPlayerLogout {
            abandon(player.uid)
            removeGloves(player)
        }
    }

    private fun tick() {
        if (clock.cycle % CHECK_INTERVAL != 0) {
            return
        }
        for ((uid, catch) in catches.entries.toList()) {
            if (clock.cycle >= catch.expires) {
                abandon(uid)
                uid.resolve(playerList)
                    ?.mes("Your falcon has left its prey. You see it heading back toward the falconer.")
            }
        }
        for (player in playerList) {
            if (player.hasGlove() && !player.coords.inFalconryArea()) {
                abandon(player.uid)
                removeGloves(player)
                player.mes("The falcon flies back to Matthias as you leave the falconry area.")
            }
        }
    }

    private suspend fun ProtectedAccess.talkToMatthias(npc: Npc) =
        startDialogue(npc) {
            when {
                player.worn[Wearpos.RightHand.slot]?.isType(EMPTY_GLOVE) == true &&
                    catches[player.uid] == null -> {
                    chatNpc(neutral, "Lost your falcon? Here, take her back. No charge.")
                    player.worn[Wearpos.RightHand.slot] = InvObj(FALCON_GLOVE)
                }
                player.hasGlove() -> {
                    chatNpc(neutral, "Finished hunting? I can take the falcon back if you like.")
                    val done = choice2("Yes, take her back.", true, "No, I'll keep hunting.", false)
                    if (done) {
                        abandon(player.uid)
                        removeGloves(player)
                        chatNpc(happy, "Thanks. Come back any time.")
                    }
                }
                else -> {
                    chatNpc(
                        happy,
                        "Want to try your hand at falconry? I'll lend you a gyr falcon and a " +
                            "glove for $RENT_FEE coins.",
                    )
                    val choice =
                        choice3(
                            "Yes please.",
                            1,
                            "Can I pay once for good? ($INVESTMENT_FEE coins)",
                            2,
                            "No thanks.",
                            3,
                        )
                    when (choice) {
                        1 -> access.rentFalcon()
                        2 -> access.invest()
                    }
                }
            }
        }

    private fun ProtectedAccess.quickFalcon() {
        if (player.hasGlove()) {
            mes("You already have a falcon.")
            return
        }
        rentFalcon()
    }

    private fun ProtectedAccess.invest() {
        if (player.vars[INVESTED] != 0) {
            mes("You've already paid Matthias for unlimited falcon use.")
            return
        }
        if (invDel(inv, COINS, INVESTMENT_FEE).failure) {
            mes("You don't have enough coins to do that.")
            return
        }
        VarPlayerIntMapSetter.set(player, INVESTED, 1)
        mes("Matthias thanks you for your investment. Falcons are now free for you to borrow.")
        rentFalcon()
    }

    private fun ProtectedAccess.rentFalcon() {
        for (slot in BLOCKED_SLOTS) {
            if (player.worn[slot] != null && wornUnequip(slot) != WornUnequipResult.Success) {
                mes("You need to free your hands before Matthias will give you a falcon.")
                return
            }
        }
        val invested = player.vars[INVESTED] != 0
        if (!invested && invDel(inv, COINS, RENT_FEE).failure) {
            mes("You need $RENT_FEE coins to borrow a falcon.")
            return
        }
        player.worn[Wearpos.RightHand.slot] = InvObj(FALCON_GLOVE)
        mes("Matthias gives you a falcon and a glove to hold it with.")
    }

    private suspend fun ProtectedAccess.sendFalcon(kebbit: Npc, type: FalconKebbit) {
        if (player.hunterLvl < type.level) {
            mes("You need a Hunter level of ${type.level} to catch a ${type.displayName}.")
            return
        }
        val glove = player.worn[Wearpos.RightHand.slot]
        if (glove?.isType(FALCON_GLOVE) != true) {
            if (glove?.isType(EMPTY_GLOVE) == true) {
                mes("Your falcon is already out hunting.")
            } else {
                mes("You need a falcon to catch the ${type.displayName}.")
            }
            return
        }
        faceEntitySquare(kebbit)
        player.worn[Wearpos.RightHand.slot] = InvObj(EMPTY_GLOVE)
        worldRepo.projAnimSourced(player, kebbit, SpotanimType(FALCON_SPOT.asRSCM(RSCMType.SPOTANIM)), PROJANIM)
        val distance = maxOf(abs(player.coords.x - kebbit.coords.x), abs(player.coords.z - kebbit.coords.z))
        delay(1 + distance / TILES_PER_TICK)
        val level = player.hunterLvl + invisibleLevels.get(player, TrapManager.STAT)
        val chance = SkillingSuccessRate.successRate(type.low, type.high, level, MAX_LEVEL)
        if (!kebbit.isAliveInWorld() || random.randomDouble() >= chance) {
            mes("The falcon narrowly misses the ${type.displayName}.")
            delay(1 + distance / TILES_PER_TICK)
            if (player.worn[Wearpos.RightHand.slot]?.isType(EMPTY_GLOVE) == true) {
                player.worn[Wearpos.RightHand.slot] = InvObj(FALCON_GLOVE)
            }
            return
        }
        val coords = kebbit.coords
        npcRepo.despawn(kebbit, KEBBIT_RESPAWN)
        val falcon = Npc(type.falconNpc, coords)
        npcRepo.add(falcon, CATCH_DURATION)
        catches[player.uid] = Catch(type, falcon, clock.cycle + CATCH_DURATION)
        HintArrows.hintNpc(player, falcon)
        mes("The falcon successfully swoops down and captures the ${type.displayName}.")
    }

    private suspend fun ProtectedAccess.retrieve(falcon: Npc) {
        val catch = catches[player.uid]
        if (catch == null || catch.falcon !== falcon) {
            mes("This isn't your falcon.")
            return
        }
        faceEntitySquare(falcon)
        anim(RETRIEVE_SEQ)
        delay(1)
        if (catches[player.uid] !== catch) {
            return
        }
        catches.remove(player.uid)
        HintArrows.hintStop(player)
        npcRepo.del(falcon, Int.MAX_VALUE)
        for (obj in catch.kebbit.loot) {
            if (inv.freeSpace() > 0) {
                invAdd(inv, obj)
            }
        }
        player.worn[Wearpos.RightHand.slot] = InvObj(FALCON_GLOVE)
        statAdvance(TrapManager.STAT, catch.kebbit.xp * xpMods.get(player, TrapManager.STAT))
        mes("You retrieve the falcon as well as the fur of the dead kebbit.")
        rumours.onCatch(player, catch.kebbit.name)
    }

    private fun abandon(uid: PlayerUid) {
        val catch = catches.remove(uid) ?: return
        uid.resolve(playerList)?.let(HintArrows::hintStop)
        if (catch.falcon.isAliveInWorld()) {
            npcRepo.del(catch.falcon, Int.MAX_VALUE)
        }
    }

    private fun removeGloves(player: Player) {
        val glove = player.worn[Wearpos.RightHand.slot] ?: return
        if (glove.isType(FALCON_GLOVE) || glove.isType(EMPTY_GLOVE)) {
            player.worn[Wearpos.RightHand.slot] = null
        }
        player.inv.indices
            .filter { player.inv[it]?.let { obj -> obj.isType(FALCON_GLOVE) || obj.isType(EMPTY_GLOVE) } == true }
            .forEach { player.inv[it] = null }
    }

    private fun Player.hasGlove(): Boolean {
        val glove = worn[Wearpos.RightHand.slot] ?: return false
        return glove.isType(FALCON_GLOVE) || glove.isType(EMPTY_GLOVE)
    }

    private fun CoordGrid.inFalconryArea(): Boolean =
        level == 0 && x in AREA_MIN_X..AREA_MAX_X && z in AREA_MIN_Z..AREA_MAX_Z

    private companion object {
        const val MATTHIAS = "npc.hunting_npc_falconer"
        const val FALCON_GLOVE = "obj.falcon_on_gloves"
        const val EMPTY_GLOVE = "obj.falcon_gloves"
        const val COINS = "obj.coins"
        const val INVESTED = "varbit.hunter_falconry_invested"
        const val FALCON_SPOT = "spotanim.falcon_travel_anim"
        const val PROJANIM = "projanim.arrow"
        const val RETRIEVE_SEQ = "seq.human_pickupfloor"
        const val RENT_FEE = 500
        const val INVESTMENT_FEE = 500_000
        const val MAX_LEVEL = 99
        const val TILES_PER_TICK = 3
        const val KEBBIT_RESPAWN = 10
        const val CATCH_DURATION = 100
        const val CHECK_INTERVAL = 2
        const val AREA_MIN_X = 2355
        const val AREA_MAX_X = 2400
        const val AREA_MIN_Z = 3565
        const val AREA_MAX_Z = 3615

        val BLOCKED_SLOTS =
            listOf(Wearpos.RightHand.slot, Wearpos.LeftHand.slot, Wearpos.Hands.slot)
    }
}
