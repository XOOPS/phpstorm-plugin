<?php

class R
{
    public function &rows($db, string $sql): array
    {
        $result = $db->query($sql);
        if (!$db->isResultSet($result)) {
            $ret = [];
            return $ret;
        }
        while (false !== ($row = $db->fetchArray($result))) {
            $rows[] = $row;
        }

        return $rows;
    }

    public function &first($db, string $sql)
    {
        $result = $db->query($sql);
        if (!$db->isResultSet($result)) {
            $ret = null;
            return $ret;
        }
        $row = $db->fetchRow($result);

        return $row;
    }

    public function show($db, string $sql): void
    {
        $result = $db->query($sql);
        if (!$db->isResultSet($result)) {
            return;
        }
        $row = $db->fetchRow($result);
        echo $row[0];
    }
}
