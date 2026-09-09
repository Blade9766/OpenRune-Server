plugins {
    id("base-conventions")

}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.api.attr)
    implementation(projects.api.instances)
    implementation(projects.api.music)
    implementation(projects.content.other.pets)
    implementation(projects.api.serverConfig)
    implementation(projects.content.generic.genericLocs)
    implementation(projects.api.bosses)
    implementation(projects.api.combat.combatFormulas)
    implementation(projects.api.spells)
    implementation(projects.content.interfaces.bank)
    implementation(projects.content.skills.magic.spellAttacks)
}
