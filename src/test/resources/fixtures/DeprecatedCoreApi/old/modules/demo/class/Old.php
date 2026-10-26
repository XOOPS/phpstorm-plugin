<?php

class Old extends XoopsObject
{
    public function __construct()
    {
        parent::XoopsObject();
        $this->initVar('title', XOBJ_DTYPE_UNICODE_TXTBOX, null, true, 255);
        $this->destoryVars('junk');
    }

    public function render($tpl)
    {
        $tree = new XoopsTree('t', 'id', 'pid');
        $tpl->assign_by_ref('tree', $tree);
    }
}
