<?php

use Xmf\Request;

require \dirname(__DIR__, 3) . '/mainfile.php';

$id = Request::getText('id', '', 'POST');
echo $id;
if (Request::hasVar('new', 'POST')) {
    $new = true;
}
if (Request::hasVar('_method', 'POST') && Request::hasVar('_method', 'POST')) {
    $method = 'x';
}
$_GET['categoryid'] = 5;
foreach (Request::get('POST', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM) as $k => $v) {
    $all[$k] = $v;
}
if (empty(Request::getVar('x', null, 'GET', 'none', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM))) {
    $x = 1;
}
$a = Request::getVar('a', null, 'default', 'none', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM);
if (Request::hasVar($name, 'POST')) {
    $dyn = 1;
}
if (Request::hasVar('submit', 'POST') && Request::hasVar('passwd', 'POST')) {
    $both = 1;
}
if (!(Request::hasVar('step', 'POST') && isset($_SESSION['req']))) {
    $mixed = 1;
}
$ok = (Request::hasVar('a', 'GET') && Request::hasVar('b', 'GET'));
if (isset($_POST)) {
    $always = 1;
}
