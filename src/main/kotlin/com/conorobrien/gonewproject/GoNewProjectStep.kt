package com.conorobrien.gonewproject

import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.util.EnvironmentUtil
import java.nio.file.Path

/** The Go-specific part of the New Project dialog: module path, template and Go version. */
class GoNewProjectStep(parent: NewProjectWizardStep) : AbstractNewProjectWizardStep(parent) {

    // EnvironmentUtil returns the login-shell environment, which matters on macOS where an
    // IDE started from the Dock doesn't see the PATH that Homebrew or the Go installer set up.
    private val sdks: List<GoSdk> = GoSdkDetector.detect(
        env = EnvironmentUtil.getEnvironmentMap(),
        home = Path.of(System.getProperty("user.home")),
        isWindows = SystemInfo.isWindows,
    )

    private val modulePathProperty = propertyGraph.property("")
    private val templateProperty = propertyGraph.property(GoTemplate.CONSOLE)
    private val goVersionProperty = propertyGraph.property(sdks.firstOrNull()?.version ?: GoModule.FALLBACK_GO_VERSION)

    override fun setupUI(builder: Panel) {
        with(builder) {
            row("Module path:") {
                textField()
                    .bindText(modulePathProperty)
                    .align(AlignX.FILL)
                    .applyToComponent { emptyText.setText("Defaults to the project name") }
                    .validationOnInput { field ->
                        if (field.text.isBlank()) null else GoModule.validateModulePath(field.text.trim())?.let { error(it) }
                    }
                    .validationOnApply { field ->
                        GoModule.validateModulePath(effectiveModulePath(field.text))?.let {
                            error(if (field.text.isBlank()) "The project name isn't a valid module path. Enter one here." else it)
                        }
                    }
                    .comment("Usually where the code will be published, e.g. github.com/you/myapp")
            }
            row("Template:") {
                comboBox(GoTemplate.entries)
                    .bindItem(templateProperty)
            }
            row("Go version:") {
                textField()
                    .bindText(goVersionProperty)
                    .columns(12)
                    .validationOnInput { field -> GoModule.validateGoVersion(field.text.trim())?.let { error(it) } }
                    .validationOnApply { field -> GoModule.validateGoVersion(field.text.trim())?.let { error(it) } }
                    .comment(sdkComment())
            }
        }
    }

    override fun setupProject(project: Project) {
        val base = baseData
        val name = base?.name ?: project.name
        val dir = when {
            base != null -> Path.of(base.path, base.name)
            project.basePath != null -> Path.of(project.basePath!!)
            else -> return
        }
        val spec = GoProjectSpec(
            projectName = name,
            modulePath = effectiveModulePath(modulePathProperty.get()),
            goVersion = goVersionProperty.get().trim(),
            template = templateProperty.get(),
        )

        try {
            GoScaffold.write(dir, spec)
        } catch (e: Exception) {
            LOG.warn("Couldn't write Go project files to $dir", e)
            ApplicationManager.getApplication().invokeLater({
                Messages.showErrorDialog(project, "Couldn't create the Go project files: ${e.message}", "Go New Project")
            }, ModalityState.nonModal())
            return
        }

        createModule(project, name, dir)
        VfsUtil.markDirtyAndRefresh(true, true, true, dir)
        openWhenReady(project, dir.resolve(GoScaffold.primaryFile(spec)))
    }

    /** What goes in go.mod: the typed path, or the project name with spaces turned into dashes. */
    private fun effectiveModulePath(typed: String): String =
        typed.trim().ifEmpty { (baseData?.name ?: "").trim().replace(Regex("""\s+"""), "-") }

    private fun sdkComment(): String {
        val sdk = sdks.firstOrNull()
            ?: return "Go wasn't found on this machine. Install it from " +
                "<a href=\"https://go.dev/dl/\">go.dev/dl</a>; the project is created either way."
        val others = sdks.size - 1
        val suffix = if (others > 0) " ($others other installation${if (others > 1) "s" else ""} found)" else ""
        return "Detected Go ${sdk.version} at ${sdk.root}$suffix"
    }

    /**
     * IntelliJ IDEA shows files through modules, so give the project a plain (non-Java) module
     * rooted at the project folder — the same module type File | New | Project | Empty Project uses.
     */
    private fun createModule(project: Project, name: String, dir: Path) {
        val manager = ModuleManager.getInstance(project)
        if (manager.findModuleByName(name) != null) return

        val module = WriteAction.computeAndWait<com.intellij.openapi.module.Module, RuntimeException> {
            manager.newModule(dir.resolve("$name.iml"), GENERAL_MODULE_TYPE_ID)
        }
        ModuleRootModificationUtil.addContentRoot(module, FileUtil.toSystemIndependentName(dir.toString()))
    }

    private fun openWhenReady(project: Project, file: Path) {
        StartupManager.getInstance(project).runAfterOpened {
            ApplicationManager.getApplication().invokeLater({
                if (project.isDisposed) return@invokeLater
                val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file) ?: return@invokeLater
                FileEditorManager.getInstance(project).openFile(virtualFile, true)
            }, ModalityState.nonModal())
        }
    }

    private companion object {
        /** GeneralModuleType.TYPE_ID; written out to avoid depending on an implementation class. */
        const val GENERAL_MODULE_TYPE_ID = "GENERAL_MODULE"

        val LOG = logger<GoNewProjectStep>()
    }
}
