import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.10"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "org.xoops"
// `-PbuildNumber=N` appends a build number (1.0.0-alpha.4.N): PhpStorm only hot-swaps an installed plugin when the
// incoming version is newer, so a dev build installed over the same version string always asks for a restart.
// The release workflow checks the git tag against `pluginVersion` in gradle.properties, so that is the one place it lives.
version = providers.gradleProperty("pluginVersion").get() + (findProperty("buildNumber")?.let { ".$it" } ?: "")

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // `-PlocalIde=<PhpStorm install dir>` runs the suite against an installed IDE (newer parser, same tests).
        val localIde = findProperty("localIde")?.toString()
        if (localIde != null) local(localIde) else phpstorm("2024.3.5")
        bundledPlugin("com.jetbrains.php")
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    autoReload = true
    buildSearchableOptions = false
    instrumentCode = false

    pluginConfiguration {
        ideaVersion {
            sinceBuild = "243"
            untilBuild = provider { null }
        }
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // Without this the compiler emits delegating stubs for every default method of a platform Kotlin
        // interface (ToolWindowFactory.getAnchor/getIcon/manage …); the Plugin Verifier reads those stubs as
        // overrides of @ApiStatus.Internal API and fails the build.
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
    test {
        useJUnit()
    }
}
