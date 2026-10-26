<?php
defined('XOOPS_ROOT_PATH') || exit('Restricted access');

// Bundled SDK: the guard above is the only line that mentions the core.
class Sdk
{
    public function cookie(string $name)
    {
        return isset($_COOKIE[$name]) ? $_COOKIE[$name] : null;
    }
}
