package org.rsmod.content.quest.area.lumbridge.tearsofguthix

import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player

internal var Player.togStoriesHeard by intVarBit("varbit.tog_juna_stories")
internal var Player.togCollecting by boolVarBit("varbit.tog_minigame_collecting")
internal var Player.togTears by intVarBit("varbit.tog_tears_collected")
internal var Player.togBestTears by intVarBit("varbit.tog_max_tears_collected")
internal var Player.togCountdown by intVarBit("varbit.tog_countdown")
internal var Player.togQpAtLastVisit by intVarBit("varbit.tog_qp_before_return")
internal var Player.togXpBillionsAtLastVisit by intVarBit("varbit.tog_xp_billions")
internal var Player.togXpAtLastVisit by intVarp("varp.tog_xp_at_last_visit")
internal var Player.togLastVisitDay by intVarp("varp.tog_last_visit_day")
internal var Player.togRemindersOff by boolVarBit("varbit.tog_helper_disabled")
internal val Player.togQuestPoints: Int by intVarp("varp.qp")
