package org.rsmod.content.quest.area.ardougne.regicide

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.BOOK
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_MAKE_BOMB
import org.rsmod.game.entity.Player

/**
 * The two people in Kandarin who can help with the naphtha. Elena, the chemistry-minded one Lord
 * Iorwerth's naphtha answer points the player to, sends them on to the Chemist in Rimmington
 * without being told what it is for; the Chemist, shown the Big Book of Bangs, explains his
 * fractionalising still and lets the player use it.
 */
@Singleton
class RegicideChemistry @Inject constructor(private val regicide: RegicideQuest) {

    /** The Chemist's "Your quest." option: from the book onwards, and after the quest for more naphtha. */
    fun chemistOffers(player: Player): Boolean = regicide.stage(player) >= STAGE_MAKE_BOMB

    suspend fun Dialogue.chemist() {
        chatPlayer(neutral, "Good day. I was hoping you could help me with something. I have a book which mentions the fractional distillation of coal tar. I don't know how to do that though.")
        if (!player.inv.contains(BOOK) && player.chemistChat == 0) {
            chatNpc(quiz, "A book, you say? Bring it with you and I'll gladly take a look.")
            return
        }
        chatNpc(neutral, "Ah, you'll be wanting to use the still then. It's just outside.")
        chatPlayer(quiz, "How do I use it?")
        chatNpc(neutral, "It's quite simple really. You have to get the still heat up very high for it to work. However, if it gets too hot it will burn out and you will have to start over.")
        chatNpc(neutral, "You must also watch the pressure, if it gets too high you will also have to start over. There are valves to control pressure and tar flow. These will also affect the heat of the still.")
        chatPlayer(quiz, "Is that all?")
        player.chemistChat = 1
        chatNpc(neutral, "You'll also need plenty of coal to keep the still hot.")
        chatPlayer(happy, "I see, thanks for the information.")
    }

    /** Elena's part: once, while the bomb is being made and the Chemist has not yet helped. */
    fun elenaOffers(player: Player): Boolean =
        regicide.stage(player) == STAGE_MAKE_BOMB && player.elenaChat == 0 && player.chemistChat == 0

    suspend fun Dialogue.elena() {
        chatPlayer(neutral, "Elena, I could use your help. I need to get something called naphtha out of some coal tar, and I've no idea how.")
        chatNpc(quiz, "Naphtha? That's an odd thing to be after. What do you need it for?")
        chatPlayer(neutral, "I'd rather not say, I'm afraid.")
        chatNpc(neutral, "Fair enough. You'll want a still for that, and not the sort you can knock up at home. The Chemist in Rimmington has a fractionalising still outside his house.")
        player.elenaChat = 1
        chatNpc(neutral, "Tell him I sent you, he's always been helpful to me.")
        chatPlayer(happy, "Thanks Elena.")
    }
}
