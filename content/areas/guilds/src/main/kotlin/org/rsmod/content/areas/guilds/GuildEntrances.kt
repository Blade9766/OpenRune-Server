package org.rsmod.content.areas.guilds

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseAttackLvl
import org.rsmod.api.player.stat.baseStrengthLvl
import org.rsmod.api.player.stat.farmingLvl
import org.rsmod.api.player.stat.fishingLvl
import org.rsmod.api.player.stat.magicLvl
import org.rsmod.api.player.stat.miningLvl
import org.rsmod.api.player.stat.rangedLvl
import org.rsmod.api.player.stat.woodcuttingLvl
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/**
 * A guarded way into a guild. [inside] tells which side of the entrance a tile is on: players
 * inside may always leave, and only players outside are held to [canEnter]. [refuse] tells a
 * player why they were turned away, and [onEnter] runs as a player outside is let through.
 */
class GuildEntrance(
    val name: String,
    val locs: List<String>,
    val inside: (CoordGrid) -> Boolean,
    val canEnter: (Player) -> Boolean,
    val refuse: suspend ProtectedAccess.(loc: String) -> Unit,
    val onEnter: (ProtectedAccess.() -> Unit)? = null,
)

object GuildEntrances {
    private const val HEROES_QUEST = "quest_heroes"
    private const val LEGENDS_QUEST = "quest_legends"

    val fishing =
        GuildEntrance(
            name = "Fishing Guild",
            locs = listOf("loc.fishguilddoor"),
            inside = { it.z >= 3394 },
            canEnter = { it.fishingLvl >= 68 },
            refuse = { mes("You need a Fishing level of 68 to enter the Fishing Guild.") },
        )

    val ranging =
        GuildEntrance(
            name = "Ranging Guild",
            locs = listOf("loc.ranging_guild_door"),
            inside = { (it.x - 2658) - (it.z - 3438) > 0 },
            canEnter = { it.rangedLvl >= 40 },
            refuse = {
                startDialogue {
                    chatNpcSpecific(
                        "Ranging Guild Doorman",
                        "npc.ranging_guild_doorman",
                        neutral,
                        "Hey you can't come in here. You must be a level 40 ranger to enter.",
                    )
                }
            },
        )

    val wizards =
        GuildEntrance(
            name = "Wizards' Guild",
            locs = listOf("loc.magicguild_door_l", "loc.magicguild_door_r"),
            inside = { it.x in 2585..2596 },
            canEnter = { it.magicLvl >= 66 },
            refuse = { loc ->
                val text =
                    if (loc == "loc.magicguild_door_l") {
                        "You need a magic level of 66. The magical energy in here is unsafe for " +
                            "those below that level."
                    } else {
                        "You need a magic level of 66 for admittance to the guild. The magical " +
                            "energy inside the guild is unsafe for anyone below that level. For " +
                            "any other business please ring for attention."
                    }
                startDialogue {
                    chatNpcSpecific("Wizard Distentor", "npc.guild_wizard", neutral, text)
                }
            },
        )

    val mining =
        GuildEntrance(
            name = "Mining Guild",
            locs = listOf("loc.mguild_door"),
            inside = { it.z <= 9756 },
            canEnter = { it.miningLvl >= 60 },
            refuse = { refuseMining() },
        )

    val woodcutting =
        GuildEntrance(
            name = "Woodcutting Guild",
            locs = listOf("loc.wcguild_gatel", "loc.wcguild_gater"),
            inside = { it.x in 1563..1657 },
            canEnter = { it.woodcuttingLvl >= 60 },
            refuse = { mes("You need a Woodcutting level of 60 to enter the Woodcutting Guild.") },
        )

    val farming =
        GuildEntrance(
            name = "Farming Guild",
            locs =
                listOf(
                    "loc.kebos_farming_guild_door_left_closed",
                    "loc.kebos_farming_guild_door_right_closed",
                ),
            inside = { it.z >= 3723 },
            canEnter = { it.farmingLvl >= 45 },
            refuse = {
                startDialogue {
                    chatNpcSpecific(
                        "Guildmaster Jane",
                        "npc.farming_guild_master",
                        neutral,
                        "Hey! You need a Farming level of at least 45 to enter the Farming Guild.",
                    )
                }
            },
        )

    val heroes =
        GuildEntrance(
            name = "Heroes' Guild",
            locs = listOf("loc.herodoor_l", "loc.herodoor_r"),
            inside = { it.x <= 2901 },
            canEnter = { QuestRequirements.hasCompleted(it, HEROES_QUEST) },
            refuse = {
                startDialogue {
                    chatNpcSpecific("Achietties", "npc.achietties", neutral, "Greetings. Welcome to the Heroes' Guild.")
                    chatNpcSpecific(
                        "Achietties",
                        "npc.achietties",
                        neutral,
                        "Only the greatest heroes of this land may gain entrance to this guild.",
                    )
                }
            },
        )

    val legendsGrounds =
        GuildEntrance(
            name = "Legends' Guild grounds",
            locs = listOf("loc.legendsguildgatel", "loc.legendsguildgater"),
            inside = { it.z >= 3350 },
            canEnter = {
                QuestRequirements.hasCompleted(it, LEGENDS_QUEST) ||
                    QuestRequirements.isOnQuest(it, LEGENDS_QUEST)
            },
            refuse = {
                mes("A nearby guard approaches you...")
                startDialogue {
                    chatNpcSpecific("Legends' Guard", "npc.legends_guild_guard1", neutral, "Yes, how can I help you?")
                    mesbox("You need to complete the Legends' quest before you can enter the Legends' Guild.")
                }
            },
            onEnter = {
                if (!QuestRequirements.hasCompleted(player, LEGENDS_QUEST)) {
                    mes("A guard nods at you as you walk past.")
                }
            },
        )

    val legendsHall =
        GuildEntrance(
            name = "Legends' Guild",
            locs = listOf("loc.legendsguilddoorl", "loc.legendsguilddoorr"),
            inside = { it.z >= 3374 },
            canEnter = { QuestRequirements.hasCompleted(it, LEGENDS_QUEST) },
            refuse = {
                mesbox("You need to complete the Legends' quest before you can enter the Legends' Guild.")
            },
        )

    val warriors =
        GuildEntrance(
            name = "Warriors' Guild",
            locs = listOf("loc.warguild_door_front"),
            inside = { it.x >= 2877 },
            canEnter = {
                it.baseAttackLvl + it.baseStrengthLvl >= 130 ||
                    it.baseAttackLvl >= 99 ||
                    it.baseStrengthLvl >= 99
            },
            refuse = {
                startDialogue {
                    chatNpcSpecific(
                        "Ghommal",
                        "npc.warguild_ghommal",
                        neutral,
                        "You may not enter the Warrior's Guild as you are right now.",
                    )
                }
            },
        )

    val all: List<GuildEntrance> =
        listOf(fishing, ranging, wizards, mining, woodcutting, farming, heroes, legendsGrounds, legendsHall, warriors)

    suspend fun ProtectedAccess.refuseMining() {
        startDialogue {
            chatNpcSpecific("Dwarf", "npc.mguild_dwarf1", neutral, "Sorry, but you're not experienced enough to go in there.")
            mesbox("You need a Mining level of 60 to access the Mining Guild.")
        }
    }
}
