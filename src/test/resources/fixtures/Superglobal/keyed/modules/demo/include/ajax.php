<?php

use Xmf\Request;

require \dirname(__DIR__, 3) . '/mainfile.php';

$id = $_POST['id'];
echo $id;
if (isset($_POST['new'])) {
    $new = true;
}
if (Request::hasVar('_method', 'POST') && null !== $_POST['_method']) {
    $method = 'x';
}
$_GET['categoryid'] = 5;
foreach ($_POST as $k => $v) {
    $all[$k] = $v;
}
if (empty($_GET['x'])) {
    $x = 1;
}
$a = $_REQUEST['a'];
if (isset($_POST[$name])) {
    $dyn = 1;
}
if (isset($_POST['submit'], $_POST['passwd'])) {
    $both = 1;
}
if (!isset($_POST['step'], $_SESSION['req'])) {
    $mixed = 1;
}
$ok = isset($_GET['a'], $_GET['b']);
if (isset($_POST)) {
    $always = 1;
}
