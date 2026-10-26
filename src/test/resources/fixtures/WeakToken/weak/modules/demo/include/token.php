<?php

$token    = md5(uniqid(mt_rand(), true));
$csrfKey  = bin2hex(random_bytes(16));
$hash     = md5($password);
$ok       = $token == $_SESSION['token'];
$same     = 0 === strcmp($secret, $given);
$strict   = hash_equals($secret, $given);
$count    = mt_rand(1, 10);
