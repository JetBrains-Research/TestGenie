plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}
rootProject.name = "TestSpark"
include("JUnitRunner")
include("core")
include("langwrappers")
include("kotlin")
include("java")
