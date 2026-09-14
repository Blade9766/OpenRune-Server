package org.rsmod.content.quest.area.paterdomus.priestinperil.npcs

import dev.openrune.types.MesAnimType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onApNpc1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.paterdomus.priestinperil.PaterdomusCoords
import org.rsmod.content.quest.area.paterdomus.priestinperil.PaterdomusDoors
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.BLESSED_WATER
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.DREZEL_CELL
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.DREZEL_MAUSOLEUM
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.ESSENCE_NEEDED
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.GOLDEN_KEY
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.IRON_KEY
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.MURKY_WATER
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.NOTED_PURE_ESSENCE
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.NOTED_RUNE_ESSENCE
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.PURE_ESSENCE
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.RUNE_ESSENCE
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.STAGE_CELL_UNLOCKED
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.STAGE_COFFIN_SEALED
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.STAGE_ESSENCE
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.STAGE_MEET_IN_MAUSOLEUM
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.STAGE_MET_DREZEL
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.STAGE_ROALD_FURIOUS
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.WOLFBANE
import org.rsmod.content.quest.area.paterdomus.priestinperil.holdsAnywhere
import org.rsmod.content.quest.area.paterdomus.priestinperil.triedFakeKey
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Drezel, the priest of Paterdomus. He is two multinpcs on the quest varp: the prisoner in the
 * top floor cell (stages 4-8) and the priest holding the holy barrier in the mausoleum (from
 * stage 8), so every op is bound to those base types. His lines go through
 * [Dialogue.chatNpcSpecific] with the matching base type, which lets the cell gate script speak
 * for him too.
 */
