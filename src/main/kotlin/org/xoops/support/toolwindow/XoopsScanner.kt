package org.xoops.support.toolwindow

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.streams.asSequence

data class ModuleInfo(
    val dirname: String,
    val root: Path,
    val hasXoopsVersion: Boolean,
    val hasModuleJson: Boolean,
    val templateCount: Long,
    val languageFileCount: Long,
    /** `$modversion['version']` from the manifest, when readable. */
    val version: String? = null,
    /** Language packs: directories under `language/`. */
    val languageCount: Long = 0,
    /** PHP files under `preloads/`. */
    val preloadCount: Long = 0,
    /** PHP files under `class/` and `src/` (the PSR-4 trees). */
    val classCount: Long = 0,
    /** `$modversion['blocks'][]` entries in the manifest. */
    val blockCount: Int = 0,
    val hasAdmin: Boolean = false,
    /** An installer SQL file under `sql/` present. */
    val hasSql: Boolean = false,
    val hasTests: Boolean = false,
    val minPhp: String? = null,
    val minXoops: String? = null,
    /** `preloads/autoloader.php` or `composer.json`: the module loads its classes PSR-4 style. */
    val hasAutoloader: Boolean = false,
    /** A file under `admin/` mentions `Xmf\Module\Admin`. */
    val usesXmfAdmin: Boolean = false,
    /** Some PHP file of the module declares `strict_types=1`. */
    val hasStrictTypes: Boolean = false,
    /** Lines in the module's PHP files, or -1 when the column was not requested. */
    val phpLines: Long = -1,
)

/** What the scan computes beyond the cheap counts; the expensive ones follow the Overview column settings. */
data class ScanOptions(val phpLines: Boolean = false)

data class Finding(
    val path: Path,
    val line: Int,
    val message: String,
)

data class ProjectReport(
    val isXoops: Boolean,
    val webRoot: Path,
    val coreVersion: String?,
    val modules: List<ModuleInfo>,
    val findings: List<Finding>,
    /** Smarty major shipped by the core, read from the Smarty class file (`2`, `3`, `4`, `5`). */
    val smartyVersion: String? = null,
)

/**
 * Filesystem-level scan for the tool window: module overview plus the two
 * checks the PSI inspections cannot do — templates registered in
 * xoops_version.php but missing on disk, and standard-Smarty `{ }` delimiters
 * in .tpl files (XOOPS templates use `<{ }>`).
 *
 * Pure java.nio, no IDE indices — safe to run on a background thread.
 */
class XoopsScanner {

    fun scan(projectRoot: Path, options: ScanOptions = ScanOptions()): ProjectReport {
        val root = projectRoot.toAbsolutePath().normalize()
        val webRoot = sequenceOf(root, root.resolve("htdocs"))
            .firstOrNull { it.resolve("mainfile.php").isRegularFile() }
        val standaloneModule = root.resolve("xoops_version.php").isRegularFile()

        if (webRoot == null && !standaloneModule) {
            return ProjectReport(false, root, null, emptyList(), emptyList())
        }

        val moduleRoots: List<Path> = when {
            webRoot != null -> {
                val modulesDir = webRoot.resolve("modules")
                if (modulesDir.isDirectory()) {
                    Files.list(modulesDir).use { stream ->
                        stream.asSequence()
                            .filter { it.isDirectory() && it.resolve("xoops_version.php").isRegularFile() }
                            .sortedBy { it.name.lowercase() }
                            .toList()
                    }
                } else {
                    emptyList()
                }
            }
            else -> listOf(root)
        }

        val modules = moduleRoots.map { inspectModule(it, options) }
        val findings = ArrayList<Finding>()
        for (moduleRoot in moduleRoots) {
            checkRegisteredTemplates(moduleRoot, findings)
            checkTemplateDelimiters(moduleRoot, findings)
        }

        val coreVersion = webRoot
            ?.resolve("include/version.php")
            ?.takeIf { it.isRegularFile() }
            ?.let { readSmall(it) }
            ?.let { VERSION.find(it)?.groupValues?.get(1) }

        return ProjectReport(true, webRoot ?: root, coreVersion, modules, findings, webRoot?.let(::smartyVersion))
    }

