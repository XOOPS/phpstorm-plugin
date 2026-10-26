<?php

require_once XOOPS_ROOT_PATH . '/kernel/object.php';
include_once $GLOBALS['xoops']->path('kernel/user.php');
include_once __DIR__ . '/class/xoopsuser.php';
$mimetypes = include XOOPS_ROOT_PATH . '/include/mimetypes.inc.php';
include_once XOOPS_ROOT_PATH . '/class/errorhandler.php';

xoops_load('xoopscache');
xoops_load("XoopsCache");
xoops_load('xoopsmediauploader', 'core');
xoops_load('utility', 'mymodule');

$adminObject = \Xmf\Module\Admin::getInstance();
$adminObject = \Xmf\Module\Admin::getInstance();
