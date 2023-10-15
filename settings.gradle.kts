rootProject.name = "ModifierAspect"

include(":lib_modifier_aspect", ":lib_modifier_aspect:aspect-api")

private val hasPlugin = File(rootDir, "gradle.properties")
    .inputStream()
    .use { java.util.Properties().apply { load(it) } }
    .getProperty("bcu.groupId")
    .replace(".", File.separator)
    .let { File(File(rootDir, "repos"), it) }
    .takeIf { it.isDirectory }
    ?.list()
    ?.filter { it == "modifier-aspect"}
    .isNullOrEmpty()
    .not()

if (hasPlugin) {
    // Demo
    include(":app")
}