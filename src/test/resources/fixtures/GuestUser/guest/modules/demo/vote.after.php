<?php
require_once __DIR__ . '/header.php';

if (isset($xoopsUser) && is_object($xoopsUser)) {
    $groups = $xoopsUser->getGroups();
}
if ($xoopsUser instanceof \XoopsUser) {
    $uid = $xoopsUser->uid();
}
