package org.rsmod.content.quest.area.ardougne.undergroundpass

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.DOLL
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.IBAN_BOOK
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.KARDIA
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.KARDIA_CAT
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_SEARCH
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_DOLL
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Kardia, who lives over the broken bridges with a chest full of things she should not have, and
 * her cat, who is usually somewhere else.
 *
 * She will not give the doll up and she will not leave her house while anyone is watching it. The
 * one thing that moves her is the cat: hand it over and she carries it back to its basket, which
 * is long enough to get the chest open.
 */
@Singleton
class KardiaTheWitch
@Inject
constructor(
    private val quest: UndergroundPassQuest,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
) : PluginScript() {

    private val openChestType by lazy { locType(CHEST_OPEN) }
    private val shutChestType by lazy { locType(CHEST_SHUT) }

    override fun ScriptContext.startup() {
        for (witch in listOf(KARDIA, UndergroundPassQuest.visibleTwin(KARDIA))) {
            onOpNpc1(witch) { startDialogue(it.npc) { kardia() } }
        }
        for (cat in listOf(KARDIA_CAT, UndergroundPassQuest.visibleTwin(KARDIA_CAT))) {
            onOpNpc1(cat) { pickUpCat() }
            onOpNpc3(cat) { startDialogue(it.npc) { chatPlayer(neutral, "Here, puss.") } }
        }
        onOpLoc1(WINDOW) { lookThroughWindow() }
        onOpLoc1(CHEST_SHUT) { openChest(it.loc) }
        onOpLoc1(CHEST_OPEN) { searchChest() }
    }

    private fun locType(name: String): ObjectServerType =
        ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC)) ?: error("Missing loc: $name")

    private suspend fun Dialogue.kardia() {
        if (player.dollTaken) {
            chatNpc(angry, "Something's been in my chest. If I find out it was you...")
            return
        }
        if (player.gaveCat == 1) {
            chatNpc(happy, "There you are, my lovely. Come to your basket.")
            return
        }
        chatNpc(sad, "Have you seen a cat? Black one. She's been gone two days and there are things out there that eat cats.")
        when (
            choice3(
                "I'll look for her.", 1,
                "What's in the chest?", 2,
                "How do I kill Iban?", 3,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "I'll look for her.")
                chatNpc(happy, "Will you? Oh, bless you. She'll be north of here somewhere, being a nuisance.")
            }
            2 -> {
                chatPlayer(quiz, "What's in the chest?")
                chatNpc(angry, "Nothing that's yours. Keep away from it.")
            }
            3 -> {
                chatPlayer(quiz, "How do I kill Iban?")
                chatNpc(worried, "You don't say that name in this house. Get out.")
            }
        }
    }

    private suspend fun ProtectedAccess.pickUpCat() {
        arriveDelay()
        if (player.gaveCat == 1) {
            mes("The cat is home where it belongs.")
            return
        }
        if (invAdd(inv, CAT_ITEM).failure) {
            mes("You don't have enough inventory space.")
            return
        }
        anim(SEQ_SEARCH)
        soundSynth(CAT_SOUND)
        delay(1)
        UndergroundPassQuest.setVarBit(player, "varbit.upass_gavecat", 1)
        mesbox(
            "The cat lets you pick her up without a word of complaint, which is more than most " +
                "things down here would do. Kardia will want her back.",
        )
    }

    private suspend fun ProtectedAccess.lookThroughWindow() {
        arriveDelay()
        if (player.dollTaken) {
            mes("The chest inside stands open and empty.")
            return
        }
        mesbox(
            "Through the window you can see a chest at the foot of the bed, and Kardia sitting " +
                "where she can watch it. She is not going to move while you are standing here.",
        )
    }

    private suspend fun ProtectedAccess.openChest(chest: BoundLocInfo) {
        arriveDelay()
        if (player.gaveCat == 0) {
            mes("Kardia is watching the chest. There is no getting into it with her sitting there.")
            return
        }
        soundSynth(CHEST_SOUND)
        locRepo.change(chest, openChestType, CHEST_TICKS)
        delay(1)
        searchChest()
    }

    private suspend fun ProtectedAccess.searchChest() {
        arriveDelay()
        if (player.dollTaken) {
            mes("The chest is empty.")
            return
        }
        anim(SEQ_SEARCH)
        delay(1)
        if (invAdd(inv, DOLL).failure) {
            mes("You don't have enough inventory space to take everything in the chest.")
            return
        }
        player.dollTaken = true
        invAddOrDrop(objRepo, IBAN_BOOK)
        for (potion in CHEST_POTIONS) {
            invAddOrDrop(objRepo, potion)
        }
        quest.advanceTo(this, STAGE_DOLL)
        objbox(
            DOLL,
            "Under the potions and the papers there is a doll, sewn out of sacking and stuck all " +
                "over with hair. It has his face on it.",
        )
    }

    private companion object {
        const val WINDOW = "loc.upass_witchwindow"
        const val CHEST_SHUT = "loc.cavewitchchest"
        const val CHEST_OPEN = "loc.cavewitchchestopen"
        const val CAT_ITEM = "obj.cavewitchcat"
        const val CAT_SOUND = "synth.happy_meeoow"
        const val CHEST_SOUND = "synth.chest_open"
        const val CHEST_TICKS = 200

        val CHEST_POTIONS = arrayOf("obj.1dose2restore", "obj.1dose2attack")
    }
}
