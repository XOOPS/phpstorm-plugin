<?php

namespace XoopsModules\Demo;

defined('XOOPS_ROOT_PATH') || exit('Restricted access');

class Redundant
{
    private $db;

    /** install/page_tablesfill.php: the isResultSet() exit comes first, the `!$result` exit is redundant. */
    public function count()
    {
        $result = $this->db->query('SELECT COUNT(*) FROM t');
        if (!$this->db->isResultSet($result)) {
            throw new \RuntimeException('query failed');
        }
        if (!$result) {
            return 0;
        }
        [$count] = $this->db->fetchRow($result);

        return $count;
    }

    /** Reported: only the legacy exit guards the fetch. */
    public function legacy()
    {
        $result = $this->db->query('SELECT COUNT(*) FROM t');
        if (!$result) {
            return 0;
        }
        [$count] = $this->db->fetchRow($result);

        return $count;
    }
}
