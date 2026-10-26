<?php
include __DIR__ . '/../../mainfile.php';
$pass = (string) ($_POST['pass'] ?? '');
$criteria = new CriteriaCompo(new Criteria('loginname', 'admin'));
$criteria->add(new Criteria('pass', md5($pass)));
$stored = md5($pass);