class Drezel
@Inject
constructor(
    private val priestInPeril: PriestInPerilQuest,
    private val doors: PaterdomusDoors,
    private val objRepo: ObjRepository,
) : PluginScript() {

    private val finalEssenceGiven = priestInPeril.quest.attribute(name = "FINAL_ESSENCE_GIVEN", default = false)

    override fun ScriptContext.startup() {
        onOpNpc1(DREZEL_CELL) { startDialogue(it.npc) { inCell() } }
        onApNpc1(DREZEL_CELL) {
            if (isWithinApRange(it.npc, CELL_TALK_RANGE)) {
                startDialogue(it.npc) { inCell() }
            }
        }
        onOpNpc1(DREZEL_MAUSOLEUM) { startDialogue(it.npc) { inMausoleum() } }
        onOpNpcU(DREZEL_MAUSOLEUM) { useOnDrezel(it.objType.internalName) }
        onOpLoc1(CELL_GATE) { openCellGate(it.loc) }
        onOpLocU(CELL_GATE, IRON_KEY) { unlockCell(it.loc) }
        onOpLocU(CELL_GATE, GOLDEN_KEY) { tryGoldenKey() }
    }

    private suspend fun Dialogue.drezel(mood: MesAnimType, text: String) {
        val head = if (player.coords.level == PaterdomusCoords.CELL_GATE.level) DREZEL_CELL else DREZEL_MAUSOLEUM
        chatNpcSpecific(NAME, head, mood, text)
    }

    /* The cell */

    private suspend fun Dialogue.inCell() {
        when (priestInPeril.stage(player)) {
            STAGE_ROALD_FURIOUS -> meeting()
            STAGE_MET_DREZEL -> howGoesIt()
            STAGE_CELL_UNLOCKED -> cellOpen(justUnlocked = false)
            STAGE_COFFIN_SEALED -> {
                chatPlayer(
                    happy,
                    "I poured the blessed water over the coffin. That vampyre shouldn't cause us any " +
                        "trouble now.",
                )
                priestInPeril.advanceTo(access, STAGE_MEET_IN_MAUSOLEUM)
                drezel(
                    happy,
                    "Excellent work, adventurer! Now, meet me down in the mausoleum. We need to " +
                        "revert the damage those Zamorakians did as soon as possible!",
                )
            }
            else -> {
                drezel(
                    neutral,
                    "Meet me down in the mausoleum. We need to revert the damage those Zamorakians " +
                        "did as soon as possible!",
                )
                chatPlayer(neutral, "Okay.")
            }
        }
    }

    private suspend fun Dialogue.meeting() {
        chatPlayer(neutral, "Hello.")
        drezel(
            shocked,
            "Oh! You do not appear to be one of those Zamorakians who imprisoned me! Who are you " +
                "and why are you here?",
        )
        chatPlayer(
            neutral,
            "My name's ${player.displayName}. King Roald sent me to find out what was going on at " +
                "the temple. I take it you're Drezel?",
        )
        drezel(
            happy,
            "That's right! Oh, praise be to Saradomin! I feared that when those Zamorakians " +
                "attacked this place, Misthalin would be doomed!",
        )
        chatPlayer(quiz, "Why? What are they planning to do?")
        drezel(
            worried,
            "I believe they aim to desecrate the blessings placed upon the holy River Salve by the " +
                "Seven Priestly Warriors themselves! We'd be left defenceless against the vampyres " +
                "of Morytania!",
        )
        when (choice2("Seven Priestly Warriors?", true, "What do we do?", false)) {
            true -> {
                chatPlayer(quiz, "Seven Priestly Warriors?")
                drezel(
                    neutral,
                    "Indeed. While this isn't the... ideal time for a history lesson, this " +
                        "knowledge may help you with our next steps, so allow me to explain...",
                )
                history()
                chatPlayer(quiz, "I see. And you think those Zamorakians are going to try and remove the blessings on the river?")
                drezel(worried, "I fear that is almost certainly their goal.")
                chatPlayer(quiz, "So what do we do?")
            }
            false -> chatPlayer(quiz, "So what do we do?")
        }
        priestInPeril.advanceTo(access, STAGE_MET_DREZEL)
        drezel(
            neutral,
            "First, we need to get me out of this cell. The leader of those Zamorakians probably " +
                "has the key. I believe he's the one in the hood.",
        )
        chatPlayer(quiz, "So I need to go and find this key. Anything else I should be aware of?")
        drezel(
            worried,
            "I'm afraid so. When the Salve was first blessed, a small number of vampyres were left " +
                "trapped on this side of it. They were heavily weakened, but still posed a " +
                "dangerous threat.",
        )
        drezel(
            neutral,
            "Even with them weakened, the brave Saradominists who built this temple were unable to " +
                "kill them. Instead, they trapped them in coffins and buried them outside the " +
                "temple.",
        )
        drezel(
            worried,
            "This coffin here contains one of those vampyres. The Zamorakians must have dug it up " +
                "and brought it in here with them.",
        )
        chatPlayer(quiz, "But why?")
        drezel(
            sad,
            "Well, as those fiends pointed out to me with delight, as I am the descendant of one " +
                "of the Seven Priestly Warriors who blessed the river, it will likely recognise the " +
                "smell of my blood.",
        )
        drezel(worried, "If I get too close, it will probably wake up and kill me, very slowly and painfully.")
        chatPlayer(quiz, "Maybe I could kill it somehow?")
        drezel(
            neutral,
            "I would not risk waking it. Even now, it could pose a huge threat. The priests of old " +
                "would incapacitate trapped vampyres with the blessings of Saradomin. Perhaps " +
                "there's a way for you do the same.",
        )
        chatPlayer(
            neutral,
            "Right. So, I need to find the key to your cell and do something about the vampyre. " +
                "Got it.",
        )
        drezel(neutral, "Good luck.")
    }

    private suspend fun Dialogue.history() {
        drezel(
            neutral,
            "It is said that many years ago, the land that is now Morytania was a peaceful " +
                "Saradominist kingdom. Alas, all that ended when the vampyres invaded, claiming the " +
                "land as their own.",
        )
        drezel(
            neutral,
            "Morytania wasn't enough for them though. Soon, they set their sights on these lands as " +
                "well. Misthalin would have quickly fallen to the vampyres, were it not for the " +
                "bravery of the Seven Priestly Warriors.",
        )
        drezel(
            neutral,
            "They led the armies of Misthalin against the vampyres, with the war culminating in a " +
                "great battle that took place right where this temple stands.",
        )
        drezel(
            neutral,
            "For ten days and nights they fought, never sleeping, never eating, fuelled only by " +
                "their faith in Saradomin.",
        )
        drezel(
            sad,
            "On the eleventh day, reinforcements arrived, but when they did, they found the Seven " +
                "Priestly Warriors had been slain by the vampyres, along with most of their forces.",
        )
        drezel(
            neutral,
            "However, their sacrifice was not in vain. Before they were defeated, the Seven " +
                "Priestly Warriors managed to bless the Salve with the holy power of Saradomin.",
        )
        drezel(
            neutral,
            "Those blessings remain to this day, ensuring no vampyre can cross the river. The " +
                "temple here was built soon after. It guards the source of the River Salve, and " +
                "acts as the only passage into Morytania.",
        )
        chatPlayer(quiz, "And what about the mausoleum below?")
        drezel(
            neutral,
            "That is the most important part of the temple, for it is where the passage into " +
                "Morytania is found. It is also the final resting place of the Seven Priestly " +
                "Warriors.",
        )
        drezel(
            neutral,
            "Their tombs are built around the well that provides direct access to the source of " +
                "the Salve, with a golden gift placed upon each of the tombs as a mark of respect.",
        )
    }

    private suspend fun Dialogue.howGoesIt() {
        drezel(
            quiz,
            "How goes it, adventurer? Any luck in finding the key to the cell or a way of stopping " +
                "the vampyre yet?",
        )
        when {
            player.inv.contains(IRON_KEY) -> {
                chatPlayer(neutral, "I have this key I found down in the mausoleum.")
                drezel(happy, "Excellent work! Quickly, try it on the gate, and see if it will free me!")
            }
            player.inv.contains(MURKY_WATER) -> {
                chatPlayer(quiz, "I have some water from the Salve. Do you think we could use this to deal with the vampyre?")
                drezel(
                    neutral,
                    "Hmm... Yes, that could work. However, I'll first need to see if it's already " +
                        "been desecrated by those Zamorakians.",
                )
                chatPlayer(neutral, "Which means I'm going to need to get this gate open first.")
                drezel(neutral, "Indeed.")
            }
            player.inv.contains(GOLDEN_KEY) -> {
                chatPlayer(
                    confused,
                    "I have this key I took from the leader of the Zamorakians, but it doesn't seem " +
                        "to fit the lock on the gate.",
                )
                drezel(confused, "Strange... The right key must be nearby. Maybe they hid it somewhere...")
            }
            else -> {
                chatPlayer(sad, "No, not yet...")
                drezel(
                    neutral,
                    "Well, don't give up adventurer! The key must be nearby. Most likely the leader " +
                        "of those Zamorakians has it. I believe he's the one in the hood.",
                )
                drezel(
                    neutral,
                    "As for the vampyre, the priests of old would incapacitate the ones they'd " +
                        "trapped with the blessings of Saradomin. Perhaps there's a way for you do " +
                        "the same.",
                )
            }
        }
        if (choice2("Could you tell me more about this temple?", true, "I'll get going.", false)) {
            chatPlayer(quiz, "Could you tell me more about this temple?")
            history()
            chatPlayer(neutral, "I see. Thanks for the information. I'll get going.")
        } else {
            chatPlayer(neutral, "I'll get going.")
        }
    }

    private suspend fun Dialogue.cellOpen(justUnlocked: Boolean) {
        if (!justUnlocked && player.inv.contains(BLESSED_WATER)) {
            drezel(
                neutral,
                "Excellent work getting the cell unlocked! Now we just need the vampyre dealing " +
                    "with. That water I blessed for you should do it. Pour it on the coffin, quickly!",
            )
            return
        }
        if (player.inv.contains(MURKY_WATER)) {
            drezel(happy, "Excellent work! Now we just need the vampyre dealing with.")
            chatPlayer(neutral, "Well, I have some water from the Salve here.")
            drezel(neutral, "Very good. Let me have a look at it.")
            blessWater()
            if (!priestInPeril.waterBlessedBefore.get(player)) {
                priestInPeril.waterBlessedBefore.set(player, true)
                drezel(
                    worried,
                    "It's as I feared. Those Zamorakians have already desecrated the Salve. " +
                        "However, I've managed to purify this water again with the blessings of " +
                        "Saradomin. You should be able to use it against the vampyre now.",
                )
                chatPlayer(neutral, "I'll do that right away.")
            }
            return
        }
        drezel(
            happy,
            "Excellent work! Now we just need the vampyre dealing with. The priests of old would " +
                "incapacitate the ones they'd trapped with the blessings of Saradomin.",
        )
        chatPlayer(quiz, "Blessings of Saradomin? How did they do that?")
        drezel(neutral, "The most common way was with holy water.")
        chatPlayer(quiz, "Like the water of the Salve perhaps?")
        drezel(
            neutral,
            "Well, that may well depend on if the Zamorakians have already desecrated it. One step " +
                "at a time though. First, why don't you try and get hold of some?",
        )
    }

    private suspend fun Dialogue.blessWater() {
        npc?.anim(PRAY_SEQ)
        access.soundSynth(BLESS_SOUND)
        val count = player.inv.count(MURKY_WATER)
        access.invReplace(player.inv, MURKY_WATER, count, BLESSED_WATER)
        objbox(BLESSED_WATER, "Drezel inspects the water. He then says some sort of prayer.")
    }

    private suspend fun ProtectedAccess.openCellGate(gate: BoundLocInfo) {
        arriveDelay()
        if (priestInPeril.stage(player) < STAGE_CELL_UNLOCKED) {
            mesbox("The cell gate is securely locked.")
            return
        }
        walkThroughCellGate(gate)
    }

    private fun ProtectedAccess.walkThroughCellGate(gate: BoundLocInfo) {
        val inside = coords.x > gate.coords.x
        val dest = CoordGrid(if (inside) gate.coords.x else gate.coords.x + 1, gate.coords.z, gate.coords.level)
        doors.walkThrough(this, listOf(gate.coords to CELL_GATE), dest, GATE_OPEN_SOUND, GATE_CLOSE_SOUND)
    }

    private suspend fun ProtectedAccess.unlockCell(gate: BoundLocInfo) {
        arriveDelay()
        val stage = priestInPeril.stage(player)
        when {
            stage < STAGE_MET_DREZEL -> mes("Nothing interesting happens.")
            stage == STAGE_MET_DREZEL -> {
                soundSynth(UNLOCK_SOUND)
                invDel(inv, IRON_KEY)
                objbox(IRON_KEY, "You use the key to unlock the cell gate.")
                priestInPeril.advanceTo(this, STAGE_CELL_UNLOCKED)
                startDialogue {
                    chatPlayer(happy, "The key fit the lock!")
                    cellOpen(justUnlocked = true)
                }
                walkThroughCellGate(gate)
            }
            stage < STAGE_MEET_IN_MAUSOLEUM ->
                startDialogue { drezel(angry, "Aargh! What are you trying to lock me back in here for?") }
            else -> mes("Nothing interesting happens.")
        }
    }

    private suspend fun ProtectedAccess.tryGoldenKey() {
        arriveDelay()
        if (priestInPeril.stage(player) >= STAGE_CELL_UNLOCKED) {
            mes("Nothing interesting happens.")
            return
        }
        objbox(GOLDEN_KEY, "You try to use the key to unlock the gate. The key is a similar size to the lock, but does not fit.")
        player.triedFakeKey = true
        startDialogue { chatPlayer(confused, "Hmm... Why was that monk carrying this key if it's not for this cell?") }
    }

    /* The mausoleum */

    private suspend fun Dialogue.inMausoleum() {
        val stage = priestInPeril.stage(player)
        when {
            stage == STAGE_MEET_IN_MAUSOLEUM -> repairingTheBarrier()
            stage in STAGE_ESSENCE until STAGE_COMPLETE -> essence()
            stage >= STAGE_COMPLETE && !priestInPeril.blessed.get(player) -> blessing()
            stage >= STAGE_COMPLETE -> afterQuest()
        }
    }

    private suspend fun Dialogue.repairingTheBarrier() {
        drezel(
            worried,
            "Ah, ${player.displayName}. Glad you made it. Things are worse than I feared down " +
                "here. I'm not sure if I will be able to repair the damage.",
        )
        chatPlayer(worried, "How bad is it?")
        drezel(
            worried,
            "From what I can tell, those Zamorakians used some kind of potion to pollute the " +
                "Salve. With this being the source, it has already started to spread down the " +
                "river.",
        )
        drezel(
            worried,
            "Before long, the blessings placed upon the river will be irreversibly damaged. There " +
                "will be nothing to stop the vampyres from invading Misthalin at their leisure.",
        )
        chatPlayer(quiz, "Could you not bless the river like you did with that water earlier?")
        drezel(
            sad,
            "I'm afraid not. Blessing a small bucket is one thing, but the power I have from " +
                "Saradomin is nowhere near enough to cleanse an entire river.",
        )
        drezel(
            neutral,
            "However, I believe there might still be a way to undo this, but I'm going to need " +
                "your help. This passage is the only route between Morytania and Misthalin. The " +
                "barrier here draws power from the river.",
        )
        drezel(
            neutral,
            "I have managed to reinforce the barrier, but I must continue focusing on it to keep it " +
                "intact. While I do that, I will need you to soak up the evil magic that potion has " +
                "released into the river.",
        )
        chatPlayer(quiz, "Soak up the magic? How?")
        drezel(
            neutral,
            "I'm sure you know of runes, used by mages to power their spells. The essence used to " +
                "create them absorbs magical potential from runic altars to be released later.",
        )
        chatPlayer(
            neutral,
            "Ah, I see where this is going. You think we can use some rune essence to absorb the " +
                "magic that potion released into the Salve?",
        )
        priestInPeril.advanceTo(access, STAGE_ESSENCE)
        drezel(
            neutral,
            "Exactly. If you could bring me fifty essence, I should be able to reverse the damage " +
                "done. Be quick though. The longer we wait, the worse things will get.",
        )
    }

    private suspend fun Dialogue.essence() {
        val needed = ESSENCE_NEEDED - priestInPeril.essenceGiven(player)
        if (finalEssenceGiven.get(player)) {
            finish()
            return
        }
        val carried = carriedEssence()
        if (carried == 0) {
            when {
                player.inv.contains(NOTED_RUNE_ESSENCE) || player.inv.contains(NOTED_PURE_ESSENCE) -> {
                    chatPlayer(happy, "I brought you some rune essence.")
                    drezel(
                        confused,
                        "You have brought me notes saying 'I promise to pay the bearer on demand " +
                            "the listed number of Rune Essence.' How is that supposed to help me?",
                    )
                }
                needed == ESSENCE_NEEDED -> {
                    chatPlayer(quiz, "What am I supposed to do again?")
                    drezel(neutral, "Bring me fifty rune essence so that I can undo the damage done by those Zamorakians.")
                }
                else -> {
                    chatPlayer(quiz, "How much more essence do I need to bring you?")
                    drezel(neutral, "I need $needed more.")
                }
            }
            return
        }
        val last = carried >= needed
        chatPlayer(happy, if (last) "I brought you some more essence." else "I brought you some rune essence.")
        drezel(worried, "Quickly, give them to me!")
        handOverEssence(access, needed)
        objbox(RUNE_ESSENCE, "You give Drezel some essence.")
        if (last) {
            finish()
            return
        }
        drezel(happy, "Thank you. I need ${ESSENCE_NEEDED - priestInPeril.essenceGiven(player)} more.")
    }

    /**
     * Takes up to [needed] unnoted essence. The last one is only counted when the quest completes
     * in [finish], so a closed dialogue never leaves the quest a step short of its reward.
     */
    private fun handOverEssence(access: ProtectedAccess, needed: Int) {
        val rune = access.inv.count(RUNE_ESSENCE).coerceAtMost(needed)
        val pure = access.inv.count(PURE_ESSENCE).coerceAtMost(needed - rune)
        if (rune > 0) {
            access.invDel(access.inv, RUNE_ESSENCE, rune)
        }
        if (pure > 0) {
            access.invDel(access.inv, PURE_ESSENCE, pure)
        }
        val given = rune + pure
        if (given >= needed) {
            finalEssenceGiven.set(access.player, true)
            if (given > 1) {
                priestInPeril.quest.advanceQuestStage(access, given - 1)
            }
            return
        }
        priestInPeril.quest.advanceQuestStage(access, given)
    }

    private suspend fun Dialogue.finish() {
        drezel(
            happy,
            "Excellent! That should do it! I will bless these stones and place them within the " +
                "well so they can soak up the magic from that potion. Misthalin will be safe from " +
                "the vampyres once more!",
        )
        chatPlayer(quiz, "What about the Zamorakians upstairs?")
        drezel(
            neutral,
            "They hopefully shouldn't be too much trouble now that their leader is dead, but I will " +
                "write to the King and ask him to make sure they're dealt with properly.",
        )
        drezel(
            happy,
            "For now though, please take this dagger. It has been handed down within my family for " +
                "generations and is filled with the power of Saradomin. Consider it a small thank " +
                "you for your help here.",
        )
        finalEssenceGiven.set(player, false)
        priestInPeril.quest.completeQuest(access)
    }

    private fun Dialogue.carriedEssence(): Int = player.inv.count(RUNE_ESSENCE) + player.inv.count(PURE_ESSENCE)

    private suspend fun ProtectedAccess.useOnDrezel(obj: String) {
        arriveDelay()
        val stage = priestInPeril.stage(player)
        val isEssence = obj == RUNE_ESSENCE || obj == PURE_ESSENCE
        if (!isEssence || stage !in STAGE_ESSENCE until STAGE_COMPLETE || finalEssenceGiven.get(player)) {
            mes("Nothing interesting happens.")
            return
        }
        val needed = ESSENCE_NEEDED - priestInPeril.essenceGiven(player)
        val last = inv.count(RUNE_ESSENCE) + inv.count(PURE_ESSENCE) >= needed
        handOverEssence(this, needed)
        mes("You give the priest your blank runes.")
        if (last) {
            startDialogue { finish() }
        }
    }

    private suspend fun Dialogue.blessing() {
        chatPlayer(quiz, "So can I pass through that barrier now?")
        drezel(
            neutral,
            "Into Morytania? Yes, you can. The barrier will prevent the servants of Zamorak from " +
                "entering Misthalin, but it is safe for those blessed by Saradomin.",
        )
        drezel(
            worried,
            "I must warn you though, Morytania is an evil land. The region is filled with " +
                "creatures and monsters more terrifying than any you'll find on this side of the " +
                "Salve.",
        )
        drezel(
            worried,
            "Worst of all are the vampyres, the rulers of Morytania. Be very cautious of their " +
                "kind. Many are completely immune to human weaponry. Those that aren't, are still " +
                "very hard to kill.",
        )
        chatPlayer(neutral, "I'll be careful. Anything else I should know?")
        drezel(
            neutral,
            "The first settlement you'll likely come across is Canifis, a town of werewolves. They " +
                "probably won't attack you unless provoked, but you should still be cautious.",
        )
        drezel(
            neutral,
            "You should keep that dagger I gave you to hand. Its magic will prevent them from " +
                "taking on their wolf form.",
        )
        chatPlayer(neutral, "I see. Thanks.")
        drezel(neutral, "One last thing. While in Morytania, stay alert for any mention of the Myreque.")
        chatPlayer(quiz, "The Myreque?")
        drezel(
            neutral,
            "Yes. They're a band of freedom fighters. They fight to protect the humans trapped in " +
                "Morytania from the tyranny of the vampyres. Their hope is to one day rid the region " +
                "of all evil.",
        )
        drezel(
            happy,
            "Veliaf, the leader of their Mort Myre group, is an old friend. I imagine they'd " +
                "appreciate the help of someone like you, if you can find them.",
        )
        chatPlayer(neutral, "I'll keep an eye out for them.")
        drezel(neutral, "Right, I think that's everything. Just the blessing itself now. Hold still a moment.")
        npc?.anim(PRAY_SEQ)
        access.spotanim(BLESS_SPOTANIM)
        access.soundSynth(BLESS_SOUND)
        mesbox("Drezel blesses you, allowing you to pass through the barrier into Morytania.")
        priestInPeril.blessed.set(player, true)
        drezel(happy, "Good luck out there. Stay safe.")
        when (choice2("Do you know of anything I can do in Morytania?", true, "And you. See you later.", false)) {
            true -> moryHelp()
            false -> chatPlayer(happy, "And you. See you later.")
        }
    }

    private suspend fun Dialogue.moryHelp() {
        chatPlayer(quiz, "Do you know of anything I can do in Morytania?")
        drezel(neutral, "Well... I don't think so. There's not much to do in that place.")
    }

    private suspend fun Dialogue.afterQuest() {
        if (!player.holdsAnywhere(WOLFBANE)) {
            chatPlayer(worried, "I've lost my wolfbane dagger.")
            if (player.inv.isFull()) {
                drezel(neutral, "Yes I know, but you have no space for it at the moment so I can't return it to you.")
                return
            }
            access.invAddOrDrop(objRepo, WOLFBANE)
            drezel(
                happy,
                "Yes I know! Luckily for you it washed up on the banks of the Salve earlier! Here, " +
                    "take it again, but please try and be more careful this time. It's a family " +
                    "heirloom after all!",
            )
            chatPlayer(happy, "Thanks for that!")
            return
        }
        chatPlayer(happy, "Hello again, Drezel. Is there anything that I can help you out with around here?")
        moryHelp()
    }

    private companion object {
        const val NAME = "Drezel"
        const val CELL_GATE = "loc.pip_prisondoor"
        const val CELL_TALK_RANGE = 3

        const val PRAY_SEQ = "seq.human_pray"
        const val BLESS_SPOTANIM = "spotanim.druidicspirit_priest_bless"
        const val BLESS_SOUND = "synth.prayer_recharge"
        const val UNLOCK_SOUND = "synth.unlock"
        const val GATE_OPEN_SOUND = "synth.door_open"
        const val GATE_CLOSE_SOUND = "synth.door_close"
    }
}
