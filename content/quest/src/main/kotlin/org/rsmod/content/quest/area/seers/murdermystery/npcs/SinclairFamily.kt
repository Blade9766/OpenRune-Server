package org.rsmod.content.quest.area.seers.murdermystery.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.seers.murdermystery.MurderMysteryQuest
import org.rsmod.content.quest.area.seers.murdermystery.MurderMysteryQuest.Companion.POISON_ASKED_SALESMAN
import org.rsmod.content.quest.area.seers.murdermystery.MurderMysteryQuest.Companion.POISON_CLAIM_HEARD
import org.rsmod.content.quest.area.seers.murdermystery.Suspect
import org.rsmod.content.quest.area.seers.murdermystery.murderFoundThread
import org.rsmod.content.quest.area.seers.murdermystery.murderPoisonProgress
import org.rsmod.content.quest.area.seers.murdermystery.murderSuspect
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Lord Sinclair's six children. They all answer the same four questions; which ones the player
 * may ask depends on having the study thread and on having heard from the poison salesman, and
 * asking the murderer about their poison is what lets the player go and disprove their story.
 */
class SinclairFamily @Inject constructor(private val murder: MurderMysteryQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        for (suspect in Suspect.entries) {
            for (npc in suspect.npcs) {
                onOpNpc1(npc) { startDialogue(it.npc) { talk(suspect) } }
            }
        }
    }

    private suspend fun Dialogue.talk(suspect: Suspect) {
        when {
            murder.isComplete(player) ->
                chatNpc(neutral, "Apparently you aren't as stupid as you look.")
            murder.isInvestigating(player) -> interview(suspect)
            else -> access.mes(if (suspect.male) "He is ignoring you." else "She is ignoring you.")
        }
    }

    private suspend fun Dialogue.interview(suspect: Suspect) {
        chatPlayer(neutral, "I'm here to help the guards with their investigation.")
        greeting(suspect)
        val options = mutableListOf(
            "Who do you think is responsible?" to Topic.Responsible,
            "Where were you when the murder happened?" to Topic.Whereabouts,
        )
        if (player.murderFoundThread) {
            options += "Do you recognise this thread?" to Topic.Thread
        }
        if (player.murderPoisonProgress >= POISON_ASKED_SALESMAN) {
            options += "Why'd you buy poison the other day?" to Topic.Poison
        }
        when (choose(options)) {
            Topic.Responsible -> {
                chatPlayer(neutral, "Who do you think is responsible?")
                responsible(suspect)
            }
            Topic.Whereabouts -> {
                chatPlayer(neutral, "Where were you when the murder happened?")
                whereabouts(suspect)
            }
            Topic.Thread -> {
                chatPlayer(confused, "Do you recognise this thread?")
                thread(suspect)
            }
            Topic.Poison -> {
                chatPlayer(neutral, "Why'd you buy poison the other day?")
                poison(suspect)
                if (
                    player.murderSuspect == suspect.id &&
                        player.murderPoisonProgress == POISON_ASKED_SALESMAN
                ) {
                    player.murderPoisonProgress = POISON_CLAIM_HEARD
                }
            }
        }
    }

    private suspend fun Dialogue.greeting(suspect: Suspect) {
        when (suspect) {
            Suspect.Anna -> chatNpc(neutral, "Oh really? What do you want to know then?")
            Suspect.Bob -> chatNpc(neutral, "I suppose I had better talk to you then.")
            Suspect.Carol -> chatNpc(neutral, "Well, ask what you want to know then.")
            Suspect.David ->
                chatNpc(
                    neutral,
                    "And? Make this quick, I have better things to do than be interrogated by " +
                        "halfwits all day.",
                )
            Suspect.Elizabeth ->
                chatNpc(neutral, "What's so important you need to bother me with then?")
            Suspect.Frank -> {
                chatNpc(neutral, "Good for you. Now what do you want?")
                chatNpc(sad, "...And can you spare me any money? I'm a little short...")
            }
        }
    }

    private suspend fun Dialogue.responsible(suspect: Suspect) {
        when (suspect) {
            Suspect.Anna -> {
                chatNpc(neutral, "It was clearly an intruder.")
                chatPlayer(confused, "Well, I don't think it was.")
                chatNpc(angry, "It was one of our lazy servants then.")
            }
            Suspect.Bob ->
                chatNpc(
                    neutral,
                    "I don't really care as long as no one thinks it's me. Maybe it was that " +
                        "strange poison seller who headed towards the seers village.",
                )
            Suspect.Carol ->
                chatNpc(
                    neutral,
                    "I don't know. I think it's very convenient that you have arrived here so " +
                        "soon after it happened. Maybe it was you.",
                )
            Suspect.David -> {
                chatNpc(neutral, "I don't really know or care. Frankly, the old man deserved to die.")
                chatNpc(
                    neutral,
                    "There was a suspicious red headed man who came to the house the other day " +
                        "selling poison now I think about it. Last I saw he was headed towards " +
                        "the tavern in the Seers village.",
                )
            }
            Suspect.Elizabeth ->
                chatNpc(
                    neutral,
                    "Could have been anyone. The old man was an idiot. He's been asking for it " +
                        "for years.",
                )
            Suspect.Frank -> {
                chatNpc(neutral, "I don't know.")
                chatNpc(
                    neutral,
                    "You don't know how long it takes an inheritance to come through do you? I " +
                        "could really use that money pretty soon...",
                )
            }
        }
    }

    private suspend fun Dialogue.whereabouts(suspect: Suspect) {
        when (suspect) {
            Suspect.Anna ->
                chatNpc(
                    neutral,
                    "In the library. No one else was there so you'll just have to take my word " +
                        "for it.",
                )
            Suspect.Bob -> {
                chatNpc(neutral, "I was walking by myself in the garden.")
                chatPlayer(neutral, "And can anyone vouch for that?")
                chatNpc(neutral, "No. But I was.")
            }
            Suspect.Carol ->
                chatNpc(
                    angry,
                    "Why? Are you accusing me of something? You seem to have a very high opinion " +
                        "of yourself. I was in my room if you must know, alone.",
                )
            Suspect.David ->
                chatNpc(
                    angry,
                    "That is none of your business. Are we finished now, or are you just going " +
                        "to stand there irritating me with your idiotic questions all day?",
                )
            Suspect.Elizabeth -> {
                chatNpc(neutral, "I was out.")
                chatPlayer(neutral, "Care to be any more specific?")
                chatNpc(
                    angry,
                    "Not really. I don't have to justify myself to the likes of you, you know. I " +
                        "know the King personally you know. Now are we finished here?",
                )
            }
            Suspect.Frank -> {
                chatNpc(neutral, "I don't know, somewhere around here probably.")
                chatNpc(
                    angry,
                    "Could you spare me a few coins? I'll be able to pay you double tomorrow it's " +
                        "just there's this poker night tonight in town...",
                )
            }
        }
    }

    private suspend fun Dialogue.thread(suspect: Suspect) {
        val matches = suspect.thread in player.inv
        when (suspect) {
            Suspect.Anna -> {
                mesbox("You show Anna the thread from the study.")
                if (matches) {
                    chatNpc(
                        neutral,
                        "It's some Green thread. It's not exactly uncommon is it? My trousers " +
                            "are made of the same material.",
                    )
                } else {
                    chatNpc(neutral, "Not really, no. Thread is fairly common.")
                }
            }
            Suspect.Bob -> {
                mesbox("You show him the thread you discovered.")
                if (matches) {
                    chatNpc(
                        confused,
                        "It's some red thread. I suppose you think that's some kind of clue? It " +
                            "looks like the material my trousers are made of.",
                    )
                } else {
                    chatNpc(confused, "It's some thread. Great clue. No, really.")
                }
            }
            Suspect.Carol -> {
                mesbox("You show Carol the thread found at the crime scene.")
                if (matches) {
                    chatNpc(
                        neutral,
                        "It's some red thread... it kind of looks like the same material as my " +
                            "trousers. But obviously it's not.",
                    )
                } else {
                    chatNpc(
                        confused,
                        "It's some thread. Sorry, do you have a point here? Or do you just enjoy " +
                            "wasting peoples time?",
                    )
                }
            }
            Suspect.David -> {
                mesbox("You show him the thread you found on the study window.")
                if (matches) {
                    chatNpc(
                        angry,
                        "It's some Green thread, like my trousers are made of. Are you finished? " +
                            "I'm not sure which I dislike more about you, your face or your " +
                            "general bad odour.",
                    )
                } else {
                    chatNpc(confused, "No. Can I go yet? Your face irritates me.")
                }
            }
            Suspect.Elizabeth -> {
                mesbox("You show her the thread from the study window.")
                if (matches) {
                    chatNpc(
                        neutral,
                        "Looks like Blue thread to me. If you can't work that out for yourself I " +
                            "don't hold much hope of you solving this crime.",
                    )
                    chatPlayer(
                        neutral,
                        "It looks a lot like the material your trousers are made of doesn't it?",
                    )
                    chatNpc(angry, "I suppose it does. So what?")
                } else {
                    chatNpc(
                        confused,
                        "It's some thread. You're not very good at this whole investigation " +
                            "thing are you?",
                    )
                }
            }
            Suspect.Frank -> {
                mesbox("Frank examines the thread from the crime scene.")
                if (matches) {
                    chatNpc(
                        neutral,
                        "It kind of looks like the same material as my trousers are made of... " +
                            "same colour anyway. Think it's worth anything? Can I have it? Or " +
                            "just some money?",
                    )
                } else {
                    chatNpc(
                        confused,
                        "It looks like thread to me, but I'm not exactly an expert. Is it worth " +
                            "something? Can I have it? Actually, can you spare me a few gold?",
                    )
                }
            }
        }
    }

    private suspend fun Dialogue.poison(suspect: Suspect) {
        when (suspect) {
            Suspect.Anna ->
                chatNpc(
                    angry,
                    "That useless Gardener Stanford has let his compost heap fester. It's an " +
                        "eyesore to the garden! So I bought some poison from a travelling " +
                        "salesman so that I could kill off some of the wildlife living in it.",
                )
            Suspect.Bob -> {
                chatNpc(confused, "What's it to you anyway?")
                chatNpc(
                    angry,
                    "If you absolutely must know, we had a problem with the beehive in the " +
                        "garden, and as all of our servants are so pathetically useless, I " +
                        "decided I would deal with it myself. So I did.",
                )
            }
            Suspect.Carol -> {
                chatNpc(
                    confused,
                    "I don't see what on earth it has to do with you, but the drain outside was",
                )
                chatNpc(
                    angry,
                    "blocked, and as nobody else here has the intelligence to even unblock a " +
                        "simple drain I felt I had to do it myself.",
                )
            }
            Suspect.David -> {
                chatNpc(
                    angry,
                    "There was a nest of spiders upstairs between the two servants' quarters. " +
                        "Obviously I had to kill them before our pathetic servants whined at my " +
                        "father some more.",
                )
                chatNpc(
                    confused,
                    "Honestly, it's like they expect to be treated like royalty! If I had my way " +
                        "I would fire the whole workshy lot of them!",
                )
            }
            Suspect.Elizabeth -> {
                chatNpc(
                    neutral,
                    "There was a nest of mosquitos under the fountain in the garden, which I " +
                        "killed with poison the other day. You can see for yourself if you're " +
                        "capable of managing that, which I somehow doubt.",
                )
                chatPlayer(angry, "I hate mosquitos!")
                chatNpc(neutral, "Doesn't everyone?")
            }
            Suspect.Frank -> {
                chatNpc(
                    neutral,
                    "Would you like to buy some? I'm kind of strapped for cash right now, I'll " +
                        "sell it to you cheap, it's hardly been used at all.",
                )
                chatNpc(
                    neutral,
                    "I just used a bit to clean that family crest outside up a bit. Do you think " +
                        "I can get much money for the family crest, actually? It's cleaned up a " +
                        "bit now.",
                )
            }
        }
    }

    private suspend fun Dialogue.choose(options: List<Pair<String, Topic>>): Topic =
        when (options.size) {
            2 -> choice2(options[0].first, options[0].second, options[1].first, options[1].second)
            3 ->
                choice3(
                    options[0].first,
                    options[0].second,
                    options[1].first,
                    options[1].second,
                    options[2].first,
                    options[2].second,
                )
            else ->
                choice4(
                    options[0].first,
                    options[0].second,
                    options[1].first,
                    options[1].second,
                    options[2].first,
                    options[2].second,
                    options[3].first,
                    options[3].second,
                )
        }

    private enum class Topic {
        Responsible,
        Whereabouts,
        Thread,
        Poison,
    }
}
