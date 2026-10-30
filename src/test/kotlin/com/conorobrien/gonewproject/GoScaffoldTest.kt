package com.conorobrien.gonewproject

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class GoScaffoldTest {

    @Test
    fun `module path validation`() {
        assertNull(GoModule.validateModulePath("myapp"))
        assertNull(GoModule.validateModulePath("github.com/conor/my-app/v2"))
        assertNotNull(GoModule.validateModulePath(""))
        assertNotNull(GoModule.validateModulePath("my app"))
        assertNotNull(GoModule.validateModulePath("/abs"))
        assertNotNull(GoModule.validateModulePath("a//b"))
        assertNotNull(GoModule.validateModulePath("a/.hidden"))
        assertNotNull(GoModule.validateModulePath("a/b@v1"))
    }

    @Test
    fun `go version validation`() {
        listOf("1.21", "1.25.3", "1.26rc1", "1.26beta2").forEach { assertNull(it, GoModule.validateGoVersion(it)) }
        listOf("go1.25", "1.25.", "2.0", "1.025", "latest").forEach { assertNotNull(it, GoModule.validateGoVersion(it)) }
    }

    @Test
    fun `package names`() {
        assertEquals("strutil", GoModule.packageName("github.com/conor/strutil"))
        assertEquals("gothing", GoModule.packageName("github.com/conor/go-thing"))
        assertEquals("svc", GoModule.packageName("github.com/conor/svc/v2"))
        assertEquals("lib", GoModule.packageName("example.com/123"))
        assertEquals("lib", GoModule.packageName("example.com/main"))
    }

    @Test
    fun `version line parsing`() {
        assertEquals("1.24.7", GoSdkDetector.parseVersionLine("go1.24.7"))
        assertEquals("1.26rc1", GoSdkDetector.parseVersionLine("go1.26rc1\n"))
        assertNull(GoSdkDetector.parseVersionLine("devel go1.26-abcdef"))
    }

    @Test
    fun `versions sort newest first with prereleases below releases`() {
        val sorted = listOf("1.24.7", "1.26rc1", "1.25.10", "1.26.0", "1.25.2")
            .sortedByDescending { GoSdkDetector.versionKey(it) }
        assertEquals(listOf("1.26.0", "1.26rc1", "1.25.10", "1.25.2", "1.24.7"), sorted)
    }

    @Test
    fun `detects an sdk from a VERSION file`() {
        val root = Files.createTempDirectory("goroot")
        Files.writeString(root.resolve("VERSION"), "go1.25.3\ntime 2026-09-01T00:00:00Z\n")
        val sdk = GoSdkDetector.sdkAt(root)
        assertEquals("1.25.3", sdk?.version)
    }

    @Test
    fun `library template aliases import only when needed`() {
        val aliased = GoScaffold.files(spec("github.com/conor/go-thing", GoTemplate.LIBRARY))
        assertTrue(aliased.getValue("gothing_test.go").contains("gothing \"github.com/conor/go-thing\""))

        val plain = GoScaffold.files(spec("github.com/conor/strutil", GoTemplate.LIBRARY))
        assertTrue(plain.getValue("strutil_test.go").contains("\t\"github.com/conor/strutil\""))
    }

    @Test
    fun `write never overwrites existing files`() {
        val dir = Files.createTempDirectory("goproj")
        Files.writeString(dir.resolve("main.go"), "package main // mine\n")

        val written = GoScaffold.write(dir, spec("example.com/hello", GoTemplate.CONSOLE))

        assertFalse("main.go" in written)
        assertEquals("package main // mine\n", Files.readString(dir.resolve("main.go")))
        assertEquals("module example.com/hello\n\ngo 1.25.3\n", Files.readString(dir.resolve("go.mod")))
        assertTrue("main_test.go" in written)
    }

    private fun spec(modulePath: String, template: GoTemplate) =
        GoProjectSpec(projectName = "demo", modulePath = modulePath, goVersion = "1.25.3", template = template)
}
