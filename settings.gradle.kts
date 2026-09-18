// The settings file is the first thing Gradle reads. It defines *which* projects exist.
// (build.gradle.kts files then define what each project *does*.)

rootProject.name = "data-quality-rule-engine"

// dq-engine       -> the library we are delivering. This is the product.
// dq-example-api  -> a Spring Boot host that embeds the library. Demo only, never published.
//
// The example is a separate module so that the library never drags a web server,
// an embedded Tomcat, or Spring Boot auto-configuration into a host that only
// wants to validate records (e.g. a batch worker or a scheduled job).
include("dq-engine")
include("dq-example-api")

dependencyResolutionManagement {
    // Declare repositories in exactly one place. FAIL_ON_PROJECT_REPOS makes it a
    // build error if a module tries to add its own repository later, which is how
    // an unvetted artifact source sneaks into a build.
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS

    repositories {
        mavenCentral()
    }
}
