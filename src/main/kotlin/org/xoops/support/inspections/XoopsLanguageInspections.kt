package org.xoops.support.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.ConstantReference
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpNamespace
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor
import org.xoops.support.psi.Php
import org.xoops.support.psi.XoopsPaths

/**
 * Language catalogs: `_MI_` lives in modinfo.php, `_AM_` in admin.php, `_MD_` in main.php,
 * `_MB_` in blocks.php, `_CO_` in common.php. A canonical file with a foreign prefix gets
 * one note per constant. A non-canonical file (`modinfo1.php`, a backup copy) gets ONE
 * file-level note, not one per define.
 */
class XoopsLanguageRoleInspection : XoopsInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            override fun visitFile(file: PsiFile) {
                val php = file as? PhpFile ?: return
                if (skip(php) || !XoopsPaths.isLanguageFile(php) || !isModuleCatalog(php)) return
                val defines = Php.calls(php, "define").mapNotNull { call ->
                    val name = Php.firstStringArg(call) ?: return@mapNotNull null
                    val prefix = roles.keys.firstOrNull { name.startsWith(it) } ?: return@mapNotNull null
                    Triple(call, name, prefix)
                }
                if (defines.isEmpty()) return
                val fileName = php.name.lowercase()
                val expected = roles.entries.firstOrNull { it.value == fileName }?.key
                if (expected == null) {
                    if (loadedExplicitly(php, php.name.removeSuffix(".php").removeSuffix(".PHP"))) return
                    holder.registerProblem(
                        php.firstChild ?: php,
                        "XOOPS: '${php.name}' is not a language catalog the core loads (expected ${roles.values.joinToString()}); " +
                            "${defines.size} role-prefixed constants will never be defined from here",
                        ProblemHighlightType.WEAK_WARNING,
                    )
                    return
                }
                for ((call, name, prefix) in defines) {
                    if (prefix == expected) continue
                    if (expected == "_AM_" && legacyAdminPrefixes.any { name.startsWith(it) }) continue
                    holder.registerProblem(
                        call.parameters.firstOrNull() as? StringLiteralExpression ?: call,
                        "XOOPS: $name belongs in ${roles[prefix]}, not ${php.name}",
                        ProblemHighlightType.WEAK_WARNING,
                    )
                }
            }
        }
    }

    /**
     * A module's own catalog, directly under `language/<lang>/`. The core's `htdocs/language/`
     * (global.php, pmsg.php …), Frameworks' moduleadmin `main.php`, and sub-catalogs such as the system
     * module's `language/<lang>/admin/<fct>.php` (loaded by name with `xoops_loadLanguage('admin/…')`)
     * follow their own loaders, not the module role files.
     */
    private fun isModuleCatalog(file: PhpFile): Boolean {
        val vf = file.virtualFile ?: return false
        if (XoopsPaths.moduleRoot(vf) == null) return false
        // Frameworks/moduleclasses/moduleadmin ships a xoops_version.php but is a library: its loader
        // includes language/<lang>/main.php by path, so the role prefixes do not apply.
        if (XoopsPaths.path(file).contains("/frameworks/")) return false
        val afterLanguage = XoopsPaths.path(file).substringAfterLast("/language/")
        return afterLanguage.count { it == '/' } == 1
    }

    /** `xoops_loadLanguage('cpanel', …)` / `$helper->loadLanguage('cpanel')` somewhere in the project. */
    private fun loadedExplicitly(file: PhpFile, base: String): Boolean {
        val project = file.project
        var found = false
        com.intellij.psi.search.PsiSearchHelper.getInstance(project).processAllFilesWithWordInLiterals(
            base, com.intellij.psi.search.GlobalSearchScope.projectScope(project),
        ) { f ->
            val loads = if (f is PhpFile) Php.calls(f, "xoops_loadLanguage") + Php.methodCalls(f, "loadLanguage") else emptyList()
            found = loads.any { Php.firstStringArg(it).equals(base, true) }
            !found
        }
        return found
    }

    companion object {
        /** System's `_MD_AM_*` and the D3 / Protector `_MD_A_*` are admin constants by name. */
        private val legacyAdminPrefixes = listOf("_MD_AM_", "_MD_A_")

        val roles = linkedMapOf("_MI_" to "modinfo.php", "_AM_" to "admin.php", "_MD_" to "main.php", "_MB_" to "blocks.php", "_CO_" to "common.php")
    }
}

/**
 * A page script (it boots `mainfile.php`) uses `_MD_` / `_CO_` / `_AM_` constants but loads no catalog: neither
 * `xoops_loadLanguage()`, nor the XMF helper's `loadLanguage()`, nor an include of
 * header.php / admin_header.php / common.php that loads it on the page's behalf.
 */
class XoopsLanguageLoadInspection : XoopsInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            override fun visitFile(file: PsiFile) {
                val php = file as? PhpFile ?: return
                if (skip(php) || XoopsPaths.isLanguageFile(php) || XoopsPaths.path(php).endsWith("/xoops_version.php")) return
                if (PsiTreeUtil.findChildOfType(php, PhpNamespace::class.java) != null || PsiTreeUtil.findChildOfType(php, PhpClass::class.java) != null) return
                val constants = PsiTreeUtil.findChildrenOfType(php, ConstantReference::class.java)
                    .filter { c -> prefixes.any { c.name?.startsWith(it) == true } }
                if (constants.isEmpty()) return
                if (Php.calls(php, "xoops_loadLanguage").isNotEmpty() || Php.methodCalls(php, "loadLanguage").isNotEmpty()) return
                val top = Php.topLevelStatements(php)
                if (top.any { Php.includes(it, "header.php", "admin_header.php", "common.php", "cp_header.php") }) return
                // Only a page script loads for itself. A file that does not boot mainfile.php is included by
                // one that did and uses its catalogs: system admin/<fct>/main.php (admin.php loads them),
                // blocks (the block loader), admin_footer.php, Smarty plugins — 22 of 22 XoopsCore27 findings.
                if (top.none { Php.includes(it, "mainfile.php") }) return
                holder.registerProblem(
                    constants.first() as com.intellij.psi.PsiElement,
                    "XOOPS: ${constants.first().name} is used but no language catalog is loaded in this file (xoops_loadLanguage() / \$helper->loadLanguage())",
                    ProblemHighlightType.WEAK_WARNING,
                )
            }
        }
    }

    private val prefixes = listOf("_MD_", "_CO_", "_AM_", "_MB_")
}
