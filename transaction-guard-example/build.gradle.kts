plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":transaction-guard-spring-boot-starter"))
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    runtimeOnly("com.h2database:h2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
