import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        // Compile against the current IntelliJ IDEA line. The wizard APIs used here
        // have been stable since 2025.3, so older 2025.3+ IDEs are allowed below.
        intellijIdea("2026.2.3")
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            // 2025.3 = first unified (free-tier) IntelliJ IDEA distribution.
            sinceBuild = "253"
            untilBuild = provider { null }
        }
    }
}
