plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}

dependencies {
    testImplementation(projects.api.invStorage)
    testImplementation(projects.api.registry)
    testImplementation(libs.fastutil)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.attr)
    implementation(projects.api.serverConfig)
    implementation(libs.rsprot.api)
    implementation(projects.content.generic.genericLocs)
    implementation(projects.api.bosses)
    implementation(projects.api.combat.combatFormulas)
    implementation(projects.api.spells)
    implementation(projects.content.interfaces.bank)
    implementation(projects.content.skills.magic.spellAttacks)
}
