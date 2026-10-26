package org.xoops.support.toolwindow

/**
 * The per-module columns of the Overview, each one switchable in Settings → XOOPS Support.
 * The settings store the *hidden* keys: a fresh install shows everything, an update keeps what
 * the user hid and shows columns that did not exist before.
 */
enum class OverviewColumn(val key: String, val label: String, val legend: String, val expensive: Boolean = false) {
    VERSION("version", "Version", "\$modversion['version']"),
    MIN_PHP("minphp", "PHP≥", "\$modversion['min_php']"),
    MIN_XOOPS("minxoops", "XOOPS≥", "\$modversion['min_xoops']"),
    MANIFEST("manifest", "Manifest", "xoops_version.php / module.json"),
    TEMPLATES("tpl", "TPL", "templates (.tpl)"),
    LANGUAGE_FILES("lang", "Lang", "language files"),
    LANGUAGES("langs", "Langs", "language packs"),
    PRELOADS("pre", "Pre", "preloads"),
    CLASSES("cls", "Cls", "PHP files under class/ and src/"),
    BLOCKS("blk", "Blk", "block entries in the manifest"),
    ADMIN("admin", "Admin", "admin/ present"),
    SQL("sql", "SQL", "installer SQL present"),
    TESTS("tests", "Tests", "tests/ present"),
    AUTOLOAD("autoload", "PSR-4", "preloads/autoloader.php or composer.json"),
    XMF_ADMIN("xmfadmin", "Xmf", "admin/ uses Xmf\\Module\\Admin"),
    STRICT_TYPES("strict", "strict", "declare(strict_types=1) somewhere in the module"),
    FINDINGS("findings", "Findings", "scanner findings in the module"),
    LINES("lines", "PHP lines", "lines in the module's PHP files (slow on large trees)", expensive = true);

    companion object {
        fun byKey(key: String): OverviewColumn? = entries.firstOrNull { it.key == key }
    }
}
