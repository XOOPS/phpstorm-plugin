<?php

function demo_notify_iteminfo($category, $item_id)
{
    $sql = 'SELECT name FROM ' . $GLOBALS['xoopsDB']->prefix('demo_items') . ' WHERE id = ' . (int) $item_id;
    $result       = $GLOBALS['xoopsDB']->query($sql); // TODO: error check
    if (!$GLOBALS['xoopsDB']->isResultSet($result)) {
        return null;
    }
    $resultArray  = $GLOBALS['xoopsDB']->fetchArray($result);
    $item['name'] = $resultArray['name'];

    return $item;
}
