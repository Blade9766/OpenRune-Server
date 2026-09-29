plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.areaChecker)
    implementation(projects.api.combat.combatCommons)
    implementation(projects.api.combat.combatManager)
    implementation(projects.api.dropTable)
    implementation(projects.api.dropTablePlugin)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.registry)
    implementation(projects.api.spells)
    implementation(projects.api.utils.utilsSkills)
    implementation(projects.content.drops)
    implementation(projects.content.other.pets)
    implementation(projects.content.quest)
    implementation(projects.engine.utilsBits)
}
