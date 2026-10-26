<?php

class H
{
    public $db;

    public function sql($criteria): string
    {
        // kernel/comment.php: the allow-listed builder result is concatenated, not getSort().
        $sql = 'SELECT * FROM t';
        $sql .= ' ORDER BY ' . self::buildOrderBy($criteria->getSort(), $criteria->getOrder(), ['id', 'title'], 'id');

        return $sql;
    }

    public function raw($criteria): string
    {
        return 'SELECT * FROM t ORDER BY ' . $criteria->getSort() . ' ' . $criteria->getOrder();
    }
}
