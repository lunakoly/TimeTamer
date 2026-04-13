plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "org.lunakoly.timetamer"
version = "1.0-SNAPSHOT"

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(24)
}

tasks.test {
    useJUnitPlatform()
}