plugins {
    `java-library`
}

dependencies {
    api(project(":transaction-guard-spring"))
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.boot:spring-boot-restclient")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("org.springframework.boot:spring-boot-restclient")
    testImplementation("org.springframework:spring-test")
    testImplementation("org.assertj:assertj-core")
}
