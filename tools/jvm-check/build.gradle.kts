plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.20"
}

kotlin { jvmToolchain(21) }

val repo = rootDir.parentFile.parentFile

sourceSets {
    main {
        kotlin {
            srcDir(repo.resolve("core/nlu/src/main/kotlin"))
            srcDir(repo.resolve("core/data/src/main/kotlin"))
            include(
                "com/bolohisab/nlu/**",
                "com/bolohisab/data/security/PinHasher.kt",
                "com/bolohisab/data/security/PinLockout.kt",
                "com/bolohisab/data/backup/BackupCrypto.kt",
                "com/bolohisab/data/backup/BackupModels.kt",
            )
        }
        resources { srcDir(repo.resolve("core/nlu/src/main/resources")) }
    }
    test {
        kotlin {
            srcDir(repo.resolve("core/nlu/src/test/kotlin"))
            srcDir(repo.resolve("core/data/src/test/kotlin"))
        }
        resources { srcDir(repo.resolve("core/nlu/src/test/resources")) }
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
