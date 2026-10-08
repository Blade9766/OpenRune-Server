plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.content.interfaces.bank)
    implementation(projects.api.pluginCommons)
    implementation(projects.content.quest)
    implementation(projects.content.skills.runecrafting)
}
