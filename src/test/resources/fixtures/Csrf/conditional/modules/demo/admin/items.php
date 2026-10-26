<?php
// A gate behind an unrelated condition, and one in a POST-only block that GET walks past: neither covers.
use Xmf\Request;

$op = Request::getString('op', 'list', 'GET');
if ($debugMode) {
    if (!$GLOBALS['xoopsSecurity']->check()) {
        exit;
    }
}
if ('POST' === Request::getMethod()) {
    if (!$GLOBALS['xoopsSecurity']->check()) {
        exit;
    }
}

switch ($op) {
    case 'save':
        $handler->insert($item);
        break;
}
