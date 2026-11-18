<?php
require dirname(__DIR__, 2) . '/mainfile.php';
header($_SERVER['SERVER_PROTOCOL'] . ' 403 Forbidden');
header("$proto 500 Internal Server Error");
if (empty($items)) {
    header('HTTP/1.0 404 Not Found');
}
