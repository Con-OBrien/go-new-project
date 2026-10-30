package com.conorobrien.gonewproject

import java.nio.file.Files
import java.nio.file.Path

enum class GoTemplate(val label: String) {
    CONSOLE("Console application"),
    LIBRARY("Library"),
    HTTP_SERVICE("HTTP service");

    override fun toString(): String = label
}

data class GoProjectSpec(
    val projectName: String,
    val modulePath: String,
    val goVersion: String,
    val template: GoTemplate,
)

/** Rules for module paths and go directives, a pragmatic subset of golang.org/x/mod/module.CheckPath. */
object GoModule {

    /** Used when no Go installation is found. Users can still edit it in the dialog. */
    const val FALLBACK_GO_VERSION = "1.25.0"

    private val elementChars = Regex("""^[A-Za-z0-9._~-]+$""")
    private val goVersionPattern = Regex("""^1\.(0|[1-9]\d*)(\.(0|[1-9]\d*))?((rc|beta)[1-9]\d*)?$""")
    private val majorVersionSuffix = Regex("""^v[0-9]+$""")

    /** Returns an error message, or null when [path] is a usable module path. */
    fun validateModulePath(path: String): String? {
        if (path.isEmpty()) return "Enter a module path"
        if (path.any { it.isWhitespace() }) return "Module path can't contain spaces"
        if (path.startsWith("/") || path.endsWith("/")) return "Module path can't start or end with '/'"
        for (element in path.split("/")) {
            if (element.isEmpty()) return "Module path can't contain '//'"
            if (!elementChars.matches(element)) {
                return "'$element' can only use letters, digits and . _ ~ -"
            }
            if (element.startsWith(".") || element.endsWith(".")) {
                return "'$element' can't start or end with '.'"
            }
        }
        return null
    }

    /** Returns an error message, or null when [version] is valid for a go directive (e.g. 1.25, 1.25.3, 1.26rc1). */
    fun validateGoVersion(version: String): String? =
        if (goVersionPattern.matches(version)) null else "Use a Go version like 1.25 or 1.25.3"

    /** The last meaningful path element, skipping a /vN major-version suffix. */
    fun lastElement(modulePath: String): String {
        val elements = modulePath.split("/").filter { it.isNotEmpty() }
        val last = elements.lastOrNull() ?: return ""
        return if (majorVersionSuffix.matches(last) && elements.size > 1) elements[elements.size - 2] else last
    }

    /** A valid Go package name derived from the module path: "github.com/me/go-thing" -> "gothing". */
    fun packageName(modulePath: String): String {
        val cleaned = lastElement(modulePath).lowercase().filter { it in 'a'..'z' || it in '0'..'9' }
        return if (cleaned.isEmpty() || cleaned.first().isDigit() || cleaned in goKeywords) "lib" else cleaned
    }

    private val goKeywords = setOf(
        "break", "case", "chan", "const", "continue", "default", "defer", "else", "fallthrough", "for",
        "func", "go", "goto", "if", "import", "interface", "map", "package", "range", "return",
        "select", "struct", "switch", "type", "var", "main",
    )
}

/** Builds and writes the starter files. No IntelliJ dependencies, so it can be unit tested. */
object GoScaffold {

    /** Relative path -> contents, in the order they should be written. */
    fun files(spec: GoProjectSpec): Map<String, String> {
        val files = LinkedHashMap<String, String>()
        files["go.mod"] = goMod(spec)
        when (spec.template) {
            GoTemplate.CONSOLE -> {
                files["main.go"] = consoleMain()
                files["main_test.go"] = consoleTest()
            }
            GoTemplate.LIBRARY -> {
                val pkg = GoModule.packageName(spec.modulePath)
                files["$pkg.go"] = libraryMain(pkg)
                files["${pkg}_test.go"] = libraryTest(pkg, spec.modulePath)
            }
            GoTemplate.HTTP_SERVICE -> {
                files["main.go"] = httpMain()
                files["main_test.go"] = httpTest()
            }
        }
        files[".gitignore"] = gitignore(spec)
        files["README.md"] = readme(spec)
        return files
    }

