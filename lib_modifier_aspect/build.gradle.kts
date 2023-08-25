plugins {
    id("java-library")
    id("kotlin")
}

group = properties["bcu.groupId"] as String
version = "1.0.0-beta"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project("aspect-api"))
    implementation(project(":lib_bcu_plugin:plugin-api"))
}

mavenPublish()