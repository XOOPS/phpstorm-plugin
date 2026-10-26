<?php

namespace XoopsModules\Demo;

class Cleaner
{
    public function emptyDirectory(string $src): bool
    {
        $iterator = new \DirectoryIterator($src);
        foreach ($iterator as $fObj) {
            if ($fObj->isFile()) {
                $filename = $fObj->getPathname();
                $fObj     = null;
                if (!\unlink($filename)) {
                    return false;
                }
            }
        }

        return true;
    }

    public function removeEntry(\SplFileInfo $info): void
    {
        if ($info->isFile() || $info->isLink()) {
            \unlink($info->getRealPath());
        }
    }

    public function removeBlindly(\SplFileInfo $info): void
    {
        \unlink($info->getRealPath());
    }
}
