<?php
// Shape of system/admin/modulesadmin/main.php: two $op-scoped gates before the switches.
use Xmf\Request;

$op = Request::getString('op', 'list');

if (in_array($op, ['confirm', 'submit'], true)) {
    if (!$GLOBALS['xoopsSecurity']->check()) {
        $op = 'list';
    }
}

if (in_array($op, ['order', 'display'], true)) {
    if (!$GLOBALS['xoopsSecurity']->check()) {
        http_response_code(403);
        exit;
    }
    $module_handler = xoops_getHandler('module');
    switch ($op) {
        case 'order':
            $module_handler->insert($module);
            break;
        case 'display':
            $module_handler->insert($module);
            break;
    }
    exit;
}

switch ($op) {
    case 'confirm':
        $module_handler->insert($module);
        break;
    case 'remove':
        $module_handler->delete($module);
        break;
    case 'list':
    default:
        echo 'list';
        break;
}
