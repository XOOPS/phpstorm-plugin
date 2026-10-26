package org.xoops.support

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Project-level persistent settings (Settings → Languages & Frameworks → XOOPS Support).
 *
 * [coreVersion] overrides the core line read from `include/version.php`: `"Auto"` (default) trusts
 * the tree, anything else is a version such as `2.5.9` or `2.7.4` and is what the version-gated
 * quick-fixes see. A standalone module checkout has no `version.php`, so this is the only way to
 * tell the plugin that the module targets an old core.
 */
@Service(Service.Level.PROJECT)
@State(name = "XoopsSupportSettings", storages = [Storage("xoopsSupport.xml")])
class XoopsSettings : PersistentStateComponent<XoopsSettings> {

    @JvmField
    var enabled: Boolean = true

    @JvmField
    var suppressStartupNotification: Boolean = false

    /** Off by default: a scan of a monorepo with hundreds of modules on tool-window open froze the IDE (Alpha 2). */
    @JvmField
    var autoScanOnToolWindowOpen: Boolean = false

    @JvmField
    var coreVersion: String = AUTO

    /**
     * Overview columns the user switched off, by [org.xoops.support.toolwindow.OverviewColumn.key].
     * Storing the hidden set means a fresh install shows every column and an update keeps the
     * user's choice while columns that are new to the release appear.
     */
    @JvmField
    var hiddenOverviewColumns: MutableList<String> = ArrayList()

    fun resolvedCoreVersion(): String = coreVersion.ifBlank { AUTO }

    fun isColumnShown(column: org.xoops.support.toolwindow.OverviewColumn): Boolean = column.key !in hiddenOverviewColumns

    fun setColumnShown(column: org.xoops.support.toolwindow.OverviewColumn, shown: Boolean) {
        if (shown) hiddenOverviewColumns.remove(column.key) else if (column.key !in hiddenOverviewColumns) hiddenOverviewColumns.add(column.key)
    }

    override fun getState(): XoopsSettings = this

    override fun loadState(state: XoopsSettings) {
        XmlSerializerUtil.copyBean(state, this)
        if (coreVersion.isBlank()) coreVersion = AUTO
    }

    companion object {
        const val AUTO = "Auto"

        /** Offered in the settings combo; the field accepts any `major.minor[.patch]`. */
        val KNOWN_VERSIONS = listOf(AUTO, "2.5.9", "2.5.10", "2.5.11", "2.5.12", "2.7.0", "2.7.4", "2.8.0", "4.0.0")

        fun getInstance(project: Project): XoopsSettings = project.getService(XoopsSettings::class.java)
    }
}
