package org.rsmod.content.events.halloween

import jakarta.inject.Singleton
import java.time.LocalDate
import java.time.Month

@Singleton
class HalloweenSeason {
    var override: Boolean? = null

    val isActive: Boolean
        get() = override ?: (LocalDate.now().month == Month.OCTOBER)
}
