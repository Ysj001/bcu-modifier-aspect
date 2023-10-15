plugins {
    `kotlin-dsl`
}

private val reposDir = File(rootDir, "../repos")

repositories {
    maven { url = reposDir.toURI() }
    maven { setUrl("https://maven.aliyun.com/nexus/content/groups/public/") }
    google()
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(gradleKotlinDsl())
    implementation("com.android.tools.build:gradle-api:8.1.1")
    implementation("io.github.ysj001.bcu:plugin:2.0.1")
    val properties = org.jetbrains.kotlin
        .konan.properties
        .loadProperties(File(rootDir, "../gradle.properties").absolutePath)
    val groupId = properties["bcu.groupId"] as String
    val modifierAspectVersion = properties["bcu.modifier.aspect.version"] as String
    val hasPlugin = groupId
        .replace(".", File.separator)
        .let { File(reposDir, it) }
        .run { isDirectory && !list().isNullOrEmpty() }
    if (hasPlugin) {
        implementation("$groupId:modifier-aspect:$modifierAspectVersion")
    }
}
