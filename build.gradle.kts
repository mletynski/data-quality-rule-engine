// The root project contains no Java code. It only owns coordinates shared by
// every module, so that `dq-engine` and `dq-example-api` are versioned together.
//
// Deliberately NOT doing cross-project configuration here (no `subprojects { ... }`
// block). Each module configures itself. With two modules the small amount of
// duplication is cheaper to read than the indirection, and Gradle's newer
// isolated-projects model discourages one project reaching into another.

allprojects {
    group = "com.dq"
    version = "0.1.0-SNAPSHOT"
}
