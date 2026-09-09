package org.rsmod.content.areas.misc.stronghold

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.midiJingle
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The reward at the end of each level - Gift of Peace, Grain of Plenty, Box of Health and Cradle
 * of Life - plus the Dead Explorer, the notice by the cradle and the two books.
 *
 * Claiming a reward sets the level's emote varbit, which is also what marks the level completed
 * for the doors and shortcut portals. The Cradle keeps handing out boots on later searches.
 */
class StrongholdRewardScript @Inject constructor(private val objRepo: ObjRepository) : PluginScript() {
    private val allStats: List<String> by lazy {
        ServerCacheManager.getStats().values.map { RSCM.getReverseMapping(RSCMType.STAT, it.id) }
    }

    override fun ScriptContext.startup() {
        onOpLoc1(Stronghold.WAR_CHEST) { giftOfPeace() }
        onOpLoc1(Stronghold.FAMINE_SACK) { grainOfPlenty() }
        onOpLoc1(Stronghold.PESTILENCE_CHEST) { boxOfHealth() }
        onOpLoc1(Stronghold.DEATH_CRADLE) { cradleOfLife() }
        onOpLoc1(Stronghold.DEAD_EXPLORER) { deadExplorer() }
        onOpLoc1(Stronghold.DEATH_NOTICE) { readNotice() }
        onOpHeld1(Stronghold.STRONGHOLD_NOTES) { readStrongholdNotes() }
        onOpHeld1(Stronghold.SECURITY_BOOK) { readSecurityBook() }
    }

    private suspend fun ProtectedAccess.giftOfPeace() {
        arriveDelay()
        anim(Stronghold.CHEST_ANIM)
        if (Level.WAR.isCompleted(player)) {
            mes("You have already been given the gift of peace.")
            return
        }
        claim(Level.WAR)
        statRestore(STAT_HITPOINTS)
        statRestore(STAT_PRAYER)
        objbox(Stronghold.COINS, Level.WAR.rewardMessage)
    }

    private suspend fun ProtectedAccess.grainOfPlenty() {
        arriveDelay()
        anim(Stronghold.SEARCH_ANIM)
        if (Level.FAMINE.isCompleted(player)) {
            mes("You search the sack, but the grain is all you find.")
            return
        }
        claim(Level.FAMINE)
        objbox(Stronghold.COINS, Level.FAMINE.rewardMessage)
    }

    private suspend fun ProtectedAccess.boxOfHealth() {
        arriveDelay()
        anim(Stronghold.CHEST_ANIM)
        if (Level.PESTILENCE.isCompleted(player)) {
            statRestoreAll(allStats)
            mes("The box is empty, but its medicine still restores you.")
            return
        }
        claim(Level.PESTILENCE)
        statRestoreAll(allStats)
        objbox(Stronghold.COINS, Level.PESTILENCE.rewardMessage)
    }

    private suspend fun ProtectedAccess.cradleOfLife() {
        arriveDelay()
        anim(Stronghold.SEARCH_ANIM)
        if (!Level.DEATH.isCompleted(player)) {
            claim(Level.DEATH)
            statRestoreAll(allStats)
            mesbox(Level.DEATH.rewardMessage)
        } else {
            mesbox("You search the cradle and find another pair of boots waiting for you.")
        }
        val boots =
            choice3(
                "Fancy boots",
                Stronghold.FANCY_BOOTS,
                "Fighting boots",
                Stronghold.FIGHTING_BOOTS,
                "Fancier boots",
                Stronghold.FANCIER_BOOTS,
                title = "Which boots would you like?",
            )
        invAddOrDrop(objRepo, boots)
        objbox(boots, "You take the ${boots.bootsName()} from the cradle.")
    }

    private fun ProtectedAccess.claim(level: Level) {
        vars[level.emoteVarbit] = 1
        player.midiJingle(level.jingle)
        if (level.coins > 0) {
            invAddOrDrop(objRepo, Stronghold.COINS, level.coins)
        }
        mes("You have unlocked the '${level.emoteName}' emote.")
    }

    private suspend fun ProtectedAccess.deadExplorer() {
        arriveDelay()
        anim(Stronghold.SEARCH_ANIM)
        if (invTotal(inv, Stronghold.STRONGHOLD_NOTES) > 0) {
            mes("You rummage around in the dead explorer's bag... You don't find anything.")
            return
        }
        mes("You rummage around in the dead explorer's bag.....")
        delay(1)
        invAddOrDrop(objRepo, Stronghold.STRONGHOLD_NOTES)
        objbox(Stronghold.STRONGHOLD_NOTES, "You find a battered book of notes about the Stronghold.")
    }

    private suspend fun ProtectedAccess.readNotice() {
        mesbox(
            "Congratulations, adventurer! You have braved the Stronghold of Security and learned " +
                "how to keep your account safe. Use a unique password, enable two-factor " +
                "authentication and set a bank PIN. Stay secure!"
        )
    }

    private suspend fun ProtectedAccess.readStrongholdNotes() {
        mesbox(
            "The Stronghold of Security lies beneath Barbarian Village. Its four levels - the " +
                "Vault of War, the Catacomb of Famine, the Pit of Pestilence and the Sepulchre " +
                "of Death - each hold a reward for those who prove they know how to stay safe."
        )
        mesbox(
            "The doors will test you. Answer them wisely, and never, ever give your password " +
                "or bank PIN to anyone."
        )
    }

    private suspend fun ProtectedAccess.readSecurityBook() {
        mesbox(
            "Keep your account secure: never share your password, set a bank PIN with any " +
                "banker, enable two-factor authentication on your account and email, and " +
                "report anyone who asks for your details."
        )
    }

    private fun String.bootsName(): String =
        when (this) {
            Stronghold.FANCY_BOOTS -> "fancy boots"
            Stronghold.FIGHTING_BOOTS -> "fighting boots"
            else -> "fancier boots"
        }

    private companion object {
        const val STAT_HITPOINTS = "stat.hitpoints"
        const val STAT_PRAYER = "stat.prayer"
    }
}
