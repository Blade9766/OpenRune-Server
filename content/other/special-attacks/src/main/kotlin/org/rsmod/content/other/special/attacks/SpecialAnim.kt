package org.rsmod.content.other.special.attacks

import org.rsmod.api.config.constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.game.entity.PathingEntity

/**
 * Plays a special attack's animation above every block animation.
 *
 * An incoming hit sets the target's `defend_anim` before the attacker's special resolves, and an
 * animation only replaces a pending one of strictly lower priority. Special attack sequences are
 * authored at priority 5 or 6, the same band as the block animations, so on any tick the player
 * is hit the special's animation is silently dropped and only its graphic plays.
 */
internal fun ProtectedAccess.specialAnim(seq: String) {
    anim(seq, priority = constants.combat_special_anim_priority)
}

internal fun PathingEntity.specialAnim(seq: String) {
    anim(seq, priority = constants.combat_special_anim_priority)
}
