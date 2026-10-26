<?php
defined('XOOPS_ROOT_PATH') || exit('Restricted access');

function xoops_setcookie($name, $value = '', $expire = 0, $path = '/', $domain = '', $secure = false, $httponly = true)
{
    $options = ['expires' => $expire, 'path' => $path, 'domain' => $domain, 'secure' => $secure, 'httponly' => $httponly];

    return setcookie($name, $value, $options);
}
