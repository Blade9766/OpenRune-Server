package org.rsmod.content.quest.area.morytania.ghostsahoy.npcs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BOAT_GIVEN
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BOAT_NONE
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BOOK
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BOWL_OF_MILKY_TEA
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BOWL_OF_TEA
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.CRONE
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.CUP_OF_MILKY_TEA
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.CUP_OF_TEA
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.GHOSTSPEAK
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.GHOSTSPEAK_ENCHANTED
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.MANUAL
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.PORCELAIN_CUP
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.ROBES
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.STAGE_ENCHANTED
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.STAGE_FIND_CRONE
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.STAGE_GATHER
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.TEA_ASKED
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.TEA_DRUNK
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.TOY_BOAT
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.TOY_BOAT_REPAIRED
import org.rsmod.content.quest.area.morytania.ghostsahoy.owns
import org.rsmod.content.quest.manager.menu
import org.rsmod.game.entity.Npc
import org.rsmod.game.inv.Inventory
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Old Crone in her hut by the water west of the Port Phasmatys farm. A cup of milky nettle
 * tea in her own porcelain cup brings back her memory of Necrovarus; asking how to help her
 * earns her lost son's model ship. She keeps each of the three enchantment items as it is handed
 * over and enchants the ghostspeak amulet once she has all of them.
 *
 * The enchantment swaps the amulet in a single step before any animation plays, so nothing can
 * interrupt it half done. If the enchanted amulet is later lost before Necrovarus is commanded,
 * she still has the materials and will enchant another ghostspeak amulet.
 */
