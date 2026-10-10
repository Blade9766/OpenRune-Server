plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}

dependencies {
    implementation(projects.api.bosses)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.combat.combatCommons)
    implementation(projects.api.route)
    testImplementation(projects.api.invStorage)
    testImplementation(projects.engine.events)
    testImplementation(projects.engine.game)
    testImplementation(projects.engine.plugin)
}
