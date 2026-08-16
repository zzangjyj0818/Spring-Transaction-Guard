plugins {
    base
    id("org.springframework.boot") version "4.1.0" apply false
    id("com.vanniktech.maven.publish") version "0.37.0" apply false
}

group = "io.github.zzangjyj0818"
version = "0.3.0-SNAPSHOT"

val springBootVersion = "4.1.0"
val openFeignVersion = "13.13"

subprojects {
    apply(plugin = "java-library")

    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
    }

    dependencies {
        "api"(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
        "annotationProcessor"(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
        "testImplementation"(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    if (name != "transaction-guard-example") {
        apply(plugin = "com.vanniktech.maven.publish")
    }
}

extra["openFeignVersion"] = openFeignVersion
