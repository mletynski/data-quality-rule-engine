plugins {
    // Plain `java`, not `java-library`: nothing depends on this module, so the
    // api/implementation distinction buys us nothing here.
    java
    // The Spring Boot plugin adds `bootRun` and builds an executable fat jar.
    alias(libs.plugins.spring.boot)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Import Spring Boot's BOM as a Gradle platform. This is what lets us write
    // starter dependencies below with no version: Boot decides the versions of
    // Spring, Tomcat, Jackson, SLF4J and ~400 other libraries, consistently.
    //
    // Note we do NOT use the `io.spring.dependency-management` plugin that older
    // Spring guides reach for. Gradle has native `platform()` support now; one
    // fewer plugin, same result.
    implementation(platform(libs.spring.boot.dependencies))
    implementation("org.springframework.boot:spring-boot-starter-web")

    // The whole point of this module: embed the library exactly as a real host would.
    implementation(project(":dq-engine"))

    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-parameters"))
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
