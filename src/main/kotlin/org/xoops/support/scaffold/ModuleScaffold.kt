package org.xoops.support.scaffold

/**
 * The files of a new module, as `relative path → content`, pure and testable. The output passes
 * this plugin's own inspections (file headers, guards, template registration, language roles,
 * `include_once` for the core header, no `xoops_cp_header()` twice), which the test asserts.
 *
 * `legacy`: `xoops_version.php`, `index.php`, English language files and the index template.
 * `hybrid` adds the XOOPS 2.5.x+ module layout the XoopsModules repositories use: a PSR-4
 * `preloads/autoloader.php`, a `class/Helper.php` on `Xmf\Module\Helper`, `include/common.php`,
 * and an admin side built on `Xmf\Module\Admin`.
 */
object ModuleScaffold {

    val DIRNAME = Regex("[a-z][a-z0-9_]{0,31}")

    fun files(dirname: String, hybrid: Boolean): Map<String, String> {
        require(DIRNAME.matches(dirname)) { "dirname must match ${DIRNAME.pattern}" }
        val up = dirname.uppercase()
        val cap = dirname.replaceFirstChar { it.uppercase() }
        val out = LinkedHashMap<String, String>()
        out["xoops_version.php"] = xoopsVersion(dirname, up, cap, hybrid)
        out["index.php"] = indexPhp(dirname, cap, hybrid)
        out["language/english/modinfo.php"] = modinfo(up, cap, hybrid)
        out["language/english/main.php"] = mainLang(up, cap)
        out["templates/${dirname}_index.tpl"] = indexTpl(dirname, up)
        out["templates/index.html"] = ""
        out["language/index.html"] = ""
        out["language/english/index.html"] = ""
        if (hybrid) {
            out["preloads/autoloader.php"] = autoloader(cap)
            out["class/Helper.php"] = helper(cap)
            out["include/common.php"] = common(cap)
            out["language/english/admin.php"] = adminLang(up, cap)
            out["admin/admin_header.php"] = adminHeader(cap)
            out["admin/admin_footer.php"] = adminFooter(cap)
            out["admin/index.php"] = adminIndex(cap)
            out["admin/menu.php"] = adminMenu(up, cap)
            out["class/index.html"] = ""
            out["include/index.html"] = ""
            out["preloads/index.html"] = ""
            out["admin/index.html"] = ""
        }
        return out
    }

    private fun header(cap: String, what: String): String = """
        |<?php
        |/**
        | * $cap module for XOOPS: $what
        | *
        | * @copyright   XOOPS Project (https://xoops.org)
        | * @license     GPL-2.0-or-later (https://www.gnu.org/licenses/gpl-2.0.html)
        | * @package     $cap
        | */
        |""".trimMargin()

    private const val GUARD = "defined('XOOPS_ROOT_PATH') || exit('Restricted access');"

    private fun xoopsVersion(dirname: String, up: String, cap: String, hybrid: Boolean): String = header(cap, "module manifest") + """
        |
        |${'$'}modversion['name']        = _MI_${up}_NAME;
        |${'$'}modversion['version']     = '1.0.0';
        |${'$'}modversion['description'] = _MI_${up}_DESC;
        |${'$'}modversion['author']      = 'XOOPS Project';
        |${'$'}modversion['credits']     = 'XOOPS Project';
        |${'$'}modversion['license']     = 'GPL-2.0-or-later';
        |${'$'}modversion['license_url'] = 'https://www.gnu.org/licenses/gpl-2.0.html';
        |${'$'}modversion['official']    = 0;
        |${'$'}modversion['image']       = 'assets/images/logo.png';
        |${'$'}modversion['dirname']     = '$dirname';
        |${'$'}modversion['min_php']     = '7.4';
        |${'$'}modversion['min_xoops']   = '2.5.11';
        |
        |// Front side
        |${'$'}modversion['hasMain']       = 1;
        |${'$'}modversion['templates'][] = [
        |    'file'        => '${dirname}_index.tpl',
        |    'description' => 'Module index',
        |];
        |""".trimMargin() + (if (!hybrid) "" else """
        |
        |// Admin side
        |${'$'}modversion['hasAdmin']   = 1;
        |${'$'}modversion['adminindex'] = 'admin/index.php';
        |${'$'}modversion['adminmenu']  = 'admin/menu.php';
        |${'$'}modversion['system_menu'] = 1;
        |""".trimMargin())

    private fun indexPhp(dirname: String, cap: String, hybrid: Boolean): String = header(cap, "front page") + """
        |
        |require dirname(__DIR__, 2) . '/mainfile.php';
        |${if (hybrid) "require_once __DIR__ . '/include/common.php';\n|" else ""}
        |${'$'}GLOBALS['xoopsOption']['template_main'] = '${dirname}_index.tpl';
        |require_once XOOPS_ROOT_PATH . '/header.php';
        |
        |${'$'}GLOBALS['xoopsTpl']->assign('message', '');
        |
        |require_once XOOPS_ROOT_PATH . '/footer.php';
        |""".trimMargin().replace("\n|\n", "\n")

