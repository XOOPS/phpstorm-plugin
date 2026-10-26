<?php

$xoopsOption['template_main'] = 'demo_index.tpl';

function demo_show_item($id)
{
    $GLOBALS['xoopsOption']['template_main'] = 'demo_item.tpl';
    return $GLOBALS['xoopsOption']['template_main'];
}

function demo_with_global()
{
    global $xoopsOption;
    $xoopsOption['template_main'] = 'demo_list.tpl';
}

function demo_with_param(array $xoopsOption)
{
    return $xoopsOption['template_main'];
}
