<?php
defined('XOOPS_ROOT_PATH') || exit('Restricted access');

// Smarty 4 on core 2.7: valid API.
class XoopsTpl extends Smarty
{
    public function setup($xoopsConfig)
    {
        $this->assignByRef('xoops', $xoopsConfig);
        $xoopsConfig['x'] = 1;
    }
}
