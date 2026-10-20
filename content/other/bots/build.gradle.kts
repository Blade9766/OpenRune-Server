plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.api.invPlugin)
    implementation(projects.api.net)
    implementation(projects.api.registry)
    implementation(projects.api.spells)
    implementation(projects.content.interfaces.bank)
    implementation(projects.content.other.cheatMenu)
    implementation(projects.content.other.grandExchange)
    implementation(projects.content.quest)
    implementation(projects.content.skills.magic.spellTeleports)
    implementation(projects.content.skills.mining)
    implementation(projects.content.skills.slayer)
    implementation(projects.content.skills.smithing)
    implementation(projects.content.skills.utils)
    implementation(projects.content.skills.woodcutting)
    implementation(projects.content.travel.wayfinder)
    implementation(libs.fastutil)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.toml)
    implementation(libs.jackson.module.kotlin)
    testImplementation(projects.api.invStorage)
    implementation(libs.rsprot.api)
}
