package org.rsmod.content.events.halloween

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.enums.HalloweenTrickEnums
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc4
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class TrickOrTreatScript
@Inject
constructor(
    private val season: HalloweenSeason,
    private val npcRepo: NpcRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (treater in HalloweenTrickEnums.halloween_trick_or_treaters.mapNotNull { it.value }) {
            onOpNpc4(treater.internalName) { trickOrTreat(it.npc) }
        }
        for (ghost in GHOSTS) {
            onOpNpc1(ghost) { talkToGhost(it.npc) }
        }
    }

    private suspend fun ProtectedAccess.trickOrTreat(npc: Npc) {
        if (!startTrickOrTreat(season, npc)) {
            return
        }
        if (player.isRecentTreater(npc.slotId)) {
            startDialogue(npc) { chatNpc(neutral, random.pick(RECENT_LINES)) }
            return
        }
        rememberTreater(npc.slotId)
        when {
            useTerrifyingCharm() -> guaranteedTreats(npc, CHARM_LINES)
            player.ghostlyPresence -> {
                player.ghostlyPresence = false
                guaranteedTreats(npc, GHOSTLY_LINES)
            }
            else -> rollOutcome(npc)
        }
    }

    private suspend fun ProtectedAccess.guaranteedTreats(npc: Npc, lines: List<String>) {
        addTreats(GUARANTEED_TREATS)
        startDialogue(npc) { chatNpc(happy, random.pick(lines)) }
    }

    private suspend fun ProtectedAccess.rollOutcome(npc: Npc) {
        val roll = random.of(100)
        when {
            roll < TREAT_CHANCE -> {
                addTreats(random.of(MIN_TREATS, MAX_COMMON_TREATS))
                startDialogue(npc) { chatNpc(happy, random.pick(TREAT_LINES)) }
            }
            roll < JUNK_CHANCE && inv.hasFreeSpace() -> junkTrick(npc)
            roll < TRANSFORM_CHANCE -> transformTrick(npc)
            else -> ghostTrick(npc)
        }
    }

    private suspend fun ProtectedAccess.junkTrick(npc: Npc) {
        val junk = HalloweenTrickEnums.halloween_trick_junk.mapNotNull { it.value }
        val obj = random.pick(junk)
        invAdd(inv, obj.internalName)
        val article = if (obj.internalName == WOOL) "some" else "a"
        mes("You receive $article ${obj.name}.")
        startDialogue(npc) { chatNpc(laugh, random.pick(JUNK_LINES)) }
    }

    private suspend fun ProtectedAccess.transformTrick(npc: Npc) {
        npc.say("Trick!")
        val praying = vars[PROTECT_FROM_MAGIC] != 0
        if (praying && random.of(100) < PRAYER_BLOCK_CHANCE) {
            mes("Your prayer prevents the trick!")
            startDialogue(npc) { chatNpc(sad, random.pick(BLOCKED_LINES)) }
            return
        }
        val forms = HalloweenTrickEnums.halloween_trick_transforms.mapNotNull { it.value }
        val form = random.pick(forms)
        mes("The trick turns you into a ${form.name.lowercase()}!")
        transmog(form.internalName)
        delay(TRANSFORM_TICKS)
        resetTransmog()
        if (praying) {
            startDialogue(npc) { chatNpc(laugh, random.pick(UNBLOCKED_LINES)) }
        }
    }

    private suspend fun ProtectedAccess.ghostTrick(npc: Npc) {
        val type = ServerCacheManager.getNpc(random.pick(GHOSTS).asRSCM(RSCMType.NPC)) ?: return
        player.ghostVision = 1
        val ghost = Npc(type, random.of(player.coords, 1))
        npcRepo.add(ghost, GHOST_DURATION)
        ghost.facePlayer(player)
        ghost.say(if (wearsGhostspeak()) random.pick(GHOST_GREETINGS) else GHOST_WAIL)
        startDialogue(npc) { chatNpc(laugh, random.pick(GHOST_LINES)) }
    }

    private suspend fun ProtectedAccess.talkToGhost(ghost: Npc) {
        startDialogue(ghost) {
            if (!access.wearsGhostspeak()) {
                chatPlayer(quiz, "Hello, what are you doing here?")
                chatNpc(neutral, "OoooOoo. OooOooOOOOo OooOOooo!")
                chatPlayer(confused, "I see...")
                return@startDialogue
            }
            if (player.ghostlyPresence) {
                ghostlyGala()
                return@startDialogue
            }
            chatPlayer(quiz, "Hello, what are you doing here?")
            chatNpc(
                confused,
                "I'm not sure. One minute I'm at our Ghostly Gala, the next I'm in your " +
                    "physical realm!",
            )
            chatPlayer(neutral, "These tricksters must have summoned you!")
            chatNpc(
                happy,
                "Well if it's a trick they're after, this should make you especially spooky!",
            )
            player.ghostlyPresence = true
            mesbox("You feel a ghostly presence about you.")
            chatPlayer(happy, "Oh, uh, thank you!")
            chatNpc(
                happy,
                "You're welcome! Now I must get back to the Gala, I wouldn't want to miss the " +
                    "Dance Macabre!",
            )
            chatPlayer(happy, "Goodbye!")
        }
    }

    private suspend fun Dialogue.ghostlyGala() {
        chatPlayer(quiz, "Hello, how's the Gala?")
        chatNpc(happy, "Oh, for a moment I thought you were a ghost as well!")
        chatNpc(
            happy,
            "The Ghostly Gala is splendid and, of course, spooky! We do it every year around " +
                "this time, you know?",
        )
        chatPlayer(quiz, "So I've heard! Maybe I could see it one day?")
        chatNpc(
            neutral,
            "I'm afraid that could be a little tricky on account of your... tangibility.",
        )
        chatPlayer(sad, "Oh, I suppose so...")
        chatNpc(
            happy,
            "Perhaps one day we shall teach you to hold your own frightful festivity to match " +
                "our own Gala!",
        )
        chatPlayer(happy, "I'd like that!")
    }

    private fun ProtectedAccess.wearsGhostspeak(): Boolean =
        GHOSTSPEAK_AMULETS.any { it in worn }

    private fun ProtectedAccess.useTerrifyingCharm(): Boolean {
        val slot = worn.objs.indexOfFirst { it?.id == CHARM.asRSCM(RSCMType.OBJ) }
        if (slot == -1) {
            return false
        }
        val uses = worn.getValue(slot).vars + 1
        if (uses >= CHARM_USES) {
            invDel(worn, CHARM, slot = slot)
            mes("Your Terrifying charm crumbles away after its final scare.")
            return true
        }
        val charm = checkNotNull(ServerCacheManager.getItem(CHARM.asRSCM(RSCMType.OBJ)))
        return invReplaceSlot(worn, slot, 1, charm, vars = uses).success
    }

    private companion object {
        const val CHARM = "obj.hw22_trick_charm"
        const val WOOL = "obj.hw22_trick_wool"
        const val PROTECT_FROM_MAGIC = "varbit.prayer_protectfrommagic"
        const val GHOST_WAIL = "OOooOoOOOOoooOO!"

        const val CHARM_USES = 3
        const val GUARANTEED_TREATS = 5
        const val MIN_TREATS = 3
        const val MAX_COMMON_TREATS = 5
        const val TREAT_CHANCE = 55
        const val JUNK_CHANCE = 70
        const val TRANSFORM_CHANCE = 85
        const val PRAYER_BLOCK_CHANCE = 85
        const val TRANSFORM_TICKS = 6
        const val GHOST_DURATION = 50

        val GHOSTS = listOf("npc.hw22_trick_ghost_1", "npc.hw22_trick_ghost_2")
        val GHOSTSPEAK_AMULETS =
            listOf("obj.amulet_of_ghostspeak", "obj.amulet_of_ghostspeak_enchanted")

        val TREAT_LINES =
            listOf(
                "What a frightfully fantastic costume, you've earned these treats!",
                "Ooh, so spooky! Enjoy these treats!",
                "Wow! A creepy costume to rival the Grim Reaper himself! You'll probably enjoy " +
                    "these treats more, though.",
                "Spooktacular! Take these treats and be sure to show other people your ghastly " +
                    "getup!",
            )
        val GHOSTLY_LINES =
            listOf(
                "Oh my, there is something simply frightful about you! Enjoy the treats...",
                "Now you've really embraced the spooky spirit! Please, take these treats!",
                "You don't often see that sort of costume... You've definitely earned these " +
                    "treats!",
            )
        val CHARM_LINES =
            listOf(
                "What is that presence?! It's as if Death himself is with you. Please, take " +
                    "these treats!",
                "Wow! Now that costume sends shivers up my spine! You've earned these treats!",
                "Seems like you could teach me a thing or two! Enjoy these treats!",
            )
        val JUNK_LINES =
            listOf(
                "No treats this time, enjoy a trick instead!",
                "Trick, eh? How about this spooky specimen!",
                "Perhaps this will be spooky enough for you!",
            )
        val BLOCKED_LINES =
            listOf(
                "It didn't work?! Well, I'll spook you next time!",
                "Praying for protection? That's no fun...",
            )
        val UNBLOCKED_LINES =
            listOf(
                "That trick was too tricky even for your prayers!",
                "Your prayers couldn't protect you this time!",
            )
        val GHOST_LINES =
            listOf(
                "A minor haunting! How about that for a trick?",
                "How about a visit from a spooky someone?",
                "Is this ghastly enough for you!",
            )
        val GHOST_GREETINGS = listOf("Greetings.", "Oh, hello there.", "Don't be afraid!")
        val RECENT_LINES =
            listOf(
                "As much as I love your costume, why not show it to some other people as well?",
                "You're not getting anything more from me so soon!",
                "You tried that on me recently, spread the holiday spirit to some other people " +
                    "too!",
            )
    }
}
