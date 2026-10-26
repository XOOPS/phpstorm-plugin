<?php

namespace XoopsModules\Demo;

class Handler
{
    public $db;

    public function updateCounter(int $id): bool
    {
        $sql = 'UPDATE ' . $this->db->prefix('demo_items') . ' SET counter=counter+1 WHERE itemid = ' . $id;
        if ($this->db->exec($sql)) {
            return true;
        }

        return false;
    }

    public function count(): int
    {
        $sql = 'SELECT COUNT(*) FROM ' . $this->db->prefix('demo_items');
        $result = $this->db->query($sql);
        [$count] = $this->db->fetchRow($result);

        return (int) $count;
    }

    public function insert($obj, bool $force = false)
    {
        $sql2 = 'INSERT INTO ' . $this->db->prefix('demo_items') . ' (title) VALUES (' . $this->db->quote($obj->title) . ')';
        if ($force) {
            $result = $this->db->exec($sql2);
        } else {
            $result = $this->db->query($sql2);
        }

        return $result;
    }

    public function raw(string $unknown)
    {
        return $this->db->queryF($unknown);
    }
}
