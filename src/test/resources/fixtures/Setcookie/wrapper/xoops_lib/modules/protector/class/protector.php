<?php

class Protector
{
    // Runs from mainfile.php's precheck, before include/common.php defines xoops_setcookie().
    public function purgeCookies()
    {
        $domain = defined('XOOPS_COOKIE_DOMAIN') ? XOOPS_COOKIE_DOMAIN : '';
        foreach ($_COOKIE as $key => $value) {
            setcookie($key, '', time() - 3600, '/', $domain);
        }
    }
}
