plugins {
    `java-library`
}

dependencies {
    api(project(":transaction-guard-core"))
    api("org.springframework:spring-aop")
    api("org.springframework:spring-web")
    api("org.springframework:spring-tx")
    api("org.aspectj:aspectjweaver")
    compileOnly("io.github.openfeign:feign-core:${rootProject.extra["openFeignVersion"]}")

    testImplementation("org.springframework:spring-context")
    testImplementation("org.springframework:spring-jdbc")
    testImplementation("org.springframework:spring-test")
    testImplementation("io.github.openfeign:feign-core:${rootProject.extra["openFeignVersion"]}")
    testRuntimeOnly("com.h2database:h2")
}
