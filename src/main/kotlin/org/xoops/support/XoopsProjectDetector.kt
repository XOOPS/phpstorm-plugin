package org.xoops.support

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile

/**
 * Detects a XOOPS install (mainfile.php at the project root or under htdocs/)
 * or a standalone XOOPS module (xoops_version.php at the root) and reports the
 * detected core version in a balloon notification.
 */
class XoopsProjectDetector : ProjectActivity {

    override suspend fun execute(project: Project) {
        val base = project.guessProjectDir() ?: return

        val webRoot: VirtualFile? = sequenceOf(base, base.findChild("htdocs"))
            .filterNotNull()
            .firstOrNull { it.findChild("mainfile.php") != null }

        val isStandaloneModule = base.findChild("xoops_version.php") != null

        if (webRoot == null && !isStandaloneModule) {
            return
        }

        val settings = XoopsSettings.getInstance(project)
        if (settings.suppressStartupNotification) {
            return
        }

        val coreVersion = webRoot
            ?.findFileByRelativePath("include/version.php")
            ?.let(::readXoopsVersion)

        val title = if (webRoot != null) "XOOPS install detected" else "XOOPS module detected"
        val content = coreVersion ?: "Core line not determined — inspections use default (2.5.12+) rules"

        NotificationGroupManager.getInstance()
            .getNotificationGroup("XOOPS Support")
            .createNotification(title, content, NotificationType.INFORMATION)
            .addAction(NotificationAction.createSimpleExpiring("Don't show again") {
                settings.suppressStartupNotification = true
            })
            .notify(project)
    }

    private fun readXoopsVersion(versionFile: VirtualFile): String? = runCatching {
        Regex("XOOPS_VERSION'\\s*,\\s*'([^']+)'")
            .find(VfsUtilCore.loadText(versionFile))
            ?.groupValues
            ?.get(1)
    }.getOrNull()
}
