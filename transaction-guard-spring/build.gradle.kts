plugins {
    `java-library`
}

dependencies {
    api(project(":transaction-guard-core"))
    implementation("org.springframework:spring-aop")
    implementation("org.springframework:spring-tx")
}
