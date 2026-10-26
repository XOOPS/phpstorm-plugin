<?php

class H
{
    public $db;

    public function getCount(string $sql): int
    {
        $result = $this->db->query($sql);
        [$count] = $this->db->fetchRow($result);

        return (int) $count;
    }

    public function getObjects(string $sql)
    {
        $ret = [];
        $result = $this->db->query($sql);
        while (false !== ($row = $this->db->fetchArray($result))) {
            $ret[] = $row;
        }

        return $ret;
    }

    public function guardedInCondition($db, $r): array
    {
        $ret = [];
        while ($db->isResultSet($r) && false !== ($row = $db->fetchArray($r))) {
            $ret[] = $row;
        }

        return $ret;
    }

    public function guardedByTruthiness($db, $result)
    {
        if (!$result) {
            return false;
        }

        return $db->fetchRow($result);
    }

    public function guardedByEarlyExit($db, $r): array
    {
        if (!$db->isResultSet($r)) {
            return [];
        }

        return $db->fetchArray($r);
    }

    public function reversed($db, $r): array
    {
        $rows = [];
        if (!$db->isResultSet($r)) {
            while ($row = $db->fetchBoth($r)) {
                $rows[] = $row;
            }
        }

        return $rows;
    }
}
