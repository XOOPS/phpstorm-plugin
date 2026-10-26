<?php

class DemoPaths
{
    public function inside(string $file, string $root, string $basePath, string $path): bool
    {
        // kernel/block.php: realpath() of the file, separator-terminated base: containment.
        $realPath     = realpath($file);
        $resolvedRoot = realpath($root);
        if ($realPath !== false && str_starts_with($realPath, $resolvedRoot . DIRECTORY_SEPARATOR)) {
            return true;
        }
        // maintenance.php: trimmed, re-terminated haystack and a prefix held in a variable.
        $avatarRootPrefix = rtrim((string) realpath($root), DIRECTORY_SEPARATOR) . DIRECTORY_SEPARATOR;
        if (str_starts_with(rtrim($realPath, DIRECTORY_SEPARATOR) . DIRECTORY_SEPARATOR, $avatarRootPrefix)) {
            return true;
        }
        // Shape tests, not containment.
        if (str_starts_with($path, '/') || strpos($file, '.') === 0) {
            return false;
        }
        if (strpos($_SERVER['HTTP_REFERER'], XOOPS_URL . '/modules/news/submit.php') === 0) {
            return false;
        }
        // edituser.php: realpath()'d, but no separator: /uploads2 passes a check for /uploads.
        $oldavatar_path = realpath(XOOPS_UPLOAD_PATH . '/' . $file);
        if (0 === strpos($oldavatar_path, (string) realpath(XOOPS_UPLOAD_PATH))) {
            return true;
        }
        // Separator, but the path is not resolved.
        return str_starts_with($path, $basePath . '/');
    }

    /** xoopskernel.php: a URL path against the site's base path. */
    protected function themeRedirectPathMatchesBase(string $path, string $basePath): bool
    {
        return $basePath === '' || $path === $basePath || str_starts_with($path, $basePath . '/');
    }

    /** ray_context.php: an array-key prefix. */
    public function excludeByPrefix(array $vars, string $prefix): array
    {
        foreach (array_keys($vars) as $key) {
            if (strpos($key, $prefix) === 0) {
                unset($vars[$key]);
            }
        }

        return $vars;
    }
}
