<?php
use Xmf\Request;

require_once __DIR__ . '/admin_header.php';

$op = Request::getString('op', 'list', 'POST');
if ('POST' === Request::getMethod() && !$GLOBALS['xoopsSecurity']->check()) {
    redirect_header('item.php', 3, 'token');
}

switch ($op) {
    case 'delete':
        $itemHandler->delete($item, true);
        break;
    default:
        break;
}
