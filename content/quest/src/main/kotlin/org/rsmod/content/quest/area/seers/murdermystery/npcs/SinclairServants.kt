package org.rsmod.content.quest.area.seers.murdermystery.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.seers.murdermystery.MurderMysteryQuest
import org.rsmod.content.quest.area.seers.murdermystery.MurderMysteryQuest.Companion.POISON_ASKED_SALESMAN
import org.rsmod.content.quest.area.seers.murdermystery.murderPoisonProgress
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Sinclair household staff. Each one blames a different member of the family and, once the
 * poison salesman has been questioned, knows what one of them said they bought poison for.
 */
class SinclairServants @Inject constructor(private val murder: MurderMysteryQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        for (servant in Servant.entries) {
            onOpNpc1(servant.npc) { startDialogue(it.npc) { talk(servant) } }
        }
    }

    private suspend fun Dialogue.talk(servant: Servant) {
        when {
            murder.isComplete(player) ->
                chatNpc(happy, "Thank you for all your help in solving the murder.")
            murder.isInvestigating(player) -> interview(servant)
            else -> notStarted(servant)
        }
    }

    private suspend fun Dialogue.notStarted(servant: Servant) {
        when (servant) {
            Servant.Donovan -> chatNpc(neutral, "I have no interest in talking to gawkers.")
            Servant.Pierre -> chatNpc(neutral, "The guards told me not to talk to anyone.")
            Servant.Hobbes -> chatNpc(angry, "This is private property! Please leave!")
            Servant.Louisa ->
                chatNpc(neutral, "I'm far too upset to talk to random people right now.")
            Servant.Mary -> access.mes("She is ignoring you.")
            Servant.Stanford -> chatNpc(angry, "Have you no shame? We are all grieving at the moment.")
        }
    }

    private suspend fun Dialogue.interview(servant: Servant) {
        chatPlayer(neutral, "I'm here to help the guards with their investigation.")
        chatNpc(if (servant == Servant.Mary) confused else neutral, "How can I help?")
        val topic =
            if (player.murderPoisonProgress >= POISON_ASKED_SALESMAN) {
                choice4(
                    "Who do you think is responsible?",
                    Topic.Responsible,
                    "Where were you at the time of the murder?",
                    Topic.Whereabouts,
                    "Did you hear any suspicious noises at all?",
                    Topic.Noises,
                    "Do you know why so much poison was bought recently?",
                    Topic.Poison,
                )
            } else {
                choice3(
                    "Who do you think is responsible?",
                    Topic.Responsible,
                    "Where were you at the time of the murder?",
                    Topic.Whereabouts,
                    "Did you hear any suspicious noises at all?",
                    Topic.Noises,
                )
            }
        when (topic) {
            Topic.Responsible -> {
                chatPlayer(neutral, "Who do you think is responsible?")
                responsible(servant)
            }
            Topic.Whereabouts -> {
                chatPlayer(confused, "Where were you at the time of the murder?")
                whereabouts(servant)
            }
            Topic.Noises -> {
                chatPlayer(neutral, "Did you hear any suspicious noises at all?")
                noises(servant)
            }
            Topic.Poison -> {
                chatPlayer(neutral, "Do you know why so much poison was bought recently?")
                poison(servant)
            }
        }
    }

    private suspend fun Dialogue.responsible(servant: Servant) {
        when (servant) {
            Servant.Donovan -> {
                chatNpc(
                    neutral,
                    "Oh... I really couldn't say. I wouldn't really want to point any fingers at " +
                        "anybody. If I had to make a guess I'd have to say it was probably Bob " +
                        "though.",
                )
                chatNpc(
                    neutral,
                    "I saw him arguing with Lord Sinclair about some missing silverware from the " +
                        "Kitchen. It was a very heated argument.",
                )
            }
            Servant.Pierre -> {
                chatNpc(confused, "Honestly? I think it was Carol.")
                chatNpc(
                    neutral,
                    "I saw her in a huge argument with Lord Sinclair in the library the other " +
                        "day. It was something to do with stolen books. She definitely seemed " +
                        "upset enough to have done it afterwards.",
                )
            }
            Servant.Hobbes -> {
                chatNpc(
                    neutral,
                    "Well, in my considered opinion it must be David. The man is nothing more " +
                        "than a bully And I happen to know that poor Lord Sinclair and David had " +
                        "a massive argument in the living",
                )
                chatNpc(
                    neutral,
                    "room about the way he treats the staff, the other day. I did not intend to " +
                        "overhear their conversation, but they were shouting so loudly I could " +
                        "not help but Overhear it. David definitely used the words",
                )
                chatNpc(
                    confused,
                    "'I am going to kill you!' as well. I think he should be the prime suspect. " +
                        "He has a nasty temper that one.",
                )
            }
            Servant.Louisa -> {
                chatNpc(neutral, "Elizabeth.")
                chatNpc(
                    neutral,
                    "Her father confronted her about her constant petty thieving, and was " +
                        "devastated to find she had stolen a silver needle which had meant a lot " +
                        "to him.",
                )
                chatNpc(sad, "You could hear their argument from Lumbridge!")
            }
            Servant.Mary -> {
                chatNpc(
                    neutral,
                    "Oh I don't know... Frank was acting kind of funny... After that big " +
                        "argument him and the Lord had the other day by the beehive... so",
                )
                chatNpc(
                    confused,
                    "I guess maybe him... but it's really scary to think someone here might " +
                        "have been responsible. I actually hope it was a burglar...",
                )
            }
            Servant.Stanford -> {
                chatNpc(
                    angry,
                    "It was Anna. She is seriously unbalanced. She trashed the garden once then " +
                        "tried to blame it on me! I bet it was her. It's just the kind of thing " +
                        "she'd do!",
                )
                chatNpc(
                    neutral,
                    "She really hates me and was arguing with Lord Sinclair about trashing the " +
                        "garden a few days ago.",
                )
            }
        }
    }

    private suspend fun Dialogue.whereabouts(servant: Servant) {
        when (servant) {
            Servant.Donovan ->
                chatNpc(
                    confused,
                    "Me? I was sound asleep here in the servants Quarters. It's very hard work " +
                        "as a handyman around here, There's always something to do!",
                )
            Servant.Pierre ->
                chatNpc(
                    neutral,
                    "I was in town at the Inn. When I got back the house was swarming with " +
                        "guards who told me what had happened. Sorry.",
                )
            Servant.Hobbes ->
                chatNpc(
                    neutral,
                    "I was assisting the cook with the evening meal. I gave Mary His Lordships' " +
                        "dinner, and sent her to take it to him, then heard the scream as she " +
                        "found the body.",
                )
            Servant.Louisa ->
                chatNpc(
                    confused,
                    "I was right here with Hobbes and Mary. You can't suspect me surely!",
                )
            Servant.Mary -> {
                chatNpc(
                    sad,
                    "I was with Hobbes and Louisa in the Kitchen helping to prepare Lord " +
                        "Sinclair's meal, and then when I took it to his study... I saw... oh, " +
                        "it was horrible... he was....",
                )
                mesbox(
                    "She seems to be on the verge of crying. You decide not to push her anymore " +
                        "for details.",
                )
            }
            Servant.Stanford ->
                chatNpc(
                    neutral,
                    "Right here, by my little shed. It's very cosy to sit and think in.",
                )
        }
    }

    private suspend fun Dialogue.noises(servant: Servant) {
        when (servant) {
            Servant.Donovan -> {
                chatNpc(confused, "Hmmm..... No, I didn't, but I sleep very soundly at night.")
                chatPlayer(
                    confused,
                    "So you didn't hear any sounds of a struggle or any barking from the guard " +
                        "dog next to his study window?",
                )
                chatNpc(
                    confused,
                    "Now you mention it, no. It is odd I didn't hear anything like that. But I " +
                        "do sleep very soundly as I said and wouldn't necessarily have heard it " +
                        "if there was any such noise.",
                )
            }
            Servant.Pierre -> {
                chatNpc(confused, "Well, like what?")
                chatPlayer(neutral, "Any sounds of a struggle with Lord Sinclair?")
                chatNpc(confused, "No, I don't remember hearing anything like that.")
                chatPlayer(neutral, "How about the guard dog barking at all?")
                chatNpc(
                    neutral,
                    "I hear him bark all the time. It's one of his favorite things to do. I " +
                        "can't say I did the night of the murder though as I wasn't close enough " +
                        "to hear either way.",
                )
            }
            Servant.Hobbes -> {
                chatNpc(confused, "How do you mean 'suspicious'?")
                chatPlayer(neutral, "Any sounds of a struggle with Lord Sinclair?")
                chatNpc(confused, "No, I definitely didn't hear anything like that.")
                chatPlayer(neutral, "How about the guard dog barking at all?")
                chatNpc(
                    neutral,
                    "You know, now you come to mention it I don't believe I did. I suppose that " +
                        "is Proof enough that it could not have been an intruder who is " +
                        "responsible.",
                )
            }
            Servant.Louisa -> {
                chatNpc(neutral, "Suspicious? What do you mean suspicious?")
                chatPlayer(neutral, "Any sounds of a struggle with an intruder for example?")
                chatNpc(confused, "No, I'm sure I don't recall any such thing.")
                chatPlayer(neutral, "How about the guard dog barking at an intruder?")
                chatNpc(
                    neutral,
                    "No, I didn't. If you don't have anything else to ask can You go and leave " +
                        "me alone now? I have a lot of cooking to do for this evening.",
                )
            }
            Servant.Mary -> {
                chatNpc(neutral, "I don't really remember hearing anything out of the ordinary.")
                chatPlayer(neutral, "No sounds of a struggle then?")
                chatNpc(confused, "No, I don't remember hearing anything like that.")
                chatPlayer(neutral, "How about the guard dog barking?")
                chatNpc(
                    neutral,
                    "Oh that horrible dog is always barking at nothing but I don't think I did...",
                )
            }
            Servant.Stanford -> {
                chatNpc(confused, "Not that I remember.")
                chatPlayer(
                    confused,
                    "So no sounds of a struggle between Lord Sinclair and an intruder?",
                )
                chatNpc(neutral, "Not to the best of my recollection.")
                chatPlayer(neutral, "How about the guard dog barking?")
                chatNpc(neutral, "Not that I can recall.")
            }
        }
    }

    private suspend fun Dialogue.poison(servant: Servant) {
        when (servant) {
            Servant.Donovan -> {
                chatNpc(
                    neutral,
                    "Well, I do know Frank bought some poison recently to clean the family crest " +
                        "that's outside.",
                )
                chatNpc(
                    neutral,
                    "It's very old and rusty, and I couldn't clean it myself, so he said he " +
                        "would buy some cleaner and clean it himself. He probably just got some " +
                        "from that Poison Salesman who came to the door the other day...",
                )
                chatNpc(neutral, "You'd really have to ask him though.")
            }
            Servant.Pierre -> {
                chatNpc(
                    neutral,
                    "Well, I know David said that he was going to do something about the " +
                        "spiders' nest that's between the two servants' quarters upstairs.",
                )
                chatNpc(
                    neutral,
                    "He made a big deal about it to Mary the Maid, calling her useless and " +
                        "incompetent. I felt quite sorry for her actually. You'd really have to " +
                        "ask him though.",
                )
            }
            Servant.Hobbes -> {
                chatNpc(
                    neutral,
                    "Well, I do know that Elizabeth was extremely annoyed by the mosquito nest " +
                        "under the fountain in the garden, and was going to do something about " +
                        "it. I suspect any poison she bought would have been",
                )
                chatNpc(neutral, "enough to get rid of it. A Good job too, I hate mosquitos.")
                chatPlayer(angry, "Yeah, so do I.")
                chatNpc(neutral, "You'd really have to ask her though.")
            }
            Servant.Louisa -> {
                chatNpc(
                    neutral,
                    "I told Carol to buy some from that strange poison salesman and clean the " +
                        "drains before they began to smell any worse. She was the one who " +
                        "blocked them in the first place with a load",
                )
                chatNpc(
                    neutral,
                    "of beans that she bought for some reason. There were far too many to eat, " +
                        "and they were almost rotten when she bought them anyway! You'd really " +
                        "have to ask her though.",
                )
            }
            Servant.Mary -> {
                chatNpc(
                    neutral,
                    "I overheard Anna saying to Stanford that if he didn't do something about " +
                        "the state of his compost heap, she was going to.",
                )
                chatNpc(
                    neutral,
                    "She really doesn't get on well with Stanford. I really have no idea why. " +
                        "You'd really have to ask her though.",
                )
            }
            Servant.Stanford -> {
                chatNpc(
                    neutral,
                    "Well, Bob mentioned to me the other day he wanted to get rid of the bees in " +
                        "that hive over there. I think I saw him buying poison",
                )
                chatNpc(
                    neutral,
                    "from that poison salesman the other day. I assume it was to sort out those " +
                        "bees. You'd really have to ask him though.",
                )
            }
        }
    }

    private enum class Servant(val npc: String) {
        Donovan("npc.donovan_the_family_handyman"),
        Pierre("npc.pierre_the_family_dog_handler"),
        Hobbes("npc.hobbes_the_butler"),
        Louisa("npc.louisa_the_cook"),
        Mary("npc.mary_the_maid"),
        Stanford("npc.stanford_the_gardener"),
    }

    private enum class Topic {
        Responsible,
        Whereabouts,
        Noises,
        Poison,
    }
}
