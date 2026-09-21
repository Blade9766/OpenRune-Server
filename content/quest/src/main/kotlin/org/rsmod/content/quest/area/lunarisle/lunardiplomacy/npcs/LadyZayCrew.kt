package org.rsmod.content.quest.area.lunarisle.lunardiplomacy.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_ASKED_COOK
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_ASKED_FIRST_MATE
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_ASKED_LEE
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_ASKED_LOOKOUT
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_ASKED_NAVIGATOR
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_AT_LUNAR_ISLE
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_BLAMED_NAVIGATOR
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_FOUND_CULPRIT
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_JINX_LIFTED
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_LEARNT_OF_JINX
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_SAILED_IN_CIRCLE
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The crew of the Lady Zay. Six of them move the investigation of the jinx on: the navigator, the
 * lookout, the cook, 'Lecherous' Lee, the first mate and finally the cabin boy, who owns up and
 * hands over the emerald lens. The rest just chat about whatever is happening aboard.
 *
 * `npc.lunar_pirate_navigator_multi` and `npc.lunar_pirate_cabin_boy` are multi-npcs (on Dream
 * Mentor's progress and on this quest's stage), so their ops are bound on those base types. The
 * cabin boy's own type also stands in the Moon Clan's town after he goes to confront the girl.
 */
class LadyZayCrew @Inject constructor(private val lunar: LunarDiplomacyQuest) : PluginScript() {
    private enum class Phase { Docked, Circling, Investigating, Symbols, Ready, Arrived }

    override fun ScriptContext.startup() {
        onOpNpc1(NAVIGATOR) { startDialogue(it.npc) { navigator() } }
        onOpNpc1(LOOKOUT) { startDialogue(it.npc) { lookout() } }
        onOpNpc1(COOK) { startDialogue(it.npc) { cook() } }
        onOpNpc1(LEE) { startDialogue(it.npc) { lee() } }
        onOpNpc1(FIRST_MATE) { startDialogue(it.npc) { firstMate() } }
        onOpNpc1(CABIN_BOY) { startDialogue(it.npc) { cabinBoy() } }
        onOpNpc1(CABIN_BOY_ASHORE) { startDialogue(it.npc) { cabinBoyAshore() } }
        for ((pirate, lines) in CHATTER) {
            onOpNpc1(pirate) { startDialogue(it.npc) { chatter(lines) } }
        }
    }

    private fun Dialogue.phase(): Phase {
        val stage = lunar.stage(player)
        return when {
            stage < STAGE_SAILED_IN_CIRCLE -> Phase.Docked
            stage < STAGE_BLAMED_NAVIGATOR -> Phase.Circling
            stage < STAGE_FOUND_CULPRIT -> Phase.Investigating
            stage < STAGE_JINX_LIFTED -> Phase.Symbols
            stage < STAGE_AT_LUNAR_ISLE -> Phase.Ready
            else -> Phase.Arrived
        }
    }

    private suspend fun Dialogue.navigator() {
        when (lunar.stage(player)) {
            STAGE_SAILED_IN_CIRCLE -> {
                chatPlayer(quiz, "Are you the navigator?")
                chatNpc(neutral, "That I am. Why?")
                chatPlayer(angry, "Think you could plot us a course that isn't a BIG STUPID CIRCLE?")
                chatNpc(
                    angry,
                    "My course was true, like all my others! If you've a problem with how I do my " +
                        "job, take it up with the Captain. I won't be criticised by a stowaway!",
                )
                chatPlayer(angry, "Maybe I will go and talk to the Captain about you.")
                lunar.advanceTo(access, STAGE_ASKED_NAVIGATOR)
                chatNpc(bored, "You do that. Oh, I'm sooo scared.")
                chatPlayer(angry, "You'd better be. Right, I'm going.")
            }
            STAGE_ASKED_NAVIGATOR -> {
                chatNpc(bored, "What do you want? Spoken to the Captain yet?")
                chatPlayer(angry, "Not yet, but I will.")
                chatNpc(bored, "Well go on then. You're still standing here.")
            }
            STAGE_BLAMED_NAVIGATOR -> jinx()
            else ->
                when (phase()) {
                    Phase.Docked -> {
                        chatPlayer(quiz, "So... what are you doing?")
                        chatNpc(
                            happy,
                            "I'm the navigator! Once we're moving it's my job to check our bearings " +
                                "against the charts with my sextant. Without me this lot could end " +
                                "up hours late, or miles off course!",
                        )
                        chatPlayer(quiz, "Right. But what are you doing now?")
                        chatNpc(neutral, "Oh, just relaxing until we cast off.")
                    }
                    Phase.Symbols, Phase.Investigating -> {
                        chatNpc(worried, "Found that jinx yet? I've no wish to end up full of mackerel like the last one.")
                    }
                    Phase.Ready -> chatNpc(happy, "The charts look better already. I knew it wasn't my fault!")
                    else -> chatNpc(happy, "Right on course, as always. Told you my navigation was flawless.")
                }
        }
    }

