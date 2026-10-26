<?php
defined('XOOPS_ROOT_PATH') || exit('Restricted access');

class DemoCorePreload extends XoopsPreloadItem
{
    // Shape of system/preloads/core.php: a local copy, the global is untouched.
    public static function eventCoreIncludeCommonEnd($args)
    {
        $xoopsModule = $GLOBALS['xoopsModule'] ?? null;
        $mid         = is_object($xoopsModule) ? $xoopsModule->getVar('mid') : 0;
    }
}
