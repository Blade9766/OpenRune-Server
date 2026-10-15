package org.rsmod.content.quest.area.desert.touristtrap.npcs

import jakarta.inject.Inject
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.desert.touristtrap.MiningCampSecurity
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.DESERT_BOOTS
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.DESERT_ROBE
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.DESERT_SHIRT
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.MALE_SLAVE_MULTI
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.PICKLOCK_VARBIT
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.SLAVE_BOOTS
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.SLAVE_ROBE
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.SLAVE_SHIRT
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.SLAVE_VARBIT
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.SOUND_PICK_LOCK
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_FREED_SLAVE
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_SPOKEN_SLAVE
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_TRADED_CLOTHES
import org.rsmod.content.quest.area.desert.touristtrap.setVarBit
import org.rsmod.content.quest.area.desert.touristtrap.wearingSlaveRobes
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The slaves of the Desert Mining Camp.
 *
 * One of them, chained to the rocks by the east wall, is planning his escape and will swap his
 * slave clothes for a set of desert clothes if the player can pick the locks on his bracelets.
 * He is a multi-npc on `varbit.tourtrap_qip_mineslave`: after the trade the player sees the
 * escaping slave in his place.
 */
class MiningSlaves
@Inject
constructor(
    private val quest: TouristTrapQuest,
    private val security: MiningCampSecurity,
    private val aiInteractions: AiPlayerInteractions,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(MALE_SLAVE_MULTI) { startDialogue(it.npc) { maleSlave() } }
        for (slave in BUSY_SLAVES) {
            onOpNpc1(slave) { busySlave(it.npc) }
        }
        onOpNpc1(ROWDY_SLAVE) { rowdySlave(it.npc) }
    }

    private suspend fun ProtectedAccess.busySlave(slave: Npc) {
        slave.say("Can't you see I'm busy?")
        mes("This person doesn't want to talk to you.")
        if (player.wearingSlaveRobes() || security.isExempt(player)) {
            return
        }
        val guard = security.guardNear(coords, GUARD_SIGHT) ?: return
        delay(1)
        with(security) { caughtInFancyClothes(guard) }
    }

    private fun ProtectedAccess.rowdySlave(slave: Npc) {
        slave.say(ROWDY_LINES.random())
        slave.opPlayer2(player, aiInteractions)
    }

    private suspend fun Dialogue.maleSlave() {
        val stage = quest.stage(player)
        when {
            stage >= STAGE_TRADED_CLOTHES -> {
                if (player.wearingAllSlaveClothes()) {
                    chatNpc(neutral, "Not much to do here but mine all day long.")
                    return
                }
                setVarBit(player, SLAVE_VARBIT, 0)
                chatNpcSpecific(
                    "Male slave",
                    MALE_SLAVE_HEAD,
                    sad,
                    "Oh bother, I was caught by the guards again... Listen, if you can get me some " +
                        "Desert Clothes, I'll trade you for my slaves clothes again.. Do you want to " +
                        "trade?",
                )
                offerTrade()
            }
            stage == STAGE_FREED_SLAVE -> {
                chatNpc(quiz, "Do you have the Desert Clothes yet?")
                offerTrade()
            }
            stage == STAGE_SPOKEN_SLAVE -> {
                chatNpc(
                    quiz,
                    "Hello again, do you want to try and unlock my chains? I'd really appreciate " +
                        "it!",
                )
                offerToUnlock()
            }
            else -> newRecruit()
        }
    }

    private suspend fun Dialogue.newRecruit() {
        chatNpc(quiz, "You look like a new 'recruit'. How long have you been here?")
        if (choice2("I've just arrived.", true, "Oh, I've been here ages.", false)) {
            chatPlayer(neutral, "I've just arrived.")
            chatNpc(
                happy,
                "Yeah, it looks like it as well. It's a shame that I won't be around long enough " +
                    "to get to know you. I'm making a break for it today. I have a plan to get " +
                    "out of here! It's amazing in its sophistication.",
            )
        } else {
            chatPlayer(neutral, "Oh, I've been here ages.")
            chatNpc(
                quiz,
                "That's funny, I haven't seen you around here before. You're clothes look too " +
                    "clean for you to have been here ages.",
            )
            if (!choice2("Okay, you caught me out.", true, "The guards allow me to clean my clothes.", false)) {
                chatPlayer(neutral, "The guards allow me to clean my clothes.")
                chatNpc(
                    angry,
                    "Oh, a special relationship with the guards heh? How very nice of them. Maybe " +
                        "you could persuade them to let me out of here?",
                )
                return
            }
            chatPlayer(neutral, "Okay, you caught me out.")
            chatNpc(
                happy,
                "Ah ha! I knew it! A new recruit then? It's a shame that I won't be around long " +
                    "enough to get to know you. I'm making a break for it today.",
            )
            chatNpc(happy, "I have a plan to get out of here! It's amazing in its sophistication.")
        }
        val doors =
            choice2(
                "What are those big wooden doors in the corner of the compound?",
                true,
                "Oh yes, that sounds interesting.",
                false,
            )
        if (doors) {
            chatPlayer(quiz, "What are those big wooden doors in the corner of the compound?")
            chatNpc(
                confused,
                "They lead to an underground mine, but you really don't want to go down there.",
            )
            chatNpc(
                confused,
                "I've only seen slaves and guards go down there, I never see the slaves come back " +
                    "up. At least up here you have a nice view and a bit of sun. " +
                    "<col=000080>-- The slave smiles at you and goes back to his work. --</col>",
            )
            return
        }
        chatPlayer(happy, "Oh yes, that sounds interesting.")
        chatNpc(happy, "Yes, it is actually. I have all the details figured out except for one.")
        if (!choice2("What's that then?", true, "Oh, that's a shame.", false)) {
            chatPlayer(neutral, "Oh, that's a shame...")
            chatPlayer(neutral, "Still, 'worse things happen at sea right?'")
            chatNpc(confused, "You've obviously never worked as a slave")
            chatNpc(angry, "...in a mining camp...")
            chatNpc(angry, "...in the middle of the desert.")
            chatPlayer(neutral, "Well I suppose I'd better be getting on my way now...")
            chatNpc(
                neutral,
                "<col=000080>-- The slave nods in agreement and goes back to work. --</col>",
            )
            return
        }
        chatPlayer(quiz, "What's that then?")
        chatNpc(
            neutral,
            "<col=000080>-- The slave rattles the chains on his arms loudly. --</col> These " +
                "bracelets, I can't seem to get them off. If I could get them off, I'd be able to " +
                "climb my way out of here.",
        )
        val undo =
            choice2(
                "I can try to undo them for you.",
                true,
                "That's ridiculous, you're talking rubbish.",
                false,
            )
        if (!undo) {
            chatPlayer(angry, "That's ridiculous, you're talking rubbish.")
            chatNpc(happy, "No, it's true, I can make a break for it If I can just get these bracelets off.")
            if (choice2("Good luck!", true, "I can try to undo them for you.", false)) {
                chatPlayer(neutral, "Good luck!")
                chatNpc(neutral, "Thanks... same to you.")
                return
            }
        }
        tryToUndoThem()
    }

    private suspend fun Dialogue.tryToUndoThem() {
        chatPlayer(neutral, "I can try to undo them for you.")
        quest.advanceTo(access, STAGE_SPOKEN_SLAVE)
        chatNpc(
            confused,
            "Really, that would be great... <col=000080>-- The slave looks at you strangely. " +
                "--</col> Hang on a minute... I suppose you want something for doing this?",
        )
        chatNpc(
            confused,
            "The last time I did a trade in this place, I nearly lost the shirt from my back!",
        )
        if (!choice2("It's funny you should say that...", true, "That sounds awful.", false)) {
            chatPlayer(neutral, "That sounds awful.")
            chatNpc(
                neutral,
                "Yeah, bunch of no hopers, tried to rob me blind. But I guess that's what you get " +
                    "when you deal with convicts.",
            )
            return
        }
        chatPlayer(neutral, "It's funny you should say that actually.")
        chatNpc(neutral, "<col=000080>-- The slave looks at you blankly. --</col>")
        chatNpc(neutral, "Yeah, go on!")
        chatPlayer(quiz, "If I can get the chains off, you have to give me something, okay?")
        chatNpc(quiz, "Sure, what do you want?")
        chatPlayer(happy, "I want your clothes!")
        chatNpc(shocked, "Blimey!")
        chatPlayer(quiz, "I can dress like a slave and gain access to the mine area to scout it out.")
        chatNpc(
            neutral,
            "You're either incredibly brave or incredibly stupid. But what would I wear if you " +
                "take my clothes? Get me some nice desert clothes and I'll think about it?",
        )
    }

    private suspend fun Dialogue.offerToUnlock() {
        val go =
            choice2(
                "Yeah, okay, let's give it a go.",
                true,
                "I need to do some other things first.",
                false,
                title = "Unlock Prisoner's Chains?",
            )
        if (!go) {
            chatPlayer(neutral, "I need to do some other things first.")
            chatNpc(
                neutral,
                "Okay, fair enough. Let me know when you want to give it another go.",
            )
            return
        }
        chatPlayer(neutral, "Yeah, okay, let's give it a go.")
        chatNpc(happy, "Great!")
        pickTheLock()
    }

    /**
     * The chains are picked with whatever comes to hand, or a lockpick if the player has one.
     * A failure or two goes unnoticed, but a guard who is watching will not miss a third.
     */
    private suspend fun Dialogue.pickTheLock() {
        while (true) {
            if (LOCKPICK in player.inv) {
                objbox(LOCKPICK, "You use your lockpick to help you undo the locks on the chains.")
            } else {
                mesbox("You use some nearby bits of wood and wire to try and pick the lock.")
            }
            access.soundSynth(SOUND_PICK_LOCK)
            val bonus = if (LOCKPICK in player.inv) LOCKPICK_BONUS else 0
            if (access.statRandom("stat.thieving", PICK_LOW + bonus, PICK_HIGH, 0)) {
                break
            }
            val attempts = (player.vars[PICKLOCK_VARBIT] + 1).coerceAtMost(MAX_ATTEMPTS)
            setVarBit(player, PICKLOCK_VARBIT, attempts)
            access.mes("You fail!")
            val guard = security.guardNear(player.coords, GUARD_SIGHT)
            if (attempts >= SPOTTED_AFTER && guard != null) {
                setVarBit(player, PICKLOCK_VARBIT, 0)
                caughtPickingLocks(guard)
                return
            }
            val again =
                choice2(
                    "Yeah, I'll give it another go.",
                    true,
                    "I'll try something different instead.",
                    false,
                )
            if (!again) {
                chatNpc(confused, "Are you givin' in already?")
                chatPlayer(quiz, "I just want to try something else.")
                chatNpc(bored, "Okay, if you want to try again, let me know.")
                return
            }
        }
        setVarBit(player, PICKLOCK_VARBIT, 0)
        mesbox("You hear a satisfying 'click' as you tumble the lock mechanism.")
        quest.advanceTo(access, STAGE_FREED_SLAVE)
        chatNpc(happy, "Great! You did it! Do you want to trade clothes now?")
        offerTrade()
    }

    private suspend fun Dialogue.caughtPickingLocks(guard: Npc) {
        mesbox("A nearby guard spots you!")
        access.ifClose()
        npc?.say("Oh oh!")
        access.mes("Slave: Oh oh!")
        guard.facePlayer(player)
        guard.say("Oi, what are you two doing?")
        access.mes("Guard: Oi, what are you two doing?")
        delay(2)
        with(security) { access.throwInCell(guard) }
    }

    private suspend fun Dialogue.offerTrade() {
        if (!choice2("Yes, I'll trade.", true, "No thanks...", false)) {
            chatPlayer(neutral, "No thanks...")
            chatNpc(neutral, "Okay, fair enough, let me know if you change your mind though.")
            return
        }
        chatPlayer(neutral, "Yes, I'll trade.")
        val missing = OUTFIT.filter { (desert, _) -> !player.ownsOnPerson(desert) }
        if (missing.isNotEmpty()) {
            chatNpc(neutral, "I need ${describe(missing.map { it.first })} if you want these clothes off me.")
            return
        }
        chatNpc(
            happy,
            "Great! You have the Desert Clothes! <col=000080>-- The slave starts undressing right " +
                "in front of you --</col> Okay, here's the clothes, I won't need them anymore.",
        )
        for ((desert, slave) in OUTFIT) {
            swap(desert, slave)
        }
        access.rebuildAppearance()
        objbox(SLAVE_ROBE, "The slave gives you his dirty, flea infested robe.")
        objbox(SLAVE_SHIRT, "The slave gives you his muddy, sweat soaked shirt.")
        objbox(SLAVE_BOOTS, "The slave gives you a smelly pair of decomposing boots.")
        quest.advanceTo(access, STAGE_TRADED_CLOTHES)
        setVarBit(player, SLAVE_VARBIT, 1)
        chatNpcSpecific("Escaping slave", ESCAPED_SLAVE_HEAD, happy, "Right, I'm off! Good luck!")
        chatPlayer(happy, "Yeah, good luck to you too!")
    }

    /** A carried piece is swapped in the pack; a worn one is swapped where the player wears it. */
    private fun Dialogue.swap(desert: String, slave: String) {
        if (desert in player.inv) {
            access.invReplace(access.inv, desert, 1, slave)
        } else {
            access.invReplace(player.worn, desert, 1, slave)
        }
    }

    private fun Player.ownsOnPerson(obj: String): Boolean =
        obj in inv || obj in worn

    private fun Player.wearingAllSlaveClothes(): Boolean =
        SLAVE_SHIRT in worn && SLAVE_ROBE in worn && SLAVE_BOOTS in worn

    /** "a desert robe, a desert shirt and desert boots", or whichever of those are missing. */
    private fun describe(missing: List<String>): String {
        val names =
            missing.map {
                when (it) {
                    DESERT_ROBE -> "a desert robe"
                    DESERT_SHIRT -> "a desert shirt"
                    else -> "desert boots"
                }
            }
        return if (names.size == 1) names[0]
        else names.dropLast(1).joinToString(", ") + " and " + names.last()
    }

    private companion object {
        const val MALE_SLAVE_HEAD = "npc.mining_slave_male"
        const val ESCAPED_SLAVE_HEAD = "npc.escaped_slave"
        const val ROWDY_SLAVE = "npc.slave_rowdy"
        const val LOCKPICK = "obj.lockpick"

        const val GUARD_SIGHT = 6
        const val PICK_LOW = 111
        const val PICK_HIGH = 250
        const val LOCKPICK_BONUS = 40
        const val MAX_ATTEMPTS = 3
        const val SPOTTED_AFTER = 2

        /* Robe first: that is the order the slave names them in, and the order he hands them over. */
        val OUTFIT =
            listOf(DESERT_ROBE to SLAVE_ROBE, DESERT_SHIRT to SLAVE_SHIRT, DESERT_BOOTS to SLAVE_BOOTS)

        val BUSY_SLAVES =
            listOf(
                "npc.mining_slave_male",
                "npc.mining_slave_female",
                "npc.tourtrap_qip_slave_male_1",
                "npc.tourtrap_qip_mining_slave_male_2",
                "npc.tourtrap_qip_mining_slave_female_1",
                "npc.tourtrap_qip_mining_slave_female_2",
            )

        val ROWDY_LINES =
            listOf(
                "Hey, you're in for a good beating!",
                "I'm gonna teach you some respect!",
                "Oi! Are you looking at me!",
                "Oi! I'm gonna give you some trouble!",
                "You wanna piece of me?",
            )
    }
}