    private suspend fun Dialogue.jinx() {
        chatPlayer(angry, "So, call yourself a navigator? Way to plot a course in a big circle!")
        chatNpc(angry, "Your rudeness is appalling! My course was expertly calculated. There's only one reason we failed to arrive.")
        chatPlayer(quiz, "Oh really? Wrong sort of waves? Steering wheel stuck?")
        chatNpc(
            neutral,
            "Look for yourself: every co-ordinate is plotted to a visible landmark. No, it must be " +
                "magic. Somebody has jinxed this ship.",
        )
        chatNpc(
            shifty,
            "Though a stowaway turning up aboard at the exact moment the jinx struck would make " +
                "the obvious suspect. And we all know what we do to a jinx.",
        )
        chatPlayer(worried, "Uh... we do?")
        chatNpc(worried, "Horrible business, the last one. I never knew a body could hold so much mackerel.")
        lunar.advanceTo(access, STAGE_LEARNT_OF_JINX)
        chatPlayer(worried, "Right. I'd better find this jinx before anyone else blames me for it.")
    }

    private suspend fun Dialogue.lookout() {
        if (lunar.stage(player) == STAGE_LEARNT_OF_JINX) {
            chatPlayer(quiz, "Do you know anything about a jinx?")
            chatNpc(worried, "A jinx? Evil things, they are!")
            chatPlayer(quiz, "What exactly is a jinx?")
            chatNpc(
                neutral,
                "Sometimes it's a person with such rotten luck that everything they touch goes " +
                    "wrong. But the worse kind is a curse, often laid on an object, so misfortune " +
                    "follows whoever owns it.",
            )
            chatNpc(
                worried,
                "On land that means tripping over roots. At sea, with only wood between you and a " +
                    "cold, murky death, a jinx can kill the whole crew. Best thing is to get it off " +
                    "the ship, dead or alive.",
            )
            chatPlayer(quiz, "So someone with magic could lay a jinx on purpose? Someone like... the Moon Clan?")
            chatNpc(
                neutral,
                "I suppose so. But they'd only curse a whole ship if someone from our landing party " +
                    "had offended them badly at that feast.",
            )
            lunar.advanceTo(access, STAGE_ASKED_LOOKOUT)
            chatPlayer(happy, "So someone at the feast brought this on us. Thanks, 'Eagle-eye', you've been very helpful.")
            return
        }
        when (phase()) {
            Phase.Docked -> {
                chatPlayer(quiz, "See anything good through that telescope?")
                chatNpc(neutral, "Just those beasts on the island. The captain says we might have a barbecue out there later.")
                chatPlayer(quiz, "You don't sound keen. Wouldn't a beach barbecue be fun?")
                chatNpc(sad, "You haven't met the cook yet, have you?")
            }
            Phase.Circling -> chatNpc(confused, "I've watched the same bit of coast go past three times now. Most peculiar.")
            Phase.Investigating, Phase.Symbols -> chatNpc(worried, "Get that jinx off the ship before it drowns the lot of us!")
            Phase.Ready -> chatNpc(happy, "Clear skies ahead for once.")
            Phase.Arrived -> chatNpc(neutral, "Land ho! Well, we're already on it, but I like saying it.")
        }
    }

