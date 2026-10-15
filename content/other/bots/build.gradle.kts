plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.combat.combatCommons)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.invPlugin)
    implementation(projects.api.net)
    implementation(projects.api.registry)
    implementation(projects.content.interfaces.bank)
    implementation(projects.content.other.grandExchange)
    implementation(projects.content.skills.mining)
    implementation(projects.content.skills.woodcutting)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.toml)
    implementation(libs.jackson.module.kotlin)
}
