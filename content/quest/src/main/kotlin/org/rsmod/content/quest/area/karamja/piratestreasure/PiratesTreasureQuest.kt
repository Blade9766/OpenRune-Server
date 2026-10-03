package org.rsmod.content.quest.area.karamja.piratestreasure

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Pirate's Treasure.
 *
 * The stage is the whole cache varp `varp.hunt`, endstate 4 from `dbrow.quest_piratestreasure`:
 * - [STAGE_STARTED]: Redbeard Frank wants a bottle of Karamjan rum smuggled past customs.
 * - [STAGE_KEY]: Frank has his rum and has handed over One-Eyed Hector's chest key.
 * - [STAGE_MESSAGE]: the pirate message from Hector's chest points at Falador Park.
 * - [STAGE_COMPLETE]: Hector's casket has been dug up.
 *
 * The smuggling and the dig are server-only flags on `varp.piratetreasure_state`: Luthas's job,
 * the bananas and rum in his crate, the rum that reached Wydin's back room, Wydin's job and whether
 * the gardener has already ambushed the dig. Working for Luthas is open to anyone, so only the
 * quest's own flags are cleared when the quest is reset.
 */
@Singleton
class PiratesTreasureQuest :
    QuestScript(
        QUEST_KEY,
        "varp.hunt",
        rewards { extra("One-Eyed Hector's Treasure Chest") },
        ItemRewardDisplay(CASKET, zoom = 300),
    ) {
    override fun ScriptContext.init() {
        quest.onVarSync(::clearWhenReset)
    }

    override fun subTitle(): String =
        "talking to <col=800000>Redbeard Frank</col> outside <col=800000>The Rusty Anchor</col> " +
            "in <col=800000>Port Sarim</col>."

    override fun questLog(player: ProtectedAccess): String =
        questJournal(player) {
            val p = access.player
            objective(
                "<red>Redbeard Frank</red> in Port Sarim knows where some treasure is buried, but " +
                    "he will only tell me for a bottle of <red>Karamjan rum</red>.",
            ) {}
            objective(
                "The rum is sold on <red>Karamja</red>, but the <red>customs officers</red> there " +
                    "confiscate any spirits found on travellers leaving the island.",
            ) {
                visibleWhen { stage(p) == STAGE_STARTED && !p.ptRumShipped && !p.ptCrateRum }
            }
            objective(
                "<red>Luthas</red> pays for crates of bananas to be filled, and customs never " +
                    "search his crates. I could hide the rum in one.",
            ) {
                visibleWhen { stage(p) == STAGE_STARTED && p.ptLuthasJob && !p.ptRumShipped }
            }
            objective(
                "I have hidden the rum in Luthas's banana crate. Once it is full of bananas, " +
                    "Luthas will ship it to <red>Wydin's</red> food store in Port Sarim.",
            ) {
                visibleWhen { stage(p) == STAGE_STARTED && p.ptCrateRum }
            }
            objective(
                "The crate has been shipped to Wydin's food store in Port Sarim. I need to get " +
                    "into his <red>back room</red> to find the rum.",
            ) {
                visibleWhen { stage(p) == STAGE_STARTED && p.ptRumShipped }
            }
            objective(
                "I should bring the rum to Redbeard Frank.",
            ) {
                visibleWhen { stage(p) == STAGE_STARTED && access.inv.contains(KARAMJA_RUM) }
            }
            objective(
                "Frank gave me the key to his old captain <red>One-Eyed Hector's</red> chest, " +
                    "upstairs in the <red>Blue Moon Inn</red> in Varrock.",
            ) {
                visibleWhen { stage(p) == STAGE_KEY }
            }
            objective(
                "Hector's chest held a message: Saradomin points to the X which marks the spot " +
                    "in <red>Falador Park</red>. I will need a <red>spade</red>.",
            ) {
                visibleWhen { stage(p) == STAGE_MESSAGE }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Redbeard Frank of Port Sarim promised to tell me where some treasure was hidden " +
                    "in exchange for a bottle of Karamjan rum.",
            )
            line(
                "I hid the rum in a crate of Luthas's bananas, smuggled it past the customs " +
                    "officers to Wydin's food store, and gave it to Frank.",
            )
            line(
                "Frank's key opened One-Eyed Hector's chest in the Blue Moon Inn, where a message " +
                    "led me to Falador Park. There I dug up Hector's treasure, despite an angry " +
                    "gardener.",
            )
        }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isComplete(player: Player): Boolean = stage(player) >= STAGE_COMPLETE

    private fun clearWhenReset(player: Player) {
        if (stage(player) != 0) {
            return
        }
        player.ptCrateRum = false
        player.ptRumShipped = false
        player.ptWydinJob = false
        player.ptGardenerAppeared = false
    }

    companion object {
        const val QUEST_KEY = "quest_piratestreasure"

        const val STAGE_STARTED = 1
        const val STAGE_KEY = 2
        const val STAGE_MESSAGE = 3
        const val STAGE_COMPLETE = 4

        const val REDBEARD_FRANK = "npc.redbeard_frank"
        const val LUTHAS = "npc.luthas"
        const val WYDIN = "npc.wydin"
        const val GARDENER = "npc.pirate_irate_gardener"

        const val KARAMJA_RUM = "obj.karamja_rum"
        const val BANANA_RUM = "obj.tbwt_banana_in_karamja_rum"
        const val SLICED_BANANA_RUM = "obj.tbwt_sliced_banana_in_karamja_rum"
        const val BANANA = "obj.banana"
        const val COINS = "obj.coins"
        const val CHEST_KEY = "obj.chest_key"
        const val PIRATE_MESSAGE = "obj.piratemessage"
        const val CASKET = "obj.pirate_casket"
        const val WHITE_APRON = "obj.white_apron"
        const val SPADE = "obj.spade"
        const val CHARMED_RING = "obj.ring_of_charos_unlocked"

        const val BANANA_CRATE = "loc.bananacrate"
        const val GROCERY_CRATE = "loc.grocerycrate"
        const val WYDIN_DOOR = "loc.wydindoor"
        const val WYDIN_DOOR_OPEN = "loc.wydindooropen"
        const val HECTORS_CHEST = "loc.piratechest"
        const val HECTORS_CHEST_OPEN = "loc.piratechestopen"

        const val CRATE_CAPACITY = 10
        const val LUTHAS_WAGE = 30

        /** The middle of the dirt X south of the Saradomin statue in Falador Park. */
        val TREASURE_SPOT = CoordGrid(2999, 3383, 0)
    }
}

var Player.ptLuthasJob by boolVarBit("varbit.piratetreasure_luthas_job")
var Player.ptCrateBananas by intVarBit("varbit.piratetreasure_crate_bananas")
var Player.ptCrateRum by boolVarBit("varbit.piratetreasure_crate_rum")
var Player.ptRumShipped by boolVarBit("varbit.piratetreasure_rum_shipped")
var Player.ptWydinJob by boolVarBit("varbit.piratetreasure_wydin_job")
var Player.ptGardenerAppeared by boolVarBit("varbit.piratetreasure_gardener_appeared")