    private fun inspectModule(moduleRoot: Path, options: ScanOptions): ModuleInfo {
        val manifest = moduleRoot.resolve("xoops_version.php").takeIf { it.isRegularFile() }?.let { readSmall(it) } ?: ""
        val languageDir = moduleRoot.resolve("language")
        val phpFiles = listFiles(moduleRoot, ".php")
        return ModuleInfo(
            minPhp = manifestValue(manifest, "min_php"),
            minXoops = manifestValue(manifest, "min_xoops"),
            hasAutoloader = moduleRoot.resolve("preloads/autoloader.php").isRegularFile() || moduleRoot.resolve("composer.json").isRegularFile(),
            usesXmfAdmin = listFiles(moduleRoot.resolve("admin"), ".php").any { readSmall(it)?.contains("Xmf\\Module\\Admin") == true },
            hasStrictTypes = phpFiles.asSequence().mapNotNull(::readSmall).any { STRICT_TYPES.containsMatchIn(it) },
            phpLines = if (options.phpLines) phpFiles.sumOf { countLines(it) } else -1,
            dirname = moduleRoot.name,
            root = moduleRoot,
            hasXoopsVersion = moduleRoot.resolve("xoops_version.php").isRegularFile(),
            hasModuleJson = moduleRoot.resolve("module.json").isRegularFile(),
            templateCount = countFiles(moduleRoot.resolve("templates"), ".tpl"),
            languageFileCount = countFiles(languageDir, ".php"),
            version = MODULE_VERSION.find(manifest)?.groupValues?.get(1),
            languageCount = if (languageDir.isDirectory()) Files.list(languageDir).use { s -> s.filter { it.isDirectory() }.count() } else 0,
            preloadCount = countFiles(moduleRoot.resolve("preloads"), ".php"),
            classCount = countFiles(moduleRoot.resolve("class"), ".php") + countFiles(moduleRoot.resolve("src"), ".php"),
            blockCount = BLOCK_ENTRY.findAll(manifest).count(),
            hasAdmin = moduleRoot.resolve("admin").isDirectory(),
            hasSql = countFiles(moduleRoot.resolve("sql"), ".sql") > 0,
            hasTests = moduleRoot.resolve("tests").isDirectory() || moduleRoot.resolve("test").isDirectory(),
        )
    }

    /** 2.5: `class/smarty/Smarty.class.php` (`$_version = '2.6.x'`); 2.7+: vendor `smarty/smarty` (`SMARTY_VERSION = '4.x'` / `'5.x'`). */
    private fun smartyVersion(webRoot: Path): String? {
        val candidates = sequenceOf(
            "class/libraries/vendor/smarty/smarty/src/Smarty.php",
            "class/libraries/vendor/smarty/smarty/libs/Smarty.class.php",
            "xoops_lib/vendor/smarty/smarty/src/Smarty.php",
            "class/smarty/Smarty.class.php",
        ).map(webRoot::resolve).filter { it.isRegularFile() }
        for (file in candidates) {
            val text = readSmall(file) ?: continue
            SMARTY_VERSION.find(text)?.let { return it.groupValues[1] }
        }
        return null
    }

    private fun checkRegisteredTemplates(moduleRoot: Path, findings: MutableList<Finding>) {
        val manifest = moduleRoot.resolve("xoops_version.php")
        val content = manifest.takeIf { it.isRegularFile() }?.let { readSmall(it) } ?: return
        for (match in REGISTERED_TEMPLATE.findAll(content)) {
            val template = match.groupValues[1].replace('\\', '/')
            val exists = sequenceOf("templates", "templates/blocks", "templates/admin", "blocks", ".")
                .any { moduleRoot.resolve(it).resolve(template).isRegularFile() }
            if (!exists) {
                findings.add(
                    Finding(manifest, lineAt(content, match.range.first), "Registered template missing on disk: $template"),
                )
            }
        }
    }

