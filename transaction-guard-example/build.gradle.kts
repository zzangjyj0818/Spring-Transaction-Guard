plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":transaction-guard-spring-boot-starter"))
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.github.openfeign:feign-core:${rootProject.extra["openFeignVersion"]}")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
