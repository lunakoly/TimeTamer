plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
}

group = "org.lunakoly.timetamer"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(libs.dotenv)
    implementation(libs.kotlinx.datetime)
    implementation(libs.tgbotapi)

    implementation(project(":model"))

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(24)

    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

tasks.test {
    useJUnitPlatform()
}