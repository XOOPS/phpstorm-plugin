<?php
if (!defined('XOOPS_ROOT_PATH')) {
    http_response_code(404);
    exit;
}
header('Content-Type: application/json');
header("Cache-Control: no-store");
echo json_encode($GLOBALS['xoopsConfig']['sitename']);
