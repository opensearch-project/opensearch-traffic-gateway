/*
 * This file was generated by the Gradle 'init' task.
 */

plugins {
    // Apply the java Plugin to add support for Java.
    java

    // test coverage
    jacoco

    // auto formatting
    id("com.diffplug.spotless")

    // lombok
    id("io.freefair.lombok")
}

group = "org.opensearch.trafficgateway"
version = "0.0.1"

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

val opensearchMigrationsVersion: String by rootProject.extra("0.1.0.6")
val log4jVersion: String by rootProject.extra("2.22.1")

dependencies {
    constraints {
        implementation("org.apache.commons:commons-text:1.11.0")
        implementation("org.apache.commons:commons-lang3:3.14.0")
        implementation("io.netty:netty-all:4.1.115.Final")
        implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
        implementation("io.burt:jmespath-jackson:0.6.0")
    }

    // log4j
    implementation(platform("org.apache.logging.log4j:log4j-bom:${log4jVersion}"))
    implementation("org.apache.logging.log4j:log4j-core")
    implementation("org.apache.logging.log4j:log4j-api")
    implementation("org.apache.logging.log4j:log4j-layout-template-json")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.30")

    // get rid of FindBugs warnings
    testImplementation("com.google.code.findbugs:findbugs-annotations:3.0.1")

    // Use JUnit Jupiter for testing.
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // mockito
    testImplementation(platform("org.mockito:mockito-bom:5.10.0"))
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.mockito:mockito-junit-jupiter")

    // assertj
    testImplementation(platform("org.assertj:assertj-bom:3.25.3"))
    testImplementation("org.assertj:assertj-core")
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}



tasks.test {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
    finalizedBy(tasks.jacocoTestCoverageVerification)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)

    violationRules {
        rule {
            limit {
                minimum = "0.1".toBigDecimal()  // TODO
            }
        }
    }
}

spotless {
    java {
        palantirJavaFormat()
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror", "-parameters"))
}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<Tar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<Zip> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
