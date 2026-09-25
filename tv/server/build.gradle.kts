plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}
kotlin { jvmToolchain(17) }
dependencies {
    api(project(":engine"))
    api(libs.coroutines.core)
    api(libs.ktor.server.core)
    api(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.json)
    testImplementation(kotlin("test"))
    testImplementation(libs.coroutines.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.cio)
    testImplementation(libs.ktor.client.websockets)
    testImplementation(libs.ktor.client.content.negotiation)
    testRuntimeOnly(libs.slf4j.simple)
}
tasks.test {
    useJUnitPlatform()
    systemProperty("fixturesDir", rootProject.file("../controller/src/protocol/fixtures").absolutePath)
    systemProperty("updateFixtures", project.hasProperty("updateFixtures").toString())
}
