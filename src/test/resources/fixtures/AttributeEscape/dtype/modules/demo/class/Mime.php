<?php

class Mime extends XoopsObject
{
    public function __construct()
    {
        $this->initVar('mime_id', XOBJ_DTYPE_INT, null, false);
        $this->initVar('mime_ext', XOBJ_DTYPE_TXTBOX, null, true, 60);
        $this->initVar('mime_raw', XOBJ_DTYPE_OTHER, null, false);
    }
}
