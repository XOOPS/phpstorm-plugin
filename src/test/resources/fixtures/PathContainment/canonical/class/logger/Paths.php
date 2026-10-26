<?php

defined('XOOPS_ROOT_PATH') || exit('Restricted access');

class Paths
{
    private $file;

    /** filelogger.php: both sides realpath()'d, slashes normalised, separator-terminated. */
    public function insideRoot(): bool
    {
        $root = realpath(XOOPS_ROOT_PATH);
        if (false === $root) {
            return true;
        }
        $dir = realpath(dirname($this->file));
        $root = rtrim(str_replace('\\', '/', $root), '/') . '/';
        $dir  = rtrim(str_replace('\\', '/', $dir), '/') . '/';

        return 0 === stripos($dir, $root);
    }

    /** ScannerProcess.php: a prefix stripped for display, nothing is allowed or refused. */
    public static function relativeToRoot(string $pathname): string
    {
        $root = rtrim(str_replace('\\', '/', XOOPS_ROOT_PATH), '/');
        $path = str_replace('\\', '/', $pathname);

        return ('' !== $root && str_starts_with($path, $root . '/')) ? substr($path, strlen($root)) : $path;
    }

    /** upd_2.5.11-to-2.7.0: the only caller passes a realpath()'d value. */
    public static function delete(string $folderPath): bool
    {
        $resolvedRoot = realpath($folderPath);

        return false !== $resolvedRoot && self::isAllowedDeleteRoot($resolvedRoot);
    }

    private static function isAllowedDeleteRoot(string $path): bool
    {
        $prefix = rtrim(realpath(XOOPS_ROOT_PATH), '\\/') . DIRECTORY_SEPARATOR;

        return str_starts_with($path, $prefix);
    }

    /** Reported: a public method's caller is unknown. */
    public static function allowed(string $path): bool
    {
        $prefix = rtrim(realpath(XOOPS_ROOT_PATH), '\\/') . DIRECTORY_SEPARATOR;

        return str_starts_with($path, $prefix);
    }

    /** Reported: str_replace() that removes more than a separator is not canonical. */
    public function stripped(): bool
    {
        $root = realpath(XOOPS_ROOT_PATH) . '/';
        $dir  = str_replace('..', '', realpath(dirname($this->file))) . '/';

        return 0 === strpos($dir, $root);
    }
}
