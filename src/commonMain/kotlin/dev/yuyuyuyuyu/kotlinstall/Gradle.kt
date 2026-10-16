package dev.yuyuyuyuyu.kotlinstall

import okio.Path
import okio.Path.Companion.toPath

data class Build(
    val project: String,
    val commands: List<Command>,
)

object Gradle {
    fun isProject(directory: Path) =
        listOf("settings.gradle.kts", "settings.gradle", "build.gradle.kts", "build.gradle")
            .any { files.exists(directory / it) }

    fun build(
        project: Path,
        work: Path,
        host: Host,
    ): Build {
        val script = work / "kotlinstall.init.gradle"
        files.write(script) { writeUtf8(initScript) }
        val manifest = work / "gradle-executables.txt"
        val name = work / "gradle-project.txt"
        val gradle = if (isRegularFile(project / "gradlew")) listOf("sh", "gradlew") else listOf("gradle")
        execute(
            gradle +
                listOf(
                    "--no-daemon",
                    "--init-script",
                    script.toString(),
                    "-Pkotlinstall.manifest=$manifest",
                    "-Pkotlinstall.name=$name",
                    "-Pkotlinstall.host=${host.konanTarget}",
                    "kotlinstall",
                ),
            project,
        )
        if (!isRegularFile(manifest) || !isRegularFile(name)) {
            fail("Gradle did not report the name and the executables of the project")
        }
        return Build(
            files.read(name) { readUtf8() },
            files
                .read(manifest) { readUtf8() }
                .lines()
                .filter { it.isNotBlank() }
                .map { it.toPath() }
                .map { Command(it.name.removeSuffix(".kexe"), it) },
        )
    }
}

private val initScript =
    """
    def parameters = gradle.startParameter.projectProperties
    def manifest = new File(parameters['kotlinstall.manifest'])
    def name = new File(parameters['kotlinstall.name'])
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
                name.text = root.name
            }
        }
    }
    """.trimIndent()
