package org.xoops.support.toolwindow

import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JToolBar
import javax.swing.SwingUtilities
import javax.swing.event.HyperlinkEvent

class XoopsToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = XoopsToolWindowPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

class XoopsToolWindowPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val overview = JEditorPane("text/html", "<html><body>Scanning…</body></html>")
    private val status = JLabel("Ready")
    private val refreshButton = JButton("Refresh")
    private var findings: List<Finding> = emptyList()

    init {
        overview.isEditable = false
        overview.addHyperlinkListener { event ->
            if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                event.description?.removePrefix("finding:")?.toIntOrNull()?.let(::openFinding)
            }
        }
        val toolbar = JToolBar().apply {
            isFloatable = false
            refreshButton.addActionListener { refresh() }
            add(refreshButton)
            addSeparator()
            add(status)
        }
        add(toolbar, BorderLayout.NORTH)
        add(JBScrollPane(overview), BorderLayout.CENTER)
        if (org.xoops.support.XoopsSettings.getInstance(project).autoScanOnToolWindowOpen) {
            refresh()
        } else {
            overview.text = "<html><body>Click <b>Refresh</b> to scan the project (or enable auto-scan in Settings → XOOPS Support).</body></html>"
            status.text = "Not scanned"
        }
    }

    fun refresh() {
        val basePath = project.basePath ?: return
        refreshButton.isEnabled = false
        status.text = "Scanning…"

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Scanning XOOPS project", false) {
            override fun run(indicator: ProgressIndicator) {
                val settings = org.xoops.support.XoopsSettings.getInstance(project)
                val report = XoopsScanner().scan(Path.of(basePath), ScanOptions(phpLines = settings.isColumnShown(OverviewColumn.LINES)))
                SwingUtilities.invokeLater { apply(report) }
            }
        })
    }

    private fun apply(report: ProjectReport) {
        if (project.isDisposed) {
            return
        }
        findings = report.findings
        overview.text = render(report)
        overview.caretPosition = 0
        status.text = if (report.isXoops) {
            "${report.modules.size} modules, ${report.findings.size} findings"
        } else {
            "Not a XOOPS project"
        }
        refreshButton.isEnabled = true
    }

    private fun openFinding(index: Int) {
        val finding = findings.getOrNull(index) ?: return
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(finding.path) ?: return
        OpenFileDescriptor(project, file, (finding.line - 1).coerceAtLeast(0), 0).navigate(true)
    }

    private fun render(report: ProjectReport): String = buildString {
        append("<html><body style='font-family:sans-serif'>")
        if (!report.isXoops) {
            append("<p>No mainfile.php or xoops_version.php found.</p></body></html>")
            return@buildString
        }
        append("<h2>XOOPS ").append(escape(report.coreVersion ?: "(version unknown)")).append("</h2>")
        append("<p><b>Smarty:</b> ").append(escape(report.smartyVersion ?: "not found")).append("</p>")
        append("<p><b>Web root:</b> <code>").append(escape(report.webRoot.toString())).append("</code></p>")

        val m = report.modules
        append("<h3>Modules (").append(m.size).append(")</h3>")
        append("<p>")
        append(m.sumOf { it.templateCount }).append(" templates, ")
        append(m.sumOf { it.languageFileCount }).append(" language files, ")
        append(m.sumOf { it.preloadCount }).append(" preloads, ")
        append(m.sumOf { it.classCount }).append(" classes, ")
        append(m.sumOf { it.blockCount }).append(" blocks; ")
        append(m.count { it.hasAdmin }).append(" with admin, ")
        append(m.count { it.hasSql }).append(" with SQL, ")
        append(m.count { it.hasTests }).append(" with tests, ")
        append(m.count { it.hasModuleJson }).append(" with module.json")
        append("</p>")
        val settings = org.xoops.support.XoopsSettings.getInstance(project)
        val columns = OverviewColumn.entries.filter(settings::isColumnShown)
        val findingsPerModule = report.findings.groupingBy { f -> m.firstOrNull { f.path.startsWith(it.root) }?.dirname }.eachCount()
        append("<table border='0' cellspacing='0' cellpadding='3'>")
        append("<tr><td><b>Module</b></td>")
        for (column in columns) append("<td><b>").append(escape(column.label)).append("</b></td>")
        append("</tr>")
        fun yes(b: Boolean) = if (b) "yes" else "-"
        for (module in m) {
            append("<tr><td><b>").append(escape(module.dirname)).append("</b></td>")
            for (column in columns) {
                val (value, numeric) = when (column) {
                    OverviewColumn.VERSION -> (module.version ?: "?") to false
                    OverviewColumn.MIN_PHP -> (module.minPhp ?: "-") to false
                    OverviewColumn.MIN_XOOPS -> (module.minXoops ?: "-") to false
                    OverviewColumn.MANIFEST -> buildList {
                        if (module.hasXoopsVersion) add("xoops_version.php")
                        if (module.hasModuleJson) add("module.json")
                    }.joinToString(" + ").ifEmpty { "none" } to false
                    OverviewColumn.TEMPLATES -> module.templateCount.toString() to true
                    OverviewColumn.LANGUAGE_FILES -> module.languageFileCount.toString() to true
                    OverviewColumn.LANGUAGES -> module.languageCount.toString() to true
                    OverviewColumn.PRELOADS -> module.preloadCount.toString() to true
                    OverviewColumn.CLASSES -> module.classCount.toString() to true
                    OverviewColumn.BLOCKS -> module.blockCount.toString() to true
                    OverviewColumn.ADMIN -> yes(module.hasAdmin) to false
                    OverviewColumn.SQL -> yes(module.hasSql) to false
                    OverviewColumn.TESTS -> yes(module.hasTests) to false
                    OverviewColumn.AUTOLOAD -> yes(module.hasAutoloader) to false
                    OverviewColumn.XMF_ADMIN -> yes(module.usesXmfAdmin) to false
                    OverviewColumn.STRICT_TYPES -> yes(module.hasStrictTypes) to false
                    OverviewColumn.FINDINGS -> (findingsPerModule[module.dirname] ?: 0).toString() to true
                    OverviewColumn.LINES -> (if (module.phpLines < 0) "-" else module.phpLines.toString()) to true
                }
                append(if (numeric) "<td align='right'>" else "<td>").append(escape(value)).append("</td>")
            }
            append("</tr>")
        }
        append("</table>")
        append("<p>")
        append(columns.joinToString(", ") { "<i>${escape(it.label)}</i> ${escape(it.legend)}" })
        append(". Columns are chosen in Settings → XOOPS Support.</p>")

        append("<h3>Findings (").append(report.findings.size).append(")</h3>")
        if (report.findings.isEmpty()) {
            append("<p>None.</p>")
        } else {
            append("<ul>")
            report.findings.forEachIndexed { index, finding ->
                append("<li><a href='finding:").append(index).append("'>")
                append(escape(finding.path.fileName.toString())).append(":").append(finding.line)
                append("</a> — ").append(escape(finding.message)).append("</li>")
            }
            append("</ul>")
        }
        append("</body></html>")
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
