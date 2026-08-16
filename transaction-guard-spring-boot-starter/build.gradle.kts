plugins {
    `java-library`
}

dependencies {
    api(project(":transaction-guard-spring-boot-autoconfigure"))
    api("org.springframework.boot:spring-boot-starter")
}