    private suspend fun Dialogue.cook() {
        if (lunar.stage(player) == STAGE_ASKED_LOOKOUT) {
            chatPlayer(happy, "Hello there!")
            chatNpc(neutral, "Galley's shut fer restockin'! Come back later if'n ye be wantin' food!")
            chatPlayer(neutral, "Actually, I'm after information.")
            chatNpc(confused, "Wussat? Some kinda grog? If'n it ain't grog I don't stock it!")
            chatPlayer(
                neutral,
                "I think someone offended the Moon Clan and got the ship jinxed. Do you know " +
                    "anything about that?",
            )
            chatNpc(
                neutral,
                "The Moon Clan? Can't say I do. When the rest went off to that big feast I stayed " +
                    "aboard. I don't trust food I ain't cooked meself!",
            )
            chatPlayer(quiz, "So the whole crew except you went to a feast on Lunar Isle. With drink?")
            chatNpc(happy, "Aye, ter be sure!")
            lunar.advanceTo(access, STAGE_ASKED_COOK)
            chatPlayer(happy, "A crew of drunken pirates at a Moon Clan feast. I think I know where to start looking. Thanks, Beefy!")
            return
        }
        when (phase()) {
            Phase.Arrived -> chatNpc(happy, "Galley's open! Suqah stew, anyone? No? Suit yerselves.")
            else -> {
                chatPlayer(neutral, "Hello.")
                chatNpc(neutral, "The galley ain't open fer food yet, whipper-snapper. Come back later!")
                chatPlayer(confused, "Something something open, something something food?")
                chatNpc(angry, "Aye, yer yella-bellied land-lizard!")
            }
        }
    }

    private suspend fun Dialogue.lee() {
        if (lunar.stage(player) == STAGE_ASKED_COOK) {
            chatPlayer(quiz, "I've a couple of questions about that feast on Lunar Isle.")
            chatNpc(happy, "Aaahh, yes... I was quite taken with their hospitality!")
            chatPlayer(quiz, "Did you see anyone being rude to the Moon Clan, or slip away from the feast?")
            chatNpc(
                neutral,
                "We were all in high spirits, but nobody was rude. Only one I saw sneak off was the " +
                    "First Mate. He headed back towards the ship and came back twenty minutes later.",
            )
            lunar.advanceTo(access, STAGE_ASKED_LEE)
            chatPlayer(happy, "The First Mate, eh? Thank you, you've been very helpful.")
            return
        }
        when (phase()) {
            Phase.Docked -> chatNpc(happy, "Can't wait to get back to that island. The ladies there are something else.")
            Phase.Circling -> {
                chatPlayer(quiz, "What happened to the ship?")
                chatNpc(confused, "No idea! She doesn't normally do that!")
            }
            Phase.Arrived -> chatNpc(happy, "Back on Lunar Isle! Now, where did I see that pretty mage...")
            else -> chatNpc(neutral, "I don't deal in gossip. Ask someone else.")
        }
    }

