<?php

use Xmf\Request;

require \dirname(__DIR__, 3) . '/mainfile.php';

foreach ($_POST['groups'] as $gid) {
    $perm->addRight('view', $gid);
}
$n = count($_POST['ids']);
$id = (int)$_GET['id'];
$start = intval($_GET['start']);
$op = isset($_POST['op']) ? $_POST['op'] : 'list';
switch ($op) {
    case 'save':
        break;
}
$limit = $_GET['limit'] ?? 20;
$criteria->setLimit($limit);
if ($_GET['type'] === 'rss') {
    $x = 1;
}
echo 'Hello ' . $_GET['name'];
$title = $_POST['title'];
echo $title . '!';
$body = trim($_POST['body']);
$cats = $_POST['cats'];
$out = implode(',', $cats);
$x = $_POST['unknown'];
doSomething($x);
$obj->setVar('description', $_POST['description']);
$item = new Item();
$item->setVar('tags', $_POST['tags']);
$item->setVar('title2', $_POST['title2']);
echo "Hi {$_POST['who']}";
if (!$_POST['ids2']) {
    $e = 1;
}
$s = implode($_POST['parts']);
$m = max($_POST['n'], 10);
if (isset($_POST['a']['b'])) {
    $f = 1;
}
$_POST['c']['d'] = 1;
reset($_POST['r']);
$v0 = $_POST['v'][0];
$c = $_POST['cv'];
$packed = compact('c');
echo $c;
$off = array_slice($all, $_GET['offset']);
