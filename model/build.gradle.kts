plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "org.lunakoly.timetamer,model"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(libs.kotlinx.datetime)
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(24)
}

tasks.test {
    useJUnitPlatform()
}