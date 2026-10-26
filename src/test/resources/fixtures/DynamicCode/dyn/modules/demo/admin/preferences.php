<?php
require __DIR__ . '/admin_header.php';
foreach ($_POST as $k => $v) {
    ${$k} = $v;
}
$name = 'x';
$$name = 1;
extract($_POST);
$ok = "{$name}";
