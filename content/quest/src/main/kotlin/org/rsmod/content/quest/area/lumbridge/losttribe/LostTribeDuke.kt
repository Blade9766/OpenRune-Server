package org.rsmod.content.quest.area.lumbridge.losttribe

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.Constants
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.BROOCH
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.DRAGON_SLAYER
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.HAM_ROBES_FOUND
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.HAM_TOLD_DUKE
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.RUNE_MYSTERIES
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.SILVERWARE
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_ASKING
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_CONTACT
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_EMOTES_LEARNT
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_PERMISSION
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_READ_BOOK
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_SHOWN_BROOCH
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_SILVERWARE_MISSING
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_TREATY
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_TUNNEL_DUG
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_WITNESS_FOUND
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.TREATY
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.quest.manager.menu
import org.rsmod.game.entity.Player

/**
 * Duke Horacio's half of The Lost Tribe. The Duke's own script (in the Lumbridge area module) adds
 * [option] to his menu and hands the choice to [talk]; Sigmund stands beside him and chips in.
 */
@Singleton
class LostTribeDuke
@Inject
constructor(private val lostTribe: LostTribeQuest, private val objRepo: ObjRepository) {

    /** The Lost Tribe line the Duke's menu gains at the player's stage, or null. */
    fun option(player: Player): String? =
        when (lostTribe.stage(player)) {
            STAGE_ASKING -> "What happened in the cellar?"
            STAGE_WITNESS_FOUND -> "${lostTribe.witness(player).displayName} says he saw something in the cellar"
            STAGE_TUNNEL_DUG -> "I dug through the rubble..."
            STAGE_READ_BOOK -> "I found out about the symbol..."
            STAGE_EMOTES_LEARNT -> "I spoke to the generals in the goblin village..."
            STAGE_CONTACT -> "I've made contact with the cave goblins..."
            STAGE_PERMISSION,
            STAGE_SHOWN_BROOCH,
            STAGE_SILVERWARE_MISSING,
            STAGE_TREATY -> "What was I doing again?"
            else -> null
        }

    suspend fun Dialogue.talk() {
        with(lostTribe) {
            when (stage(player)) {
                STAGE_ASKING -> {
                    chatPlayer(quiz, "What happened in the cellar?")
                    chatNpc(
                        neutral,
                        "The wall collapsed because of an earthquake. I'm waiting for a team of builders to " +
                            "come and repair it.",
                    )
                }
                STAGE_WITNESS_FOUND -> convince()
                STAGE_PERMISSION -> {
                    chatPlayer(quiz, "What was I doing again?")
                    chatNpc(
                        neutral,
                        "You were helping to investigate what happened in the cellar. Come back when you've " +
                            "found something out.",
                    )
                }
                STAGE_TUNNEL_DUG -> tunnel()
                STAGE_SHOWN_BROOCH -> {
                    chatPlayer(quiz, "What was I doing again?")
                    chatNpc(
                        neutral,
                        "Please find out what you can about that brooch. The librarian in Varrock might be " +
                            "able to help identify the symbol.",
                    )
                }
                STAGE_READ_BOOK -> {
                    chatPlayer(
                        happy,
                        "I found out about the symbol. It was the symbol of one of the ancient goblin tribes!",
                    )
                    sigmund(angry, "You see, your grace? I told you goblins were behind this!")
                    chatNpc(
                        confused,
                        "I still find it surprising that such a well-crafted brooch would have a goblin " +
                            "symbol on it. Perhaps you should find out more about this ancient tribe.",
                    )
                }
                STAGE_EMOTES_LEARNT -> {
                    chatPlayer(
                        neutral,
                        "I spoke to the goblin generals in the goblin village. They told me about an ancient " +
                            "goblin tribe that went to live underground.",
                    )
                    sigmund(
                        angry,
                        "What more proof do we need? Nasty, smelly goblins have been living under our feet " +
                            "all this time! We must crush them at once!",
                    )
                    chatNpc(
                        worried,
                        "Hmm, perhaps you are right. I will send word to the army to prepare for an " +
                            "underground assault.",
                    )
                    chatNpc(
                        neutral,
                        "${player.displayName}, I would still like you to find out more about this tribe. It " +
                            "cannot hurt to know one's enemy.",
                    )
                }
                STAGE_CONTACT -> contact()
                STAGE_SILVERWARE_MISSING -> silverware()
                STAGE_TREATY -> {
                    chatPlayer(quiz, "What was I doing again?")
                    chatNpc(neutral, "You were taking the peace treaty to the cave goblins.")
                    if (!ownsItem(access, TREATY)) {
                        chatPlayer(sad, "I, er, lost the treaty.")
                        chatNpc(bored, "*sigh* All right, here's another copy.")
                        access.invAddOrDrop(objRepo, TREATY)
                    }
                }
            }
        }
    }

    private suspend fun Dialogue.convince() {
        with(lostTribe) {
            val witness = witness(player)
            chatPlayer(
                neutral,
                "${witness.displayName} says he saw something in the cellar. Like a goblin with big eyes.",
            )
            chatNpc(
                neutral,
                "Yes, he mentioned that to me. But I think he was imagining things. Goblins live in natural " +
                    "caves but everyone knows they don't have the wit to make their own tunnels.",
            )
            sigmund(
                worried,
                "Yes your grace, but if there is any possibility that this is a goblin incursion then we " +
                    "should take that possibility very seriously!",
            )
            chatPlayer(neutral, "I think we should at least investigate.")
            if (QuestRequirements.hasCompleted(player, DRAGON_SLAYER)) {
                sigmund(
                    neutral,
                    "Your grace, this is the adventurer who defeated the dragon Elvarg. I think you should " +
                        "listen to ${player.himOrHer()}.",
                )
            } else if (QuestRequirements.hasCompleted(player, RUNE_MYSTERIES)) {
                sigmund(
                    neutral,
                    "Your grace, this adventurer took that talisman you discovered to Sedridor. I think you " +
                        "should listen to ${player.himOrHer()}.",
                )
            }
            chatNpc(
                neutral,
                "Hmm, very well. I give you permission to investigate this mystery. If there is a blocked " +
                    "tunnel then perhaps you should try to un-block it.",
            )
            advanceTo(access, STAGE_PERMISSION)
        }
    }

    private suspend fun Dialogue.tunnel() {
        with(lostTribe) {
            chatPlayer(happy, "I dug through the rubble in the cellar and found a tunnel!")
            if (BROOCH !in player.inv) {
                sigmund(angry, "You see, your grace? It is a goblin attack!")
                chatNpc(neutral, "That tunnel could have many origins. I suggest you investigate further.")
                return
            }
            chatPlayer(neutral, "On the ground I found this brooch.")
            chatNpc(
                confused,
                "I've never seen anything like that before. It doesn't come from Lumbridge. What do you " +
                    "think, Sigmund?",
            )
            sigmund(
                neutral,
                "It is unknown to me, your grace. But the fact it is there is enough to prove " +
                    "${witness(player).displayName}'s story. It must have been dropped by a goblin as it fled.",
            )
            chatNpc(confused, "I've never heard of a goblin wearing something so well-crafted.")
            sigmund(angry, "Then it must have been stolen!")
            chatNpc(quiz, "But it wasn't stolen from us. Where could it be from?")
            sigmund(
                angry,
                "That doesn't matter! You said yourself that goblins couldn't have made that, so they must " +
                    "have stolen it from somewhere.",
            )
            sigmund(angry, "Horrible, thieving goblins have broken into our cellar! We must retaliate immediately!")
            sigmund(
                angry,
                "First we should wipe out the goblins east of the river, then we can march on the goblin " +
                    "village to the north-west...",
            )
            chatNpc(angry, "I will not commit troops until I have proof that goblins are behind this.")
            chatNpc(
                neutral,
                "${player.displayName}, please find out what you can about this brooch. The librarian in " +
                    "Varrock might be able to help identify the symbol.",
            )
            advanceTo(access, STAGE_SHOWN_BROOCH)
        }
    }

    private suspend fun Dialogue.contact() {
        with(lostTribe) {
            chatPlayer(
                neutral,
                "I've made contact with the cave goblins. They say they were following a seam and broke into " +
                    "the cellar by mistake.",
            )
            sigmund(angry, "And I suppose you believe them, goblin-lover?")
            chatPlayer(neutral, "Well, they seemed friendlier than most goblins, and nothing was taken from the cellar.")
            chatNpc(
                sad,
                "Actually, something was taken. Sigmund has informed me that some of the castle silverware is " +
                    "missing from the cellar.",
            )
            chatNpc(sad, "Unless it is returned, I am afraid I will have no option but war.")
            advanceTo(access, STAGE_SILVERWARE_MISSING)
        }
    }

    private suspend fun Dialogue.silverware() {
        val options = buildList {
            add("What was I doing again?" to 1)
            if (player.lostTribeHam == HAM_ROBES_FOUND) {
                add("Did you know Sigmund is a member of HAM?" to 2)
            }
            if (SILVERWARE in player.inv) {
                add("I found the missing silverware in the HAM cave!" to 3)
            }
        }
        when (menu(options)) {
            1 -> {
                chatPlayer(quiz, "What was I doing again?")
                chatNpc(
                    sad,
                    "We are preparing to go to war with the underground goblins over the missing silverware. " +
                        "If you think the cave goblins are innocent you should present evidence quickly!",
                )
            }
            2 -> {
                chatPlayer(quiz, "Did you know Sigmund is a member of HAM?")
                chatNpc(
                    neutral,
                    "Hmm, I had suspected it. But however much I disapprove of the HAM movement it's not " +
                        "actually illegal. I'll keep an eye on him though.",
                )
                player.lostTribeHam = HAM_TOLD_DUKE
            }
            3 -> returnSilverware()
        }
    }

    private suspend fun Dialogue.returnSilverware() {
        with(lostTribe) {
            chatPlayer(happy, "I found the missing silverware in the HAM cave!")
            if (access.invDel(access.inv, SILVERWARE).failure) {
                return
            }
            chatNpc(angry, "Sigmund! Is this your doing?")
            sigmund(
                worried,
                "Of...of course not! The goblins must have, um, dropped the silverware as they ran away.",
            )
            chatNpc(
                angry,
                "Don't lie to me! I knew you were a HAM member but I didn't think you would stoop to this. You " +
                    "are dismissed from my service.",
            )
            sigmund(
                angry,
                "But don't you see it was for the best? For goblins to be living under our feet like this... " +
                    "ugh. It doesn't matter how civilized they are: all sub-human species must be wiped out!",
            )
            chatNpc(angry, "That's enough! Get out of my castle now!")
            chatNpc(
                neutral,
                "I see I was ill-advised. Unless there is an act of aggression by the cave goblins there is no " +
                    "need for war.",
            )
            mesbox("The Duke writes a document and signs it.")
            access.invAddOrDrop(objRepo, TREATY)
            advanceTo(access, STAGE_TREATY)
            chatNpc(
                neutral,
                "This peace treaty specifies the border between Lumbridge and the Cave Goblin realm. Please " +
                    "take it to the cave goblins and tell them I would like to meet with their leader to sign it.",
            )
        }
    }

    private fun Player.himOrHer(): String =
        if (appearance.bodyType == Constants.bodytype_a) "him" else "her"
}
