package org.rsmod.content.quest.area.wilderness.magearena.npcs

import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.wilderness.magearena.God
import org.rsmod.content.quest.area.wilderness.magearena.KolodionFight
import org.rsmod.content.quest.area.wilderness.magearena.MageArena2Quest
import org.rsmod.content.quest.area.wilderness.magearena.MageArena2Quest.Companion.STAGE_COMPONENTS
import org.rsmod.content.quest.area.wilderness.magearena.MageArena2Quest.Companion.STAGE_HUNTING
import org.rsmod.content.quest.area.wilderness.magearena.MageArenaCoords
import org.rsmod.content.quest.area.wilderness.magearena.MageArenaQuest
import org.rsmod.content.quest.area.wilderness.magearena.MageArenaQuest.Companion.CASTS_TO_UNLOCK
import org.rsmod.content.quest.area.wilderness.magearena.MageArenaQuest.Companion.STAGE_CAPE
import org.rsmod.content.quest.area.wilderness.magearena.MageArenaQuest.Companion.STAGE_DEFEATED
import org.rsmod.content.quest.area.wilderness.magearena.MageArenaQuest.Companion.STAGE_DUEL
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Kolodion, master of battle magic, in the cave beneath the arena. He runs both miniquests:
 * the duel that opens the arena, the training talk afterwards, and the hunt for the three god
 * followers whose remains let him imbue a god cape.
 */
