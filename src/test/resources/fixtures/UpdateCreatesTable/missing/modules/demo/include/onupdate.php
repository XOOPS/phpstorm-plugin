<?php

function xoops_module_update_demo($module, $prev)
{
    $db = \XoopsDatabaseFactory::getDatabaseConnection();
    $db->queryF('CREATE TABLE IF NOT EXISTS ' . $db->prefix('demo_items') . ' (id INT NOT NULL)');
    $db->queryF('CREATE TABLE IF NOT EXISTS ' . $db->prefix('demo_tags') . ' (id INT NOT NULL)');

    return true;
}
