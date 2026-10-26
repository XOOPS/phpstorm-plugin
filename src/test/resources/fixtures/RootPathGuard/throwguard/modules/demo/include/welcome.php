<?php
if (!defined('XOOPS_ROOT_PATH') || !is_object($forumObject) || !is_object($GLOBALS['xoopsUser'])) {
    return;
}

function demo_welcome(): string
{
    return 'hello';
}
