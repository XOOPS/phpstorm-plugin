<?php
// Shape of edituser.php: password confirmation compared loosely.
use Xmf\Request;

include __DIR__ . '/header.php';

$errors   = [];
$password = Request::getVar('password', '', 'POST', 'string', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM);
if (!empty($password)) {
    if (strlen($password) < 5) {
        $errors[] = 'Too short';
    }
    $vpass = Request::getVar('vpass', '', 'POST', 'string', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM);
    if ($password !== $vpass) {
        $errors[] = 'The passwords differ';
    } elseif (mb_strtolower($password, 'UTF-8') === 'admin') {
        $errors[] = 'Same as the name';
    }
}
if ($password != '') {
    echo 'changing';
}
$addtoken = true;
if ($addtoken != false) {
    echo 'token';
}

function demo_check_pass($pass, $vpass)
{
    // Parameters: their type is the caller's, no fix.
    if ($pass != $vpass) {
        return false;
    }

    return true;
}
