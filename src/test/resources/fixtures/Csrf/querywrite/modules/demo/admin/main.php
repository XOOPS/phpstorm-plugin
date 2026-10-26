<?php
use Xmf\Request;

require_once __DIR__ . '/admin_header.php';

$op = Request::getString('op', 'list', 'GET');
switch ($op) {
    case 'purge':
        $sql = 'DELETE FROM ' . $GLOBALS['xoopsDB']->prefix('demo_log') . ' WHERE 1';
        $GLOBALS['xoopsDB']->query($sql);
        break;
    case 'list':
        $result = $GLOBALS['xoopsDB']->query('SELECT * FROM ' . $GLOBALS['xoopsDB']->prefix('demo_log'));
        break;
    default:
        break;
}
