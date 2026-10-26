<?php

class L
{
    public $db;

    public function getObjects(string $sql)
    {
        $ret = [];
        $result = $this->db->query($sql);
        if (!$this->db->isResultSet($result)) {
            return $ret;
        }
        while (false !== ($row = $this->db->fetchArray($result))) {
            $ret[] = $row;
        }

        return $ret;
    }

    public function getCount(string $sql): int
    {
        if (!$this->db->isResultSet($result = $this->db->query($sql))) {
            return 0;
        }
        [$count] = $this->db->fetchRow($result);

        return (int) $count;
    }

    public function nested(string $sql)
    {
        return $this->db->fetchArray($this->db->query($sql));
    }

    public function unbraced(string $sql)
    {
        $row = null;
        if ($this->db->isResultSet($result = $this->db->query($sql))) $row = $this->db->fetchArray($result);

        return $row;
    }

    public function elseBranch($result): array
    {
        if (!$this->db->isResultSet($result)) {
            return [];
        } else {
            return $this->db->fetchArray($result);
        }
    }
}
