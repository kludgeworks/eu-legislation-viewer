rootProject.name = "eu.legislation.cli"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("gradle/libs/libs.versions.toml"))
        }
    }
}

// Note: We can't use TYPESAFE_PROJECT_ACCESSORS because the project name doesn't follow the naming convention
