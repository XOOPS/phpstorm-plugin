<?php
if (!defined('XOOPS_ROOT_PATH')) {
    header('HTTP/1.0 404 Not Found');
    exit;
}
header('Content-Type: application/json');
header("Cache-Control: no-store");
echo json_encode($GLOBALS['xoopsConfig']['sitename']);
