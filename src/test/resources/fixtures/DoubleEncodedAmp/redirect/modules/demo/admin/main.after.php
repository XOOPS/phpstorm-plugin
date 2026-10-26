<?php
require __DIR__ . '/admin_header.php';
$adminObject->addItemButton('Add', 'main.php?op=new&type=1', 'add');
redirect_header('index.php?op=list&id=' . $id, 2, 'Saved');
echo '<a href="main.php?op=list&amp;id=1">list</a>';
$adminObject->addItemButton('Fish &amp; Chips', 'index.php', 'list');
header('X-Label: Fish &amp; Chips');
header('Location: view.php?id=1&page=2');
