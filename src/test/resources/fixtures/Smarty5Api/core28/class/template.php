<?php
defined('XOOPS_ROOT_PATH') || exit('Restricted access');

// Smarty 5 on core 2.8: both are gone.
class XoopsTpl extends Smarty
{
    public function setup($xoopsConfig)
    {
        $this->assignByRef('xoops', $xoopsConfig);
        $xoopsConfig['x'] = 1;
    }
}