    /** The file to open in the editor once the project is ready. */
    fun primaryFile(spec: GoProjectSpec): String = when (spec.template) {
        GoTemplate.LIBRARY -> "${GoModule.packageName(spec.modulePath)}.go"
        else -> "main.go"
    }

    /**
     * Writes the files under [dir]. Existing files are left untouched, so creating a
     * project in a folder that already has code never overwrites it.
     * Returns the relative paths that were written.
     */
    fun write(dir: Path, spec: GoProjectSpec): List<String> {
        Files.createDirectories(dir)
        val written = mutableListOf<String>()
        for ((relative, content) in files(spec)) {
            val target = dir.resolve(relative)
            if (Files.exists(target)) continue
            target.parent?.let { Files.createDirectories(it) }
            Files.writeString(target, content)
            written.add(relative)
        }
        return written
    }

    private fun goMod(spec: GoProjectSpec) = """
        |module ${spec.modulePath}
        |
        |go ${spec.goVersion}
        |""".trimMargin()

    private fun consoleMain() = """
        |package main
        |
        |import (
        |	"fmt"
        |	"os"
        |)
        |
        |func main() {
        |	name := "world"
        |	if len(os.Args) > 1 {
        |		name = os.Args[1]
        |	}
        |	fmt.Println(greeting(name))
        |}
        |
        |func greeting(name string) string {
        |	return fmt.Sprintf("Hello, %s!", name)
        |}
        |""".trimMargin()

    private fun consoleTest() = """
        |package main
        |
        |import "testing"
        |
        |func TestGreeting(t *testing.T) {
        |	tests := []struct {
        |		name string
        |		in   string
        |		want string
        |	}{
        |		{name: "default", in: "world", want: "Hello, world!"},
        |		{name: "custom", in: "Gopher", want: "Hello, Gopher!"},
        |	}
        |	for _, tt := range tests {
        |		t.Run(tt.name, func(t *testing.T) {
        |			if got := greeting(tt.in); got != tt.want {
        |				t.Errorf("greeting(%q) = %q, want %q", tt.in, got, tt.want)
        |			}
        |		})
        |	}
        |}
        |""".trimMargin()

    private fun libraryMain(pkg: String) = """
        |// Package $pkg is a starter library.
        |package $pkg
        |
        |import "fmt"
        |
        |// Hello returns a greeting for name, or for the world when name is empty.
        |func Hello(name string) string {
        |	if name == "" {
        |		name = "world"
        |	}
        |	return fmt.Sprintf("Hello, %s!", name)
        |}
        |""".trimMargin()

    private fun libraryTest(pkg: String, modulePath: String): String {
        // Alias the import only when the package name differs from the path's last element.
        val import = if (GoModule.lastElement(modulePath) == pkg) "\"$modulePath\"" else "$pkg \"$modulePath\""
        return """
            |package ${pkg}_test
            |
            |import (
            |	"fmt"
            |	"testing"
            |
            |	$import
            |)
            |
            |func TestHello(t *testing.T) {
            |	tests := []struct {
            |		name string
            |		in   string
            |		want string
            |	}{
            |		{name: "empty", in: "", want: "Hello, world!"},
            |		{name: "named", in: "Gopher", want: "Hello, Gopher!"},
            |	}
            |	for _, tt := range tests {
            |		t.Run(tt.name, func(t *testing.T) {
            |			if got := $pkg.Hello(tt.in); got != tt.want {
            |				t.Errorf("Hello(%q) = %q, want %q", tt.in, got, tt.want)
            |			}
            |		})
            |	}
            |}
            |
            |func ExampleHello() {
            |	fmt.Println($pkg.Hello("Gopher"))
            |	// Output: Hello, Gopher!
            |}
            |""".trimMargin()
    }

