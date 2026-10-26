<?php

/** Cookie helper of the XOOPS demo module. */
class U
{
    public static function cookie(string $name, string $value, int $expire): void
    {
        setcookie($name, $value, $expire, '/', '', false, true);
    }
}