class Kolodion
@Inject
constructor(
    private val mageArena: MageArenaQuest,
    private val mageArena2: MageArena2Quest,
    private val fight: KolodionFight,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(MageArenaCoords.KOLODION) { startDialogue(it.npc) { talk(it.npc) } }
        onOpNpcU(MageArenaCoords.KOLODION) { useOnKolodion(it.npc, it.objType) }
    }

    private suspend fun Dialogue.talk(npc: Npc) {
        when (mageArena.stage(player)) {
            0 -> introduction()
            STAGE_DUEL -> duelInProgress()
            STAGE_DEFEATED, STAGE_CAPE -> afterDuel()
            else -> afterMiniquest(npc)
        }
    }

    /* ---------------------------------------------------------------- Mage Arena I */

    private suspend fun Dialogue.introduction() {
        chatPlayer(quiz, "Hello there. What is this place?")
        if (player.statBase("stat.magic") < MageArenaQuest.MAGIC_REQ) {
            chatNpc(
                angry,
                "Do not waste my time with trivial questions. I am the Great Kolodion, master of " +
                    "battle magic. I have an arena to run.",
            )
            chatPlayer(quiz, "Can I enter?")
            chatNpc(laugh, "Hah! A wizard of your level? Don't be absurd.")
            return
        }
        chatNpc(
            neutral,
            "I am the great Kolodion, master of battle magic, and this is my battle arena. Top " +
                "wizards travel from all over Gielinor to fight here.",
        )
        introductionOptions()
    }

    private suspend fun Dialogue.introductionOptions() {
        when (
            choice3(
                "Can I fight here?", 1,
                "What's the point of that?", 2,
                "That's barbaric!", 3,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "Can I fight here?")
                chatNpc(
                    neutral,
                    "My arena is open to any high level wizard, but this is no game. Many wizards " +
                        "fall in this arena, never to rise again. The strongest mages have been destroyed.",
                )
                chatNpc(quiz, "If you're sure you want in?")
                joinChoice()
            }
            2 -> {
                chatPlayer(quiz, "What's the point of that?")
                chatNpc(
                    neutral,
                    "We learn how to use our magic to its fullest and how to channel the forces of " +
                        "the cosmos into our world...",
                )
                chatNpc(laugh, "But mainly, I just like blasting people into dust.")
                introductionOptions()
            }
            3 -> {
                chatPlayer(angry, "That's barbaric!")
                chatNpc(neutral, "Nope, it's magic. But I know what you mean. So do you want to join us?")
                joinChoice()
            }
        }
    }

    private suspend fun Dialogue.joinChoice() {
        when (choice2("Yes indeedy.", 1, "No I don't.", 2)) {
            1 -> {
                chatPlayer(happy, "Yes indeedy.")
                chatNpc(happy, "Good, good. You have a healthy sense of competition.")
                chatNpc(
                    neutral,
                    "Remember, traveller - in my arena, hand-to-hand combat is useless. Your strength " +
                        "will diminish as you enter the arena, but the spells you can learn are amongst " +
                        "the most powerful in all of Gielinor.",
                )
                chatNpc(neutral, "Before I can accept you in, we must duel.")
                when (choice2("Okay, let's fight.", 1, "No thanks.", 2)) {
                    1 -> {
                        chatPlayer(neutral, "Okay, let's fight.")
                        chatNpc(neutral, "I must first check that you are up to scratch.")
                        chatPlayer(happy, "You don't need to worry about that.")
                        chatNpc(
                            neutral,
                            "Not just any magician can enter - only the most powerful and most feared. " +
                                "Before you can use the power of this arena, you must prove yourself " +
                                "against me.",
                        )
                        with(fight) { access.beginDuel() }
                    }
                    2 -> chatPlayer(neutral, "No thanks.")
                }
            }
            2 -> {
                chatPlayer(neutral, "No I don't.")
                chatNpc(neutral, "Your loss.")
            }
        }
    }

    private suspend fun Dialogue.duelInProgress() {
        chatPlayer(neutral, "Hello, Kolodion.")
        chatNpc(
            neutral,
            "Our duel is not over, young mage. Pull the lever by the arena wall when you are ready " +
                "to face me again, or I can send you back in myself.",
        )
        when (choice2("Send me back into the arena.", 1, "I need a moment first.", 2)) {
            1 -> {
                chatPlayer(neutral, "Send me back into the arena.")
                with(fight) { access.beginDuel() }
            }
            2 -> chatPlayer(neutral, "I need a moment first.")
        }
    }

    private suspend fun Dialogue.afterDuel() {
        chatPlayer(happy, "Hello, Kolodion.")
        chatNpc(happy, "Hello, young mage. You're a tough one.")
        chatPlayer(quiz, "What now?")
        chatNpc(
            neutral,
            "Step into the magic pool. It will take you to a chamber. There, you must decide which " +
                "god you will represent in the arena.",
        )
        chatPlayer(happy, "Thanks, Kolodion.")
        chatNpc(happy, "That's what I'm here for.")
    }

    /* ---------------------------------------------------------------- Mage Arena II */

    private suspend fun Dialogue.afterMiniquest(npc: Npc) {
        when (mageArena2.stage(player)) {
            0 -> {
                chatPlayer(happy, "Hello, Kolodion.")
                chatNpc(happy, "Hey there, how are you? Are you enjoying the bloodshed?")
                bloodshedOptions()
            }
            STAGE_HUNTING -> hunting()
            STAGE_COMPONENTS -> {
                handInRemains()
                chatNpc(
                    happy,
                    "Excellent work, ${player.displayName}, now you just need to hand me the god cape " +
                        "you wish to have imbued with great power.",
                )
            }
            else -> afterHunt()
        }
    }

    private suspend fun Dialogue.bloodshedOptions() {
        when (
            choice3(
                "I think I've had enough for now.", 1,
                "How do I master my new spells?", 2,
                "Are there any more challenges available?", 3,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "I think I've had enough for now.")
                chatNpc(neutral, "A shame. You're a good battle mage. I hope to see you soon.")
            }
            2 -> {
                chatPlayer(quiz, "How do I master my new spells?")
                chatNpc(
                    neutral,
                    "Experience, my friend, experience. Cast a spell a hundred times, in my arena or " +
                        "out in Gielinor, and you will have mastered it.",
                )
                chatPlayer(happy, "Good stuff.")
                chatNpc(laugh, "Not so good for the citizens; they won't stand a chance.")
                chatPlayer(quiz, "How am I doing so far?")
                for (god in God.entries) {
                    chatNpc(neutral, trainingProgress(god))
                }
            }
            3 -> {
                chatPlayer(quiz, "Are there any more challenges available?")
                moreChallenges()
            }
        }
    }

    private fun Dialogue.trainingProgress(god: God): String {
        val casts = mageArena.casts(player, god)
        val spell = god.spellNickname
        return when {
            casts >= CASTS_TO_UNLOCK -> "You have mastered the $spell spell."
            casts >= ALMOST_READY_CASTS -> "You are almost done mastering the $spell spell."
            casts >= HALFWAY_CASTS -> "You have completed over half of the training needed to master the $spell spell."
            else -> "You still need to train with the $spell spell before you have mastered it."
        }
    }

    private suspend fun Dialogue.moreChallenges() {
        if (!mageArena.anySpellUnlocked(player)) {
            chatNpc(neutral, "You still need to master one of the god spells. Cast it a hundred times and come back.")
            chatNpc(neutral, "I've made a note of your progress with each spell in your journal.")
            return
        }
        if (player.statBase("stat.magic") < MageArena2Quest.MAGIC_REQ) {
            chatNpc(neutral, "You have indeed shown potential, ${player.displayName}.")
            chatNpc(neutral, "However, a wizard of your level wouldn't stand a chance.")
            return
        }
        chatNpc(
            neutral,
            "I am one of the most powerful mages in existence. But even my power has limitations. " +
                "There are some beings however that have power exceeding even my own.",
        )
        chatNpc(
            neutral,
            "I have detected three life forces within the Wilderness, each of them giving out a " +
                "strange magical power, one unlike any I have ever felt before.",
        )
        chatNpc(
            neutral,
            "If we could harness the power of these beings, I could imbue your god cape with that " +
                "power, greatly increasing its potential.",
        )
        when (
            choice2(
                "Great, I've been waiting for an improvement!", 1,
                "Actually, my current cape is fine.", 2,
            )
        ) {
            1 -> {
                chatPlayer(happy, "Great, I've been waiting for an improvement!")
                chatPlayer(quiz, "What do you need me to do?")
                chatNpc(
                    neutral,
                    "I will need you to kill these three beings and bring me their remains. Even in " +
                        "death, their power should be great enough for me to harness it for our own uses.",
                )
                chatPlayer(quiz, "Where am I supposed to find these beings?")
                if (access.inv.isFull()) {
                    chatNpc(neutral, "You'll need to free up an inventory space before we continue.")
                    return
                }
                giveSymbol()
                mageArena2.quest.advanceQuestStage(access)
                chatNpc(neutral, BRING_REMAINS)
                huntOptions()
            }
            2 -> {
                chatPlayer(neutral, "Actually, my current cape is fine.")
                chatNpc(neutral, "I see... If you ever change your mind, you know where to find me.")
            }
        }
    }

    private suspend fun Dialogue.giveSymbol() {
        chatNpc(neutral, "Here, take this enchanted symbol of the gods. It will guide you to the creatures.")
        access.invAdd(access.inv, SYMBOL)
        objbox(SYMBOL, "Kolodion hands you an enchanted symbol.")
    }

    /** Only one symbol may be owned; a lost one is replaced the next time the player talks to him. */
    private suspend fun Dialogue.replaceSymbol() {
        if (access.inv.contains(SYMBOL)) {
            return
        }
        if (access.inv.isFull()) {
            chatNpc(neutral, "You'll need to free up an inventory space before we continue.")
            return
        }
        giveSymbol()
    }

    private suspend fun Dialogue.hunting() {
        val handed = handInRemains()
        if (mageArena2.allHanded(player)) {
            mageArena2.quest.advanceQuestStage(access)
            chatNpc(
                happy,
                "Excellent work, ${player.displayName}, now you just need to hand me the god cape " +
                    "you wish to have imbued with great power.",
            )
            return
        }
        if (handed) {
            chatNpc(happy, "Excellent work, ${player.displayName}, but we aren't finished yet.")
        }
        replaceSymbol()
        chatNpc(neutral, BRING_REMAINS)
        huntOptions()
    }

    /** Takes every set of remains in the pack; returns true if anything was handed over. */
    private suspend fun Dialogue.handInRemains(): Boolean {
        var handed = false
        for (god in God.entries) {
            val count = access.inv.count(god.remains)
            if (count <= 0) {
                continue
            }
            chatPlayer(happy, "I've got the remains of ${god.remainsDescription}.")
            access.invDel(access.inv, god.remains, count)
            objbox(god.remains, "Kolodion takes the ${remainsTaken(god)}.")
            mageArena2.handed.getValue(god).set(player, true)
            val credits = mageArena2.imbueCredits.getValue(god)
            credits.set(player, credits.get(player) + 1)
            mageArena2.syncVars(player)
            handed = true
        }
        return handed
    }

    private fun remainsTaken(god: God): String =
        when (god) {
            God.SARADOMIN -> "Justiciar's remains"
            God.GUTHIX -> "Ent's remains"
            God.ZAMORAK -> "Demon's heart"
        }

    private suspend fun Dialogue.huntOptions() {
        while (true) {
            when (
                choice4(
                    "Where can I find these beings?", 1,
                    "Any advice on fighting the beings?", 2,
                    "Tell me more about these beings.", 3,
                    "Thanks, bye.", 4,
                )
            ) {
                1 -> {
                    chatPlayer(quiz, "Where can I find these beings?")
                    chatNpc(
                        neutral,
                        "They can all be found in the Wilderness. The enchanted symbol can track their " +
                            "unique magical energies, leading you to their exact locations.",
                    )
                    chatNpc(neutral, "Be warned though, the symbol will take a blood sacrifice every time it's used.")
                }
                2 -> {
                    chatPlayer(quiz, "Any advice on fighting the beings?")
                    chatNpc(
                        neutral,
                        "They can't be harmed by normal magic, melee or ranged attacks, you will need to " +
                            "use the god combat spell corresponding to the creature you are fighting.",
                    )
                }
                3 -> {
                    chatPlayer(quiz, "Tell me more about these beings.")
                    chatNpc(
                        neutral,
                        "One is a justiciar. According to legend, the justiciars were the elite of " +
                            "Saradomin's forces. Some worked alone, operating as assassins behind enemy " +
                            "lines. Others led entire armies in Saradomin's name.",
                    )
                    chatNpc(neutral, "I couldn't tell you why this one wanders the Wilderness, but I suspect he has good reason.")
                    chatNpc(
                        neutral,
                        "The second is a Demon of Zamorak. From what I can tell, he is completely mad. He " +
                            "roams the Wilderness, killing all who he comes across without mercy. Even his " +
                            "own kind do not escape his fury.",
                    )
                    chatNpc(neutral, "He's known to battle the justiciar quite regularly, perhaps they are old enemies.")
                    chatNpc(
                        neutral,
                        "The final being is a Guthixian Ent. Like most followers of Guthix, he will avoid " +
                            "combat if possible. Make no mistake though, he is deadly when threatened.",
                    )
                    chatNpc(
                        neutral,
                        "He seems to take great interest in the other two creatures. Maybe he is trying " +
                            "to keep a balance between them.",
                    )
                    chatNpc(
                        neutral,
                        "All three of them give off a unique magical energy, one unlike any I have ever " +
                            "felt before. The fact that the three of them are so different, yet possess " +
                            "powers so similar, is very odd indeed.",
                    )
                    chatNpc(neutral, "I'd be very interested in learning more about the source of their power.")
                }
                else -> {
                    chatPlayer(happy, "Thanks, bye.")
                    chatNpc(happy, "Farewell, ${player.displayName}.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.afterHunt() {
        val handed = handInRemains()
        replaceSymbol()
        val pending = God.entries.filter { mageArena2.credits(player, it) > 0 }
        if (pending.isNotEmpty()) {
            val capes = pending.joinToString(", ", limit = 3) { "a ${it.displayName} cape" }
            chatNpc(neutral, "You may currently have $capes imbued, just hand over the cape when you're ready.")
        }
        if (handed || pending.isEmpty()) {
            chatNpc(neutral, IMBUE_MORE)
        }
        huntOptions()
    }

    /** Using a god cape on Kolodion is how capes get imbued. */
    private suspend fun ProtectedAccess.useOnKolodion(npc: Npc, obj: ItemServerType) {
        val god = God.byCape(obj.id)
        if (god == null) {
            mes("Kolodion has no use for that.")
            return
        }
        startDialogue(npc) {
            if (mageArena2.stage(player) < STAGE_COMPONENTS) {
                chatNpc(neutral, "I can't do anything with that yet. Bring me the remains of the three beings first.")
                return@startDialogue
            }
            if (mageArena2.credits(player, god) <= 0) {
                chatNpc(neutral, "You'll have to bring me the ${god.creatureName} creature's remains to imbue this cape.")
                return@startDialogue
            }
            when (choice2("Yes.", 1, "No.", 2, title = "Imbue your ${god.displayName} cape?")) {
                1 -> imbue(god)
                else -> {}
            }
        }
    }

    private suspend fun Dialogue.imbue(god: God) {
        if (!access.inv.contains(god.cape)) {
            return
        }
        access.invDel(access.inv, god.cape, 1)
        access.invAdd(access.inv, god.imbuedCape)
        objbox(god.imbuedCape, "Kolodion takes your cape and imbues it with the power of the gods before handing it back.")
        if (mageArena2.quest.isQuestCompleted(player)) {
            val credits = mageArena2.imbueCredits.getValue(god)
            credits.set(player, credits.get(player) - 1)
        } else {
            // The three remains of the hunt buy exactly one cape.
            for (other in God.entries) {
                mageArena2.imbueCredits.getValue(other).set(player, 0)
            }
            mageArena2.quest.completeQuest(access)
        }
        chatNpc(neutral, IMBUE_MORE)
    }

    private companion object {
        const val SYMBOL = "obj.ma2_symbol"
        const val HALFWAY_CASTS = 51
        const val ALMOST_READY_CASTS = 90

        const val BRING_REMAINS =
            "Bring me the remains of all three magical beings. Do this and I will imbue their " +
                "combined power into a single god cape of your choice."
        const val IMBUE_MORE =
            "If you would like me to imbue any more capes, just bring me the remains of the respective creature."
    }
}
