plugins {
    `java-library`
}

dependencies {
    api(project(":transaction-guard-core"))
    implementation("org.springframework:spring-aop")
    implementation("org.springframework:spring-web")
    implementation("org.springframework:spring-tx")
    implementation("org.aspectj:aspectjweaver")

    testImplementation("org.springframework:spring-context")
    testImplementation("org.springframework:spring-jdbc")
    testImplementation("org.springframework:spring-test")
    testRuntimeOnly("com.h2database:h2")
}
