package org.rsmod.content.other.bots.manager

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import kotlin.random.Random
import org.rsmod.api.player.output.Camera
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.other.bots.BotManager
import org.rsmod.content.other.bots.core.SavedAppearance
import org.rsmod.content.other.bots.world.BotAppearance
import org.rsmod.content.other.cheatmenu.PlayerDesign
import org.rsmod.game.entity.Player

/**
 * Styles a bot with the game's own character designer. Every arrow changes the bot in the world
 * at once, so that is the preview; the bot is held still while the designer is open. Apply keeps
 * the look and saves it with the bot; Cancel, Reset or closing the window put the old look back.
 */
class BotAppearanceEditor(private val bots: BotManager, private val reopen: (ProtectedAccess) -> Unit) {
    /** Opens the designer for [botId]; returns why it could not, if it could not. */
    fun open(access: ProtectedAccess, botId: Long): String? {
        val coordinator = bots.coordinator ?: return "bots are not loaded"
        val record = coordinator.find(botId.toString()) ?: return "no such bot"
        val bot = bots.playerOf(botId) ?: return "enable it first: the preview shows on the bot in the world"
        coordinator.pose(botId, hold = true)?.let { return it }
        val session = Session(botId, record.name, bot, BotAppearance.snapshot(bot))
        PlayerDesign.open(access, session)
        access.player.runClientScript(
            INIT.asRSCM(RSCMType.CLIENTSCRIPT),
            "Bot Appearance: ${record.name}",
            "Every change shows on ${record.name} in the world. Apply keeps it; Cancel or closing undoes it.",
        )
        access.ifSetEvents(MAIN, 0..LAST_CHILD, IfEvent.Op1)
        val distance = access.player.coords.chebyshevDistance(bot.coords)
        if (access.player.coords.level != bot.coords.level || distance > NEAR) {
            access.mes("${record.name} is not nearby; use Go to bot in the Bot Manager to watch the preview.")
        } else {
            frame(access, bot)
        }
        LOG.info { "${access.player.username} opened the appearance editor for bot #$botId '${record.name}'" }
        return null
    }

    /** Aims the camera beside the bot so it stands clear of the centred designer window. */
    private fun frame(access: ProtectedAccess, bot: Player) {
        val focus = bot.coords.translateX(FRAME_OFFSET)
        access.camMoveTo(focus.translateZ(-FRAME_DISTANCE), FRAME_HEIGHT, FRAME_RATE, FRAME_RATE)
        access.camLookAt(focus, LOOK_HEIGHT, FRAME_RATE, FRAME_RATE)
    }

    private inner class Session(
        private val botId: Long,
        private val name: String,
        override val target: Player,
        private val original: SavedAppearance,
    ) : PlayerDesign.Session {
        override fun confirm(access: ProtectedAccess) {
            bots.keepAppearance(botId)
            bots.coordinator?.saveSoon(botId)
            val look = BotAppearance.snapshot(target)
            LOG.info { "${access.player.username} set bot #$botId '$name' appearance to $look" }
            PlayerDesign.close(access)
            access.mes("$name's new look is saved.")
            reopen(access)
        }

        override fun button(access: ProtectedAccess, comsub: Int) {
            when (comsub) {
                RANDOMIZE -> BotAppearance.randomize(target, Random.Default, target.appearance.bodyType)
                RESET -> restore(access)
                CANCEL -> {
                    PlayerDesign.close(access)
                    reopen(access)
                }
                else -> LOG.warn { "${access.player.username} sent unknown appearance button $comsub for bot #$botId" }
            }
        }

        override fun closed(admin: Player, confirmed: Boolean) {
            if (!confirmed) {
                BotAppearance.apply(target, original)
                LOG.info { "${admin.username} left bot #$botId '$name' appearance unchanged" }
            }
            bots.coordinator?.pose(botId, hold = false)
            Camera.camReset(admin)
        }

        private fun restore(access: ProtectedAccess) {
            BotAppearance.apply(target, original)
            PlayerDesign.refresh(access, target)
        }
    }

    private companion object {
        private val LOG = InlineLogger("bots.admin")
        private const val INIT = "clientscript.bot_design_init"
        private const val MAIN = "component.player_design:main"
        private const val RANDOMIZE = 3
        private const val RESET = 6
        private const val CANCEL = 9
        private const val LAST_CHILD = 9
        private const val NEAR = 14
        private const val FRAME_OFFSET = 5
        private const val FRAME_DISTANCE = 10
        private const val FRAME_HEIGHT = 1300
        private const val LOOK_HEIGHT = 150
        private const val FRAME_RATE = 100
    }
}
