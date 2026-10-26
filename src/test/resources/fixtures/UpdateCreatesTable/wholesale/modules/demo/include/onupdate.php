<?php

function xoops_module_update_demo($module, $prev)
{
    return \XoopsModules\Demo\Common\Install::createMissingTables($module);
}
