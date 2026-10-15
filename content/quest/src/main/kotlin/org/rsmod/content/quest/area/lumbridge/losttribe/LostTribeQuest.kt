package org.rsmod.content.quest.area.lumbridge.losttribe

import dev.openrune.types.MesAnimType
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.quest.area.varrock.demonslayer.fadeFromBlack
import org.rsmod.content.quest.area.varrock.demonslayer.fadeToBlack
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Lost Tribe.
 *
 * The stage is `varbit.lost_tribe_quest` (bits 0-10 of `varp.lost_tribe`, which also carries the
 * quest's sub-state varbits), endstate 11 from `dbrow.quest_losttribe`. The values are pinned by
 * the cache multis: the cellar shelves become rubble at [STAGE_ASKING] and a hole at
 * [STAGE_TUNNEL_DUG], Kazgar and Mistag's Follow option appear at [STAGE_SILVERWARE_MISSING],
 * the castle Sigmund leaves and his H.A.M. double appears at [STAGE_TREATY]. The goblin emotes
 * unlock at [STAGE_EMOTES_LEARNT] (see the emotes plugin).
 *
 * `varbit.lost_tribe_contact` remembers which Lumbridge resident saw the goblin ([Witness]) and
 * `varbit.lost_tribe_ham` how far the player has got exposing Sigmund ([HAM_ROBES_FOUND],
 * [HAM_SILVERWARE_FOUND]).
 */
@Singleton
class LostTribeQuest :
    QuestScript(
        QUEST_KEY,
        "varp.lost_tribe",
        rewards {
            xp("stat.mining", MINING_XP)
            item(RING_OF_LIFE, label = "A ring of life")
            scroll(
                "3,000 Mining XP",
                "A ring of life",
                "Goblin Bow and Salute emotes",
                "Access to the Dorgesh-Kaan mine",
                "Nardok's Bone Weapons store",
            )
        },
        ItemRewardDisplay(RING_OF_LIFE, zoom = 200),
        completionJingle = Quest.QUEST_COMPLETE_2_JINGLE,
        questVarbit = "varbit.lost_tribe_quest",
    ) {
    override fun ScriptContext.init() {
        quest.onVarSync(::syncVars)
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun canStart(player: Player): Boolean =
        QuestRequirements.hasCompleted(player, GOBLIN_DIPLOMACY) &&
            QuestRequirements.hasCompleted(player, RUNE_MYSTERIES)

    fun advanceTo(access: ProtectedAccess, stage: Int) {
        val remaining = stage - stage(access.player)
        if (remaining > 0) {
            quest.advanceQuestStage(access, remaining)
        }
    }

    fun complete(access: ProtectedAccess) {
        quest.completeQuest(access)
    }

    fun witness(player: Player): Witness = Witness.entries[player.lostTribeWitness]

    /** Clears every sub-state varbit when the quest is reset to not started. */
    fun syncVars(player: Player) {
        if (stage(player) != 0) {
            return
        }
        for (varbit in SUB_STATE_VARBITS) {
            if (player.vars[varbit] != 0) {
                VarPlayerIntMapSetter.set(player, varbit, 0)
            }
        }
    }

    /* Asking the townsfolk about the cellar */

    /** The option [witness]'s own menu gains while the player is asking around, or null. */
    fun cellarQuestion(player: Player, witness: Witness): String? {
        val stage = stage(player)
        val asking = stage == STAGE_ASKING || (stage in STAGE_WITNESS_FOUND..STAGE_PERMISSION && witness(player) == witness)
        return if (asking) witness.question else null
    }

    /**
     * For residents whose current conversation has no menu to add the question to: offers it up
     * front and returns true when the player asked it (and the conversation is over).
     */
    suspend fun Dialogue.offerCellarQuestion(witness: Witness): Boolean {
        val question = cellarQuestion(player, witness) ?: return false
        if (!choice2(question, true, "Never mind.", false)) {
            return false
        }
        askAboutCellar(witness)
        return true
    }

    suspend fun Dialogue.askAboutCellar(witness: Witness) {
        chatPlayer(quiz, witness.question)
        if (witness(player) != witness) {
            when (witness) {
                Witness.Cook ->
                    chatNpc(
                        sad,
                        "Oh no, it's terrible, isn't it? There was rock dust everywhere, it got on all my ingredients!",
                    )
                Witness.Hans ->
                    chatNpc(
                        neutral,
                        "The wall collapsed. I had to spend all day clearing it up! The Duke says it was an earthquake.",
                    )
                Witness.Aereck ->
                    chatNpc(
                        neutral,
                        "I heard there was some damage down there, but I'm afraid I don't know any more than that. " +
                            "The Duke says it was an earthquake.",
                    )
                Witness.Bob ->
                    chatNpc(neutral, "I heard the wall fell down. The Duke says it was an earthquake.")
            }
            return
        }
        if (stage(player) >= STAGE_WITNESS_FOUND) {
            chatNpc(neutral, "I told you about the creature I saw. You should go and tell the Duke.")
            return
        }
        chatNpc(
            worried,
            "Last night I was in the kitchen and I heard a noise from the cellar. I opened the trapdoor and saw " +
                "a creature dart into a hole in the wall.",
        )
        chatNpc(
            worried,
            "It looked a bit like a goblin, but it had big bulging eyes. It wasn't wearing armour, but it had " +
                "this odd helmet with a light on it.",
        )
        chatNpc(
            sad,
            "The tunnel was too dark for me to follow it, so I went to tell the Duke. But when we went down to " +
                "the cellar the hole had been blocked up, and no one believes me.",
        )
        chatPlayer(neutral, "I believe you.")
        chatNpc(
            happy,
            "Thank you, ${player.displayName}! If you can convince the Duke I'm telling the truth then we can get " +
                "to the bottom of this mystery.",
        )
        advanceTo(access, STAGE_WITNESS_FOUND)
    }

    suspend fun Dialogue.sigmund(mood: MesAnimType, text: String) =
        chatNpcSpecific("Sigmund", SIGMUND_CHATHEAD, mood, text)

    suspend fun Dialogue.duke(mood: MesAnimType, text: String) =
        chatNpcSpecific("Duke Horacio", DUKE, mood, text)

    /** The Mistag/Kazgar shortcut: a fade to black and a walk through the tunnels. */
    suspend fun ProtectedAccess.guideThroughTunnels(guide: String, dest: CoordGrid) {
        fadeToBlack()
        telejump(dest, TeleportType.Exempt)
        mes("$guide shows you the way through the tunnels.")
        delay(1)
        fadeFromBlack()
        closeFadeOverlay()
    }

    fun ownsItem(access: ProtectedAccess, item: String): Boolean =
        item in access.player.inv || item in access.bank

    override fun subTitle(): String =
        "talking to <col=800000>Sigmund</col> in <col=800000>Lumbridge Castle</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val p = access.player
            val stage = stage(p)
            val witness = witness(p)
            objective(
                "<red>Sigmund</red>, the Duke's adviser, thinks monsters caused the recent damage to the " +
                    "castle <red>cellar</red>. He asked me to find out whether anyone in <red>Lumbridge</red> " +
                    "saw anything.",
            ) {
                visibleWhen { stage in STAGE_ASKING..STAGE_WITNESS_FOUND }
                stageAtLeast(
                    STAGE_WITNESS_FOUND,
                    "${witness.displayName} saw a goblin-like creature with big eyes. I should tell the <red>Duke</red>.",
                )
            }
            objective(
                "The Duke has given me permission to investigate the cellar. If there is a blocked tunnel I " +
                    "should try to unblock it with a <red>pickaxe</red>.",
            ) {
                visibleWhen { stage == STAGE_PERMISSION }
            }
            objective(
                "I dug through the rubble in the cellar and found a tunnel. I should take a <red>light source</red> " +
                    "and see what I can find in there.",
            ) {
                visibleWhen { stage == STAGE_TUNNEL_DUG }
                hasItem(BROOCH, "I found a <red>brooch</red> in the tunnel. I should show it to the <red>Duke</red>.")
            }
            objective(
                "The Duke asked me to identify the symbol on the brooch. The librarian in <red>Varrock</red> " +
                    "might be able to help.",
            ) {
                visibleWhen { stage == STAGE_SHOWN_BROOCH }
                hasItem(BOOK, "I found a book about goblin tribes. I should <red>read</red> it.")
            }
            objective(
                "The symbol belongs to the <red>Dorgeshuun</red>, an ancient goblin tribe. The generals in " +
                    "<red>Goblin Village</red> might know more about them.",
            ) {
                visibleWhen { stage == STAGE_READ_BOOK }
            }
            objective(
                "The generals taught me the goblin bow and salute. I should find the tribe in the tunnels " +
                    "beneath the cellar, following the <red>symbols</red> on the walls, and greet them.",
            ) {
                visibleWhen { stage == STAGE_EMOTES_LEARNT }
            }
            objective(
                "I met <red>Mistag</red> of the Dorgeshuun, who wants peace with the surface. I should tell " +
                    "the <red>Duke</red>.",
            ) {
                visibleWhen { stage == STAGE_CONTACT }
            }
            objective(
                "The Duke says the castle <red>silverware</red> is missing and will go to war unless it is " +
                    "returned.",
            ) {
                visibleWhen { stage == STAGE_SILVERWARE_MISSING }
                hasItem(CHEST_KEY, "I stole a key from <red>Sigmund</red>.")
                custom(
                    p.lostTribeHam >= HAM_ROBES_FOUND,
                    "Sigmund's chest held <red>H.A.M.</red> robes. The H.A.M. hideout is west of the castle.",
                )
                hasItem(SILVERWARE, "I found the silverware in the H.A.M. hideout. I should return it to the Duke.")
            }
            objective(
                "The Duke has signed a <red>peace treaty</red>. I should take it to <red>Mistag</red> in the " +
                    "Dorgeshuun mines.",
            ) {
                visibleWhen { stage == STAGE_TREATY }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Sigmund, the Duke of Lumbridge's adviser, blamed monsters for a collapse in the castle " +
                    "cellar. Behind the rubble I found a tunnel and a brooch bearing the symbol of the " +
                    "Dorgeshuun, a goblin tribe lost for thousands of years.",
            )
            line(
                "With the goblin bow the generals taught me I made friends with Mistag of the Dorgeshuun, " +
                    "then proved that Sigmund, a member of H.A.M., had hidden the castle silverware to start " +
                    "a war.",
            )
            line(
                "The Duke and Ur-tag, headman of the Dorgeshuun, signed a peace treaty, and the Dorgeshuun " +
                    "mines are open to me.",
            )
        }

    enum class Witness(val displayName: String, val question: String) {
        Hans("Hans", "Do you know what happened in the cellar?"),
        Bob("Bob", "Do you know what happened in the castle cellar?"),
        Cook("The cook", "Do you know what happened in the castle cellar?"),
        Aereck("Father Aereck", "Do you know what happened in the castle cellar?"),
    }

    companion object {
        const val QUEST_KEY = "quest_losttribe"
        const val GOBLIN_DIPLOMACY = "quest_goblindiplomacy"
        const val RUNE_MYSTERIES = "quest_runemysteries"
        const val DRAGON_SLAYER = "quest_dragonslayer"

        const val STAGE_ASKING = 1
        const val STAGE_WITNESS_FOUND = 2
        const val STAGE_PERMISSION = 3
        const val STAGE_TUNNEL_DUG = 4
        const val STAGE_SHOWN_BROOCH = 5
        const val STAGE_READ_BOOK = 6
        const val STAGE_EMOTES_LEARNT = 7
        const val STAGE_CONTACT = 8
        const val STAGE_SILVERWARE_MISSING = 9
        const val STAGE_TREATY = 10
        const val STAGE_COMPLETE = 11

        const val HAM_ROBES_FOUND = 1
        const val HAM_TOLD_DUKE = 2
        const val HAM_SILVERWARE_FOUND = 3

        const val MINING_REQ = 17
        const val AGILITY_REQ = 13
        const val THIEVING_REQ = 13
        const val MINING_XP = 3000.0

        const val SIGMUND = "npc.lost_tribe_sigmund"
        const val SIGMUND_CHATHEAD = "npc.lost_tribe_sigmund_there"
        const val SIGMUND_HAM = "npc.lost_tribe_sigmund_ham"
        const val DUKE = "npc.duke_of_lumbridge"
        const val MISTAG = "npc.lost_tribe_mistag"
        const val MISTAG_CHATHEAD = "npc.lost_tribe_mistag_1op"
        const val KAZGAR = "npc.lost_tribe_guide"
        const val URTAG = "npc.lost_tribe_cutscene_urtag"
        const val NARDOK = "npc.dttd_bone_dealer"

        const val BROOCH = "obj.lost_tribe_brooch"
        const val BOOK = "obj.lost_tribe_book"
        const val CHEST_KEY = "obj.lost_tribe_chest_key"
        const val SILVERWARE = "obj.lost_tribe_silverware"
        const val TREATY = "obj.lost_tribe_treaty"
        const val RING_OF_LIFE = "obj.ring_of_life"
        const val MINING_HELMET = "obj.cave_goblin_mining_helmet_unlit"

        /** Beside Kazgar, just inside the tunnel from the castle cellar. */
        val KAZGAR_ARRIVAL = CoordGrid(3229, 9610, 0)

        /** At the end of the marked path, in front of Mistag. */
        val MINES_ARRIVAL = CoordGrid(3317, 9612, 0)

        /** The castle cellar, beside the hole in the east wall. */
        val CELLAR_ARRIVAL = CoordGrid(3218, 9617, 0)

        private val MINE_X = 3309..3330
        private val MINE_Z = 9600..9660

        val SUB_STATE_VARBITS =
            listOf(
                "varbit.lost_tribe_returned_brooch",
                "varbit.lost_tribe_ham",
                "varbit.lost_tribe_mistag_denial",
                "varbit.lost_tribe_maze_warned",
                "varbit.lost_tribe_contact",
                "varbit.lost_tribe_hole_2_dug",
                "varbit.lost_tribe_sigmund_accused",
                "varbit.lost_tribe_sigmund_leaving",
                "varbit.lost_tribe_bookmark",
            )

        /** Mistag's refusal while the player mines in the Dorgeshuun mines before the treaty. */
        fun mineRefusal(player: Player, coords: CoordGrid): String? {
            if (coords.level != 0 || coords.x !in MINE_X || coords.z !in MINE_Z) {
                return null
            }
            if (QuestRequirements.hasCompleted(player, QUEST_KEY)) {
                return null
            }
            return "Mistag: We won't permit surface-dwellers to mine here until there is peace between " +
                "our peoples."
        }
    }
}
