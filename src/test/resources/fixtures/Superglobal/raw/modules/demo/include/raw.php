<?php

use Xmf\Request;

$x = $_POST['unknown'];
doSomething($x);
if (empty($_GET['q'])) {
    $e = 1;
}
if (null !== $_POST['m']) {
    $m = 1;
}
if ($_POST['m2'] === null) {
    $m = 2;
}
if (null != $_GET['l']) {
    $m = 3;
}
$all = $_POST;
if (empty($_POST)) {
    $none = 1;
}
foreach ($_REQUEST as $k => $v) {
    $copy[$k] = $v;
}
$r = $_REQUEST['op'];
switch ($r) {
    case 'a':
        break;
}
$v0 = $_POST['v'][0];
echo "Hi {$_POST['who']}";
$_POST['w'] = 1;
sort($_POST['s']);
$dyn = $_GET[$k];
