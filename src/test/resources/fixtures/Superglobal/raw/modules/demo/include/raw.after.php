<?php

use Xmf\Request;

$x = Request::getVar('unknown', null, 'POST', 'none', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM);
doSomething($x);
if (empty(Request::getVar('q', null, 'GET', 'none', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM))) {
    $e = 1;
}
if (Request::hasVar('m', 'POST')) {
    $m = 1;
}
if (!Request::hasVar('m2', 'POST')) {
    $m = 2;
}
if (null != Request::getVar('l', null, 'GET', 'none', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM)) {
    $m = 3;
}
$all = Request::get('POST', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM);
if (empty(Request::get('POST', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM))) {
    $none = 1;
}
foreach (Request::get('default', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM) as $k => $v) {
    $copy[$k] = $v;
}
$r = Request::getString('op', '', 'default');
switch ($r) {
    case 'a':
        break;
}
$v0 = Request::getVar('v', null, 'POST', 'none', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM)[0];
echo "Hi {$_POST['who']}";
$_POST['w'] = 1;
sort($_POST['s']);
$dyn = Request::getVar($k, null, 'GET', 'none', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM);
