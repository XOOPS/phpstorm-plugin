<?php

class VersionControlMySQL
{
    public $xoopsDB;

    public function purge(int $id)
    {
        $sql = 'DELETE FROM versions WHERE id = ' . $id;
        $this->xoopsDB->exec($sql);
        $this->queryF($sql);
    }

    public function queryF($sql)
    {
        return $this->xoopsDB->exec($sql);
    }
}
