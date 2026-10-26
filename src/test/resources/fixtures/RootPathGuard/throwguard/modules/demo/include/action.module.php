<?php declare(strict_types=1);

use XoopsModules\Demo\Helper;

/** @var Helper $helper */
if (!defined('XOOPS_ROOT_PATH')) {
    throw new \RuntimeException('Restricted access');
}

function xoops_module_pre_install_demo(\XoopsModule $module): bool
{
    return true;
}
