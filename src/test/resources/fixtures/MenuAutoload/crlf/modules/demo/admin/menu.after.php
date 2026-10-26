<?php  declare(strict_types=1);

/*
 * You may not change or alter any portion of this comment or credits
 * of supporting developers from this source code or any supporting source code
 * which is considered copyrighted (c) material of the original comment or credit authors.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

/**
 * @copyright    2000-2026 XOOPS Project (https://xoops.org)
 * @license      GNU GPL 2.0 or later (https://www.gnu.org/licenses/gpl-2.0.html)
 * @author       Kazumi Ono (AKA onokazu) http://www.myweb.ne.jp/, https://xoops.org/, http://jp.xoops.org/
 * @author       XOOPS Development Team
 */

use Xmf\Module\Admin;
use XoopsModules\Demo\Helper;

$moduleDirName      = \basename(\dirname(__DIR__));
$moduleDirNameUpper = \mb_strtoupper($moduleDirName);

require_once \dirname(__DIR__) . '/preloads/autoloader.php';
$helper = Helper::getInstance();
$helper->loadLanguage('common');
$adminmenu[] = ['title' => 'Home', 'link' => 'admin/index.php'];
