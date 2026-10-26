<?php declare(strict_types=1);

use XoopsModules\Demo\Helper;

require_once \dirname(__DIR__) . '/preloads/autoloader.php';
$helper = Helper::getInstance();
$adminmenu   = [];
$adminmenu[] = ['title' => 'Home', 'link' => 'admin/index.php'];
