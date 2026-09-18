plugins {
    // `java-library`, not `java`. The difference is the `api` vs `implementation`
    // distinction below -- a plain `java` project cannot express it. Any project
    // meant to be depended on by others should use `java-library`.
    `java-library`
}

java {
    // A toolchain pins the JDK used to compile and test, independently of whatever
    // JDK happens to be on the developer's PATH. Without it, the build silently
    // produces different bytecode on different machines.
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }

    // Publish sources and javadoc alongside the jar. For a library this is not a
    // nicety: it is how the person embedding it reads the API from their IDE.
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    // ONE third-party dependency. That is the whole dependency footprint a host takes
    // on by embedding this library.
    //
    // There is deliberately no JSON library here. Evaluating a stored expression is
    // *intrinsic* to what a rule engine does -- remove it and there is no library left.
    // Parsing a wire format is not: records reach different hosts as JSON, as Avro, as
    // JDBC rows. So JSON lives in the host (see dq-example-api), behind the
    // `DataRecord` seam, and the engine cannot even see it. The proof is mechanical:
    // no JSON library is on this module's compile classpath, so a JSON import here
    // would not compile.
    //
    // `implementation` rather than `api`: SpEL does not appear in any public
    // signature, so it is not forced onto consumers' compile classpath. Promoting it
    // to `api` would have to be a conscious act -- and the moment to ask whether we
    // should be exposing a Spring type at all.
    implementation(libs.spring.expression)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)

    // JUnit's engine is discovered at runtime by the platform launcher. Declaring it
    // explicitly (rather than relying on Gradle injecting it) keeps the test runtime
    // reproducible.
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    // Never depend on the build machine's default charset.
    options.encoding = "UTF-8"
    // -Xlint:all surfaces the compiler warnings that are off by default.
    // -parameters keeps real parameter names in the bytecode, which record-style
    // APIs and any future reflection-based binding depend on.
    options.compilerArgs.addAll(listOf("-Xlint:all", "-parameters"))
}

tasks.javadoc {
    // "doclint" is javadoc's own validator. We keep every check except `missing`:
    //   - kept: broken {@link} targets, malformed HTML, bad @param/@return names.
    //     These are real defects -- a broken @link means the API docs lie.
    //   - dropped: "no comment" on record accessors and canonical constructors, which
    //     are already documented by the record's own @param tags.
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:all,-missing", "-quiet")
    }
}

tasks.test {
    useJUnitPlatform()

    // A deliberately small heap. The million-record test is meaningless on a large one:
    // an engine that accumulated results would simply succeed slowly. At 256 MB it
    // cannot -- accumulating even a fraction of the outcomes exhausts the heap, so the
    // test fails loudly if bounded memory ever regresses.
    maxHeapSize = "256m"

    testLogging {
        events("passed", "skipped", "failed")
    }
}
