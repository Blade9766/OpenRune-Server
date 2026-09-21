package org.rsmod.content.quest.area.ardougne.undergroundpass

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.AMULETS
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.DOOMION
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.GAUNTLETS
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.HALF_SOULLESS
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.HOLTHION
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.KALRAG
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.OTHAINIAN
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_SEARCH
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_DEMON_DEATH
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The three things of Iban's that have to be taken off something living: his blood out of Kalrag,
 * his shadow out of the chest the three demons stand over, and his dove out of the cages in the
 * north of the cavern.
 *
 * The cages are watched by the half-soulless, whose touch takes the skin off anyone reaching past
 * them bare-handed. Klank's gauntlets are the whole reason he offers them.
 */
@Singleton
class IbansIngredients
@Inject
constructor(private val doll: DollOfIban, private val random: GameRandom) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(SHADOW_CHEST) { openShadowChest() }
        onOpLoc1(DOVE_CAGE) { searchCage(real = true) }
        onOpLoc1(DECOY_CAGE) { searchCage(real = false) }
        for (soulless in listOf(HALF_SOULLESS, UndergroundPassQuest.visibleTwin(HALF_SOULLESS))) {
            onOpNpc1(soulless) { touchHalfSoulless() }
        }
    }

    /**
     * The chest behind the demons. It only opens for someone carrying all three of their amulets,
     * which means all three of them have to go down.
     */
    private suspend fun ProtectedAccess.openShadowChest() {
        arriveDelay()
        if (player.shadowChestOpen) {
            mes("The chest is empty and the lid will not shut again.")
            return
        }
        val held = AMULETS.count { invContains(inv, it) }
        if (held < AMULETS.size) {
            mesbox(
                "There is no lock on the chest, but it will not lift. Three iron rings are set " +
                    "into the lid, and there is a demon wearing the match of each of them.",
            )
            return
        }
        if (!with(doll) { holdingDoll() }) {
            mes("Whatever is in there would need catching in something. The doll, perhaps.")
            return
        }
        anim(SEQ_SEARCH)
        soundSynth(CHEST_SOUND)
        delay(2)
        for (amulet in AMULETS) {
            invDel(inv, amulet)
        }
        player.shadowChestOpen = true
        mesbox("The lid comes up, and what comes out of the chest is not light and is not smoke.")
        with(doll) { addIngredient(Ingredient.SHADOW_OF_IBAN) }
    }

    /**
     * Sixteen cages hang in the north of the cavern and fifteen of them hold nothing at all. The
     * one that does is fixed by the map, not by chance, so the search is the same every time.
     */
    private suspend fun ProtectedAccess.searchCage(real: Boolean) {
        arriveDelay()
        if (!player.worn.contains(GAUNTLETS) && !invContains(inv, GAUNTLETS)) {
            mes("The bars are crusted with something that burns. I am not reaching in bare-handed.")
            return
        }
        anim(SEQ_SEARCH)
        delay(1)
        if (!real) {
            mes("Nothing but feathers and rust.")
            return
        }
        if (player.doveOnDoll == 1) {
            mes("The cage is empty now.")
            return
        }
        if (!with(doll) { holdingDoll() }) {
            mes("There are remains in here, but nothing to put them in.")
            return
        }
        mesbox(
            "At the back of the cage, under a hundred years of dust, is a dove. It has been dead " +
                "a very long time and it has not rotted at all.",
        )
        with(doll) { addIngredient(Ingredient.DOVE_OF_IBAN) }
    }

    private suspend fun ProtectedAccess.touchHalfSoulless() {
        if (player.worn.contains(GAUNTLETS)) {
            mes("It reaches for you and finds nothing but dwarf leather.")
            return
        }
        soundSynth(SOULLESS_SOUND)
        takeInstantHit(HitType.Typeless, random.of(SOULLESS_MIN, SOULLESS_MAX))
        mes("The half-soulless lays a hand on you, and everything it touches goes cold.")
    }

    private companion object {
        const val SHADOW_CHEST = "loc.upassshutchest1"
        const val DOVE_CAGE = "loc.upass_cage_dummy"
        const val DECOY_CAGE = "loc.upass_cage_dummy_dummy"
        const val CHEST_SOUND = "synth.chest_open"
        const val SOULLESS_SOUND = "synth.ghost_attack"
        const val SOULLESS_MIN = 2
        const val SOULLESS_MAX = 8
    }
}

/**
 * Kalrag has been feeding on whatever comes down the corridor north of the tomb for as long as
 * Iban has been here, and there is enough of him in her to be going on with.
 */
class KalragKillHook
@Inject
constructor(private val doll: DollOfIban, private val launcher: ProtectedAccessLauncher) :
    NpcDeathKillHook {

    private val kalragIds: Set<Int> by lazy {
        setOf(KALRAG.asRSCM(RSCMType.NPC), UndergroundPassQuest.visibleTwin(KALRAG).asRSCM(RSCMType.NPC))
    }

    override fun onKill(context: NpcDeathKillContext) {
        if (context.npc.id !in kalragIds) {
            return
        }
        val hero = context.hero
        if (hero.venomOnDoll == 1) {
            return
        }
        launcher.launch(hero) { drainKalrag() }
    }

    private suspend fun ProtectedAccess.drainKalrag() {
        if (!with(doll) { holdingDoll() }) {
            mes("Whatever is running out of the spider would need catching in something.")
            return
        }
        mesbox(
            "The spider's blood is not a spider's blood. It goes into the doll of its own accord " +
                "and the doll grows warm.",
        )
        with(doll) { addIngredient(Ingredient.BLOOD_OF_IBAN) }
    }
}

/** Each of Iban's three demons carries one of the rings the chest behind them is locked with. */
class DemonKillHook
@Inject
constructor(
    private val launcher: ProtectedAccessLauncher,
    private val objRepo: ObjRepository,
) : NpcDeathKillHook {

    private val amuletsByNpc: Map<Int, Pair<String, String>> by lazy {
        buildMap {
            val demons =
                listOf(
                    DOOMION to (AMULETS[0] to "varbit.upass_amulet_doomion"),
                    OTHAINIAN to (AMULETS[1] to "varbit.upass_amulet_othainian"),
                    HOLTHION to (AMULETS[2] to "varbit.upass_amulet_holthion"),
                )
            for ((npc, reward) in demons) {
                put(npc.asRSCM(RSCMType.NPC), reward)
                put(UndergroundPassQuest.visibleTwin(npc).asRSCM(RSCMType.NPC), reward)
            }
        }
    }

    override fun onKill(context: NpcDeathKillContext) {
        val (amulet, varbit) = amuletsByNpc[context.npc.id] ?: return
        launcher.launch(context.hero) { claimAmulet(amulet, varbit) }
    }

    private suspend fun ProtectedAccess.claimAmulet(amulet: String, varbit: String) {
        soundSynth(SOUND_DEMON_DEATH)
        UndergroundPassQuest.setVarBit(player, varbit, 1)
        invAddOrDrop(objRepo, amulet)
        mes("The demon leaves an amulet behind it.")
    }
}
