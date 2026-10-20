package org.rsmod.content.skills.magic.spell.teleports

import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.utils.time.epochMinute
import org.rsmod.game.entity.Player

/**
 * Every spellbook's home teleport shares one 30-minute recharge, kept as the epoch minute of the
 * last completed cast in `varp.aide_tele_timer`; the client greys the spell out from the same varp.
 */
public object HomeTeleportCooldown {
    public const val MINUTES: Int = 30
    internal const val CAST_QUEUE: String = "queue.home_teleport_cast"

    /** Whole minutes until a home teleport cast at [lastCastMinute] recharges at [nowMinute]; 0 once ready. */
    public fun minutesLeft(lastCastMinute: Int, nowMinute: Int): Int {
        if (lastCastMinute <= 0) {
            return 0
        }
        val elapsed = nowMinute - lastCastMinute
        if (elapsed >= MINUTES) {
            return 0
        }
        return (MINUTES - elapsed).coerceAtMost(MINUTES)
    }

    public fun minutesLeft(player: Player): Int = minutesLeft(player.lastHomeTeleport, epochMinute())

    public fun message(minutes: Int): String {
        val unit = if (minutes == 1) "minute" else "minutes"
        return "You need to wait another $minutes $unit to cast this spell."
    }

    /** True from the click until the caster lands or the cast is interrupted. */
    public fun isCasting(player: Player): Boolean = CAST_QUEUE in player.weakQueueList

    internal fun recordCast(player: Player) {
        player.lastHomeTeleport = epochMinute()
    }
}

private var Player.lastHomeTeleport by intVarp("varp.aide_tele_timer")