    private fun modinfo(up: String, cap: String, hybrid: Boolean): String = header(cap, "module information (English)") + """
        |
        |define('_MI_${up}_NAME', '$cap');
        |define('_MI_${up}_DESC', '$cap module for XOOPS');
        |""".trimMargin() + (if (!hybrid) "" else """
        |
        |// Admin menu
        |define('_MI_${up}_ADMENU_HOME', 'Home');
        |define('_MI_${up}_ADMENU_ABOUT', 'About');
        |""".trimMargin())

    private fun mainLang(up: String, cap: String): String = header(cap, "front-side strings (English)") + """
        |
        |define('_MD_${up}_TITLE', '$cap');
        |""".trimMargin()

    private fun adminLang(up: String, cap: String): String = header(cap, "admin-side strings (English)") + """
        |
        |define('_AM_${up}_HOME', 'Home');
        |""".trimMargin()

    private fun indexTpl(dirname: String, up: String): String = """
        |<{* $dirname index *}>
        |<div class="module-$dirname">
        |    <h1><{${'$'}smarty.const._MD_${up}_TITLE|escape}></h1>
        |    <p><{${'$'}message|default:''|escape}></p>
        |</div>
        |""".trimMargin()

    private fun autoloader(cap: String): String = header(cap, "PSR-4 autoloader for XoopsModules\\$cap") + """
        |
        |spl_autoload_register(static function (string ${'$'}class): void {
        |    ${'$'}prefix = 'XoopsModules\\$cap\\';
        |    if (0 !== strncmp(${'$'}class, ${'$'}prefix, strlen(${'$'}prefix))) {
        |        return;
        |    }
        |    ${'$'}file = dirname(__DIR__) . '/class/' . str_replace('\\', '/', substr(${'$'}class, strlen(${'$'}prefix))) . '.php';
        |    if (is_file(${'$'}file)) {
        |        require ${'$'}file;
        |    }
        |});
        |""".trimMargin()

    private fun helper(cap: String): String = header(cap, "module helper") + """
        |
        |namespace XoopsModules\$cap;
        |
        |$GUARD
        |
        |/**
        | * One instance per request: paths, URLs, config and handlers of this module.
        | */
        |class Helper extends \Xmf\Module\Helper
        |{
        |    public ${'$'}debug;
        |
        |    public function __construct(${'$'}debug = false)
        |    {
        |        ${'$'}this->debug   = ${'$'}debug;
        |        ${'$'}this->dirname = basename(dirname(__DIR__));
        |        parent::__construct(${'$'}this->dirname);
        |    }
        |
        |    public static function getInstance(${'$'}debug = false): self
        |    {
        |        static ${'$'}instance;
        |        if (null === ${'$'}instance) {
        |            ${'$'}instance = new static(${'$'}debug);
        |        }
        |
        |        return ${'$'}instance;
        |    }
        |}
        |""".trimMargin()

    private fun common(cap: String): String = header(cap, "shared bootstrap of the module pages") + """
        |
        |$GUARD
        |
        |require_once dirname(__DIR__) . '/preloads/autoloader.php';
        |
        |/** @var \XoopsModules\$cap\Helper ${'$'}helper */
        |${'$'}helper = \XoopsModules\$cap\Helper::getInstance();
        |${'$'}helper->loadLanguage('common');
        |""".trimMargin()

    private fun adminHeader(cap: String): String = header(cap, "admin bootstrap") + """
        |
        |require dirname(__DIR__, 3) . '/include/cp_header.php';
        |require_once dirname(__DIR__) . '/include/common.php';
        |
        |${'$'}adminObject = \Xmf\Module\Admin::getInstance();
        |${'$'}helper->loadLanguage('admin');
        |""".trimMargin()

    private fun adminFooter(cap: String): String = header(cap, "admin footer") + """
        |
        |xoops_cp_footer();
        |""".trimMargin()

    private fun adminIndex(cap: String): String = header(cap, "admin dashboard") + """
        |
        |require __DIR__ . '/admin_header.php';
        |
        |xoops_cp_header();
        |${'$'}adminObject->displayNavigation(basename(__FILE__));
        |${'$'}adminObject->displayIndex();
        |
        |require __DIR__ . '/admin_footer.php';
        |""".trimMargin()

    private fun adminMenu(up: String, cap: String): String = header(cap, "admin menu") + """
        |
        |${'$'}adminmenu   = [];
        |${'$'}adminmenu[] = [
        |    'title' => _MI_${up}_ADMENU_HOME,
        |    'link'  => 'admin/index.php',
        |    'icon'  => 'assets/images/icons/32/home.png',
        |];
        |${'$'}adminmenu[] = [
        |    'title' => _MI_${up}_ADMENU_ABOUT,
        |    'link'  => 'admin/about.php',
        |    'icon'  => 'assets/images/icons/32/about.png',
        |];
        |""".trimMargin()
}
