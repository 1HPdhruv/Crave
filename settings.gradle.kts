pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "GaG"
include(":app")

// ---------------------------------------------------------------------------
// Keep build output out of iCloud Drive.
//
// This project lives under ~/Desktop, which on this machine is a symlink into
// ~/Library/Mobile Documents/com~apple~CloudDocs. iCloud's file provider
// intercepts reads and writes on everything it syncs, so Gradle's output
// snapshotting (DirectorySnapshotter -> FileInputStream.read) blocks on the
// tens of thousands of small files that KSP, javac and dexing produce. The
// build then sits near 0% CPU while cloudd/bird/fileproviderd saturate the
// machine, and iCloud resolves the write races by leaving "<name> 2.<ext>"
// conflict copies behind, which later break resource merging and dexing.
//
// Build output is fully regenerable, so it does not belong in a synced folder.
// ~/Library/Caches is never synced by iCloud. Override with -PgagBuildDir=...
// ---------------------------------------------------------------------------
val gagBuildRoot: String = providers.gradleProperty("gagBuildDir").orNull
    ?: "${providers.systemProperty("user.home").get()}/Library/Caches/GagBuild"

gradle.beforeProject {
    val relative = path.removePrefix(":").replace(':', '/').ifEmpty { "root" }
    layout.buildDirectory.set(File("$gagBuildRoot/$relative"))
}
