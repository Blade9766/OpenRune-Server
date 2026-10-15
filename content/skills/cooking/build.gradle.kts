plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.achievementDiaryTasks)
    implementation(projects.content.interfaces.bank)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.registry)
    implementation(projects.content.skills.utils)
    implementation(projects.content.generic.genericLocs)
    implementation(projects.content.quest)
}
