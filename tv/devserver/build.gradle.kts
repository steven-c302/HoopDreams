plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":server"))
    runtimeOnly(libs.slf4j.simple)
}
application { mainClass.set("partyos.devserver.MainKt") }
tasks.named<JavaExec>("run") { workingDir = rootProject.projectDir }
