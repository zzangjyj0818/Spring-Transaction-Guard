plugins {
    `java-library`
}

dependencies {
    api(project(":transaction-guard-spring"))
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.boot:spring-boot-restclient")
    compileOnly("io.github.openfeign:feign-core:${rootProject.extra["openFeignVersion"]}")
    compileOnly("io.micrometer:micrometer-core")
    compileOnly("org.springframework.boot:spring-boot-actuator")
    compileOnly("org.springframework.boot:spring-boot-actuator-autoconfigure")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("org.springframework.boot:spring-boot-restclient")
    testImplementation("org.springframework:spring-test")
    testImplementation("io.github.openfeign:feign-core:${rootProject.extra["openFeignVersion"]}")
    testImplementation("io.micrometer:micrometer-core")
    testImplementation("org.springframework.boot:spring-boot-actuator")
    testImplementation("org.springframework.boot:spring-boot-actuator-autoconfigure")
    testImplementation("org.assertj:assertj-core")
}
