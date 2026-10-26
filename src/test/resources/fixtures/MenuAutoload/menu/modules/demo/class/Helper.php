<?php

namespace XoopsModules\Demo;

class Helper
{
    public static function getInstance(): self
    {
        return new self();
    }
}
