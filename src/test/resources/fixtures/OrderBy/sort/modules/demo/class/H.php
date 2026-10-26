<?php

use Xmf\Request;

class H
{
    public $db;

    public function sql($criteria): string
    {
        $sql = 'SELECT * FROM t';
        $sql .= ' ORDER BY ' . $criteria->getSort() . ' ' . $criteria->getOrder();
        $criteria->setSort(Request::getString('sort', 'id'));
        $criteria->setOrder('ASC');

        return $sql;
    }
}
