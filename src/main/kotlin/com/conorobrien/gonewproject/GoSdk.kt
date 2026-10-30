package com.conorobrien.gonewproject

import java.nio.file.Files
import java.nio.file.Path

/** A Go installation found on disk. [version] has no "go" prefix, e.g. "1.25.3". */
data class GoSdk(val root: Path, val version: String)

/**
 * Finds Go installations without running any processes, so it is safe to call
 * while the New Project dialog is open. A Go root is identified by its VERSION file.
 */
object GoSdkDetector {

    fun detect(
        env: Map<String, String>,
        home: Path,
        isWindows: Boolean,
    ): List<GoSdk> {
        val candidates: MutableSet<Path> = LinkedHashSet()

        env["GOROOT"]?.takeIf { it.isNotBlank() }?.let { candidates.add(Path.of(it)) }

        // Each PATH entry that contains a go binary: <root>/bin/go -> <root>.
        val exe = if (isWindows) "go.exe" else "go"
        val separator = if (isWindows) ";" else ":"
        env["PATH"].orEmpty().split(separator).filter { it.isNotBlank() }.forEach { dir ->
            rootFromExecutable(safePath(dir)?.resolve(exe))?.let { candidates.add(it) }
        }

        candidates.addAll(fixedRoots(home, isWindows, env))

        // golang.org/dl, goenv, asdf and mise keep one root per version.
        versionedParents(home).forEach { parent -> candidates.addAll(childDirs(parent)) }
        childDirs(home.resolve(".asdf/installs/golang")).forEach { candidates.add(it.resolve("go")) }

        return candidates
            .mapNotNull { root -> sdkAt(root) }
            .distinctBy { it.root }
            .sortedWith(compareByDescending<GoSdk> { versionKey(it.version) }.thenBy { it.root.toString() })
    }

    /** Reads `<root>/VERSION` and returns an SDK if it looks like a Go root. */
    fun sdkAt(root: Path): GoSdk? {
        val real = try {
            root.toRealPath()
        } catch (_: Exception) {
            return null
        }
        val versionFile = real.resolve("VERSION")
        if (!Files.isRegularFile(versionFile)) return null
        val firstLine = try {
            Files.newBufferedReader(versionFile).use { it.readLine() }
        } catch (_: Exception) {
            null
        } ?: return null
        val version = parseVersionLine(firstLine) ?: return null
        return GoSdk(real, version)
    }

    /** "go1.25.3" -> "1.25.3"; "devel go1.26-abc" and anything else -> null. */
    fun parseVersionLine(line: String): String? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("go1")) return null
        val version = trimmed.removePrefix("go")
        return version.takeIf { GoModule.validateGoVersion(it) == null }
    }

    /** Sort key: numeric parts, with pre-releases (rc/beta) below the final release. */
    fun versionKey(version: String): VersionKey {
        val match = Regex("""^(\d+)\.(\d+)(?:\.(\d+))?(?:(rc|beta)(\d+))?$""").find(version)
            ?: return VersionKey(0, 0, 0, 0, 0)
        val (major, minor, patch, kind, n) = match.destructured
        val stage = when (kind) {
            "beta" -> 0
            "rc" -> 1
            else -> 2
        }
        return VersionKey(major.toInt(), minor.toInt(), patch.toIntOrNull() ?: 0, stage, n.toIntOrNull() ?: 0)
    }

    data class VersionKey(val major: Int, val minor: Int, val patch: Int, val stage: Int, val n: Int) :
        Comparable<VersionKey> {
        override fun compareTo(other: VersionKey): Int =
            compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch }, { it.stage }, { it.n })
    }

    private fun rootFromExecutable(executable: Path?): Path? {
        if (executable == null || !Files.isRegularFile(executable)) return null
        return try {
            // Resolves Homebrew and distro symlinks, e.g. /opt/homebrew/bin/go -> .../libexec/bin/go.
            executable.toRealPath().parent?.parent
        } catch (_: Exception) {
            null
        }
    }

    private fun fixedRoots(home: Path, isWindows: Boolean, env: Map<String, String>): List<Path> =
        if (isWindows) {
            listOfNotNull(
                Path.of("C:\\Program Files\\Go"),
                Path.of("C:\\Go"),
                env["LOCALAPPDATA"]?.let { safePath(it)?.resolve("Programs\\Go") },
                home.resolve("go\\go"),
            )
        } else {
            listOf(
                Path.of("/usr/local/go"),
                Path.of("/usr/lib/go"),
                Path.of("/opt/homebrew/opt/go/libexec"),
                Path.of("/usr/local/opt/go/libexec"),
                Path.of("/snap/go/current"),
                home.resolve(".local/go"),
            )
        }

    private fun versionedParents(home: Path): List<Path> = listOf(
        home.resolve("sdk"),
        home.resolve(".goenv/versions"),
        home.resolve(".local/share/mise/installs/go"),
    )

    private fun childDirs(dir: Path): List<Path> {
        if (!Files.isDirectory(dir)) return emptyList()
        return try {
            Files.list(dir).use { stream -> stream.filter { Files.isDirectory(it) }.toList() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun safePath(raw: String): Path? = try {
        Path.of(raw.trim().removeSurrounding("\""))
    } catch (_: Exception) {
        null
    }
}
