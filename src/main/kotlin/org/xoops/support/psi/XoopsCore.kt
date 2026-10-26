package org.xoops.support.psi

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import java.util.concurrent.ConcurrentHashMap

/**
 * The XOOPS core line a file belongs to, read from the nearest ancestor `include/version.php`.
 * The version file is the marker, not `mainfile.php`: an unpacked distribution has only
 * `mainfile.dist.php` until it is installed, and that is exactly the tree a plugin review runs
 * on. A standalone module checkout has no version file, so its version is unknown and every
 * rewrite is offered: the module author targets the current core.
 *
 * Gates for the QuickFixes that emit a method an older core does not have. On XOOPS 2.5.9,
 * `isResultSet()` and `exec()` are calls to undefined methods: a fatal on every page, and one
 * `php -l` cannot see. Measured in the four-plugin bake-off of 2026-10-24 (`0_pluginTests`,
 * 177 + 155 such call sites written into a 2.5.9 tree).
 */
object XoopsCore {

    /** `isResultSet()` was added to `XoopsDatabase` in 2.5.11. */
    val RESULT_SET_API = Version(2, 5, 11)

    /**
     * `exec()` shipped with 2.5.12 (and every 2.7); `queryF()` is the older forced write.
     * Pre-release suffixes are ignored, so a 2.5.11-Beta1 (which still lacked `isResultSet()`)
     * counts as 2.5.11: the stable release is the contract. The abandoned 2.6.0 alphas have
     * neither method and are not special-cased.
     */
    val EXEC_API = Version(2, 5, 12)

    /** Smarty 5 (no `assignByRef()`, no global `\Smarty`) is the template engine from core 2.8 on. */
    val SMARTY5_API = Version(2, 8, 0)

    data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {
        override fun compareTo(other: Version): Int =
            compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

        override fun toString(): String = "$major.$minor.$patch"
    }

    private val versionPattern = Regex("XOOPS_VERSION'\\s*,\\s*'XOOPS\\s+(\\d+)\\.(\\d+)(?:\\.(\\d+))?")
    private val cache = ConcurrentHashMap<String, Pair<Long, Version?>>()

    /** `'XOOPS 2.5.9'` / `'XOOPS 2.8.0-Alpha1'` / `'XOOPS 2.7'` → a comparable version, or null. */
    fun parse(versionPhp: String): Version? {
        val m = versionPattern.find(versionPhp) ?: return null
        return Version(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toIntOrNull() ?: 0)
    }

    /** Nearest ancestor `include/version.php`, or null for a standalone module. */
    fun versionFile(file: VirtualFile?): VirtualFile? {
        var dir = file?.parent
        while (dir != null) {
            dir.findFileByRelativePath("include/version.php")?.let { return it }
            dir = dir.parent
        }
        return null
    }

    /** `2.5.9` / `2.7` → a version, or null; the form the settings page stores. */
    fun parseVersionNumber(text: String): Version? {
        val m = Regex("""^\s*(\d+)\.(\d+)(?:\.(\d+))?""").find(text) ?: return null
        return Version(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toIntOrNull() ?: 0)
    }

    /** The settings override when one is set, else the tree's `include/version.php`. */
    fun version(file: PsiFile): Version? {
        val override = org.xoops.support.XoopsSettings.getInstance(file.project).resolvedCoreVersion()
        if (override != org.xoops.support.XoopsSettings.AUTO) parseVersionNumber(override)?.let { return it }
        val versionFile = versionFile(file.virtualFile) ?: return null
        val cached = cache[versionFile.path]
        if (cached != null && cached.first == versionFile.modificationStamp) return cached.second
        val parsed = runCatching { parse(VfsUtilCore.loadText(versionFile)) }.getOrNull()
        cache[versionFile.path] = versionFile.modificationStamp to parsed
        return parsed
    }

    /** True when the core is at least [required], or when no core can be determined. */
    fun atLeast(file: PsiFile, required: Version): Boolean {
        val v = version(file) ?: return true
        return v >= required
    }
}
