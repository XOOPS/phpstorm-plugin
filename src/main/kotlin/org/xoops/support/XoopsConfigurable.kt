package org.xoops.support

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import org.xoops.support.psi.XoopsCore
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/** Settings → Languages & Frameworks → XOOPS Support. Plain Swing, same layout as 1.0.0 Alpha 3. */
class XoopsConfigurable(private val project: Project) : Configurable {

    private lateinit var enabledBox: JCheckBox
    private lateinit var suppressNotifyBox: JCheckBox
    private lateinit var autoScanBox: JCheckBox
    private lateinit var coreVersionBox: JComboBox<String>
    private val columnBoxes = LinkedHashMap<org.xoops.support.toolwindow.OverviewColumn, JCheckBox>()

    override fun getDisplayName(): String = "XOOPS Support"

    override fun createComponent(): JComponent {
        val form = JPanel(GridBagLayout())
        val c = GridBagConstraints().apply {
            insets = Insets(4, 4, 4, 4)
            anchor = GridBagConstraints.WEST
            gridx = 0
            gridy = 0
        }
        enabledBox = JCheckBox("Enable XOOPS Support for this project")
        form.add(enabledBox, c)
        c.gridy++
        suppressNotifyBox = JCheckBox("Suppress startup notification")
        form.add(suppressNotifyBox, c)
        c.gridy++
        autoScanBox = JCheckBox("Auto-scan the project when the tool window opens (slow on large trees)")
        form.add(autoScanBox, c)
        c.gridy++
        form.add(JLabel("Core version:"), c)
        c.gridx = 1
        coreVersionBox = JComboBox(XoopsSettings.KNOWN_VERSIONS.toTypedArray()).apply { isEditable = true }
        form.add(coreVersionBox, c)
        c.gridx = 0
        c.gridy++
        c.gridwidth = 2
        form.add(JLabel("<html>Auto reads <code>include/version.php</code> of the nearest web root: $detected.<br>" +
            "Set a version for a standalone module checkout, or to target an older core than the tree you develop in.<br>" +
            "Quick-fixes that need <code>isResultSet()</code> (2.5.11) or <code>exec()</code> (2.5.12) follow this value.</html>"), c)
        c.gridy++
        form.add(JLabel("<html><b>Overview columns</b> (XOOPS Support tool window)</html>"), c)
        for (column in org.xoops.support.toolwindow.OverviewColumn.entries) {
            c.gridy++
            val box = JCheckBox("${column.label} — ${column.legend}" + if (column.expensive) " (slower scan)" else "")
            columnBoxes[column] = box
            form.add(box, c)
        }
        return JPanel(BorderLayout()).apply { add(form, BorderLayout.NORTH) }
    }

    private val detected: String
        get() {
            val base = project.guessProjectDir() ?: return "no project directory"
            val versionFile = sequenceOf(base, base.findChild("htdocs"))
                .filterNotNull()
                .mapNotNull { it.findFileByRelativePath("include/version.php") }
                .firstOrNull() ?: return "no include/version.php found"
            val v = runCatching { XoopsCore.parse(VfsUtilCore.loadText(versionFile)) }.getOrNull()
            return if (v != null) "XOOPS $v detected" else "version.php found but not readable"
        }

    private fun selectedVersion(): String = (coreVersionBox.editor.item?.toString() ?: XoopsSettings.AUTO).trim().ifBlank { XoopsSettings.AUTO }

    override fun isModified(): Boolean {
        val s = XoopsSettings.getInstance(project)
        return enabledBox.isSelected != s.enabled ||
            suppressNotifyBox.isSelected != s.suppressStartupNotification ||
            autoScanBox.isSelected != s.autoScanOnToolWindowOpen ||
            selectedVersion() != s.resolvedCoreVersion() ||
            columnBoxes.any { (column, box) -> box.isSelected != s.isColumnShown(column) }
    }

    override fun apply() {
        val s = XoopsSettings.getInstance(project)
        s.enabled = enabledBox.isSelected
        s.suppressStartupNotification = suppressNotifyBox.isSelected
        s.autoScanOnToolWindowOpen = autoScanBox.isSelected
        val chosen = selectedVersion()
        s.coreVersion = if (chosen == XoopsSettings.AUTO || XoopsCore.parseVersionNumber(chosen) != null) chosen else XoopsSettings.AUTO
        columnBoxes.forEach { (column, box) -> s.setColumnShown(column, box.isSelected) }
    }

    override fun reset() {
        val s = XoopsSettings.getInstance(project)
        enabledBox.isSelected = s.enabled
        suppressNotifyBox.isSelected = s.suppressStartupNotification
        autoScanBox.isSelected = s.autoScanOnToolWindowOpen
        coreVersionBox.selectedItem = s.resolvedCoreVersion()
        columnBoxes.forEach { (column, box) -> box.isSelected = s.isColumnShown(column) }
    }
}
