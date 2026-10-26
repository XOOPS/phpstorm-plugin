<?php

class CategoryForm extends XoopsForm
{
    public function __construct(&$target)
    {
        parent::__construct($target->getVar('title'), 'form', \xoops_getenv('SCRIPT_NAME'));
        demo_takes_ref(strtolower('x'));
    }
}
