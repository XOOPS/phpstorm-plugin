<?php declare(strict_types=1);
use Xmf\Module\Admin;
use XoopsModules\Demo\Helper;
require \dirname(__DIR__) . '/bootstrap.php';
$helper = Helper::getInstance();
$helper->loadLanguage('modinfo');
$adminmenu[] = ['title' => 'Home', 'link' => 'admin/index.php'];
