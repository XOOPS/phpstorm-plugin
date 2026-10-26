<?php

class XoopsUser extends XoopsObject
{
    public function __construct()
    {
        $this->initVar('uname', XOBJ_DTYPE_TXTBOX, null, true, 25);
        $this->initVar('user_sig', XOBJ_DTYPE_OTHER, null, false);
    }
}