    private suspend fun Dialogue.firstMate() {
        if (lunar.stage(player) == STAGE_ASKED_LEE) {
            chatPlayer(angry, "So, finally we come to the truth, MISTER Davey-boy. If that's even your real name.")
            chatNpc(bored, "Finally confessing to being a stowaway and a jinx, are we?")
            chatPlayer(angry, "No! I have a witness who saw you leave that feast for twenty minutes. Explain yourself!")
            chatNpc(
                neutral,
                "Ah. Yes, I did leave. I had to check the rigging was secure before the ship was " +
                    "moored for the night, in case a storm dragged her off to sea.",
            )
            chatPlayer(quiz, "Why didn't you say so before?")
            chatNpc(
                neutral,
                "It slipped my mind; it's not my job. The cabin boy is meant to secure the sails " +
                    "before we disembark. Pirate Regulations, number 445-328. But I couldn't find " +
                    "him at the feast.",
            )
            chatPlayer(shocked, "The cabin boy wasn't even at the feast? WHY DIDN'T YOU TELL ME EARLIER?")
            chatNpc(bored, "Surely a runt like that can't be of any importance.")
            lunar.advanceTo(access, STAGE_ASKED_FIRST_MATE)
            chatPlayer(neutral, "That explains why he's been acting so strangely. I'd better have a word with him.")
            return
        }
        when (phase()) {
            Phase.Docked -> {
                chatPlayer(happy, "Hey, how are you doing?")
                chatNpc(
                    bored,
                    "When stowing away aboard a vessel, one does not usually introduce oneself to " +
                        "the First Mate. I'm busy preparing to cast off; don't distract me, or the crew.",
                )
                chatPlayer(angry, "I'm not a stowaway! Lokar invited me aboard!")
            }
            Phase.Circling -> chatNpc(confused, "The rigging's fine, the sails are fine... I can't explain it.")
            Phase.Arrived -> chatNpc(neutral, "Keep out of the crew's way while we load up, would you?")
            else -> chatNpc(bored, "Have you finished interrogating the crew yet? Some of us have work to do.")
        }
    }

    private suspend fun Dialogue.cabinBoy() {
        val stage = lunar.stage(player)
        when {
            stage == STAGE_ASKED_FIRST_MATE -> confession()
            stage in STAGE_FOUND_CULPRIT until STAGE_JINX_LIFTED -> symbolsHelp()
            phase() == Phase.Circling || phase() == Phase.Investigating -> {
                chatPlayer(quiz, "So the Captain's a bad sailor, is he?")
                chatNpc(
                    worried,
                    "No! He's one of the best! I don't know why we didn't get there, and it's got " +
                        "NOTHING to do with me! IT'S NOT MY FAULT!",
                )
                chatPlayer(neutral, "Alright, calm down. I think you should lay off the coffee, youngster.")
            }
            else -> {
                chatPlayer(neutral, "Afternoon.")
                chatNpc(quiz, "Hey. You a stowaway?")
                chatPlayer(bored, "(sigh) NO, I am NOT a stowaway. Why does everyone think that?")
                chatNpc(
                    happy,
                    "No judgement here. There's plenty of barrels to hide in; I use them myself when " +
                        "the Captain's cross with me. Mostly I just make the coffee.",
                )
            }
        }
    }

    private suspend fun Dialogue.confession() {
        chatPlayer(angry, "It was you! You left the feast. You're a spy for the Moon Clan, aren't you?")
        chatNpc(shocked, "No!")
        chatPlayer(angry, "Admit it, or I'll fetch the Captain, a plank and a hungry shark!")
        chatNpc(
            sad,
            "Ok, OK! I confess! A girl from the Moon Clan lured me away. She promised me command " +
                "of the ship if I did one little job for her. Do you know how hard it is being a " +
                "boy on a ship full of pirates?",
        )
        chatPlayer(quiz, "What did she ask you to do?")
        chatNpc(
            worried,
            "She showed me some symbols and had me draw five of them around the ship. Some sort " +
                "of jinx. Please don't tell the Captain! He'll kill me!",
        )
        chatNpc(
            neutral,
            "You'll need a special lantern to see them. One's up on a wall, one's on a container, " +
                "one on a box of some sort, one on a support. The last... all I remember is that it " +
                "was big and metal.",
        )
        if (player.inv.freeSpace() < 2) {
            chatNpc(neutral, "I've got a lantern and a lens you can use, but you'll need two free spaces to carry them.")
            return
        }
        access.invAdd(access.inv, EMERALD_LENS)
        access.invAdd(access.inv, LANTERN_FRAME)
        lunar.advanceTo(access, STAGE_FOUND_CULPRIT)
        doubleobjbox(EMERALD_LENS, LANTERN_FRAME, "The cabin boy hands you an emerald lens and a lantern frame.")
        chatNpc(
            neutral,
            "Put the lens in a lantern and light it with a tinderbox, then use it to search the " +
                "ship. If you can't get oil for that frame, bring a proper bullseye lantern.",
        )
    }

