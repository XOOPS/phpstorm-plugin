<?php

function demo_count(): int
{
    global $xoopsDB;
    $sql    = 'SELECT COUNT(*) FROM ' . $xoopsDB->prefix('demo_items');
    $result = $xoopsDB->query($sql);
    if (!$xoopsDB->isResultSet($result)) {
        \trigger_error("Query Failed! SQL: $sql- Error: " . $xoopsDB->error(), E_USER_ERROR);
    }
    [$count] = $xoopsDB->fetchRow($result);

    return (int)$count;
}
