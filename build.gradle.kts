plugins {
    kotlin("jvm") version "1.4.21"
    `maven-publish`
}

group = "v3"
version = "1.5.0"

repositories {
    jcenter()
    maven { url = uri("https://kotlin.bintray.com/ktor") }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.4.21")
    implementation("io.ktor:ktor-server-core:$version")
    implementation("io.ktor:ktor-websockets:$version")
    implementation("io.ktor:ktor-gson:$version")
    implementation("ch.qos.logback:logback-classic:1.2.1")

    implementation("com.expediagroup:graphql-kotlin-server:4.0.0-alpha.11")

    testImplementation("io.ktor:ktor-server-tests:$version")
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/V3lop5/graphql-ktor-feature")
            credentials {
                username = project.findProperty("gpr.username") as String? ?: System.getenv("USERNAME")
                password = project.findProperty("gpr.token") as String? ?: System.getenv("TOKEN")
            }
        }
    }
    publications {
        create<MavenPublication>("gpr") {
            from(components["kotlin"])
        }
    }
}