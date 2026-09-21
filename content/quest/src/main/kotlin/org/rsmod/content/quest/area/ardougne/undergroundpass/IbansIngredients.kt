package org.rsmod.content.quest.area.ardougne.undergroundpass

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.AMULETS
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.DOLL
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.DOVE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.GAUNTLETS
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.KALRAG
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SHADOW
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_CHEST_OPEN
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_DOLL
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Three of Iban's four elements: his shadow in the chest the three demons were summoned to keep,
 * his conscience in the bones of a dove left in one of the Soulless' cages, and his blood in the
 * giant spider Kalrag. The fourth, his flesh, is burnt out of his tomb by the dwarves' camp.
 */
@Singleton
class IbansIngredients
@Inject
constructor(private val locRepo: LocRepository) : PluginScript() {

    private val openChestType by lazy {
        ServerCacheManager.getObject(CHEST_OPEN.asRSCM(RSCMType.LOC)) ?: error("Missing $CHEST_OPEN")
    }

    override fun ScriptContext.startup() {
        onOpLoc1(SHADOW_CHEST) { openShadowChest(it.loc) }
        onOpLoc1(DOVE_CAGE) { searchCage(real = true) }
        onOpLoc1(DECOY_CAGE) { searchCage(real = false) }
    }

    /** The chest only opens to someone carrying the amulets of all three of its keepers. */
    private suspend fun ProtectedAccess.openShadowChest(chest: BoundLocInfo) {
        arriveDelay()
        mes("You attempt to open the chest...")
        if (AMULETS.any { !inv.contains(it) }) {
            mes("But it's magically sealed.")
            return
        }
        mes("The three amulets glow red in your backpack...")
        for (amulet in AMULETS) {
            invDel(inv, amulet)
        }
        delay(1)
        mes("...You place them on the chest and it opens.")
        soundSynth(SOUND_CHEST_OPEN)
        delay(1)
        if (inv.contains(SHADOW) || player.shadowOnDoll == 1) {
            mes("But you find nothing.")
        } else {
            invAdd(inv, SHADOW)
            mes("Inside you find a strange dark liquid.")
        }
        locRepo.change(chest, openChestType, CHEST_TICKS)
    }

    /**
     * Fifteen of the cages hold nothing but an entranced prisoner; one holds the bones of a dove.
     * Every prisoner bites whoever reaches past him, and only Klank's gauntlets stop it.
     */
    private suspend fun ProtectedAccess.searchCage(real: Boolean) {
        arriveDelay()
        mes("The man seems to be entranced.")
        delay(1)
        mes("You search through the bottom of the cage...")
        delay(1)
        if (real && !inv.contains(DOVE) && player.doveOnDoll == 0) {
            invAdd(inv, DOVE)
            mes("...and find Iban's dove.")
        } else {
            mes("...But you find nothing.")
        }
        delay(1)
        mes("The soulless being bites into your arm.")
        if (player.worn.contains(GAUNTLETS)) {
            delay(1)
            mes("Klank's gauntlets protect you.")
            return
        }
        say("Aaarrgghh!")
        takeInstantHit(HitType.Typeless, BITE_DAMAGE)
    }

    private companion object {
        const val SHADOW_CHEST = "loc.upassshutchest1"
        const val CHEST_OPEN = "loc.chestopen"
        const val DOVE_CAGE = "loc.upass_cage_dummy"
        const val DECOY_CAGE = "loc.upass_cage_dummy_dummy"
        const val CHEST_TICKS = 10
        const val BITE_DAMAGE = 10
    }
}

/**
 * Kalrag feeds on the warm blood of whatever comes down to his pit, and there is enough of Iban in
 * it to count. Its poisoned blood only goes on the doll if the player is carrying it when he dies.
 */
class KalragKillHook
@Inject
constructor(
    private val quest: UndergroundPassQuest,
    private val launcher: ProtectedAccessLauncher,
) : NpcDeathKillHook {

    private val kalragIds: Set<Int> by lazy {
        setOf(KALRAG.asRSCM(RSCMType.NPC), UndergroundPassQuest.visibleTwin(KALRAG).asRSCM(RSCMType.NPC))
    }

    override fun onKill(context: NpcDeathKillContext) {
        if (context.npc.id !in kalragIds) {
            return
        }
        launcher.launch(context.hero) { kalragDies() }
    }

    private suspend fun ProtectedAccess.kalragDies() {
        mes("Kalrag slumps to the floor...")
        if (quest.stage(player) != STAGE_DOLL) {
            return
        }
        delay(1)
        mes("poison flows from the corpse over the soil.")
        delay(1)
        when {
            !inv.contains(DOLL) -> {
                mes("It quickly seeps away into the earth.")
                delay(1)
                mes("You dare not collect any without Iban's doll.")
            }
            player.venomOnDoll == 1 -> mes("You have already collected Iban's blood on the doll.")
            else -> {
                mes("You smear the doll of Iban in the poisoned blood...")
                delay(1)
                UndergroundPassQuest.setVarBit(player, "varbit.upass_venom_on_doll", 1)
                mes("It smells horrific.")
            }
        }
    }
}
