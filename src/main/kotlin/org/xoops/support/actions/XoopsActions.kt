package org.xoops.support.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import org.xoops.support.scaffold.ModuleScaffold
import org.xoops.support.toolwindow.ProjectReport
import org.xoops.support.toolwindow.XoopsScanner
import org.xoops.support.toolwindow.XoopsToolWindowPanel
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.io.File
import java.nio.file.Path
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

abstract class ProjectAction : AnAction(), DumbAware {
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

/** Tools → XOOPS Support → Show XOOPS Project Info: the scanner's summary in a dialog. */
class ShowProjectInfoAction : ProjectAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val basePath = project.basePath ?: return
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Scanning XOOPS project", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Scanning XOOPS modules…"
                val settings = org.xoops.support.XoopsSettings.getInstance(project)
                val report = XoopsScanner().scan(Path.of(basePath), org.xoops.support.toolwindow.ScanOptions(phpLines = settings.isColumnShown(org.xoops.support.toolwindow.OverviewColumn.LINES)))
                ApplicationManager.getApplication().invokeLater(
                    { Messages.showInfoMessage(project, format(report), "XOOPS Support - Project Info") },
                    ModalityState.nonModal(),
                    project.disposed,
                )
            }
        })
    }

    private fun format(report: ProjectReport): String {
        if (!report.isXoops) return "No XOOPS markers found (mainfile.php / xoops_version.php)."
        val sb = StringBuilder()
        sb.append(report.coreVersion ?: "Core version not determined").append('\n')
        sb.append("Web root: ").append(report.webRoot).append('\n')
        sb.append("Smarty: ").append(report.smartyVersion ?: "not found").append('\n')
        sb.append("Modules: ").append(report.modules.size)
            .append(" (").append(report.modules.sumOf { it.classCount }).append(" classes, ")
            .append(report.modules.sumOf { it.preloadCount }).append(" preloads, ")
            .append(report.modules.sumOf { it.templateCount }).append(" templates)").append('\n')
        for (m in report.modules.take(30)) {
            sb.append("  - ").append(m.dirname).append(' ').append(m.version ?: "?")
                .append(if (m.hasModuleJson) " [module.json]" else " [xoops_version.php]")
                .append(" tpl=").append(m.templateCount).append(" lang=").append(m.languageFileCount)
                .append(" pre=").append(m.preloadCount).append(" cls=").append(m.classCount).append('\n')
        }
        if (report.modules.size > 30) sb.append("  …\n")
        sb.append("Findings: ").append(report.findings.size).append(" (see the XOOPS Support tool window)")
        return sb.toString()
    }
}

/** Tools → XOOPS Support → Refresh XOOPS Overview: opens the tool window and rescans. */
class RefreshOverviewAction : ProjectAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("XOOPS Support") ?: return
        toolWindow.show {
            toolWindow.contentManager.contents.forEach { (it.component as? XoopsToolWindowPanel)?.refresh() }
        }
    }
}

/** New → XOOPS Module… and Tools → XOOPS Support → New XOOPS Module…: writes [ModuleScaffold.files]. */
class NewModuleAction : ProjectAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val modulesRoot = findModulesRoot(project)
        if (modulesRoot == null) {
            Messages.showErrorDialog(project, "Could not find a modules/ directory. Create htdocs/modules first.", "New XOOPS Module")
            return
        }
        val dialog = NewModuleDialog(project, modulesRoot)
        if (!dialog.showAndGet()) return
        val dirname = dialog.dirname
        val hybrid = dialog.hybrid
        try {
            WriteAction.runAndWait<Exception> {
                if (modulesRoot.findChild(dirname) != null) throw IllegalStateException("Module directory already exists: $dirname")
                val moduleDir = modulesRoot.createChildDirectory(this, dirname)
                for ((rel, content) in ModuleScaffold.files(dirname, hybrid)) {
                    val parts = rel.split('/')
                    var dir = moduleDir
                    for (segment in parts.dropLast(1)) dir = dir.findChild(segment) ?: dir.createChildDirectory(this, segment)
                    dir.createChildData(this, parts.last()).setBinaryContent(content.toByteArray(Charsets.UTF_8))
                }
            }
            Messages.showInfoMessage(
                project,
                "Created modules/$dirname (${if (hybrid) "hybrid: Helper, autoloader, admin side" else "legacy stub"}).\n" +
                    "Install it from System → Modules; the manifest, language files and index template are registered.",
                "New XOOPS Module",
            )
        } catch (ex: Exception) {
            Messages.showErrorDialog(project, ex.message ?: ex.javaClass.simpleName, "New XOOPS Module")
        }
    }

    private fun findModulesRoot(project: Project): VirtualFile? {
        val root = project.basePath?.let { VfsUtil.findFileByIoFile(File(it), true) } ?: return null
        return sequenceOf("htdocs/modules", "modules")
            .mapNotNull { VfsUtil.findRelativeFile(it, root) }
            .firstOrNull { it.isDirectory }
    }
}

private class NewModuleDialog(project: Project, private val modulesRoot: VirtualFile) : DialogWrapper(project) {
    private val dirnameField = JTextField("mymodule", 24)
    private val hybridBox = JCheckBox("Hybrid layout: Helper class, PSR-4 autoloader, include/common.php and an Xmf admin side", true)

    val dirname: String get() = dirnameField.text.trim().lowercase()
    val hybrid: Boolean get() = hybridBox.isSelected

    init {
        title = "New XOOPS Module"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val c = GridBagConstraints().apply { insets = Insets(4, 4, 4, 4); anchor = GridBagConstraints.WEST; gridx = 0; gridy = 0 }
        panel.add(JLabel("Module dirname:"), c)
        c.gridx = 1
        panel.add(dirnameField, c)
        c.gridx = 0; c.gridy++; c.gridwidth = 2
        panel.add(JLabel("<html>Letters, digits and underscore, 1 to 32 characters, starting with a letter.<br>Created under <code>${modulesRoot.path}</code>.</html>"), c)
        c.gridy++
        panel.add(hybridBox, c)
        return panel
    }

    override fun doValidate(): ValidationInfo? {
        if (!ModuleScaffold.DIRNAME.matches(dirname)) return ValidationInfo("1 to 32 characters: a letter, then letters, digits or underscore.", dirnameField)
        if (modulesRoot.findChild(dirname) != null) return ValidationInfo("modules/$dirname already exists.", dirnameField)
        return null
    }

    override fun getPreferredFocusedComponent(): JComponent = dirnameField
}
