<?php

class DemoCorePreload extends XoopsPreloadItem
{
    public static function eventCoreHeaderAddmeta($args)
    {
        global $xoopsModule, $xoopsModuleConfig;
        if ($xoopsModule->getVar('dirname') !== 'demo') {
            $xoopsModule = xoops_getHandler('module')->getByDirname('demo');
            $GLOBALS['xoopsModuleConfig'] = xoops_getHandler('config')->getConfigsByCat(0, $xoopsModule->getVar('mid'));
        }
        $cfg = xoops_getHandler('config')->getConfigsByCat(0, 3);
    }
}
