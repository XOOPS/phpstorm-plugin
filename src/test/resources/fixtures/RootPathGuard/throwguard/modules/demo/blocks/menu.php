<?php
if (!defined('XOOPS_ROOT_PATH') || !($GLOBALS['xoopsUser'] instanceof \XoopsUser)) {
    exit('Restricted access');
}

function demo_block_menu_show(): array
{
    return [];
}
