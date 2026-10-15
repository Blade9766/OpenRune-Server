package org.rsmod.content.quest.area.lunarisle.dreammentor

import org.rsmod.api.player.dialogue.Dialogue

/**
 * The conversations that lift Cyrisus' spirit. Each talk offers one set of four things to say;
 * only one of them, followed by the encouraging answer, cheers him up. The set moves on after
 * every talk, cycling through [SETS] in `varbit.dream_spirit_ques`.
 */
internal object CyrisusSpirit {
    private class Topic(val option: String, val talk: suspend Dialogue.() -> Boolean)

    /** Runs the next conversation; true when the player said the right thing. */
    suspend fun Dialogue.converse(): Boolean {
        val index = player.dreamSpiritQuestion % SETS.size
        player.dreamSpiritQuestion = (index + 1) % SETS.size
        val topics = SETS[index]
        val pick = choice4(topics[0].option, 0, topics[1].option, 1, topics[2].option, 2, topics[3].option, 3)
        return topics[pick].talk(this)
    }

    private fun gloomy(option: String, vararg lines: Pair<Boolean, String>) =
        Topic(option) {
            chatPlayer(neutral, option)
            for ((fromPlayer, line) in lines) {
                if (fromPlayer) chatPlayer(neutral, line) else chatNpc(sad, line)
            }
            false
        }

    private fun hopeful(option: String, reply: String, good: Pair<String, String>, bad: Pair<String, String>) =
        Topic(option) {
            chatPlayer(happy, option)
            chatNpc(quiz, reply)
            val (goodLine, goodReply) = good
            val (badLine, badReply) = bad
            val cheered = choice2(goodLine, true, badLine, false)
            if (cheered) {
                chatPlayer(happy, goodLine)
                chatNpc(happy, goodReply)
            } else {
                chatPlayer(neutral, badLine)
                chatNpc(sad, badReply)
            }
            cheered
        }

