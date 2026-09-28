plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.dropTable)
    implementation(projects.api.dropTablePlugin)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.registry)
    implementation(projects.api.utils.utilsSkills)
    implementation(projects.content.drops)
}
