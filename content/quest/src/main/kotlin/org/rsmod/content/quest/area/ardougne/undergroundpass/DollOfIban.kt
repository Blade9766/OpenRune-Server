package org.rsmod.content.quest.area.ardougne.undergroundpass

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeldU
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.ASHES
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.DOLL
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.DOVE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.IBAN_BOOK
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SHADOW
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Doll of Iban, and Kardia's journal that explains it.
 *
 * Kardia raised Iban by smearing his likeness with the four elements of his being: his flesh, his
 * blood, his shadow and his conscience. The same four, on the same doll, are what can destroy him.
 * The dove's bones, the dark liquid and the ashes are rubbed into the doll by hand; Kalrag's blood
 * goes on where the spider falls.
 */
@Singleton
class DollOfIban @Inject constructor() : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeld1(DOLL) { searchDoll() }
        onOpHeldU(DOLL, DOVE) { crumbleDove() }
        onOpHeldU(DOLL, SHADOW) { pourShadow() }
        onOpHeldU(DOLL, ASHES) { rubInAshes() }
        onOpHeld1(IBAN_BOOK) { readJournal() }
    }

    private suspend fun ProtectedAccess.searchDoll() {
        stopAction()
        mes("You carefully search the doll...")
        delay(1)
        if (player.venomOnDoll == 1) {
            mes("Blood has been smeared onto the doll.")
        }
        if (player.doveOnDoll == 1) {
            mes("Crushed bones have been spread onto the doll.")
        }
        if (player.ashesOnDoll == 1) {
            mes("Burnt ash has been smeared onto the doll.")
        }
        if (player.shadowOnDoll == 1) {
            mes("Dark liquid has been smeared onto the doll.")
        }
        mes("The doll is made from old wood and cloth.")
    }

    private suspend fun ProtectedAccess.crumbleDove() {
        delay(1)
        mes("You crumble the dove's skeleton into dust...")
        delay(1)
        if (invDel(inv, DOVE).failure) {
            return
        }
        UndergroundPassQuest.setVarBit(player, "varbit.upass_dove_on_doll", 1)
        mes("...and rub it into the doll.")
    }

    private suspend fun ProtectedAccess.pourShadow() {
        delay(1)
        mes("You pour the strange liquid over the doll...")
        delay(1)
        if (invDel(inv, SHADOW).failure) {
            return
        }
        UndergroundPassQuest.setVarBit(player, "varbit.upass_shadow_on_doll", 1)
        mes("...It seeps into the cloth.")
    }

    private suspend fun ProtectedAccess.rubInAshes() {
        delay(ASHES_TICKS)
        if (invDel(inv, ASHES).failure) {
            return
        }
        UndergroundPassQuest.setVarBit(player, "varbit.upass_ashes_on_doll", 1)
        mes("You rub the ashes into the doll.")
    }

    /** Kardia's own account of how she made Iban, chapter by chapter. */
    private suspend fun ProtectedAccess.readJournal() {
        stopAction()
        mes("The journal is old and covered in dust...")
        delay(2)
        mes("inside are several chapters...")
        player.readIbanBook = 1
        val chapter =
            choice4(
                "Introduction.", 1,
                "Iban.", 2,
                "The Resurrection.", 3,
                "The Four Elements.", 4,
                title = "Pick a chapter...",
            )
        val pages =
            when (chapter) {
                1 -> INTRODUCTION
                2 -> HISTORY
                3 -> RESURRECTION
                else -> elementPages()
            }
        for (page in pages) {
            mesbox(page)
        }
    }

    private suspend fun ProtectedAccess.elementPages(): List<String> {
        val element =
            choice4(
                "Flesh.", 1,
                "Blood.", 2,
                "Shadow.", 3,
                "Conscience.", 4,
                title = "The 4 Elements:",
            )
        return when (element) {
            1 -> FLESH
            2 -> BLOOD
            3 -> SHADOW_PAGES
            else -> CONSCIENCE
        }
    }

    private companion object {
        const val ASHES_TICKS = 7

        val INTRODUCTION =
            listOf(
                "Gather round, all ye followers of the dark arts. Read carefully the words that I " +
                    "hereby inscribe, as I detail the heady brew that is responsible for my " +
                    "greatest creation in all my time on this world.",
                "I am Kardia, the most wretched witch in the land; scorned by beauty, the world " +
                    "and its inhabitants, see what I have created: The most fearsome and powerful " +
                    "force of darkness the like of which has never before been seen in this " +
                    "world, in human form...",
            )
        val HISTORY =
            listOf(
                "Iban was a Black Knight who had learned to fight under the great Daquarius, " +
                    "Lord of the Black Knights. Together they had taken on the might of the White " +
                    "and the blood of a hundred soldiers had been wiped from the sword of Iban.",
                "Iban was not so different from those who tasted his blade: noble and educated, " +
                    "with a taste for the finer of things available in life. But there was " +
                    "something that made him different: Ambition.",
                "This hunger for more went far past the realm of mere mortals, into the shadowy " +
                    "places of darkness and evil. Iban's fundamental desire was to control the " +
                    "hearts and minds of his fellow men, and corrupt them into a force of evil.",
                "But dreams were all they ever were. Meeting his demise in the White Knight's " +
                    "now famous Dawn Ascent, Iban died with the bitter taste of failure in his " +
                    "mouth. Little did he know that death was only just the beginning...",
            )
        val RESURRECTION =
            listOf(
                "I knew of Iban's life, though of course I had not met him. Using the power of my " +
                    "dark practices, I vowed to resurrect this greatest of warriors. I would " +
                    "raise him again to fulfil the promise of his human life. To be a master... " +
                    "of the undead...",
            )
        val FLESH =
            listOf(
                "Taking a small doll with the likeness of Iban I smeared my effigy with the four " +
                    "elements that together bring existence into being. Essence of his darkness.",
                "At the battlefield where Iban lay, I had been able to steal a piece of Iban's " +
                    "cold flesh. Clasping some in my hand, I smeared it over the figure of Iban, " +
                    "and chanted his name with mighty incantation.",
            )
        val BLOOD =
            listOf(
                "I also needed blood, the giver of life force. By now Iban's body was a hardened " +
                    "vessel, the blood drained empty.",
                "But those caverns are home to the giant spider, a venomous creature known to " +
                    "feed on the warm blood of humans. I found and killed one of these foul " +
                    "beasts, and wiped the blood from its vile body onto the effigy of Iban.",
            )
        val SHADOW_PAGES =
            listOf(
                "Then came the hard part, recreating the parts of a man that cannot be seen or " +
                    "touched: Those intangible things, the essence of life itself.",
                "Using mystical forces and under terrible strain, I performed the ancient ritual " +
                    "of Incantia, an undertaking so dark and so powerful, that the life was " +
                    "nearly stolen from my body.",
                "When I recovered, I saw three Demons summoned, standing in a triangle, their " +
                    "energy focused on the doll of Iban. These Demons were the keepers of Iban's " +
                    "shadow, forever bound to him...",
            )
        val CONSCIENCE =
            listOf(
                "Finally, I had to make the most unique thing, the one element that separates " +
                    "man from all other beasts - his Conscience.",
                "Locked inside an old wooden cage sat a beautiful white dove. A symbol of peace, " +
                    "freedom, and hope, but also the oblivious to the darkness of the world, like " +
                    "a newborn child.",
                "Taking the dove with me, I strangled the bird, taking its life between my " +
                    "callous fingers. Truly this bird would be the conscience of Iban: innocence " +
                    "corrupted by evil...",
                "Taking crushed bones from the dove's body, I cast my mind's eye onto the body of " +
                    "Iban. My ritual was complete. I alone knew that the same process I had used " +
                    "to resurrect the soul of Iban could be used to destroy that very same evil.",
            )
    }
}
