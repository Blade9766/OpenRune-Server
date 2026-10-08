plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.content.interfaces.bank)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.attr)
    implementation(projects.content.quest)
    implementation(projects.content.skills.utils)
}
