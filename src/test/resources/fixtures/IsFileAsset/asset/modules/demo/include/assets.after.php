<?php

$file = XOOPS_ROOT_PATH . '/modules/demo/assets/' . $name;
if (file_exists($file) && is_file($file)) {
    readfile($file);
}
if (is_readable($file) && is_file($file) && $x) {
    include $file;
}
if (!file_exists($file)) {
    echo file_get_contents($file);
}
if (file_exists($file) && is_file($file)) {
    readfile($file);
}
if (file_exists($dir)) {
    mkdir($dir);
}
