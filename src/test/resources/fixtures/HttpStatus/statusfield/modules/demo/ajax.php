<?php
require dirname(__DIR__, 2) . '/mainfile.php';
header('status: 403 Forbidden');
if (empty($items)) {
    header('HTTP/1.0 404 Not Found');
}
