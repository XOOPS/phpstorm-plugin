<?php
// One side is reassigned from something that is not request text: not proven, no fix.
use Xmf\Request;

include __DIR__ . '/header.php';

$password = Request::getString('newpass', '', 'POST');
$vpass    = Request::getString('vpass', '', 'POST');
if ($legacy) {
    $vpass = $row['pass'];
}
if ($password != $vpass) {
    echo 'differ';
}
