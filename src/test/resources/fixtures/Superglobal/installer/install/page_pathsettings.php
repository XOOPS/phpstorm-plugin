<?php
require_once __DIR__ . '/include/common.inc.php';
// Raw $_GET: the XMF autoloader is not available until the user enters the xoops_lib path here.
$rawNewPath = $_GET['path'] ?? '';
