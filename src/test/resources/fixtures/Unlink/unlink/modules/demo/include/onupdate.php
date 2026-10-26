<?php

function xoops_module_update_demo($module, $previousVersion, string $templateFolder, array $files, string $other): bool
{
    foreach ($files as $v) {
        if (\is_file($templateFolder . $v)) {
            unlink($templateFolder . $v);
        }
    }
    unlink($other);

    return true;
}
