<?php

class Old extends XoopsObject
{
    public function __construct()
    {
        parent::__construct();
        $this->initVar('title', XOBJ_DTYPE_UNICODE_TXTBOX, null, true, 255);
        $this->destroyVars('junk');
    }

    public function render($tpl)
    {
        $tree = new XoopsTree('t', 'id', 'pid');
        $tpl->assign('tree', $tree);
    }
}
