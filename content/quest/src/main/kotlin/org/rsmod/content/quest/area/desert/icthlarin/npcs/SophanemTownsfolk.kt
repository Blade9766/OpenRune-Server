package org.rsmod.content.quest.area.desert.icthlarin.npcs

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.front
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.COINS
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.KLENTER_HAUNT_CYCLES
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.KLENTER_HAUNT_TIMER
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.LINEN
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_FIRST_FLASHBACK_DONE
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_JAR_RETURNED
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_PREPARING_CEREMONY
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_RETURN_JAR
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_WOKE_IN_SOPHANEM
import org.rsmod.content.quest.area.desert.icthlarin.SophanemCoords
import org.rsmod.content.quest.area.desert.icthlarin.ilhGaveLinen
import org.rsmod.content.quest.area.desert.icthlarin.ilhMetEmbalmer
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The people of Sophanem who have something to say about the quest: Klenter's angry shade, Jex
 * at the Temple of the Lesser Gods, Raetul the cloth merchant and his partner Siamun, the idle
 * workers and the priests of Icthlarin at the temple and the city gates. Until the stolen jar is
 * returned, every one of them treats the player as the grave robber they are.
 */
class SophanemTownsfolk
@Inject
constructor(
    private val quest: IcthlarinsLittleHelperQuest,
    private val shops: Shops,
    private val launcher: ProtectedAccessLauncher,
    private val random: GameRandom,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(KLENTER) { startDialogue(it.npc) { klenter() } }
        onOpNpc1(JEX) { startDialogue(it.npc) { jex() } }
        onOpLoc1(TEMPLE_TRAPDOOR) { startDialogue { chatNpcSpecific("Jex", JEX, angry, "Hey! You don't have permission to go down there.") } }
        onOpNpc1(RAETUL) { startDialogue(it.npc) { raetul() } }
        onOpNpc3(RAETUL) { openClothStore(it.npc) }
        onOpNpc1(SIAMUN) { startDialogue(it.npc) { siamun() } }
        for (worker in WORKERS) {
            onOpNpc1(worker) { startDialogue(it.npc) { worker() } }
        }
        onOpNpc1(TEMPLE_PRIEST) { startDialogue(it.npc) { templePriest() } }
        for (priest in GATE_PRIESTS) {
            onOpNpc1(priest) { startDialogue(it.npc) { gatePriest() } }
        }
        onPlayerSoftTimer(KLENTER_HAUNT_TIMER) { haunt(player) }
        onPlayerLogin {
            if (quest.stage(player) in HAUNTING_STAGES) {
                player.softTimer(KLENTER_HAUNT_TIMER, KLENTER_HAUNT_CYCLES)
            }
        }
    }

    /* Klenter */

    private suspend fun Dialogue.klenter() {
        if (player.front?.id !in GHOSTSPEAK_AMULETS) {
            chatNpc(angry, "Wooo wooo wooooo!")
            mesbox(
                "The spirit tries to converse with you, but all you can understand is his anger, " +
                    "which is directed toward you.",
            )
            return
        }
        chatNpc(angry, KLENTER_INSULTS[random.of(KLENTER_INSULTS.size)])
    }

    /**
     * Klenter's shade does not leave a thief who dawdles in peace: every so often, until the
     * first memory returns, he lashes out or makes off with a little of the player's gold.
     */
    private fun haunt(player: Player) {
        if (quest.stage(player) !in HAUNTING_STAGES) {
            player.clearSoftTimer(KLENTER_HAUNT_TIMER)
            return
        }
        if (!SophanemCoords.inSophanem(player.coords) || player.coords.level != 0) {
            return
        }
        launcher.launch(player) { spectreStrikes() }
    }

    private fun ProtectedAccess.spectreStrikes() {
        soundSynth(SPECTRE_ATTACK)
        val coins = inv.count(COINS)
        if (coins > 0 && random.of(2) == 0) {
            val stolen = (coins / STEAL_DIVISOR).coerceIn(1, MAX_STOLEN)
            invDel(inv, COINS, stolen)
            mes("A spectre swoops out of nowhere and makes off with some of your gold!")
            return
        }
        takeInstantHit(HitType.Typeless, random.of(1, SPECTRE_MAX_HIT))
        mes("A spectre swoops out of nowhere and claws at you!")
    }

    /* Jex and the Temple of the Lesser Gods */

    private suspend fun Dialogue.jex() {
        chatPlayer(happy, "Hello.")
        if (quest.stage(player) in STAGE_WOKE_IN_SOPHANEM until STAGE_RETURN_JAR) {
            chatNpc(angry, "I will not speak with a defiler of the dead. Be gone!")
            return
        }
        chatNpc(happy, "Hello there. Welcome to the Temple of the Lesser Gods.")
        if (!choice2("Could you tell me more about the lesser gods?", true, "I'd better get going.", false)) {
            chatPlayer(neutral, "I'd better get going.")
            return
        }
        chatPlayer(quiz, "Could you tell me more about the lesser gods?")
        chatNpc(
            neutral,
            "Of course. The lesser gods, or avatars as some call them, were born from the dreams of " +
                "Tumeken. They are Apmeken, Het, Crondis and Scabaras.",
        )
        while (true) {
            when (
                choice5(
                    "Could you tell me more about Apmeken?", 1,
                    "Could you tell me more about Het?", 2,
                    "Could you tell me more about Crondis?", 3,
                    "Could you tell me more about Scabaras?", 4,
                    "Interesting. Thanks for the information.", 5,
                )
            ) {
                1 -> apmeken()
                2 -> het()
                3 -> crondis()
                4 -> scabaras()
                else -> {
                    chatPlayer(happy, "Interesting. Thanks for the information.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.apmeken() {
        chatPlayer(quiz, "Could you tell me more about Apmeken?")
        chatNpc(
            happy,
            "Apmeken is the goddess of companionship. She is known to be the most mischievous, " +
                "playful and unpredictable of the minor deities. I'd say she's my personal favourite.",
        )
        chatPlayer(quiz, "So I shouldn't trust you then?")
        chatNpc(
            laugh,
            "Apmeken can appeal to the most erudite scholar or the most barefaced liar. I'd like to " +
                "be considered amongst the former category.",
        )
        chatPlayer(quiz, "I see. What does she look like?")
        chatNpc(
            neutral,
            "She has the head of a monkey, or sometimes an ape. It changes depending upon what nature " +
                "she is displaying. For example, the orangutan represents wisdom, while the baboon " +
                "represents mischief.",
        )
    }

    private suspend fun Dialogue.het() {
        chatPlayer(quiz, "Could you tell me more about Het?")
        chatNpc(
            neutral,
            "Het is the least strange of our deities to foreign eyes, for he looks just like us. I " +
                "suspect this is why his shrines are particularly targeted by thieves like that " +
                "infamous Templeton.",
        )
        chatPlayer(quiz, "What does he represent?")
        chatNpc(
            neutral,
            "Het is the god of health, both in mind and body. He is quite popular amongst soldiers as " +
                "a result, especially up in Al Kharid.",
        )
        chatPlayer(quiz, "Just soldiers?")
        chatNpc(
            neutral,
            "Not at all! Like I said, the wellbeing of the mind is also key to Het. Many great " +
                "orators also look to him for guidance. I imagine his teachings help with some of " +
                "their strenuous speeches.",
        )
        chatPlayer(quiz, "How did his followers react to the plagues here?")
        chatNpc(
            neutral,
            "With many arguments. They really couldn't decide whether it was a punishment from Het, " +
                "or an affront to him. I stayed out of that debate.",
        )
    }

    private suspend fun Dialogue.crondis() {
        chatPlayer(quiz, "Could you tell me more about Crondis?")
        chatNpc(neutral, "Crondis is a ferocious lady with the head of a mighty crocodile.")
        chatPlayer(laugh, "That must be awkward when buying helmets.")
        chatNpc(laugh, "Luckily for her, I don't think deities have much need for them.")
        chatPlayer(quiz, "So what is she the goddess of?")
        chatNpc(neutral, "Resourcefulness. A word which seems to be open to large interpretation.")
        chatPlayer(quiz, "How so?")
        chatNpc(
            neutral,
            "Most consider her to be a teacher of modesty. They say we should be resourceful by only " +
                "taking and using that which we absolutely need.",
        )
        chatPlayer(quiz, "But there's others with a different interpretation?")
        chatNpc(
            neutral,
            "Some believe she is actually the goddess of pleasure. They claim that the idea of " +
                "modesty came from a misinterpretation, and that to be resourceful is to take all " +
                "that you can.",
        )
        chatPlayer(confused, "That doesn't sound right.")
        chatNpc(
            neutral,
            "No. It is a more recent take that I suspect was born in Menaphos. There are many in that " +
                "city with more wealth than they'll ever need. Modesty is not an ideal they'll ever embody.",
        )
    }

    private suspend fun Dialogue.scabaras() {
        chatPlayer(quiz, "Could you tell me more about Scabaras?")
        chatNpc(
            neutral,
            "There are many opinions out there on Scabaras, but general knowledge isn't always the " +
                "most accurate.",
        )
        chatPlayer(quiz, "Well what are the absolute facts then?")
        chatNpc(
            neutral,
            "Scabaras is the god of isolation. He takes on the appearance of a man with the head of a " +
                "scarab. Both he and his followers were driven into hiding long ago.",
        )
        chatPlayer(quiz, "And?")
        chatNpc(neutral, "And that's it. You asked for the absolute facts. Anything beyond that is just speculation.")
    }

    /* Raetul and Siamun, cloth merchants */

    private suspend fun Dialogue.raetul() {
        chatPlayer(happy, "Hello.")
        if (quest.isAccused(player)) {
            chatNpc(angry, "Get away! You're a thief and a desecrator of the dead.")
            chatPlayer(confused, "What?")
            chatNpc(angry, "You heard me! Clear off!")
            return
        }
        if (needsLinen()) {
            sellLinen()
            return
        }
        chatNpc(neutral, "What can I do for you?")
        if (!choice2("What do you have for sale?", true, "Nothing.", false)) {
            chatPlayer(neutral, "Nothing.")
            chatNpc(neutral, "Right...")
            return
        }
        chatPlayer(quiz, "What do you have for sale?")
        chatNpc(happy, "All sorts of things. Why don't you have a look?")
        access.openClothStore(npc ?: return)
    }

    private fun Dialogue.needsLinen(): Boolean =
        quest.stage(player) == STAGE_PREPARING_CEREMONY &&
            player.ilhMetEmbalmer &&
            !player.ilhGaveLinen &&
            LINEN !in player.inv

    /** Raetul drives a hard bargain: the sale is made before the player has agreed to it. */
    private suspend fun Dialogue.sellLinen() {
        chatNpc(happy, "Good afternoon.")
        chatPlayer(quiz, "Is it?")
        chatNpc(happy, "It's hot and sunny, which is good enough for me. So can I help you with anything?")
        chatPlayer(
            neutral,
            "I've met with the town's embalmer and he mentioned that he needed a few things, linen " +
                "being one of them.",
        )
        if (player.inv.count(COINS) < LINEN_PRICE) {
            chatNpc(neutral, "Well I can sell you a sheet of linen for $LINEN_PRICE coins.")
            chatPlayer(sad, "I don't have that much on me. I'll be back.")
            return
        }
        chatNpc(happy, "Of course. That will be $LINEN_PRICE coins please and thank you.")
        if (access.invDel(access.inv, COINS, LINEN_PRICE).failure) {
            return
        }
        access.invAdd(access.inv, LINEN)
        objbox(
            LINEN,
            "The merchant takes your money and hands you a sheet of linen before you realise that a " +
                "transaction has been agreed.",
        )
        chatPlayer(shocked, "What the...")
        chatNpc(happy, "Do come again.")
    }

    private fun ProtectedAccess.openClothStore(npc: org.rsmod.game.entity.Npc) {
        if (quest.isAccused(player)) {
            mes("Raetul refuses to trade with a grave robber.")
            return
        }
        shops.open(
            player,
            CLOTH_STORE_TITLE,
            CLOTH_STORE,
            buyPercentage = CLOTH_STORE_BUY,
            sellPercentage = CLOTH_STORE_SELL,
            changePercentage = CLOTH_STORE_CHANGE,
        )
    }

    private suspend fun Dialogue.siamun() {
        if (quest.isAccused(player)) {
            chatNpc(angry, "Clear off! I'll have nothing to do with you!")
            chatPlayer(worried, "But...")
            chatNpc(angry, "Go!")
            return
        }
        chatPlayer(happy, "Hello.")
        chatNpc(neutral, "Do you need something?")
        chatPlayer(neutral, "No.")
        chatNpc(neutral, "Well I have lots to do, so good day.")
    }

    /* Workers */

    private suspend fun Dialogue.worker() {
        chatPlayer(happy, "Hello.")
        chatNpc(happy, "Hey there!")
        chatPlayer(quiz, "You seem rather happy for a busy labourer with a bad case of spots.")
        chatNpc(happy, "Of course! These spots are the best thing since log rollers!")
        chatPlayer(confused, "I really don't follow.")
        chatNpc(
            happy,
            "Well thanks to these spots, the city has been under quarantine. That means there's no " +
                "goods coming in or out. No goods means we don't have any work to do!",
        )
        chatPlayer(quiz, "But aren't the spots intolerable?")
        chatNpc(
            laugh,
            "I would take a dermatological irritation over back-breaking, mind-numbing labour any day!",
        )
        chatPlayer(confused, "Right... Okay then...")
    }

    /* The priests of Icthlarin */

    private suspend fun Dialogue.templePriest() {
        val stage = quest.stage(player)
        when {
            quest.isComplete(player) -> {
                chatPlayer(happy, "Hello.")
                chatNpc(
                    happy,
                    "Greetings. May the blessings of Icthlarin rest on your tired shoulders and set you " +
                        "at ease during these strange times.",
                )
            }
            stage > STAGE_PREPARING_CEREMONY -> {
                chatPlayer(happy, "Hello.")
                chatNpc(happy, "You have surprised me... I commend you for your efforts.")
            }
            stage >= STAGE_JAR_RETURNED -> {
                chatNpc(
                    neutral,
                    "Bother me not. I am preparing for the ceremony. I'm sure even you can curb your " +
                        "trivial demands until after that.",
                )
            }
            stage >= STAGE_RETURN_JAR -> {
                chatPlayer(happy, "Hello.")
                chatNpc(angry, "Cursed one, why have you not yet returned the burial jar of our old leader?")
                chatPlayer(neutral, "I'm working on that still.")
                chatNpc(
                    angry,
                    "I wonder how someone as insignificant as you even managed to get inside the " +
                        "pyramid, never mind reach its heart.",
                )
            }
            stage >= STAGE_FIRST_FLASHBACK_DONE + 1 -> {
                chatPlayer(happy, "Hello.")
                chatNpc(angry, "What do you want, cursed one?")
                chatPlayer(neutral, "The Sphinx told me to find the High Priest of Icthlarin.")
                chatNpc(
                    angry,
                    "A likely story. She would never reduce herself to talk to one as lowly as you, " +
                        "never mind help you!",
                )
            }
            stage >= STAGE_WOKE_IN_SOPHANEM -> {
                chatPlayer(happy, "Hello.")
                chatNpc(neutral, "What business do you have with a servant of Icthlarin?")
                chatPlayer(worried, "I need some help figuring out what's going on.")
                chatNpc(angry, "I will not aid a defiler of the dead. Be gone!")
            }
            else -> {
                chatPlayer(happy, "Hello.")
                chatNpc(
                    neutral,
                    "Greetings. May the blessings of Icthlarin rest on your tired shoulders and set you " +
                        "at ease during these strange times.",
                )
            }
        }
    }

    private suspend fun Dialogue.gatePriest() {
        if (quest.isComplete(player)) {
            chatNpc(happy, "Good day, adventurer.")
            return
        }
        chatPlayer(quiz, "Hello. Can I go through this gate?")
        if (quest.stage(player) >= STAGE_JAR_RETURNED) {
            chatNpc(neutral, "Yes. The High Priest has given you permission to come and go as you please.")
            return
        }
        chatNpc(
            neutral,
            "No. Nobody may enter or leave Sophanem for any reason due to the quarantine. Move along.",
        )
    }

    private companion object {
        const val KLENTER = "npc.ics_little_spectre"
        const val JEX = "npc.contact_jex"
        const val TEMPLE_TRAPDOOR = "loc.contact_temple_trapdoor_open"
        const val RAETUL = "npc.ics_little_linen1"
        const val SIAMUN = "npc.ics_little_linen2"
        const val TEMPLE_PRIEST = "npc.ics_little_priest"

        val WORKERS = listOf("npc.ics_little_worker1", "npc.ics_little_worker2")
        val GATE_PRIESTS = listOf("npc.ics_little_priestdoorman", "npc.ics_little_priestdoorman_alt")

        const val LINEN_PRICE = 30

        const val CLOTH_STORE = "inv.sophanem_cloth_store"
        const val CLOTH_STORE_TITLE = "Raetul and Co's Cloth Store"
        const val CLOTH_STORE_SELL = 100.0
        const val CLOTH_STORE_BUY = 55.0
        const val CLOTH_STORE_CHANGE = 1.0

        const val SPECTRE_ATTACK = "synth.ics_spectre_appear"
        const val SPECTRE_MAX_HIT = 3
        const val STEAL_DIVISOR = 20
        const val MAX_STOLEN = 50

        /** Klenter haunts the thief between waking in the city and remembering the first flashback. */
        val HAUNTING_STAGES = STAGE_WOKE_IN_SOPHANEM until STAGE_FIRST_FLASHBACK_DONE

        val KLENTER_INSULTS =
            listOf(
                "Spawn of evil!",
                "Organ snatcher!",
                "Thief!",
                "Evil doer!",
                "Grave defiler!",
                "You pathetic excuse for a thief!",
                "You worm! May the Devourer take your soul!",
                "Grave robber!",
            )

        val GHOSTSPEAK_AMULETS: Set<Int> by lazy {
            setOf("obj.amulet_of_ghostspeak", "obj.amulet_of_ghostspeak_enchanted")
                .map { it.asRSCM(RSCMType.OBJ) }
                .toSet()
        }
    }
}
