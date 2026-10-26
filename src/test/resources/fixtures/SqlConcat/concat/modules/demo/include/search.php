<?php

use Xmf\Request;

$sql    = 'SELECT * FROM ' . $xoopsDB->prefix('demo_items') . ' WHERE id=' . $_GET['id'];
$result = $xoopsDB->query($sql);
$xoopsDB->exec('DELETE FROM ' . $xoopsDB->prefix('demo_items') . ' WHERE id=' . (int)$_GET['id']);
$xoopsDB->query('SELECT 1 FROM x WHERE name=' . $xoopsDB->quote(Request::getString('n', '', 'GET')));
$xoopsDB->query("SELECT 1 FROM x WHERE name='{$_POST['name']}'");
$xoopsDB->query('SELECT * FROM t LIMIT ' . count($_POST['ids']));
