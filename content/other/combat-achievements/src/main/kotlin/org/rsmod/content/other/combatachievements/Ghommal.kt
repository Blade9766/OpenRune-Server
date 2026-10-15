package org.rsmod.content.other.combatachievements

import jakarta.inject.Inject
import org.rsmod.api.combatachievements.CombatAchievementTier
import org.rsmod.api.combatachievements.CombatAchievements
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.stat.baseAttackLvl
import org.rsmod.api.player.stat.baseStrengthLvl
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onOpNpc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Ghommal guards the Warriors' Guild door and hands out the Combat Achievement tier rewards. */
class Ghommal @Inject constructor(private val achievements: CombatAchievements) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(GHOMMAL) { startDialogue(it.npc) { ghommal() } }
    }

    private suspend fun Dialogue.ghommal() {
        chatNpc(neutral, "Can I help you?")
        val option =
            choice3(
                "What are you doing out here?",
                1,
                "I'm here to talk about Combat Achievements.",
                2,
                "No, I'm alright thanks.",
                3,
            )
        when (option) {
            1 -> guildDoor()
            2 -> combatAchievements()
            else -> chatPlayer(neutral, "No, I'm alright thanks.")
        }
    }

    private suspend fun Dialogue.guildDoor() {
        chatPlayer(quiz, "What are you doing out here?")
        chatNpc(
            neutral,
            "This here is the Warrior's Guild, a place where the strong may train. I'm the one " +
                "who decides if you are allowed in or not.",
        )
        chatPlayer(quiz, "Am I qualified to enter?")
        val qualified =
            player.baseAttackLvl + player.baseStrengthLvl >= 130 ||
                player.baseAttackLvl >= 99 ||
                player.baseStrengthLvl >= 99
        if (qualified) {
            chatNpc(happy, "Yes you are. Welcome to the Warrior's Guild!")
            chatPlayer(happy, "Thank you!")
            return
        }
        chatNpc(neutral, "You may not enter the Warrior's Guild as you are right now.")
        chatPlayer(angry, "What? But I'm a warrior!")
        chatNpc(neutral, "While you may consider yourself a warrior, I do not.")
        chatPlayer(
            happy,
            "Go on, let me in, you know you want to. I could... make it worth your while...",
        )
        chatNpc(angry, "Go away and train small one. I do not take bribes.")
        chatPlayer(quiz, "Why not?")
        chatNpc(
            neutral,
            "The Warrior's Guild has a code of honour, a code that I respect. Move along.",
        )
    }

    private suspend fun Dialogue.combatAchievements() {
        chatPlayer(neutral, "I'm here to talk about Combat Achievements.")
        val claimed = CombatAchievementTier.entries.lastOrNull { achievements.isClaimed(player, it) }
        val option =
            if (claimed != null) {
                choice4(
                    "Could you explain what they are?",
                    1,
                    "I think I've completed a combat tier!",
                    2,
                    "Could I have another sword hilt?",
                    3,
                    "I have to go.",
                    4,
                )
            } else {
                choice3(
                    "Could you explain what they are?",
                    1,
                    "I think I've completed a combat tier!",
                    2,
                    "I have to go.",
                    4,
                )
            }
        when (option) {
            1 -> explain()
            2 -> claimRewards()
            3 -> claimed?.let { replaceHilt(it) }
            else -> chatPlayer(neutral, "I have to go.")
        }
    }

    private suspend fun Dialogue.explain() {
        chatPlayer(quiz, "Could you explain what they are?")
        chatNpc(
            neutral,
            "Combat Achievements are a set of tasks for all adventurers to try and accomplish. " +
                "They range from simple missions to kill various creatures, to challenges that " +
                "will push you to your limits.",
        )
        chatNpc(
            neutral,
            "The purpose of these tasks is for you to grow and improve, experience all that " +
                "Gielinor has to offer, and become stronger in the process.",
        )
        chatNpc(quiz, "Is this something that sounds interesting to you?")
        val interested =
            choice2(
                "Yes, tell me about Combat Achievements.",
                true,
                "Not at the moment, no.",
                false,
            )
        if (!interested) {
            chatPlayer(neutral, "Not at the moment, no.")
            return
        }
        chatPlayer(happy, "Yes, tell me about Combat Achievements.")
        chatNpc(
            neutral,
            "Combat Achievements are broken down into six tiers: Easy, Medium, Hard, Elite, " +
                "Master and Grandmaster.",
        )
        chatNpc(
            neutral,
            "As you complete tasks, you'll earn points. Tasks in higher tiers give more points " +
                "than easier tasks. Your rank depends on how many points you've earned.",
        )
        chatNpc(
            neutral,
            "Whenever new tasks are added to the list, there are more points available to earn, " +
                "so the threshold needed for each rank will increase. Keep completing tasks to " +
                "maintain your rank!",
        )
        mesbox(
            "Open the Character Summary panel of your quest tab and click the Combat " +
                "Achievements section to browse tasks, bosses and rewards."
        )
        mesbox("Some tasks will require numerous criteria to be completed, so read them carefully.")
        chatNpc(
            happy,
            "I'd recommend you go kill some monsters. Come back to me when you've completed a " +
                "tier or two. I'll reward you for your efforts.",
        )
        chatPlayer(happy, "Thanks a lot, I'll get going.")
    }

    private suspend fun Dialogue.claimRewards() {
        chatPlayer(happy, "I think I've completed a combat tier!")
        chatNpc(quiz, "Oh, how have you been getting on?")
        val pending =
            CombatAchievementTier.entries.filter {
                achievements.isUnlocked(player, it) && !achievements.isClaimed(player, it)
            }
        if (pending.isEmpty()) {
            chatPlayer(neutral, "Not too well, I'm afraid.")
            chatNpc(
                neutral,
                "You have ${achievements.points(player)} points. Keep completing tasks and come " +
                    "back when you've reached the next tier.",
            )
            return
        }
        for (tier in pending) {
            if (!claimTier(tier)) {
                return
            }
        }
        chatPlayer(happy, "Thank you so much!")
    }

    private suspend fun Dialogue.claimTier(tier: CombatAchievementTier): Boolean {
        if (tier == CombatAchievementTier.Grandmaster) {
            chatPlayer(happy, "I've completed all the Combat Achievement tasks!")
            chatNpc(shocked, "Wait, really?")
            chatPlayer(
                happy,
                "Yes! It took quite a long time. Some of those tasks were extremely challenging, " +
                    "but I persevered and finished them all!",
            )
            chatNpc(happy, "You have come very far. You have earned these rewards.")
        } else {
            chatPlayer(happy, "I've completed some more Combat Achievement tasks!")
            val praise =
                if (tier == CombatAchievementTier.Master) {
                    "I can see that. You are clearly a very experienced adventurer."
                } else {
                    "I can see that. Nice job."
                }
            chatNpc(
                happy,
                "$praise You'll be expecting your ${tier.label.lowercase()} rewards?",
            )
            chatPlayer(happy, "Yes please!")
        }
        val objs = rewardObjs(tier)
        if (player.inv.freeSpace() < objs.size) {
            chatNpc(
                neutral,
                "You need at least ${objs.size} inventory spaces. Make some space and come back.",
            )
            return false
        }
        for (obj in objs) {
            access.invAdd(player.inv, obj)
        }
        VarPlayerIntMapSetter.set(player, tier.lampClaimedVarbit, 1)
        achievements.markClaimed(player, tier)
        for (line in rewardSpeech.getValue(tier)) {
            chatNpc(happy, line)
        }
        return true
    }

    private fun Dialogue.rewardObjs(tier: CombatAchievementTier): List<String> =
        buildList {
            add(tier.hilt)
            if (player.vars[tier.lampClaimedVarbit] == 0) {
                add(tier.lamp)
            }
            if (tier == CombatAchievementTier.Master) {
                add(LUCKY_PENNY)
            }
        }

    private suspend fun Dialogue.replaceHilt(tier: CombatAchievementTier) {
        chatPlayer(quiz, "Could I have another sword hilt?")
        if (player.inv.freeSpace() < 1) {
            chatNpc(neutral, "You need a free inventory space. Make some space and come back.")
            return
        }
        access.invAdd(player.inv, tier.hilt)
        objbox(tier.hilt, "Ghommal hands you another sword hilt.")
    }

    private companion object {
        const val GHOMMAL = "npc.warguild_ghommal"
        const val LUCKY_PENNY = "obj.ca_ammoslot"

        val rewardSpeech: Map<CombatAchievementTier, List<String>> =
            mapOf(
                CombatAchievementTier.Easy to
                    listOf(
                        "There you go. I've provided you with the most basic of our ancient sword " +
                            "hilts. You'll also have an easier time obtaining easy clues, " +
                            "increased max quantities offered when given boss Slayer tasks, " +
                            "bonus tokens from the Warrior guild and an extra commendation point " +
                            "from each Pest Control game."
                    ),
                CombatAchievementTier.Medium to
                    listOf(
                        "There you go. I've provided you with one of our more modest ancient " +
                            "sword hilts. You'll also have an easier time obtaining medium clues, " +
                            "increased max quantities offered when given boss Slayer tasks, bonus " +
                            "tokens from the Warrior guild and two extra commendation points from " +
                            "each Pest Control game.",
                        "Additionally, equipping one of my sword hilts at the Barrows will " +
                            "prevent your prayer points from being drained and your Dwarf " +
                            "multicannon can hold five extra cannonballs.",
                    ),
                CombatAchievementTier.Hard to
                    listOf(
                        "There you go. I've provided you with one of our sturdier ancient sword " +
                            "hilts. You'll also have an easier time obtaining hard clues and have " +
                            "increased max quantities offered when given boss Slayer tasks.",
                        "You will also have to kill fewer God Wars minions to access the " +
                            "generals, get more common ecumenical keys, gain access to our most " +
                            "expensive private God Wars rooms, receive a 50% discount on imbue " +
                            "rewards from the Nightmare Zone and Soul Wars,",
                        "and gain three extra commendation points from each Pest Control game. " +
                            "Lastly, your Dwarf multicannon can hold 15 extra cannonballs.",
                    ),
                CombatAchievementTier.Elite to
                    listOf(
                        "There you go. I've provided you with one of our fancier ancient sword " +
                            "hilts. You'll also have an easier time obtaining elite clues, have " +
                            "increased max quantities offered when given boss Slayer tasks and " +
                            "have a higher chance of superior slayer creatures appearing.",
                        "Plus, you'll have to kill even fewer God Wars minions to access the " +
                            "generals, receive even more common ecumenical keys and access to " +
                            "cheaper private God Wars rooms.",
                        "Furthermore, your Dwarf multicannon can hold 30 extra cannonballs and " +
                            "your Bracelets of slaughter or Expeditious bracelets will have a " +
                            "chance to fully recharge instead of breaking! I can also customize " +
                            "your Slayer helmet for you.",
                    ),
                CombatAchievementTier.Master to
                    listOf(
                        "There you go. I've provided you with one of our most prestigious ancient " +
                            "sword hilts, along with my cherished lucky penny! You'll also have " +
                            "increased max quantities offered when given boss Slayer tasks, " +
                            "including the deadly TzHaar creatures,",
                        "and have to kill even fewer God Wars minions to access the generals. " +
                            "Again, you'll get even more common ecumenical keys and access to even " +
                            "cheaper private God Wars rooms.",
                        "Furthermore, any Thralls you resurrect will last 100% longer. I can also " +
                            "customize your Slayer helmet even more.",
                    ),
                CombatAchievementTier.Grandmaster to
                    listOf(
                        "There you go. I've provided you with our most prestigious ancient sword " +
                            "hilt. You'll also have increased max quantities offered when given " +
                            "boss Slayer tasks, including the deadly TzHaar creatures, and have " +
                            "to kill even fewer God Wars minions to access the generals.",
                        "Plus of course, you'll get even more common ecumenical keys and access " +
                            "to even cheaper private God Wars rooms. And yes, I can also " +
                            "customize your Slayer helmet even more.",
                        "I do wonder what you will do now. Good luck, adventurer.",
                    ),
            )
    }
}
