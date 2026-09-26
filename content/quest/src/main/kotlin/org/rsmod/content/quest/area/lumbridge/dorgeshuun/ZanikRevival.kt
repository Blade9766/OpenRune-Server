package org.rsmod.content.quest.area.lumbridge.dorgeshuun

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.script.onOpHeld5
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.DEAD_ZANIK
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.JUNA_CHATHEAD
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.STAGE_TEARS
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.STAGE_ZANIK_DEAD
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.TEARS_OF_GUTHIX
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Bringing Zanik back: her body outside the hideout, the way through the Lumbridge Swamp Caves to
 * the Chasm of Tears, and Juna, who lets the player collect twenty blue tears in Zanik's bowl.
 *
 * The Tears of Guthix minigame itself isn't here; the weeping walls only answer while the tears
 * are being collected for Zanik, counted in `varbit.tog_tears_collected` and shown on the minigame's
 * own side panel.
 */
class ZanikRevival
@Inject
constructor(private val dttd: DeathToTheDorgeshuunQuest, private val scenes: DttdScenes) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(CORPSE) { inspectCorpse() }
        onOpHeld5(DEAD_ZANIK) {
            startDialogue {
                chatPlayer(
                    sad,
                    "I can't just leave Zanik's body lying around! I should take her to Juna.",
                )
            }
        }
        onOpLoc1(JUNA) { startDialogue { juna() } }
        onOpLoc1(WEEPING_WALL) { collectTear() }
        onOpLoc1(STEPPING_STONE) { jumpStones(it.loc.coords) }
        onOpLoc1(CAVE_TUNNEL_DOWN) { enterTunnel(CAVE_LANDING) }
        onOpLoc1(CAVE_TUNNEL_UP) { enterTunnel(SWAMP_LANDING) }
        onOpLoc1(ROCKS_TO_JUNA) { climbRocks(ROCKS_TO_JUNA_X, JUNA_SIDE_Z) }
        onOpLoc1(ROCKS_TO_MINE) { climbRocks(ROCKS_TO_MINE_X, MINE_SIDE_Z) }
        onPlayerLogin { restoreLostBody(player) }
    }

    /** A body lost on death goes back to where the guards left it. */
    private fun restoreLostBody(player: Player) {
        if (dttd.stage(player) != STAGE_ZANIK_DEAD) {
            return
        }
        if (DEAD_ZANIK !in player.inv && !player.dttdZanikCorpse) {
            player.dttdZanikCorpse = true
        }
        if (player.dttdCollectingTears) {
            player.dttdCollectingTears = false
        }
    }

    private suspend fun ProtectedAccess.inspectCorpse() {
        arriveDelay()
        if (dttd.stage(player) != STAGE_ZANIK_DEAD || !player.dttdZanikCorpse) {
            return
        }
        faceSquare(CORPSE_TILE)
        startDialogue {
            chatPlayer(worried, "Zanik? Are you okay?")
            chatPlayer(shocked, "Zanik!")
            mesbox("Zanik is not breathing...")
            objbox(DEAD_ZANIK, "But you notice that the mark on her forehead is glowing brightly.")
            chatPlayer(neutral, "Juna told Zanik that when the mark glowed again she should go to see her.")
            chatPlayer(neutral, "Perhaps I should take Zanik's body to Juna. I can't leave her here anyway!")
            if (!access.inv.hasFreeSpace()) {
                chatPlayer(sad, "But I'll have to free up an inventory slot first.")
                return@startDialogue
            }
            access.anim(PICKUP_SEQ)
            access.soundSynth(PICKUP_SOUND)
            if (access.invAdd(access.inv, DEAD_ZANIK).success) {
                player.dttdZanikCorpse = false
                objbox(DEAD_ZANIK, "You pick up Zanik's body.")
            }
        }
    }

    private suspend fun Dialogue.juna() {
        junaLine(neutral, "Tell me... a story...")
        when (dttd.stage(player)) {
            STAGE_ZANIK_DEAD -> {
                if (DEAD_ZANIK !in player.inv) {
                    return
                }
                chatPlayer(sad, "Zanik is dead!")
                junaLine(
                    neutral,
                    "That cannot be. She had not yet returned to learn of her destiny. The mark that the tears " +
                        "gave her had not begun to glow again.",
                )
                chatPlayer(neutral, "It's glowing now. Look.")
                if (access.invDel(access.inv, DEAD_ZANIK).failure) {
                    return
                }
                dttd.advanceTo(access, STAGE_TEARS)
                objbox(DEAD_ZANIK, "You lay Zanik's corpse in front of Juna.")
                junaLine(neutral, "This is indeed strange. I must commune with mighty Guthix and the forces of destiny.")
                access.soundSynth(MEDITATE_SOUND)
                mesbox("Juna meditates for a few moments.")
                junaLine(neutral, "Zanik's death was premature. The gods will allow it to be reversed.")
                chatPlayer(shocked, "You can just bring her back to life?!")
                junaLine(
                    neutral,
                    "The Tears of Guthix have powers beyond the understanding of mortals. They can restore her to " +
                        "life, with the gods' permission.",
                )
                bowlTerms()
            }
            STAGE_TEARS -> {
                if (player.dttdCollectingTears) {
                    junaLine(
                        neutral,
                        "Twenty tears should be sufficient. Collect twenty blue tears, but remember that green " +
                            "tears will decrease the amount of blue.",
                    )
                    return
                }
                junaLine(
                    neutral,
                    "Zanik has not yet fulfilled her destiny, so the gods will allow her to be resurrected. You " +
                        "must collect some of the Tears of Guthix to allow this to happen.",
                )
                bowlTerms()
            }
        }
    }

    private suspend fun Dialogue.bowlTerms() {
        if (QuestRequirements.hasCompleted(player, TEARS_OF_GUTHIX)) {
            junaLine(
                neutral,
                "You must collect some of the tears using Zanik's tear-bowl. I will not count this against the " +
                    "next time you can collect the tears for yourself.",
            )
        } else {
            junaLine(
                neutral,
                "You must collect some of the tears using Zanik's tear-bowl. Although I would not normally deem " +
                    "you worthy to collect the tears, I will let you into the cave for this special purpose.",
            )
        }
        junaLine(neutral, "Are you ready?")
        if (!choice2("Yes.", true, "No.", false)) {
            chatPlayer(neutral, "No.")
            junaLine(neutral, "Then the forces of destiny must wait until a time that is more convenient for you.")
            return
        }
        chatPlayer(neutral, "Yes.")
        if (!dttd.handsFree(player)) {
            junaLine(
                neutral,
                "You must have both hands free to carry the bowl. Speak to me again when your hands are free.",
            )
            return
        }
        junaLine(
            neutral,
            "Twenty tears should be sufficient. Collect twenty blue tears, but remember that green tears will " +
                "decrease the amount of blue.",
        )
        access.beginCollecting()
    }

    private fun ProtectedAccess.beginCollecting() {
        player.dttdCollectingTears = true
        player.dttdTearsCollected = 0
        ifOpenOverlay(TEARS_PANEL)
        ifSetText(TEARS_COUNT, "0")
    }

    private fun ProtectedAccess.stopCollecting() {
        player.dttdCollectingTears = false
        player.dttdTearsCollected = 0
        ifCloseSub(TEARS_PANEL)
    }

    private suspend fun ProtectedAccess.collectTear() {
        arriveDelay()
        if (dttd.stage(player) != STAGE_TEARS || !player.dttdCollectingTears) {
            mes("You need a bowl to collect the tears in.")
            return
        }
        if (!dttd.handsFree(player)) {
            mes("You need both hands free to hold the bowl.")
            return
        }
        anim(LEAN_FORWARD_SEQ)
        delay(COLLECT_TICKS)
        val roll = random.of(maxExclusive = 100)
        var tears = player.dttdTearsCollected
        when {
            roll < BLUE_CHANCE -> {
                tears++
                mes("You collect some blue tears.")
            }
            roll < BLUE_CHANCE + GREEN_CHANCE -> {
                tears = (tears - 1).coerceAtLeast(0)
                mes("Green tears splash into the bowl, spoiling some of the blue.")
            }
            else -> mes("The stream runs dry for a moment.")
        }
        anim(LEAN_BACK_SEQ)
        player.dttdTearsCollected = tears
        ifSetText(TEARS_COUNT, tears.toString())
        if (tears < TEARS_NEEDED) {
            return
        }
        mes("You have collected twenty tears.")
        stopCollecting()
        startDialogue { chatPlayer(happy, "I have the Tears.") }
        with(scenes) { revival() }
    }

    /* Getting to Juna */

    private suspend fun ProtectedAccess.jumpStones(stone: CoordGrid) {
        arriveDelay()
        val start = if (player.coords.z > stone.z) STREAM_NORTH else STREAM_SOUTH
        val landing = if (start == STREAM_NORTH) STREAM_SOUTH else STREAM_NORTH
        faceSquare(stone)
        anim(STEPPING_SEQ)
        delay(1)
        telejump(stone, TeleportType.Exempt)
        delay(1)
        if (!rollCross()) {
            anim(FALL_SEQ)
            delay(1)
            telejump(start, TeleportType.Exempt)
            mes("You slip and fall into the water!")
            extinguishLights()
            statAdvance("stat.agility", FAIL_XP)
            return
        }
        anim(STEPPING_SEQ)
        delay(1)
        telejump(landing, TeleportType.Exempt)
        statAdvance("stat.agility", CROSS_XP)
    }

    private fun ProtectedAccess.rollCross(): Boolean {
        val level = player.agilityLvl.coerceIn(1, 99)
        val chance = CROSS_LOW + (CROSS_HIGH - CROSS_LOW) * (level - 1) / 98
        return random.of(maxExclusive = 256) < chance
    }

    private fun ProtectedAccess.extinguishLights() {
        var any = false
        for ((lit, unlit) in LIGHTS) {
            while (lit in player.inv) {
                if (invReplace(inv, lit, 1, unlit).failure) {
                    break
                }
                any = true
            }
        }
        if (any) {
            mes("Your light source has been extinguished.")
        }
    }

    private suspend fun ProtectedAccess.enterTunnel(dest: CoordGrid) {
        arriveDelay()
        if (dest == SWAMP_LANDING && player.dttdCollectingTears) {
            var leave = false
            startDialogue {
                junaLine(neutral, "You will need to collect twenty tears in order to resurrect Zanik.")
                if (choice2("Okay.", false, "I want to get out now!", true)) {
                    chatPlayer(neutral, "I want to get out now!")
                    leave = true
                } else {
                    chatPlayer(neutral, "Okay.")
                }
            }
            if (!leave) {
                return
            }
            stopCollecting()
        }
        anim(CRAWL_SEQ)
        delay(CRAWL_TICKS)
        telejump(dest)
    }

    private suspend fun ProtectedAccess.climbRocks(rocksX: Int, z: Int) {
        arriveDelay()
        val west = player.coords.x < rocksX
        anim(if (west) CLIMB_DOWN_SEQ else CLIMB_UP_SEQ)
        delay(CLIMB_TICKS)
        telejump(CoordGrid(if (west) rocksX + 1 else rocksX - 1, z, CAVE_LEVEL), TeleportType.Exempt)
    }

    private suspend fun Dialogue.junaLine(mood: dev.openrune.types.MesAnimType, text: String) =
        chatNpcSpecific("Juna", JUNA_CHATHEAD, mood, text)

    private companion object {
        const val CORPSE = "loc.dttd_zanik_corpse"
        const val JUNA = "loc.tog_juna"
        const val WEEPING_WALL = "loc.tog_weepingwall"
        const val STEPPING_STONE = "loc.swamp_cave_steppingstone_b"
        const val CAVE_TUNNEL_DOWN = "loc.tog_cave_down"
        const val CAVE_TUNNEL_UP = "loc.tog_cave_up"
        const val ROCKS_TO_JUNA = "loc.tog_climbing_rocks_up"
        const val ROCKS_TO_MINE = "loc.tog_climbing_rocks_down"

        const val TEARS_PANEL = "interface.tog_sidepanel"
        const val TEARS_COUNT = "component.tog_sidepanel:count"

        const val PICKUP_SEQ = "seq.human_pickupfloor"
        const val PICKUP_SOUND = "synth.dttd_pick_up_zaniks_body"
        const val MEDITATE_SOUND = "synth.dttd_juna_meditates"
        const val LEAN_FORWARD_SEQ = "seq.tog_lean_forward_bowl"
        const val LEAN_BACK_SEQ = "seq.tog_lean_back_bowl"
        const val STEPPING_SEQ = "seq.human_steppingstonejump"
        const val FALL_SEQ = "seq.human_drowning"
        const val CRAWL_SEQ = "seq.human_crawling"
        const val CLIMB_UP_SEQ = "seq.tog_climb_slope_up"
        const val CLIMB_DOWN_SEQ = "seq.tog_climb_slope_down"

        const val TEARS_NEEDED = 20
        const val BLUE_CHANCE = 70
        const val GREEN_CHANCE = 20
        const val COLLECT_TICKS = 2
        const val CRAWL_TICKS = 2
        const val CLIMB_TICKS = 2

        /** Mod Ash's stepping stone odds out of 256, from level 1 to level 99 Agility. */
        const val CROSS_LOW = 51
        const val CROSS_HIGH = 252
        const val CROSS_XP = 3.0
        const val FAIL_XP = 1.0

        const val CAVE_LEVEL = 2
        const val ROCKS_TO_JUNA_X = 3240
        const val JUNA_SIDE_Z = 9524
        const val ROCKS_TO_MINE_X = 3239
        const val MINE_SIDE_Z = 9498

        /** Outside the hideout, where the guards dumped Zanik. */
        val CORPSE_TILE = CoordGrid(3161, 3245, 0)

        /** The stream south of the Dorgeshuun mine entrance, crossed by the stone at 3221,9554. */
        val STREAM_NORTH = CoordGrid(3221, 9556, 0)
        val STREAM_SOUTH = CoordGrid(3222, 9553, 0)

        /** Either end of the tunnel between the swamp caves and the Chasm of Tears. */
        val CAVE_LANDING = CoordGrid(3219, 9532, 2)
        val SWAMP_LANDING = CoordGrid(3226, 9542, 0)

        val LIGHTS =
            listOf(
                "obj.lit_candle" to "obj.unlit_candle",
                "obj.torch_lit" to "obj.torch_unlit",
                "obj.oil_lamp_lit" to "obj.oil_lamp_unlit",
                "obj.candle_lantern_lit" to "obj.candle_lantern_unlit",
                "obj.candle_lantern_black_lit" to "obj.candle_lantern_black_unlit",
                "obj.oil_lantern_lit" to "obj.oil_lantern_unlit",
                "obj.bullseye_lantern_lit" to "obj.bullseye_lantern_unlit",
                "obj.tog_sapphire_lantern_lit" to "obj.tog_sapphire_lantern_unlit",
                "obj.cave_goblin_mining_helmet_lit" to "obj.cave_goblin_mining_helmet_unlit",
            )
    }
}