    private suspend fun Dialogue.symbolsHelp() {
        val hasLens =
            listOf(EMERALD_LENS, UNLIT_EMERALD, LIT_EMERALD, EMPTY_EMERALD).any {
                player.inv.contains(it) || access.bank.contains(it)
            }
        if (!hasLens) {
            chatPlayer(sad, "I've lost that lens you gave me.")
            if (player.inv.freeSpace() == 0) {
                chatNpc(neutral, "I've another, but you've got no room for it.")
                return
            }
            access.invAdd(access.inv, EMERALD_LENS)
            chatNpc(neutral, "Luckily I kept a spare. Here, and don't lose this one!")
            return
        }
        chatNpc(
            worried,
            "Found them all yet? One's on a wall, one on a container, one on a box, one on a " +
                "support, and the last one was big and metal.",
        )
    }

    private suspend fun Dialogue.cabinBoyAshore() {
        if (lunar.stage(player) < STAGE_AT_LUNAR_ISLE) {
            chatNpc(neutral, "Hello there!")
            return
        }
        chatPlayer(happy, "So you plucked up the courage to confront that girl!")
        chatNpc(happy, "That I did! She turned out to be really nice. She's going to join us and become a pirate!")
        chatPlayer(quiz, "And you're not the least bit suspicious, after what she did last time?")
        chatNpc(neutral, "She---has---no---spell---on---me.")
        chatPlayer(worried, "Why are you talking like that? I think you've been hypnotised. I wonder what happens if I click my fingers...")
        access.say("*click*")
        chatNpc(confused, "*Cluck* *cluck* *bwaarrk*")
        chatPlayer(sad, "Oh dear. I'm sure you'll learn one day.")
    }

    private suspend fun Dialogue.chatter(lines: Map<Phase, String>) {
        val line = lines[phase()] ?: lines.getValue(Phase.Docked)
        chatNpc(neutral, line)
    }

