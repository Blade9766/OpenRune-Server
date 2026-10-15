plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.pluginCommons)
}

tasks.jar {
    archiveFileName.set("poh-ui-plugin.jar")
}
