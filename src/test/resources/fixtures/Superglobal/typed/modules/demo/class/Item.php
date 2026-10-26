<?php

class Item extends XoopsObject
{
    public function __construct()
    {
        $this->initVar('tags', XOBJ_DTYPE_ARRAY, null, false);
        $this->initVar('title2', XOBJ_DTYPE_TXTBOX, null, true, 255);
    }
}
