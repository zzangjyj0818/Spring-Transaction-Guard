pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "spring-transaction-guard"

include(
    "transaction-guard-core",
    "transaction-guard-spring",
    "transaction-guard-spring-boot-autoconfigure",
    "transaction-guard-spring-boot-starter",
    "transaction-guard-example",
)
