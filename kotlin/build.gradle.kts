plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))

    implementation(project(":langwrappers")) // Interfaces that cover language-specific logic
    implementation(project(":core"))
}

intellij {
    rootProject.properties["platformVersion"]?.let { version.set(it.toString()) }
    plugins.set(listOf("java", "org.jetbrains.kotlin"))
}

tasks {
    buildSearchableOptions {
        enabled = false
    }
}

kotlin {
    jvmToolchain(17)
}
