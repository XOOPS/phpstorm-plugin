<?php

class N
{
    public $db;

    public function row(string $sql): Row
    {
        $result = $this->db->query($sql);
        $data = $this->db->fetchArray($result);

        return new Row($data);
    }

    public function later(string $sql)
    {
        $result = $this->db->query($sql);
        if (!$this->db->isResultSet($result)) {
            return null;
        }
        $row = $this->db->fetchArray($result);

        return $row;
    }
}
