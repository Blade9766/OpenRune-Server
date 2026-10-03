plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}

dependencies {
    implementation(libs.guice)
    implementation(projects.api.bosses)
    implementation(projects.api.combat.combatCommons)
    implementation(projects.api.combat.combatFormulas)
    implementation(projects.api.config)
    implementation(projects.api.death)
    implementation(projects.api.generated)
    implementation(projects.api.npc)
    implementation(projects.api.player)
    implementation(projects.api.playerOutput)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.route)
    implementation(projects.content.skills.slayer)
    implementation(projects.engine.game)
    implementation(projects.engine.plugin)
    testImplementation(libs.fastutil)
    testImplementation(projects.api.gameProcess)
    testImplementation(projects.api.registry)
    testImplementation(projects.api.repo)
    testImplementation(projects.api.random)
    testImplementation(libs.or2.all.cache)
}
