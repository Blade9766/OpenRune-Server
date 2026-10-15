plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}

dependencies {
    implementation(projects.api.combat.combatCommons)
    implementation(projects.api.combat.combatWeapon)
    implementation(projects.api.net)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.registry)
    implementation(projects.api.scriptAdvanced)
    implementation(projects.api.spells)
    implementation(projects.api.attr)
    implementation(projects.content.interfaces.omnishop)
    implementation(projects.content.other.consumables)
    implementation(projects.content.interfaces.prayerTab)
    testImplementation(libs.fastutil)
}
