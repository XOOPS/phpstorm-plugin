<?php

use Xmf\Request;

require \dirname(__DIR__, 3) . '/mainfile.php';

foreach (Request::getArray('groups', [], 'POST') as $gid) {
    $perm->addRight('view', $gid);
}
$n = count(Request::getArray('ids', [], 'POST'));
$id = Request::getInt('id', 0, 'GET');
$start = Request::getInt('start', 0, 'GET');
$op = Request::getString('op', 'list', 'POST');
switch ($op) {
    case 'save':
        break;
}
$limit = Request::getVar('limit', null, 'GET', 'none', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM) ?? 20;
$criteria->setLimit($limit);
if (Request::getString('type', '', 'GET') === 'rss') {
    $x = 1;
}
echo 'Hello ' . Request::getText('name', '', 'GET');
$title = Request::getText('title', '', 'POST');
echo $title . '!';
$body = trim(Request::getText('body', '', 'POST'));
$cats = Request::getArray('cats', [], 'POST');
$out = implode(',', $cats);
$x = Request::getVar('unknown', null, 'POST', 'none', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM);
doSomething($x);
$obj->setVar('description', Request::getVar('description', null, 'POST', 'none', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM));
$item = new Item();
$item->setVar('tags', Request::getArray('tags', [], 'POST'));
$item->setVar('title2', Request::getText('title2', '', 'POST'));
echo "Hi {$_POST['who']}";
if (!Request::getVar('ids2', null, 'POST', 'none', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM)) {
    $e = 1;
}
$s = implode(Request::getArray('parts', [], 'POST'));
$m = max(Request::getVar('n', null, 'POST', 'none', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM), 10);
if (isset($_POST['a']['b'])) {
    $f = 1;
}
$_POST['c']['d'] = 1;
reset($_POST['r']);
$v0 = Request::getVar('v', null, 'POST', 'none', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM)[0];
$c = Request::getVar('cv', null, 'POST', 'none', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM);
$packed = compact('c');
echo $c;
$off = array_slice($all, Request::getVar('offset', null, 'GET', 'none', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM));
