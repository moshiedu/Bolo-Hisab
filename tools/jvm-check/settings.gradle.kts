// JVM-only build of Bolo Hisab's pure-Kotlin code (parser, typing help, PIN hashing, backup
// crypto/format), for environments without the Android SDK. Run from the repo root:
//   ./gradlew -p tools/jvm-check test
pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
dependencyResolutionManagement { repositories { mavenCentral() } }
rootProject.name = "jvm-check"
