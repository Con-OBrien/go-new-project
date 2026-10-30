package com.conorobrien.gonewproject

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.language.LanguageGeneratorNewProjectWizard
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * Adds "Go" to the language list on the left of File | New | Project.
 * The platform supplies the name, location and Git steps; [GoNewProjectStep] adds the Go options.
 */
class GoLanguageGenerator : LanguageGeneratorNewProjectWizard {

    override val name: String = "Go"

    override val icon: Icon = IconLoader.getIcon("/icons/go.svg", GoLanguageGenerator::class.java)

    override val ordinal: Int = 600

    /** Stay out of the way when JetBrains' own Go plugin is active (GoLand, or IDEA with a subscription). */
    override fun isEnabled(context: WizardContext): Boolean =
        !PluginManagerCore.isLoaded(PluginId.getId(OFFICIAL_GO_PLUGIN_ID))

    override fun createStep(parent: NewProjectWizardStep): NewProjectWizardStep = GoNewProjectStep(parent)

    private companion object {
        const val OFFICIAL_GO_PLUGIN_ID = "org.jetbrains.plugins.go"
    }
}
