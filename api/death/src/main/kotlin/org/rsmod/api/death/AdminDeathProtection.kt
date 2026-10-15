package org.rsmod.api.death

import dev.or2.central.account.Rights
import org.rsmod.api.attr.AttributeKey
import org.rsmod.game.entity.Player

private val ADMIN_DEATH_PROTECTION_ATTR: AttributeKey<Boolean> =
    AttributeKey(persistenceKey = "admin_death_protection")

public fun Player.adminDeathProtectionEnabled(): Boolean =
    attr.getOrDefault(ADMIN_DEATH_PROTECTION_ATTR, true)

public fun Player.setAdminDeathProtection(enabled: Boolean) {
    attr[ADMIN_DEATH_PROTECTION_ATTR] = enabled
}

public fun Player.hasAdminDeathProtection(): Boolean =
    modLevel.isAtLeast(Rights.ADMINISTRATOR) && adminDeathProtectionEnabled()
