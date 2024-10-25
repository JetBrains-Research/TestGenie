plugins {
    kotlin("jvm")
    `maven-publish`
}

group = "org.jetbrains.research"
val spaceUsername =
    System.getProperty("space.username")?.toString() ?: project.properties["spaceUsername"]?.toString() ?: ""
val spacePublish =
    System.getProperty("space.publish")?.toString() ?: project.properties["spacePublish"]?.toString() ?: ""

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    compileOnly(kotlin("stdlib"))

    implementation("io.github.oshai:kotlin-logging-jvm:6.0.3")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(rootProject.properties["jvmToolchainVersion"].toString().toInt())
}

tasks.register<Jar>("sourcesJar") {
    from(sourceSets.main.get().allSource)
    archiveClassifier.set("sources")
}

tasks.register<Jar>("javadocJar") {
    from(tasks.named("javadoc"))
    archiveClassifier.set("javadoc")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = group as String
            artifactId = "testspark-core"
            version = "5.0.1"
            from(components["java"])

            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])

            pom {
                inceptionYear.set("2024")
                name.set(project.name)
                description.set(project.description)
                packaging = "jar"

                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
            }
        }
    }

    repositories {
        maven {
            url = uri("https://packages.jetbrains.team/maven/p/automatically-generating-unit-tests/public")
            credentials {
                username = spaceUsername
                password = spacePublish
            }
        }
    }
}
