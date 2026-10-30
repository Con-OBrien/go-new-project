# Go New Project

Adds **Go** to *File | New | Project* in the free IntelliJ IDEA (2025.3+), without JetBrains' paid Go plugin.

The dialog asks for:

- **Module path** – blank uses the project name; otherwise e.g. `github.com/you/myapp`
- **Template** – console application, library, or HTTP service (`/healthz`, graceful shutdown)
- **Go version** – pre-filled from the newest Go installation found on the machine

It then writes `go.mod`, starter code with a table-driven test, `.gitignore` and `README.md`, creates a
plain module rooted at the folder, and opens the main file. The platform's own *Create Git repository*
checkbox still works. Existing files are never overwritten.

Go is found without running any processes, via `GOROOT`, `PATH` (using the login-shell environment, so a
Dock-launched IDE on macOS still finds Homebrew), and common locations: `/usr/local/go`, Homebrew,
`~/sdk/*` (golang.org/dl), goenv, asdf, mise, snap, and the Windows installer paths.

When JetBrains' own Go plugin is loaded (GoLand, or IDEA with a subscription), this generator hides itself
so there aren't two "Go" entries.

This plugin only creates projects. For editing, pair it with a gopls-based plugin such as
[Go Language Helper](https://plugins.jetbrains.com/plugin/34233-go-language-helper).

## Build and install

Requires JDK 21+ (Gradle downloads a matching toolchain if needed).

```sh
./gradlew test buildPlugin
```

Then in IntelliJ IDEA: *Settings | Plugins | ⚙ | Install Plugin from Disk…* and pick
`build/distributions/go-new-project-0.1.0.zip`.

To try it in a sandbox IDE instead: `./gradlew runIde`.

## Layout

| File | Purpose |
|---|---|
| `GoLanguageGenerator.kt` | Registers "Go" in the New Project language list |
| `GoNewProjectStep.kt` | Dialog fields, validation, file writing, module creation |
| `GoSdk.kt` | Finds Go installations by their `VERSION` file |
| `GoScaffold.kt` | Module-path rules and the starter file templates (no IntelliJ dependencies) |

## License

[MIT](LICENSE)
