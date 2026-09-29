plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}

dependencies {
    implementation(projects.api.attr)
    implementation(projects.api.combat.combatManager)
    implementation(projects.api.death)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.spells)
}
