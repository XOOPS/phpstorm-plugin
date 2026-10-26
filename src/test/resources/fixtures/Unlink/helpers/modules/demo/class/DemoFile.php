<?php
defined('XOOPS_ROOT_PATH') || exit('Restricted access');

class DemoFile
{
    public $path = '';

    public function pwd()
    {
        return $this->path;
    }

    public function exists()
    {
        return file_exists($this->pwd()) && is_file($this->pwd());
    }

    public function delete()
    {
        if ($this->exists()) {
            return unlink($this->pwd());
        }

        return false;
    }

    public function deleteOther($other)
    {
        if ($this->exists()) {
            return unlink($other);
        }

        return false;
    }
}

function demo_clean(array $files, $dir)
{
    foreach ($files as $f) {
        if (!is_file($f)) {
            continue;
        }
        $resolved = realpath($f);
        if (false === $resolved) {
            continue;
        }
        unlink($resolved);
    }
    $probe = $dir . '/.probe';
    if (false === file_put_contents($probe, '')) {
        return false;
    }
    unlink($probe);
    $other = $dir . '/.other';
    if ($dir !== '' && false === file_put_contents($other, '')) {
        return false;
    }
    unlink($other);

    return true;
}
