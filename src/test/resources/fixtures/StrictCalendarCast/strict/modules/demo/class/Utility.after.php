<?php

declare(strict_types=1);

class Utility
{
    public static function monthStart(): int
    {
        return mktime(0, 0, 0, (int) date('n'), (int) date('j'), (int) date('Y')) + idate(substr('Y', 0, 1), 0);
    }
}
