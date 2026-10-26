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
        if ($info->isFile()) {
            \unlink($info->getRealPath());
        }
    }

    // Reported: no check at all.
    public function removeBlindly(\SplFileInfo $info): void
    {
        \unlink($info->getRealPath());
    }

    // Reported: getPath() is the containing directory, not the entry isFile() checked.
    public function removeParent(\SplFileInfo $info): void
    {
        if ($info->isFile()) {
            \unlink($info->getPath());
        }
    }

    // Not reported: isLink() checks the link, and getPathname() is the link itself (also when it dangles).
    public function removeLink(\SplFileInfo $info): void
    {
        if ($info->isLink()) {
            \unlink($info->getPathname());
        }
    }

    // Reported: isLink() checks the link, getRealPath() resolves to its target.
    public function removeLinkTarget(\SplFileInfo $info): void
    {
        if ($info->isLink()) {
            \unlink($info->getRealPath());
        }
    }
}
