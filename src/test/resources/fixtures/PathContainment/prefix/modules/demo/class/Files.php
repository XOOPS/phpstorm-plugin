<?php

class Files
{
    public function inside(string $path, string $uploadDir): bool
    {
        if (0 === strpos($path, $uploadDir)) {
            return true;
        }
        if (str_starts_with(realpath($path), XOOPS_ROOT_PATH . '/uploads')) {
            return true;
        }
        if (strpos($name, 'x') === 0) {
            return false;
        }

        return strpos($path, 'a') !== false;
    }
}
