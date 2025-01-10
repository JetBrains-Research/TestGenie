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

    implementation(project(":core"))
}

intellijPlatform {
    pluginConfiguration {
        rootProject.properties["platformVersion"]?.let { version = it.toString() }
    }
//    apply(plugin = "java")
//    // Apply more plugins if necessary
//    apply(plugin = "kotlin")
}

tasks.named("verifyPlugin") { enabled = false }
tasks.named("runIde") { enabled = false }
tasks.named("prepareJarSearchableOptions") { enabled = false }
//tasks.named("runPluginVerifier") { enabled = false }

kotlin {
    jvmToolchain(rootProject.properties["jvmToolchainVersion"].toString().toInt())
}
