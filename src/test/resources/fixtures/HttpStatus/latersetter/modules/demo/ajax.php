<?php
require dirname(__DIR__, 2) . '/mainfile.php';
header('HTTP/1.0 404 Not Found');
if (empty($items)) {
    http_response_code(500);
}
