package org.xoops.support.completion

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiModificationTracker

/**
 * The `_MI_` / `_AM_` / … constants defined in the project's `language/` files, rebuilt
 * when the PSI modification count moves.
 *
 * A project-level service rather than `CachedValuesManager.getCachedValue(project)`: that
 * parks a lambda from this plugin's classloader in the project's user data, which survives
 * a dynamic unload and makes the IDE demand a restart. A light service is disposed with the
 * plugin.
 */
@Service(Service.Level.PROJECT)
class XoopsLanguageConstantCache(private val project: Project) {

    private var stamp = -1L
    private var constants: Map<String, String> = emptyMap()

    /** name -> origin label ("newbb/admin.php") for the type column. */
    @Synchronized
    fun constants(): Map<String, String> {
        val now = PsiModificationTracker.getInstance(project).modificationCount
        if (now != stamp) {
            constants = collect()
            stamp = now
        }
        return constants
    }

    private fun collect(): Map<String, String> {
        val found = LinkedHashMap<String, String>()
        val psiManager = PsiManager.getInstance(project)
        for (fileName in LANGUAGE_FILE_NAMES) {
            val files = FilenameIndex.getVirtualFilesByName(fileName, GlobalSearchScope.projectScope(project))
            for (virtualFile in files) {
                val path = virtualFile.path.replace('\\', '/')
                if (!path.contains("/language/") || path.contains("/vendor/")) continue
                val text = psiManager.findFile(virtualFile)?.text ?: continue
                val origin = path.substringAfter("/modules/", missingDelimiterValue = "")
                    .substringBefore("/language/")
                    .ifEmpty { "core" } + "/" + fileName
                for (match in DEFINE.findAll(text)) {
                    found.putIfAbsent(match.groupValues[1], origin)
                    if (found.size >= MAX_CONSTANTS) return found
                }
            }
        }
        return found
    }

    companion object {
        private val DEFINE = Regex("""define\s*\(\s*['"](_(?:MI|AM|MD|MB|CO)_[A-Za-z0-9_]+)['"]""")
        private val LANGUAGE_FILE_NAMES = listOf("modinfo.php", "main.php", "admin.php", "blocks.php", "common.php")
        private const val MAX_CONSTANTS = 8000

        fun getInstance(project: Project): XoopsLanguageConstantCache = project.getService(XoopsLanguageConstantCache::class.java)
    }
}
