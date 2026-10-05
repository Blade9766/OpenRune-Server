package org.rsmod.content.skills.construction.house

import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.game.entity.Player

/**
 * Set by an admin with `::freebuild`: rooms and furniture go up with no Construction or other
 * skill level, no materials, no coins and no quest, and give no experience. Cleared on logout.
 */
internal var Player.freeBuild by boolVarBit("varbit.poh_free_build")
