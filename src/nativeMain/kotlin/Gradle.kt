package dev.yuyuyuyuyu.kotlinstall

import okio.Path
import okio.Path.Companion.toPath

object Gradle {
    fun isProject(directory: Path) =
        listOf("settings.gradle.kts", "settings.gradle", "build.gradle.kts", "build.gradle").any { files.exists(directory / it) }

    fun build(project: Path, work: Path, host: Host): List<Command> {
        val script = work / "kotlinstall.init.gradle"
        files.write(script) { writeUtf8(initScript) }
        val manifest = work / "gradle-executables.txt"
        val gradle = if (isRegularFile(project / "gradlew")) listOf("sh", "gradlew") else listOf("gradle")
        execute(
            gradle + listOf(
                "--no-daemon",
                "--init-script", script.toString(),
                "-Dorg.gradle.configuration-cache=false",
                "-Dorg.gradle.configureondemand=false",
                "-Pkotlinstall.manifest=$manifest",
                "-Pkotlinstall.host=${host.konanTarget}",
                "kotlinstall",
            ),
            project,
        )
        if (!isRegularFile(manifest)) fail("Gradle did not report the executables of the project")
        return files.read(manifest) { readUtf8() }.lines().filter { it.isNotBlank() }.map { it.toPath() }
            .map { Command(it.name.removeSuffix(".kexe"), it) }
    }
}

private val initScript = """
    def parameters = gradle.startParameter.projectProperties
    def manifest = new File(parameters['kotlinstall.manifest'])
    def host = parameters['kotlinstall.host']
    def entries = []
    def required = []

    def isExecutable = { binary ->
        def type = binary.getClass()
        while (type != null) {
            if (type.name == 'org.jetbrains.kotlin.gradle.plugin.mpp.Executable') {
                return true
            }
            type = type.superclass
        }
        false
    }

    gradle.projectsEvaluated {
        gradle.rootProject.allprojects.each { project ->
            def kotlin = project.extensions.findByName('kotlin')
            if (kotlin != null && kotlin.hasProperty('targets')) {
                kotlin.targets.each { target ->
                    if (target.hasProperty('konanTarget') && target.konanTarget.name == host) {
                        target.binaries.each { binary ->
                            if (isExecutable(binary) && binary.buildType.name() == 'RELEASE') {
                                required << project.tasks.named(binary.linkTaskName)
                                entries << binary.outputFile.absolutePath
                            }
                        }
                    }
                }
            }
        }
    }

    gradle.rootProject { root ->
        root.tasks.register('kotlinstall') {
            dependsOn { required }
            doLast {
                manifest.text = entries.join('\n')
            }
        }
    }
""".trimIndent()
