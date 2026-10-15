plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}

dependencies {
    implementation(projects.api.combat.combatFormulas)
    implementation(projects.api.pluginCommons)
    implementation(projects.content.interfaces.bank)
    implementation(projects.content.quest)

    testImplementation(projects.api.registry)
}
