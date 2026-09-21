package org.rsmod.content.quest.area.ardougne.undergroundpass

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.DEAD_ORB
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.JOURNAL
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.ORBS
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.ORB_COUNT
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.PLANK
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_SEARCH
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_BIGFIRE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_DISARM
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_RUMBLE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_SPRINGTRAP
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_TRIPWIRE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_BRIDGE
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The four orbs of light and the traps set around them.
 *
 * Randas, the last of the party that came down here before Koftik, wrote what the orbs are for
 * before he died of it: they are all that keeps Iban's dark out of this half of the pass. Putting
 * them out is the only way through, and the furnace by the grid is the only thing hot enough.
 *
 * Every passage to an orb is trapped. The spring traps under the flat rocks can be disarmed, or
 * bridged with the plank from beside the bridge winch; the spear traps in the narrow corridor west
 * of the furnace can only be disarmed or run past.
 */
@Singleton
class OrbsOfLight
@Inject
constructor(
    private val quest: UndergroundPassQuest,
    private val locRepo: LocRepository,
    private val random: GameRandom,
) : PluginScript() {

    private val deadOrbType by lazy { locType(ORB_TAKEN) }

    override fun ScriptContext.startup() {
        for ((index, orb) in ORB_LOCS.withIndex()) {
            onOpLoc1(orb) { takeOrb(it.loc, index) }
        }
        onOpLoc2(FURNACE) { burnOrbs() }
        for (orb in ORBS) {
            onOpLocU(FURNACE, orb) { burnOrbs() }
        }

        onOpLoc1(SPRING_TRAP) { disarmSpringTrap(it.loc) }
        onOpLocU(SPRING_TRAP, PLANK) { plankOverTrap(it.loc) }
        onOpLoc1(SPEAR_TRAP) { disarmSpearTrap(it.loc) }
        onOpLoc1(LOG_TRAP) { disarmLogTrap(it.loc) }

        onOpHeld1(JOURNAL) { readJournal() }
        for (tablet in TABLETS) {
            onOpLoc1(tablet) { readTablet(tablet) }
        }
    }

    private fun locType(name: String): ObjectServerType =
        ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC)) ?: error("Missing loc: $name")

    private suspend fun ProtectedAccess.takeOrb(loc: BoundLocInfo, index: Int) {
        arriveDelay()
        if (player.vars[orbTakenVarbit(index)] == 1) {
            return
        }
        if (invAdd(inv, ORBS[index]).failure) {
            mes("You don't have enough inventory space.")
            return
        }
        anim(SEQ_SEARCH)
        soundSynth(ORB_SOUND)
        delay(1)
        UndergroundPassQuest.setVarBit(player, orbTakenVarbit(index), 1)
        locRepo.change(loc, deadOrbType, Int.MAX_VALUE)
        val left = ORB_COUNT - player.orbsTaken
        if (left > 0) {
            mes("You lift the orb out of its cradle. There are $left more somewhere in these caves.")
        } else {
            mesbox(
                "That is the last of the four. The furnace by the grid is the only fire down here " +
                    "hot enough to put them out for good.",
            )
        }
    }

    private suspend fun ProtectedAccess.burnOrbs() {
        arriveDelay()
        val held = ORBS.filter { invContains(inv, it) }
        if (held.isEmpty()) {
            mes("The furnace is roaring, but there is nothing of mine to put in it.")
            return
        }
        for (orb in held) {
            val index = ORBS.indexOf(orb)
            if (invDel(inv, orb).failure) {
                continue
            }
            anim(SEQ_SEARCH)
            soundSynth(SOUND_BIGFIRE)
            delay(2)
            UndergroundPassQuest.setVarBit(player, orbBurntVarbit(index), 1)
            invAdd(inv, DEAD_ORB)
            mes("The orb goes into the furnace. Its light dies.")
        }
        if (player.orbsBurnt < ORB_COUNT) {
            return
        }
        soundSynth(SOUND_RUMBLE)
        mesbox(
            "The last light goes out of the pass. Somewhere far below, something that has been " +
                "waiting a long time starts to move.",
        )
    }

    /**
     * The flat rocks hide a spring trap under the floor. Searching one disarms it; laying the
     * plank across the rock does the same job without the risk, and keeps the plank.
     */
    private suspend fun ProtectedAccess.disarmSpringTrap(loc: BoundLocInfo) {
        arriveDelay()
        faceSquare(loc.coords)
        anim(SEQ_SEARCH)
        delay(1)
        if (statBase("stat.thieving") >= SAFE_DISARM_THIEVING || random.randomBoolean(DISARM_ONE_IN)) {
            soundSynth(SOUND_DISARM)
            locRepo.del(loc, TRAP_RESET)
            mes("You find the catch under the rock and wedge it shut.")
            return
        }
        soundSynth(SOUND_SPRINGTRAP)
        takeInstantHit(HitType.Typeless, random.of(TRAP_MIN_DAMAGE, TRAP_MAX_DAMAGE))
        mes("The floor springs up and catches you across the shins!")
    }

    private suspend fun ProtectedAccess.plankOverTrap(loc: BoundLocInfo) {
        arriveDelay()
        faceSquare(loc.coords)
        anim(SEQ_SEARCH)
        delay(1)
        soundSynth(SOUND_DISARM)
        locRepo.del(loc, TRAP_RESET)
        mes("You lay the plank across the rock. Whatever is under it will stay under it.")
    }

    private suspend fun ProtectedAccess.disarmSpearTrap(loc: BoundLocInfo) {
        arriveDelay()
        faceSquare(loc.coords)
        anim(SEQ_SEARCH)
        delay(1)
        if (random.randomBoolean(DISARM_ONE_IN)) {
            soundSynth(SOUND_DISARM)
            locRepo.del(loc, TRAP_RESET)
            mes("You jam the mechanism. The spears stay in the wall.")
            return
        }
        soundSynth(SOUND_TRIPWIRE)
        takeInstantHit(HitType.Typeless, random.of(TRAP_MIN_DAMAGE, SPEAR_MAX_DAMAGE))
        mes("A spear snaps out of the wall and catches you!")
    }

    private suspend fun ProtectedAccess.disarmLogTrap(loc: BoundLocInfo) {
        arriveDelay()
        faceSquare(loc.coords)
        anim(SEQ_SEARCH)
        delay(1)
        soundSynth(SOUND_DISARM)
        locRepo.del(loc, TRAP_RESET)
        mesbox(
            "The rock is a trigger, and the rope from it runs up into the dark. You cut it, and a " +
                "log the size of a man swings down and stops dead above your head.",
        )
    }

    private suspend fun ProtectedAccess.readJournal() {
        player.readJournal = 1
        if (quest.stage(player) == STAGE_BRIDGE) {
            mesbox(
                "<col=8B0000>Randas's journal</col><br><br>Day 14. Four orbs of light, one to each " +
                    "quarter of these caves, and the dark will not come past them. The last of us " +
                    "are down to one lamp between three.",
            )
            mesbox(
                "<col=8B0000>Randas's journal</col><br><br>Day 16. Cavillier says the orbs are not " +
                    "holding it back at all. They are holding it <i>in</i>. If that is true then " +
                    "the way on is to put them out, and heaven help whoever does it.",
            )
            return
        }
        mesbox(
            "<col=8B0000>Randas's journal</col><br><br>The last page is an account of the four " +
                "orbs, and the furnace that is the only thing hot enough to kill them.",
        )
    }

    private suspend fun ProtectedAccess.readTablet(tablet: String) {
        arriveDelay()
        player.readWell = 1
        mesbox("<col=8B0000>${TABLET_TEXT[tablet]}</col>")
    }

    private companion object {
        val ORB_LOCS = arrayOf("loc.caveorb", "loc.caveorb2", "loc.caveorb3", "loc.caveorb4")
        const val ORB_TAKEN = "loc.caveorb_vis"
        const val FURNACE = "loc.furnace_upass"
        const val SPRING_TRAP = "loc.upass_double_springtrap_trigger"
        const val SPEAR_TRAP = "loc.upass_speartrap"
        const val LOG_TRAP = "loc.upass_logtrap_trigger"

        val TABLETS =
            arrayOf(
                "loc.stone_tablet1_upass",
                "loc.stone_tablet2_upass",
                "loc.stone_tablet3_upass",
                "loc.stone_tablet4_upass",
                "loc.stone_tablet5_upass",
                "loc.stone_tablet7_upass",
                "loc.stone_tablet8_upass",
            )

        val TABLET_TEXT =
            mapOf(
                "loc.stone_tablet1_upass" to
                    "Turn back. There is nothing under this hill that wants you alive.",
                "loc.stone_tablet2_upass" to
                    "The path of the righteous man is beset on all sides by the inequities of " +
                        "the selfish.",
                "loc.stone_tablet3_upass" to
                    "Four lights hold the dark. Put them out and the dark holds you.",
                "loc.stone_tablet4_upass" to
                    "Below this stone the well goes down to the cells. None who went down came up.",
                "loc.stone_tablet5_upass" to
                    "Here lies Iban, son of Zamorak, who could not be killed by any hand.",
                "loc.stone_tablet7_upass" to
                    "The doors of Iban will not open while a beating, good heart is present.",
                "loc.stone_tablet8_upass" to
                    "Blessed be the servants of the one true master of this place.",
            )

        const val ORB_SOUND = "synth.godspell_charge"
        const val TRAP_RESET = 200
        const val TRAP_MIN_DAMAGE = 3
        const val TRAP_MAX_DAMAGE = 14
        const val SPEAR_MAX_DAMAGE = 12
        const val DISARM_ONE_IN = 3
        const val SAFE_DISARM_THIEVING = 50
    }
}
