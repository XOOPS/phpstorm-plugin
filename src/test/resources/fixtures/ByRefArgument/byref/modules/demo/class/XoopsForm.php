<?php

class XoopsForm
{
    public function __construct($title, $name, $action, $method = 'post', $addtoken = true, $summary = '')
    {
    }
}

function demo_takes_ref(&$a)
{
    $a = 1;
}
