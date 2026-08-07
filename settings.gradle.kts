pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // light-common lives in GitHub Packages, which has no anonymous read even for
        // public packages. Locally: gpr.user / gpr.key in local.properties (a PAT with
        // read:packages). In CI: GPR_USER / GPR_TOKEN if set, else the run's own
        // GITHUB_ACTOR / GITHUB_TOKEN.
        maven {
            url = uri("https://maven.pkg.github.com/gi-os/BrightCommon")
            credentials {
                // takeUnless(String::isBlank), not `?:` -- an unset repository secret
                // arrives as an empty string rather than null, so a plain elvis chain
                // would hand Gradle a blank username and never reach the fallback.
                username = System.getenv("GPR_USER")?.takeUnless(String::isBlank)
                    ?: System.getenv("GITHUB_ACTOR")?.takeUnless(String::isBlank)
                    ?: providers.gradleProperty("gpr.user").orNull
                password = System.getenv("GPR_TOKEN")?.takeUnless(String::isBlank)
                    ?: System.getenv("GITHUB_TOKEN")?.takeUnless(String::isBlank)
                    ?: providers.gradleProperty("gpr.key").orNull
            }
        }
    }
}
rootProject.name = "BrightMarket"
include(":app")