    private val SETS: List<List<Topic>> =
        listOf(
            listOf(
                hopeful(
                    "Try not to worry.",
                    "You really believe this will all turn out fine?",
                    "I'm certain of it." to "That does make me feel a little better.",
                    "Honestly? Probably not." to "I knew it. I'm finished.",
                ),
                gloomy("I can't stay long.", false to "Please, don't leave me down here on my own!"),
                gloomy("It's very gloomy in here.", false to "Who knows what's lurking in the dark? I'm terrified."),
                gloomy(
                    "Something big must have dug this cave.",
                    false to "Something big? Where is it now?",
                    true to "Erm... forget I said anything.",
                ),
            ),
            listOf(
                gloomy(
                    "Did you hear something?",
                    false to "What? What was it? They've found us!",
                    true to "No, no. It was nothing. My mistake.",
                ),
                hopeful(
                    "You're looking much better.",
                    "You're just saying that. In what way?",
                    "You sound stronger, and there's colour in your face." to "Really? Then I might just get out of here.",
                    "Well, you're not as pale as a corpse any more." to "That's... not very reassuring.",
                ),
                gloomy("How long have you been lying here?", false to "Days, maybe. I can't remember any more."),
                gloomy("This cave makes my skin crawl.", false to "Mine too. You won't go, will you?"),
            ),
            listOf(
                gloomy("In a good mood today?", false to "Not really, no."),
                hopeful(
                    "What will you do first when you're out?",
                    "Wash, find some clean clothes, eat a proper meal...",
                    "That's the way to think!" to "You're right. I can almost taste it.",
                    "Don't get your hopes up just yet." to "Sorry. I got carried away.",
                ),
                gloomy("Why so glum?", false to "You'd be glum too if you were about to die."),
                gloomy("Chin up, things could be worse!", false to "Worse? I could be dead. That's about it."),
            ),
            listOf(
                gloomy(
                    "Those cuts on your legs look nasty.",
                    false to "Cuts? What cuts? Oh no. Oh no, no, no.",
                    true to "Calm down!",
                ),
                gloomy("I'm feeling a bit unwell myself.", false to "You can't be ill! You're all I've got!"),
                hopeful(
                    "You seem like a decent sort.",
                    "Do you mean that? Why tell me now?",
                    "Because it's true, that's all." to "Thank you. You're not so bad yourself.",
                    "Better say it before it's too late." to "Oh. I see. Right.",
                ),
                gloomy("There are suqahs everywhere on this island.", false to "I should never have set foot here."),
            ),
            listOf(
                gloomy(
                    "Maybe adventuring isn't for you.",
                    false to "What do you mean by that?",
                    true to "Well, look at the state of you.",
                    false to "Thanks a lot.",
                ),
                hopeful(
                    "When we're out, the first drink is on me!",
                    "Really? What are we drinking?",
                    "Anything you like, anywhere you like." to "That's very kind. I'll hold you to it.",
                    "Something cheap. Helping you isn't free, you know." to "Oh. Fair enough, I suppose.",
                ),
                gloomy("You don't smell too fresh.", false to "I'm so sorry. This is mortifying."),
                gloomy("We won't be out of here for a while.", false to "I don't think I can bear much more of this."),
            ),
            listOf(
                hopeful(
                    "I'm amazed you crawled this far.",
                    "It wasn't easy, I can tell you.",
                    "I'd have given up long before now." to "I wasn't going to give up. I wanted to live.",
                    "Did you not think about stopping?" to "I suppose part of me did.",
                ),
                gloomy("Well done on not dying!", false to "Er. Thanks?"),
                gloomy("Lucky no suqahs followed you down.", false to "Now I'm wondering why they didn't."),
                gloomy("Odd that nothing lives down here.", false to "Is that odd? Should I be worried?"),
            ),
            listOf(
                gloomy("I bet you usually look smarter.", false to "I know I look a mess. Sorry."),
                gloomy("It's awfully damp in here.", false to "Wonderful. Something else to make me worse."),
                gloomy("Spider!", false to "Where?!", true to "Only joking.", false to "Don't do that!"),
                hopeful(
                    "You'll get through this.",
                    "How can you be so sure?",
                    "Think of all the places still waiting for you!" to "You're right. There's so much I haven't seen.",
                    "Because if you don't, you've failed." to "Failed... I'll try. Maybe when I'm stronger.",
                ),
            ),
            listOf(
                gloomy("I really ought to go.", false to "Please don't. Please. I need you."),
                hopeful(
                    "What are you going to do after this?",
                    "I hadn't thought that far. What do you think?",
                    "Come travelling with me!" to "You'd do that? I'd love to!",
                    "If I were you, I'd stay indoors." to "Maybe you're right.",
                ),
                gloomy("Any chance of a reward for this?", false to "I'd pay you if I had anything left."),
                gloomy("It's freezing down here.", false to "I don't suppose you have a blanket?"),
            ),
            listOf(
                hopeful(
                    "Good job I came along, eh?",
                    "It certainly is.",
                    "Not that I'm boasting." to "Boast all you like. You've earned it.",
                    "I'm just brilliant, really." to "Careful. That sort of talk comes back to bite you.",
                ),
                gloomy("What would you do without me?", false to "Die, I imagine."),
                gloomy("Funny I happened to find you.", false to "Lucky for me, I suppose."),
                gloomy("You've no idea how long you've been here?", false to "None at all. It feels like forever."),
            ),
            listOf(
                gloomy("Do you have any family?", false to "Not nearby. Nobody would come looking for me."),
                gloomy("Have you eaten anything strange down here?", false to "Only my own boots, very nearly."),
                hopeful(
                    "What do you enjoy doing?",
                    "Fishing, believe it or not.",
                    "I love fishing too!" to "Then we'll go fishing together one day!",
                    "Fishing? That's a bit dull." to "Oh. I thought it was relaxing.",
                ),
                gloomy("Does it hurt much?", false to "Only when I breathe."),
            ),
        )
}