class OldCrone @Inject constructor(private val ahoy: GhostsAhoyQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(CRONE) { startDialogue(it.npc) { crone(it.npc) } }
    }

    private suspend fun Dialogue.crone(npc: Npc) {
        val stage = ahoy.stage(player)
        when {
            stage < STAGE_FIND_CRONE -> chatNpc(neutral, "Leave an old woman in peace, dearie. I've nothing to sell.")
            stage == STAGE_FIND_CRONE -> tea()
            stage == STAGE_GATHER -> gather(npc)
            stage == STAGE_ENCHANTED -> enchanted(npc)
            stage >= STAGE_COMPLETE -> chatNpc(happy, "So the ghosts are free at last. Thank you, dearie. Perhaps my boy will come home now.")
            else -> chatNpc(happy, "Go on, tell Velorina the good news!")
        }
    }

    private suspend fun Dialogue.tea() {
        chatPlayer(neutral, "I'm here about Necrovarus.")
        if (ahoy.teaState(player) < TEA_ASKED) {
            chatNpc(confused, "Necrovarus? The name is familiar, but my memory isn't what it was.")
            chatNpc(happy, "A nice hot cup of nettle tea always clears my head. I would love a cup of nettle tea.")
            ahoy.setTeaState(player, TEA_ASKED)
            return
        }
        when {
            player.inv.contains(CUP_OF_MILKY_TEA) -> drinkTea()
            player.inv.contains(CUP_OF_TEA) -> {
                chatNpc(sad, "Where's the milk? I can't abide tea without milk, dearie.")
            }
            player.inv.contains(BOWL_OF_TEA) || player.inv.contains(BOWL_OF_MILKY_TEA) || carriesPlainCupOfTea() -> {
                chatNpc(confused, "Tea in a bowl? Goodness, no. It must go in my porcelain cup.")
                offerCup()
            }
            else -> chatNpc(neutral, "Have you brought my nettle tea yet, dearie?")
        }
    }

    private fun Dialogue.carriesPlainCupOfTea(): Boolean =
        player.inv.contains("obj.cup_of_nettletea") || player.inv.contains("obj.cup_of_nettletea_milky")

    private suspend fun Dialogue.offerCup() {
        if (player.owns(PORCELAIN_CUP) || player.owns(CUP_OF_TEA) || player.owns(CUP_OF_MILKY_TEA)) {
            chatNpc(neutral, "You already have my cup. Pour the tea into it.")
            return
        }
        if (access.invAdd(access.inv, PORCELAIN_CUP).failure) {
            chatNpc(neutral, "I'd give you my cup, but your hands are full.")
            return
        }
        objbox(PORCELAIN_CUP, "The old crone gives you her porcelain cup.")
        chatNpc(neutral, "Pour the tea into that, and don't forget the milk.")
    }

    private suspend fun Dialogue.drinkTea() {
        if (access.invDel(access.inv, CUP_OF_MILKY_TEA).failure) {
            return
        }
        ahoy.setTeaState(player, TEA_DRUNK)
        ahoy.advanceTo(access, STAGE_GATHER)
        mesbox("The old crone sips the tea. She keeps the cup.")
        chatNpc(happy, "Mmm, lovely. The fog is lifting... Necrovarus. Yes, I remember him.")
        chatNpc(neutral, "Before he was a priest he was my teacher. We studied the old necromantic arts together, until his ambition frightened me away.")
        chatNpc(neutral, "He is powerful, but he is bound by the same laws as any ghost. A ghostspeak amulet, properly enchanted, would let you command him.")
        explainItems()
    }

    private suspend fun Dialogue.explainItems() {
        chatNpc(neutral, "I need three things to enchant one. First, the Book of Haricanto, a book of ancient spells. It was lost at sea long ago.")
        chatNpc(neutral, "Second, a manual to translate it, for it is written in a tongue I never learned. The eastern traders might have one.")
        chatNpc(neutral, "And third, something that belongs to Necrovarus himself. His mystical robes would do nicely.")
        chatNpc(neutral, "Bring me those, and your ghostspeak amulet, and I will perform the enchantment.")
    }

    private suspend fun Dialogue.gather(npc: Npc) {
        handOver()
        if (ahoy.hasGivenAll(player)) {
            enchant(npc)
            return
        }
        val topic =
            menu(
                "I'm here about Necrovarus." to Topic.ITEMS,
                "You're doing so much for me. Can I do anything for you?" to Topic.HELP,
                "Goodbye." to Topic.BYE,
            )
        when (topic) {
            Topic.ITEMS -> {
                chatPlayer(quiz, "I'm here about Necrovarus.")
                remaining()
            }
            Topic.HELP -> help()
            Topic.BYE -> chatPlayer(neutral, "Goodbye.")
        }
    }

    /** Takes whichever of the three items the player has brought, one at a time. */
    private suspend fun Dialogue.handOver() {
        for (obj in listOf(BOOK, MANUAL, ROBES)) {
            if (!player.inv.contains(obj) || given(obj)) {
                continue
            }
            if (access.invDel(access.inv, obj).failure) {
                continue
            }
            ahoy.setGiven(player, obj)
            objbox(obj, "You give the ${name(obj)} to the old crone.")
        }
    }

    private suspend fun Dialogue.remaining() {
        val missing = listOf(BOOK, MANUAL, ROBES).filterNot { given(it) }.map(::name)
        if (missing.isEmpty()) {
            return
        }
        chatNpc(neutral, "I still need ${missing.joinToString(" and ")}.")
        if (!ahoy.givenBook(player)) {
            chatNpc(neutral, "The Book of Haricanto went down with a pirate ship. Perhaps someone who sailed on her knows more.")
        }
        if (!ahoy.givenManual(player)) {
            chatNpc(neutral, "For the manual, try the eastern trader at the docks.")
        }
        if (!ahoy.givenRobes(player)) {
            chatNpc(neutral, "Necrovarus keeps his robes in his temple. You'll need to get past his door somehow.")
        }
    }

    private suspend fun Dialogue.help() {
        chatPlayer(quiz, "You're doing so much for me. Can I do anything for you?")
        val boat = ahoy.toyBoat(player)
        if (boat == BOAT_NONE) {
            chatNpc(sad, "That is kind of you. Many years ago my son ran away to sea with a crew of pirates. He was only about twelve.")
            chatNpc(sad, "I never saw him again. If you ever come across him, please give him this, so he knows his mother still thinks of him.")
            giveBoat(replacement = false)
            return
        }
        if (boat == BOAT_GIVEN && !player.owns(TOY_BOAT) && !player.owns(TOY_BOAT_REPAIRED)) {
            chatPlayer(sad, "I'm afraid I've lost the model ship you gave me.")
            chatNpc(sad, "Lost it? Oh dear. Well, I made a second one, years ago. Do be careful with it.")
            giveBoat(replacement = true)
            return
        }
        chatNpc(neutral, "Just keep looking for my son, dearie. That's all I ask.")
    }

    private suspend fun Dialogue.giveBoat(replacement: Boolean) {
        if (access.invAdd(access.inv, TOY_BOAT).failure) {
            chatNpc(neutral, "Oh, but your hands are full. Come back when you have room.")
            return
        }
        ahoy.setToyBoat(player, BOAT_GIVEN)
        ahoy.clearApplied(player)
        objbox(TOY_BOAT, "The old crone gives you a model ship.")
        if (!replacement) {
            chatNpc(sad, "It is a model of the very ship he sailed away on. Its little flag has torn off, I'm afraid.")
        }
    }

    private suspend fun Dialogue.enchant(npc: Npc) {
        chatNpc(happy, "You have brought everything! Now, give me your ghostspeak amulet.")
        val holder = amuletHolder()
        if (holder == null) {
            chatNpc(confused, "Where is your ghostspeak amulet, dearie? I can't enchant one you aren't carrying.")
            return
        }
        val slot = holder.indexOfFirst { it?.id == PLAIN_ID }
        val enchanted = checkNotNull(ServerCacheManager.getItem(ENCHANTED_ID))
        if (access.invReplaceSlot(holder, slot, 1, enchanted).failure) {
            return
        }
        ahoy.advanceTo(access, STAGE_ENCHANTED)
        chatNpc(neutral, "Stand back, dearie, and keep quiet while I work.")
        npc.anim(ENCHANT_SEQ)
        access.spotanim(ENCHANT_SPOTANIM, height = ENCHANT_HEIGHT)
        mesbox("The old crone chants over your amulet. It glows with a ghostly light.")
        chatNpc(neutral, "There. Wear it when you face Necrovarus, and he will have to obey one command. Choose it well.")
    }

    private suspend fun Dialogue.enchanted(npc: Npc) {
        if (player.owns(GHOSTSPEAK_ENCHANTED)) {
            chatNpc(neutral, "Go on, wear the amulet and command Necrovarus to free the ghosts.")
            return
        }
        chatPlayer(sad, "I've lost the enchanted amulet.")
        chatNpc(sad, "Careless! Well, I still have the book and the robes. Give me another ghostspeak amulet.")
        val holder = amuletHolder() ?: return chatNpc(neutral, "Come back with a ghostspeak amulet.")
        val slot = holder.indexOfFirst { it?.id == PLAIN_ID }
        if (access.invReplaceSlot(holder, slot, 1, checkNotNull(ServerCacheManager.getItem(ENCHANTED_ID))).failure) {
            return
        }
        npc.anim(ENCHANT_SEQ)
        access.spotanim(ENCHANT_SPOTANIM, height = ENCHANT_HEIGHT)
        mesbox("The old crone enchants your ghostspeak amulet.")
    }

    private fun Dialogue.amuletHolder(): Inventory? =
        when {
            player.inv.contains(GHOSTSPEAK) -> player.inv
            player.worn.contains(GHOSTSPEAK) -> player.worn
            else -> null
        }

    private fun Dialogue.given(obj: String): Boolean =
        when (obj) {
            BOOK -> ahoy.givenBook(player)
            MANUAL -> ahoy.givenManual(player)
            else -> ahoy.givenRobes(player)
        }

    private fun name(obj: String): String =
        when (obj) {
            BOOK -> "Book of Haricanto"
            MANUAL -> "translation manual"
            else -> "mystical robes"
        }

    private enum class Topic {
        ITEMS,
        HELP,
        BYE,
    }

    private companion object {
        const val ENCHANT_SEQ = "seq.human_enchantamuletlvl1"
        const val ENCHANT_SPOTANIM = "spotanim.enchant_amulet_lvl1"
        const val ENCHANT_HEIGHT = 92
        val PLAIN_ID: Int by lazy { GHOSTSPEAK.asRSCM() }
        val ENCHANTED_ID: Int by lazy { GHOSTSPEAK_ENCHANTED.asRSCM() }
    }
}
