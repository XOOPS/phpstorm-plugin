package org.xoops.support.psi

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

/**
 * Path policy shared by every inspection: what is module code, what is noise.
 *
 * Two tiers. [isExcluded] is generated or vendored output that nothing inspects.
 * [isThirdParty] is bundled code the module ships but did not write (`assets/`,
 * `libraries/`): the security rules still read it (an upload endpoint under
 * `assets/js/` is live code), the convention rules (headers, guards, status
 * lines, deprecations) leave it alone.
 */
object XoopsPaths {

    private val excludedSegments = listOf(
        "/vendor/", "/templates_c/", "/cache/", "/caches/", "/node_modules/", "/uploads/", "/xoops_data/",
        // Bundled libraries and scratch copies (review round 4): nothing in them is module code.
        "/assets/js/", "/jpgraph", "/jpspan", "/0oldstuff/", "/gump_examples/", "/00/",
        // Documentation and example trees of bundled libraries (224-module review): never module code.
        "/docs/", "/examples/", "/tests-src/",
    )
    private val thirdPartySegments = listOf("/assets/", "/libraries/", "/lib/", "/js/", "/jquery/", "/ckeditor/", "/tinymce/", "/pear/")
    private val testSegments = listOf("/test/", "/tests/", "/testdata/", "/fixtures/")

    /**
     * A file that runs inside XOOPS: it names the core somewhere (`XOOPS_ROOT_PATH`, a `XoopsModules`
     * namespace, `$xoopsDB`, `mainfile.php`, …). A bundled script that never mentions XOOPS — a PayPal
     * IPN handler, a TinyMCE compressor, a PEAR example — has no autoloader for `Xmf\Request` and must
     * not be rewritten to it. Measured on 224 modules: 102 such files, every one a false positive.
     */
    fun isXoopsCode(file: PsiFile): Boolean {
        // Module files that legitimately never say "xoops": language packs (bare define() lists),
        // the manifest, and directory index stubs.
        val p = path(file)
        if (p.contains("/language/") || p.endsWith("/xoops_version.php") || p.endsWith("/index.php")) return true
        val key = file.virtualFile?.path ?: return scanForXoops(file.text)
        val stamp = file.modificationStamp
        xoopsCodeCache[key]?.let { if (it.first == stamp) return it.second }
        if (xoopsCodeCache.size > 4096) xoopsCodeCache.clear()
        val result = scanForXoops(file.text)
        xoopsCodeCache[key] = stamp to result
        return result
    }

    // The direct-access guard is not evidence: RootPathGuard writes one into bundled library files
    // too, and that line alone turned ten PhpGedView / Facebook SDK scripts into "XOOPS code" on the
    // next pass (0.7.2 review).
    private fun scanForXoops(text: String): Boolean {
        val t = text.replace(guardLine, "")
        return t.contains("xoops", ignoreCase = true) || t.contains("mainfile.php") || t.contains("Xmf\\")
    }

    /** Per-file verdict keyed by path and modification stamp; a plain static, so it dies with the plugin. */
    private val xoopsCodeCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Boolean>>()

    private val guardLine = Regex("""defined\s*\(\s*['"]XOOPS_ROOT_PATH['"]\s*\)[^\n]*""")

    fun path(file: PsiFile): String =
        (file.virtualFile?.path ?: file.name).replace('\\', '/').lowercase()

    /** Vendored, generated or upload trees: never inspected, never fixed. */
    fun isExcluded(file: PsiFile): Boolean {
        val p = path(file)
        return excludedSegments.any { p.contains(it) }
    }

    /** Bundled third-party code: inspected for security, skipped by convention rules. */
    fun isThirdParty(file: PsiFile): Boolean {
        val p = path(file)
        return thirdPartySegments.any { p.contains(it) }
    }

    fun isTestTree(file: PsiFile): Boolean {
        val p = path(file)
        return testSegments.any { p.contains(it) }
    }

    fun isLanguageFile(file: PsiFile): Boolean = path(file).contains("/language/")

    fun isPhp(file: PsiFile): Boolean = path(file).let { it.endsWith(".php") || it.endsWith(".inc") }

    /**
     * The core installer (`install/include/common.inc.php`) or upgrader (`upgrade/checkmainfile.php`):
     * both run before the xoops_lib autoloader is known, so Xmf classes are not available there.
     */
    fun isInstallerOrUpgrader(file: VirtualFile?): Boolean {
        var dir = file?.parent
        while (dir != null) {
            if (dir.name == "install" && dir.findFileByRelativePath("include/common.inc.php") != null) return true
            if (dir.name == "upgrade" && dir.findChild("checkmainfile.php") != null) return true
            dir = dir.parent
        }
        return false
    }

    /** Nearest ancestor directory holding `xoops_version.php`. */
    fun moduleRoot(file: VirtualFile?): VirtualFile? {
        var dir = file?.parent
        while (dir != null) {
            if (dir.findChild("xoops_version.php") != null) return dir
            dir = dir.parent
        }
        return null
    }

    fun relativeToModule(file: VirtualFile): String? {
        val root = moduleRoot(file) ?: return null
        val rootPath = root.path.replace('\\', '/')
        val filePath = file.path.replace('\\', '/')
        return if (filePath.startsWith("$rootPath/")) filePath.substring(rootPath.length + 1) else null
    }
}
