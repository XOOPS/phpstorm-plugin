<?php

class Tags
{
    public function allowed(string $tag, array $blacklist): bool
    {
        if (!preg_match('/^[a-z][a-z0-9]*$/i', $tag)) {
            return false;
        }

        return !in_array($tag, $blacklist, true);
    }

    public function scheme(string $s): bool
    {
        if (!preg_match('/^[a-z]+$/D', $s)) {
            return false;
        }

        return $s === 'https';
    }

    public function noCompare(string $s): bool
    {
        return (bool) preg_match('/^[a-z]+$/', $s);
    }

    public function escaped(string $s): bool
    {
        if (preg_match('/^\d+\$/', $s)) {
            return $s === '1$';
        }

        return false;
    }
}
