plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
    application
}

group = "org.lunakoly.timetamer"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(libs.dotenv)
    implementation(libs.kotlinx.datetime)
    implementation(libs.tgbotapi)
    implementation(libs.kotlin.exposed.core)
    implementation(libs.kotlin.exposed.jdbc)
    implementation(libs.sqlite.jdbc)
    implementation(libs.postgresql.driver)

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

application {
    mainClass.set("org.lunakoly.timetamer.MainKt")
}
