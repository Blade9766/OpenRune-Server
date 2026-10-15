plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.content.interfaces.bank)
    implementation(projects.api.pluginCommons)
}
