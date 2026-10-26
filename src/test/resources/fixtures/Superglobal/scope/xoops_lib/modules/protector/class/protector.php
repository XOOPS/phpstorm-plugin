<?php
// Protector rewrites the raw request before XOOPS_ROOT_PATH code reads it.

class Protector
{
    public function sanitize()
    {
        foreach ($_GET as $key => $val) {
            $_GET[$key] = str_replace("\0", '', $val);
        }
        $ip = $_SERVER['REMOTE_ADDR'];
    }
}
