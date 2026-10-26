<?php
defined('XOOPS_ROOT_PATH') || exit('Restricted access');

// Shape of class/preload.php: the compared value is a substring, not the matched subject.
function demo_preloads(array $fileList): array
{
    $out = [];
    foreach ($fileList as $file) {
        if (preg_match('/(\.php)$/i', $file)) {
            $file = substr($file, 0, -4);
            if ('index' !== $file) {
                $out[] = $file;
            }
        }
    }

    return $out;
}