    private fun httpMain() = """
        |package main
        |
        |import (
        |	"context"
        |	"errors"
        |	"log"
        |	"net/http"
        |	"os"
        |	"os/signal"
        |	"syscall"
        |	"time"
        |)
        |
        |func main() {
        |	addr := ":8080"
        |	if port := os.Getenv("PORT"); port != "" {
        |		addr = ":" + port
        |	}
        |
        |	srv := &http.Server{
        |		Addr:              addr,
        |		Handler:           newMux(),
        |		ReadHeaderTimeout: 5 * time.Second,
        |	}
        |
        |	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
        |	defer stop()
        |
        |	go func() {
        |		log.Printf("listening on %s", addr)
        |		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
        |			log.Fatalf("server error: %v", err)
        |		}
        |	}()
        |
        |	<-ctx.Done()
        |	log.Println("shutting down")
        |
        |	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
        |	defer cancel()
        |	if err := srv.Shutdown(shutdownCtx); err != nil {
        |		log.Printf("shutdown error: %v", err)
        |	}
        |}
        |
        |func newMux() *http.ServeMux {
        |	mux := http.NewServeMux()
        |	mux.HandleFunc("/healthz", func(w http.ResponseWriter, r *http.Request) {
        |		if r.Method != http.MethodGet {
        |			w.Header().Set("Allow", http.MethodGet)
        |			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
        |			return
        |		}
        |		w.Header().Set("Content-Type", "text/plain; charset=utf-8")
        |		_, _ = w.Write([]byte("ok\n"))
        |	})
        |	return mux
        |}
        |""".trimMargin()

    private fun httpTest() = """
        |package main
        |
        |import (
        |	"net/http"
        |	"net/http/httptest"
        |	"testing"
        |)
        |
        |func TestHealthz(t *testing.T) {
        |	tests := []struct {
        |		name       string
        |		method     string
        |		wantStatus int
        |		wantBody   string
        |	}{
        |		{name: "get", method: http.MethodGet, wantStatus: http.StatusOK, wantBody: "ok\n"},
        |		{name: "post", method: http.MethodPost, wantStatus: http.StatusMethodNotAllowed},
        |	}
        |	for _, tt := range tests {
        |		t.Run(tt.name, func(t *testing.T) {
        |			req := httptest.NewRequest(tt.method, "/healthz", nil)
        |			rec := httptest.NewRecorder()
        |
        |			newMux().ServeHTTP(rec, req)
        |
        |			if rec.Code != tt.wantStatus {
        |				t.Fatalf("status = %d, want %d", rec.Code, tt.wantStatus)
        |			}
        |			if tt.wantBody != "" && rec.Body.String() != tt.wantBody {
        |				t.Errorf("body = %q, want %q", rec.Body.String(), tt.wantBody)
        |			}
        |		})
        |	}
        |}
        |""".trimMargin()

    private fun gitignore(spec: GoProjectSpec): String {
        val binary = if (spec.template == GoTemplate.LIBRARY) "" else "/${GoModule.lastElement(spec.modulePath)}\n"
        return """
            |# Binaries
            |${binary}/bin/
            |*.exe
            |*.exe~
            |*.dll
            |*.so
            |*.dylib
            |
            |# Test output
            |*.test
            |*.out
            |coverage.*
            |
            |# Local workspace and environment
            |go.work
            |go.work.sum
            |.env
            |""".trimMargin()
    }

    private fun readme(spec: GoProjectSpec): String {
        val usage = when (spec.template) {
            GoTemplate.CONSOLE -> "```sh\ngo run . Gopher\n```"
            GoTemplate.HTTP_SERVICE -> "```sh\ngo run .\ncurl localhost:8080/healthz\n```"
            GoTemplate.LIBRARY -> "```go\nimport \"${spec.modulePath}\"\n```"
        }
        val heading = if (spec.template == GoTemplate.LIBRARY) "Usage" else "Run"
        return "# ${spec.projectName}\n\n## $heading\n\n$usage\n\n## Test\n\n```sh\ngo test ./...\n```\n"
    }
}
