<?php

require_once XOOPS_ROOT_PATH . '/class/xoopsobject.php';
include_once $GLOBALS['xoops']->path('class/xoopsuser.php');
include_once __DIR__ . '/class/xoopsuser.php';
$mimetypes = include XOOPS_ROOT_PATH . '/class/mimetypes.inc.php';
include_once XOOPS_ROOT_PATH . '/class/errorhandler.php';

xoops_load('cache');
xoops_load("XoopsCache");
xoops_load('Uploader', 'core');
xoops_load('utility', 'mymodule');

$adminObject = new ModuleAdmin();
$adminObject = \Xmf\Module\Admin::getInstance();
