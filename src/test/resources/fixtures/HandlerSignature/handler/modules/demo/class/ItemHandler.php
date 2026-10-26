<?php

class ItemHandler extends XoopsPersistableObjectHandler
{
    public function getObjects(?CriteriaElement $criteria = null, $id_as_key = false, $as_object = true)
    {
        $ret = [];
        return $ret;
    }

    public function &getAll(?CriteriaElement $criteria = null, $fields = null, $asObject = true, $id_as_key = true)
    {
        $ret = [];
        return $ret;
    }
}

class PlainHandler extends XoopsObjectHandler
{
    public function getAll()
    {
        return [];
    }
}
