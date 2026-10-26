<?php

namespace XoopsModules\Demo;

defined('XOOPS_ROOT_PATH') || exit('Restricted access');

class Flow
{
    private $db;

    public function ternary()
    {
        $result = $this->db->query('SELECT 1');
        $row = $this->db->isResultSet($result) && $result instanceof \mysqli_result ? $this->db->fetchRow($result) : false;

        return $row;
    }

    public function chain()
    {
        $check = $this->db->query('SHOW COLUMNS');

        return $this->db->isResultSet($check) && ($check instanceof \mysqli_result) && false !== $this->db->fetchArray($check);
    }

    public function loop(array $tables)
    {
        foreach ($tables as $t) {
            $result = $this->db->query('SELECT COUNT(*) FROM ' . $t);
            if (!$this->db->isResultSet($result) || !($result instanceof \mysqli_result)) {
                break;
            }
            $row = $this->db->fetchArray($result);
        }
    }

    public function convert($result)
    {
        $ret = [];
        while (false !== ($row = $this->db->fetchArray($result))) {
            $ret[] = $row;
        }

        return $ret;
    }

    public function negatedTernary()
    {
        $result = $this->db->query('SELECT 1');

        return !$this->db->isResultSet($result) ? $this->db->fetchRow($result) : false;
    }

    public function orChain()
    {
        $result = $this->db->query('SELECT 1');

        return $this->db->isResultSet($result) || $this->db->fetchRow($result);
    }
}
