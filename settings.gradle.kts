pluginManagement {
    repositories {
        val localProxy = System.getenv("CODEX_MAVEN_PROXY")
        if (!localProxy.isNullOrBlank()) {
            maven {
                url = uri("$localProxy/repo")
                isAllowInsecureProtocol = true
                metadataSources { mavenPom(); artifact() }
            }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        val localProxy = System.getenv("CODEX_MAVEN_PROXY")
        if (!localProxy.isNullOrBlank()) {
            maven {
                url = uri("$localProxy/repo")
                isAllowInsecureProtocol = true
                metadataSources { mavenPom(); artifact() }
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "AIPCC"
include(":app")
