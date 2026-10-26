<?php

class Loose
{
    public static function monthStart(): int
    {
        return mktime(0, 0, 0, date('n'), 1, date('Y'));
    }
}