    private companion object {
        const val NAVIGATOR = "npc.lunar_pirate_navigator_multi"
        const val LOOKOUT = "npc.lunar_pirate_lookout"
        const val COOK = "npc.lunar_pirate_cook"
        const val LEE = "npc.lunar_pirate_generic_pirate_9"
        const val FIRST_MATE = "npc.lunar_pirate_first_mate"
        const val CABIN_BOY = "npc.lunar_pirate_cabin_boy"
        const val CABIN_BOY_ASHORE = "npc.lunar_pirate_cabin_boy_base_config"

        const val EMERALD_LENS = "obj.bullseye_lantern_lens_lunar_quest"
        const val LANTERN_FRAME = "obj.bullseye_lantern_nolens"
        const val UNLIT_EMERALD = "obj.bullseye_lantern_unlit_lunar_quest"
        const val LIT_EMERALD = "obj.bullseye_lantern_lit_lunar_quest"
        const val EMPTY_EMERALD = "obj.bullseye_lantern_empty_lunar_quest"

        val CHATTER: Map<String, Map<Phase, String>> =
            mapOf(
                "npc.lunar_pirate_generic_pirate_1" to
                    mapOf(
                        Phase.Docked to "Picarron Pete's the name, plunder's the game.",
                        Phase.Circling to "Round and round we go. I'm getting seasick and we haven't even left!",
                        Phase.Investigating to "A jinx, is it? Don't look at me, I was face down in the punch bowl all night.",
                        Phase.Symbols to "You're waving that lantern about like you've lost your marbles.",
                        Phase.Ready to "Heard the jinx is gone. About time!",
                        Phase.Arrived to "Lunar Isle! Time to spend some loot.",
                    ),
                "npc.lunar_pirate_generic_pirate_2" to
                    mapOf(
                        Phase.Docked to "Bedread the bold, that's me. Bold as brass.",
                        Phase.Circling to "I've seen some rum voyages, but that one takes the biscuit.",
                        Phase.Investigating to "If you're looking for someone to blame, try the navigator.",
                        Phase.Symbols to "Symbols? On this ship? I'll believe it when I see it.",
                        Phase.Ready to "We ready to sail yet or what?",
                        Phase.Arrived to "Mind the monsters out there. Nasty teeth.",
                    ),
                "npc.lunar_pirate_generic_pirate_3" to
                    mapOf(
                        Phase.Docked to "They call me Tommy 2-times. Don't ask why. Don't ask why.",
                        Phase.Circling to "We went round twice. Twice! Like me name!",
                        Phase.Investigating to "Jinxed, jinxed. Always knew this ship was jinxed, jinxed.",
                        Phase.Symbols to "Find them symbols, find them symbols.",
                        Phase.Ready to "Off we go, off we go!",
                        Phase.Arrived to "Nice island, nice island.",
                    ),
                "npc.lunar_pirate_generic_pirate_4" to
                    mapOf(
                        Phase.Docked to "*Hic* Pull up a barrel, friend. There's grog enough for all.",
                        Phase.Circling to "*Hic* Is the ship spinning, or is it just me?",
                        Phase.Investigating to "*Hic* Feast? Can't remember a thing about it.",
                        Phase.Symbols to "*Hic* I see symbols everywhere. Always do after a few of these.",
                        Phase.Ready to "*Hic* Here's to a jinx-free voyage!",
                        Phase.Arrived to "*Hic* Are we there? We're there!",
                    ),
                "npc.lunar_pirate_generic_pirate_5" to
                    mapOf(
                        Phase.Docked to "Jack Sails at your service. No relation to the navigator.",
                        Phase.Circling to "Somebody's cursed us, mark my words.",
                        Phase.Investigating to "Whoever did this, I hope the Captain keelhauls them.",
                        Phase.Symbols to "Rub those marks out quick, before something worse happens.",
                        Phase.Ready to "Feels lighter already, doesn't she?",
                        Phase.Arrived to "Nice to be back on solid ground.",
                    ),
                "npc.lunar_pirate_generic_pirate_6" to
                    mapOf(
                        Phase.Docked to "Betty B. Boppin. Don't let the name fool you, I'll have your purse before you blink.",
                        Phase.Circling to "Well that was a waste of a good wind.",
                        Phase.Investigating to "The cabin boy's been awfully jumpy lately, if you ask me.",
                        Phase.Symbols to "Found anything with that fancy lantern?",
                        Phase.Ready to "Let's get going, I've shopping to do.",
                        Phase.Arrived to "The Moon Clan sell the finest runes this side of the sea.",
                    ),
                "npc.lunar_pirate_generic_pirate_7" to
                    mapOf(
                        Phase.Docked to "Beedy-eye Jones. I see everything that goes on aboard this ship.",
                        Phase.Circling to "I saw the island. Then I saw it again. And again.",
                        Phase.Investigating to "Some folk slipped off from that feast. I saw, but I'm not telling.",
                        Phase.Symbols to "I saw you poking about with that lantern. Found anything?",
                        Phase.Ready to "Good work, landlubber. I saw the whole thing.",
                        Phase.Arrived to "Keep an eye on your pockets ashore. I'm watching mine.",
                    ),
                "npc.lunar_pirate_generic_pirate_8" to
                    mapOf(
                        Phase.Docked to "Jenny Blade. Sharpest knife on the Lady Zay.",
                        Phase.Circling to "If the navigator did this on purpose, he'll feel my blade.",
                        Phase.Investigating to "A jinx? Then find it before I start cutting people.",
                        Phase.Symbols to "Rub them out and let's be off.",
                        Phase.Ready to "About time we had some luck.",
                        Phase.Arrived to "Behave yourself ashore. The Moon Clan don't take kindly to trouble.",
                    ),
                "npc.lunar_pirate_generic_pirate_10" to
                    mapOf(
                        Phase.Docked to "Sticky Sanders. Everything I touch sticks to my fingers, funny that.",
                        Phase.Circling to "It's either the Captain or the navigator to blame, and I'm not saying which.",
                        Phase.Investigating to "Everyone at that feast was having too much fun to jinx anyone.",
                        Phase.Symbols to "Symbols? I only collect shiny things.",
                        Phase.Ready to "Onward to riches!",
                        Phase.Arrived to "So many pockets on this island, so little time.",
                    ),
            )
    }
}
