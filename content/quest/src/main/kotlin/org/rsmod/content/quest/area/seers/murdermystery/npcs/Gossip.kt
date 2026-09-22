package org.rsmod.content.quest.area.seers.murdermystery.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.seers.murdermystery.MurderMysteryQuest
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** The gossip outside the Sinclair Mansion gates, who knows every family secret and a few hints. */
class Gossip @Inject constructor(private val murder: MurderMysteryQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1("npc.gossipy_man") { startDialogue(it.npc) { gossip() } }
    }

    private suspend fun Dialogue.gossip() {
        when {
            murder.isComplete(player) ->
                chatNpc(quiz, "I heard you solved the murder. Was I of any help to you at all?")
            murder.isInvestigating(player) -> investigating()
            else ->
                chatNpc(
                    neutral,
                    "There's some kind of commotion up at the Sinclair place I hear. Not " +
                        "surprising all things considered.",
                )
        }
    }

    private suspend fun Dialogue.investigating() {
        chatPlayer(neutral, "I'm investigating the murder up at the Sinclair place.")
        chatNpc(neutral, "Murder is it? Well, I'm not really surprised...")
        when (
            choice4(
                "What can you tell me about the Sinclairs?",
                0,
                "Who do you think was responsible?",
                1,
                "I think the butler did it.",
                2,
                "I am so confused about who did it.",
                3,
            )
        ) {
            0 -> sinclairs()
            1 -> {
                chatPlayer(neutral, "Who do you think was responsible?")
                chatNpc(
                    neutral,
                    "Well, I guess it could have been an intruder, but with that big guard dog " +
                        "of theirs I seriously doubt it. I suspect it was someone closer to " +
                        "home...",
                )
                chatNpc(
                    neutral,
                    "Especially as I heard that the poison salesman in the Seers' village made a " +
                        "big sale to one of the family the other day.",
                )
            }
            2 -> {
                chatPlayer(neutral, "I think the butler did it.")
                chatNpc(
                    neutral,
                    "And I think you've been reading too many cheap detective novels. Hobbes is " +
                        "kind of uptight, but his loyalty to old Lord Sinclair is beyond question.",
                )
            }
            else -> hint()
        }
    }

    private suspend fun Dialogue.sinclairs() {
        chatPlayer(neutral, "What can you tell me about the Sinclairs?")
        chatNpc(neutral, "Well, what do you want to know?")
        when (
            choice4(
                "Tell me about Lord Sinclair.",
                0,
                "Why do the Sinclairs live so far from town?",
                1,
                "What can you tell me about his sons?",
                2,
                "What can you tell me about his daughters?",
                3,
            )
        ) {
            0 -> {
                chatPlayer(neutral, "Tell me about Lord Sinclair.")
                chatNpc(
                    neutral,
                    "Old Lord Sinclair was a great man with a lot of respect in these parts. " +
                        "More than his worthless children have anyway.",
                )
                chatPlayer(quiz, "His children? They have something to gain by his death?")
                chatNpc(neutral, "Yes. You could say that. Not that I'm one to gossip.")
            }
            1 -> {
                chatPlayer(neutral, "Why do the Sinclairs live so far from town?")
                chatNpc(
                    neutral,
                    "Well, they used to live in the big castle, but old Lord Sinclair gave it up " +
                        "so that those strange knights could live there instead. So the king " +
                        "built him a new house to the North.",
                )
                chatNpc(
                    neutral,
                    "It's more cramped than his old place, but he seemed to like it. His " +
                        "children were furious at him for doing it though!",
                )
            }
            2 -> sons()
            else -> daughters()
        }
    }

    private suspend fun Dialogue.sons() {
        chatPlayer(neutral, "What can you tell me about his sons?")
        chatNpc(
            neutral,
            "His sons eh? They all have their own skeletons in their cupboards. You'll have to be " +
                "more specific. Who are you interested in exactly?",
        )
        when (choice3("Tell me about Bob.", 0, "Tell me about David.", 1, "Tell me about Frank.", 2)) {
            0 -> {
                chatPlayer(neutral, "Tell me about Bob.")
                chatNpc(
                    neutral,
                    "Bob is an odd character indeed... I'm not one to gossip, but I heard Bob is " +
                        "addicted to Tea. He can't make",
                )
                chatNpc(neutral, "it through the day without having at least 20 cups!")
                chatNpc(
                    neutral,
                    "You might not think that's such a big thing, but he has spent thousands of " +
                        "gold to feed his habit!",
                )
                chatNpc(
                    neutral,
                    "At one point he stole a lot of silverware from the kitchen and pawned it " +
                        "just so he could afford to buy his daily tea allowance.",
                )
                chatNpc(
                    shocked,
                    "If his father ever found out, he would be in so much trouble... he might " +
                        "even get disowned!",
                )
            }
            1 -> {
                chatPlayer(neutral, "Tell me about David.")
                chatNpc(
                    neutral,
                    "David... oh David... not many people know this, but David really has an " +
                        "anger problem. He's always screaming and shouting",
                )
                chatNpc(
                    neutral,
                    "at the household servants when he's angry, and they live in a state of " +
                        "fear, always walking on eggshells around him, but none of them have the " +
                        "courage",
                )
                chatNpc(
                    neutral,
                    "to talk to his father about his behaviour. If they did, Lord Sinclair would " +
                        "almost certainly",
                )
                chatNpc(
                    neutral,
                    "kick him out of the house, as some of the servants have been there longer " +
                        "than he has, and he definitely has no right to treat them like he " +
                        "does... but I'm not one to gossip about people.",
                )
            }
            else -> {
                chatPlayer(neutral, "Tell me about Frank.")
                chatNpc(
                    neutral,
                    "I'm not one to talk ill of people behind their back, but Frank is a real " +
                        "piece of work. He is an absolutely terrible gambler... he can't pass 2 " +
                        "dogs in the street without putting a bet on which one will bark first!",
                )
                chatNpc(
                    neutral,
                    "He has already squandered all of his allowance, and I heard he had stolen a " +
                        "number of paintings of his fathers to sell to try and cover his debts, " +
                        "but he still owes a lot of",
                )
                chatNpc(
                    neutral,
                    "people a lot of money. If his father ever found out, he would stop his " +
                        "income, and then he would be in serious trouble!",
                )
            }
        }
    }

    private suspend fun Dialogue.daughters() {
        chatPlayer(neutral, "What can you tell me about his daughters?")
        chatNpc(
            neutral,
            "His daughters eh? They're all nasty pieces of work. Which of them specifically did " +
                "you want to know about?",
        )
        when (
            choice3("Tell me about Anna.", 0, "Tell me about Carol.", 1, "Tell me about Elizabeth.", 2)
        ) {
            0 -> {
                chatPlayer(neutral, "Tell me about Anna.")
                chatNpc(neutral, "Anna... ah yes... Anna has 2 great loves:")
                chatNpc(
                    neutral,
                    "Sewing and gardening. But one thing she has kept secret is that she once " +
                        "had an affair with Stanford the gardener, and tried to get him fired " +
                        "when they broke up,",
                )
                chatNpc(
                    neutral,
                    "by killing all of the flowers in the garden. If her father ever found out " +
                        "she had done that he would be so furious he would probably disown her.",
                )
            }
            1 -> {
                chatPlayer(neutral, "Tell me about Carol.")
                chatNpc(
                    neutral,
                    "Oh Carol... she is such a fool. You didn't hear this from me, but I heard a " +
                        "while ago she was conned out of a lot of money by a travelling salesman " +
                        "who sold her a box full",
                )
                chatNpc(
                    neutral,
                    "of beans by telling her they were magic. But they weren't. She sold some " +
                        "rare books from the library to cover her debts, but",
                )
                chatNpc(
                    shocked,
                    "her father would be incredibly annoyed if he ever found out - he might even " +
                        "throw her out of the house!",
                )
            }
            else -> {
                chatPlayer(neutral, "Tell me about Elizabeth.")
                chatNpc(
                    neutral,
                    "Elizabeth? Elizabeth has a strange problem... She cannot help herself, but " +
                        "is always stealing small objects - it's pretty sad that she is rich " +
                        "enough to afford to buy things, but would rather steal them instead.",
                )
                chatNpc(
                    neutral,
                    "Now, I don't want to spread stories, but I heard she even stole a silver " +
                        "needle from her father that had great sentimental value for him.",
                )
                chatNpc(
                    sad,
                    "He was devastated when it was lost, and cried for a week thinking he had " +
                        "lost it!",
                )
                chatNpc(
                    shocked,
                    "If he ever found out that it was her who had stolen it he would go " +
                        "absolutely mental, maybe even disowning her!",
                )
            }
        }
    }

    private suspend fun Dialogue.hint() {
        chatPlayer(sad, "I am so confused about who did it...")
        chatPlayer(confused, "Think you could give me any hints?")
        when (access.random.of(5)) {
            0 -> {
                chatNpc(
                    neutral,
                    "Well, I don't know if it's related, but I heard from that Poison Salesman " +
                        "in town that he sold some poison to one of the Sinclair family",
                )
                chatNpc(
                    neutral,
                    "the other day. I don't think he has any stock left now though...",
                )
            }
            1 ->
                chatNpc(
                    neutral,
                    "Well, I don't know how much help this is, but I heard that their guard dog " +
                        "will bark loudly at anyone it doesn't recognise. Maybe you should find " +
                        "out if anyone heard anything suspicious?",
                )
            2 -> {
                chatNpc(
                    neutral,
                    "My father used to be in the guard. He always wrote himself notes on a piece " +
                        "of paper so he could keep track of information easily.",
                )
                chatNpc(
                    neutral,
                    "Maybe you should try that? Don't forget to thank me if I help you solve the " +
                        "case!",
                )
            }
            3 -> {
                chatNpc(
                    neutral,
                    "Well, this might be of some help to you. My father was in the guards when " +
                        "he was younger,",
                )
                chatNpc(
                    neutral,
                    "and he always said that there isn't a crime that can't be solved through " +
                        "careful examination of the crime scene and all surrounding areas.",
                )
            }
            else -> {
                chatNpc(
                    neutral,
                    "I don't know how much help this is to you but my dad was in the guard once, " +
                        "and he told me that the marks on your hands are totally unique. He " +
                        "called them 'finger prints'.",
                )
                chatNpc(
                    neutral,
                    "He said you can find them easily on any shiny metallic surface by using a " +
                        "fine powder to mark out where the marks are, and then using some sticky " +
                        "paper to lift the print from the object.",
                )
                chatNpc(
                    neutral,
                    "I bet if you could find a way to get everyone's 'finger prints' you could " +
                        "solve the crime pretty easily!",
                )
            }
        }
    }
}