    private fun checkTemplateDelimiters(moduleRoot: Path, findings: MutableList<Finding>) {
        val templatesDir = moduleRoot.resolve("templates")
        if (!templatesDir.isDirectory()) {
            return
        }
        Files.walk(templatesDir, 8).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() && it.name.endsWith(".tpl") }
                .forEach { template ->
                    val content = readSmall(template) ?: return@forEach
                    // Same matcher as the XoopsWrongSmartyDelimiter inspection: `<{* … *}>` comments,
                    // `<{literal}>` blocks and script/style bodies are masked, so a `{$placeholder}`
                    // inside a comment is not a finding.
                    val match = org.xoops.support.inspections.SmartyText.bareTags(content).firstOrNull()
                    if (match != null) {
                        findings.add(
                            Finding(
                                template,
                                lineAt(content, match.range.first),
                                "Standard Smarty '{ }' delimiter — XOOPS templates use '<{ }>'",
                            ),
                        )
                    }
                }
        }
    }

    private fun countFiles(directory: Path, suffix: String): Long {
        if (!directory.isDirectory()) {
            return 0
        }
        Files.walk(directory, 8).use { stream ->
            return stream.asSequence()
                .count { it.isRegularFile() && it.name.lowercase().endsWith(suffix) }
                .toLong()
        }
    }

    /** Regular files under [directory] (8 levels deep) with the given suffix; vendored trees are not module code. */
    private fun listFiles(directory: Path, suffix: String): List<Path> {
        if (!directory.isDirectory()) return emptyList()
        Files.walk(directory, 8).use { stream ->
            return stream.asSequence()
                .filter { it.isRegularFile() && it.name.lowercase().endsWith(suffix) }
                .filterNot { p -> p.toString().replace('\\', '/').lowercase().let { it.contains("/vendor/") || it.contains("/node_modules/") } }
                .toList()
        }
    }

    private fun manifestValue(manifest: String, key: String): String? =
        Regex("""\${'$'}modversion\s*\[\s*['"]$key['"]\s*\]\s*=\s*['"]([^'"]+)['"]""").find(manifest)?.groupValues?.get(1)

    private fun countLines(path: Path): Long = runCatching {
        Files.newBufferedReader(path).use { r -> r.lines().count() }
    }.getOrDefault(0L)

    private fun readSmall(path: Path): String? = runCatching {
        if (Files.size(path) > MAX_BYTES) null else Files.readString(path)
    }.getOrNull()

    private fun lineAt(content: String, offset: Int): Int =
        content.take(offset.coerceAtMost(content.length)).count { it == '\n' } + 1

    private companion object {
        const val MAX_BYTES = 1_500_000L
        val VERSION = Regex("""XOOPS_VERSION'\s*,\s*'([^']+)'""")
        val MODULE_VERSION = Regex("""\${'$'}modversion\s*\[\s*['"]version['"]\s*\]\s*=\s*['"]([^'"]+)['"]""")
        val BLOCK_ENTRY = Regex("""\${'$'}modversion\s*\[\s*['"]blocks['"]\s*\]\s*\[""")
        val SMARTY_VERSION = Regex("""(?:SMARTY_VERSION\s*=\s*|\${'$'}_version\s*=\s*)['"]v?(\d+)""")
        val STRICT_TYPES = Regex("""declare\s*\(\s*strict_types\s*=\s*1\s*\)""")
        val REGISTERED_TEMPLATE = Regex("""['"](?:file|template)['"]\s*=>\s*['"]([^'"]+\.tpl)['"]""")
    }
}
