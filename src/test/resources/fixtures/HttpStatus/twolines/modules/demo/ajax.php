<?php
require dirname(__DIR__, 2) . '/mainfile.php';
header('HTTP/1.1 200 OK');
if (empty($items)) {
    header('HTTP/1.0 404 Not Found');
}
