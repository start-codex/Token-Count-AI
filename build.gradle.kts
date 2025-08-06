plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.5.0" // 2.5.0
}

group = "com.startcodex"
version = "0.0.5"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        create("IC", "2025.1") // 2025.1
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // Add necessary plugin dependencies for compilation here, example:
        // bundledPlugin("com.intellij.java")
    }

    // https://mvnrepository.com/artifact/com.knuddels/jtokkit
    implementation("com.knuddels:jtokkit:1.1.0")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251" //251
        }

        changeNotes = """
      Initial version - Token counter in status bar
    """.trimIndent()
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21" //21
        targetCompatibility = "21" // 21
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        // kotlinOptions.jvmTarget = "21"
        compilerOptions{
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    withType<Test> {
        enabled = false
    }
}
