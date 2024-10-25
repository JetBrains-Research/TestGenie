plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform")
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create(rootProject.properties["platformType"].toString(), rootProject.properties["platformVersion"].toString())
        // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file.
        bundledPlugins(listOf("com.intellij.java"))

        instrumentationTools()
    }
    implementation(kotlin("stdlib"))

    implementation(project(":langwrappers")) // Interfaces that cover language-specific logic
    implementation(project(":core"))
}

intellijPlatform {
    pluginConfiguration {
        rootProject.properties["platformVersion"]?.let { version = it.toString() }
    }
}

tasks.named("verifyPlugin") { enabled = false }
tasks.named("runIde") { enabled = false }
tasks.named("prepareJarSearchableOptions") { enabled = false }
tasks.named("publishPlugin") { enabled = false }

kotlin {
    jvmToolchain(rootProject.properties["jvmToolchainVersion"].toString().toInt())
}
