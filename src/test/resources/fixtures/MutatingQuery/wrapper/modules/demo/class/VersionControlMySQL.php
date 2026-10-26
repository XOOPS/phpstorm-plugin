<?php

class VersionControlMySQL
{
    public $xoopsDB;

    public function query($sql)
    {
        return $this->xoopsDB->query($sql);
    }

    public function bump(int $id)
    {
        $sql = 'UPDATE versions SET n = n + 1 WHERE id = ' . $id;
        return $this->query($sql);
    }

    public function pdo(int $id)
    {
        $db = new \PDO('sqlite::memory:');
        $sql = 'DELETE FROM versions WHERE id = ' . $id;
        return $db->query($sql);
    }
}
