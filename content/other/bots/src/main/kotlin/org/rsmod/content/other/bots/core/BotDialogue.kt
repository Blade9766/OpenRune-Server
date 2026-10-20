package org.rsmod.content.other.bots.core

/** What an open dialogue shows the bot. */
sealed interface DialogueScreen {
    /** A chat or message page; [button] is its click-here-to-continue component. */
    data class Page(val button: String) : DialogueScreen

    /** A chat menu; [options] are the lines it shows, top first. */
    data class Choice(val options: List<String>) : DialogueScreen
}

sealed interface DialogueReply {
    data class Continue(val button: String) : DialogueReply

    /** The 1-based line of the chat menu, as its pause-button subcomponent. */
    data class Pick(val option: Int) : DialogueReply

    data class GiveUp(val reason: String) : DialogueReply
}

/**
 * One conversation a bot holds on a route step: it continues every page and, on each chat menu,
 * picks the first of [wanted] the menu offers. A menu offering none of them, or a conversation
 * longer than [maxScreens], makes it give up rather than click something it was not told to.
 */
class BotDialogue(private val wanted: List<String>, private val maxScreens: Int = MAX_SCREENS) {
    private var screens = 0

    fun reply(screen: DialogueScreen): DialogueReply {
        if (++screens > maxScreens) {
            return DialogueReply.GiveUp("the conversation ran past $maxScreens screens")
        }
        return when (screen) {
            is DialogueScreen.Page -> DialogueReply.Continue(screen.button)
            is DialogueScreen.Choice -> pick(screen.options)
        }
    }

    private fun pick(options: List<String>): DialogueReply {
        if (options.isEmpty()) {
            return DialogueReply.GiveUp("a chat menu opened but its options are unknown")
        }
        val shown = options.map(::normalize)
        for (line in wanted.map(::normalize).filter { it.isNotEmpty() }) {
            val index = shown.indexOf(line).takeIf { it >= 0 } ?: shown.indexOfFirst { it.startsWith(line) }
            if (index >= 0) {
                return DialogueReply.Pick(index + 1)
            }
        }
        return DialogueReply.GiveUp("no wanted option among: ${options.joinToString(" | ")}")
    }

    companion object {
        const val MAX_SCREENS = 40

        private val TAG = Regex("<[^>]*>")
        private val SPACE = Regex("\\s+")

        /** Option text as compared: tags dropped, case and spacing ignored, end punctuation trimmed. */
        fun normalize(text: String): String =
            text.replace(TAG, " ").replace(SPACE, " ").trim().trimEnd('.', '!', '?').trim().lowercase()
    }
}
