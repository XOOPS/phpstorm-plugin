<?php

defined('XOOPS_ROOT_PATH') || exit('Restricted access');

/** include/file_safety.php: the existence check sits in a previous try whose catch also returns. */
function demo_remove_quietly(string $path): void
{
    try {
        if (!file_exists($path) && !is_link($path)) {
            return;
        }
    } catch (\Throwable $e) {
        return;
    }
    $previousLevel = error_reporting(0);
    try {
        $ok = unlink($path);
    } catch (\Throwable $e) {
        $ok = false;
    } finally {
        error_reporting($previousLevel);
    }
}

/** Reported: the catch falls through to unlink() without the check. */
function demo_remove_loosely(string $path): void
{
    try {
        if (!file_exists($path)) {
            return;
        }
    } catch (\Throwable $e) {
        error_log('check failed');
    }
    unlink($path);
}
