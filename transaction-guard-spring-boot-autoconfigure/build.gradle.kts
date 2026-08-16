plugins {
    `java-library`
}

dependencies {
    api(project(":transaction-guard-spring"))
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
}
