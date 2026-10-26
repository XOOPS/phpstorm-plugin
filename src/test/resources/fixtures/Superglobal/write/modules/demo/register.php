<?php
include __DIR__ . '/../../mainfile.php';
$postfields = ['uname' => 'x'];
$_SESSION['profile_post'] = array_merge($_SESSION['profile_post'] ?? [], $postfields);
$_POST = array_merge($_SESSION['profile_post'], $_POST);
