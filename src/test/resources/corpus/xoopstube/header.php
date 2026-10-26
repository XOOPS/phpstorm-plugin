<?php declare(strict_types=1);


require_once __DIR__ . '/preloads/autoloader.php';
/**
 * Module: XoopsTube
 *
 * You may not change or alter any portion of this comment or credits
 * of supporting developers from this source code or any supporting source code
 * which is considered copyrighted (c) material of the original comment or credit authors.
 *
 *
 * @category        Module
 * @author          XOOPS Development Team
 * @copyright       2001-2016 XOOPS Project (https://xoops.org)
 * @license         GNU GPL 2.0 or later (https://www.gnu.org/licenses/gpl-2.0.html)
 * @link            https://xoops.org/
 * @since           1.0.6
 */

use XoopsModules\Xoopstube\Helper;

require_once \dirname(__DIR__, 2) . '/mainfile.php';

$moduleDirName = basename(__DIR__);
$modulePath    = __DIR__;

$helper = Helper::getInstance();

require_once XOOPS_ROOT_PATH . '/modules/' . $moduleDirName . '/include/common.php';
require_once XOOPS_ROOT_PATH . '/modules/' . $moduleDirName . '/include/video.php';
require_once XOOPS_ROOT_PATH . '/modules/' . $moduleDirName . '/sbookmarks.php';
require_once XOOPS_ROOT_PATH . '/class/pagenav.php';

$helper = \XoopsModules\Xoopstube\Helper::getInstance();
$helper->loadLanguage('main');

//require_once XOOPS_ROOT_PATH . '/modules/' . $moduleDirName . '/class/TextSanitizer.php';
$myts = \MyTextSanitizer::getInstance(); // MyTextSanitizer object

if (!isset($GLOBALS['xoTheme']) || !is_object($GLOBALS['xoTheme'])) {
    require_once $GLOBALS['xoops']->path('class/theme.php');
    $GLOBALS['xoTheme'] = new \xos_opal_Theme();
}

if (!isset($GLOBALS['xoopsTpl']) || !($GLOBALS['xoopsTpl'] instanceof XoopsTpl)) {
    require $GLOBALS['xoops']->path('class/template.php');
    $xoopsTpl = new XoopsTpl();
}

//$GLOBALS['xoTheme']->addStylesheet('modules/' . $moduleDirName . '/assets/css/xtubestyle.css');

$GLOBALS['xoTheme']->addScript(XOOPS_URL . '/browse.php?Frameworks/jquery/jquery.js');
//$xoTheme->addScript($helper->url('assets/js/magnific/jquery.magnific-popup.min.js'));
//$xoTheme->addStylesheet($helper->url('assets/js/magnific/magnific-popup.css'));
//$GLOBALS['xoTheme']->addStylesheet(XOOPSTUBE_URL . '/assets/css/module.css');
$GLOBALS['xoTheme']->addStylesheet($helper->url('assets/css/module.css'));

require_once XOOPS_ROOT_PATH . '/header.php';

global $xoopModuleConfig;
