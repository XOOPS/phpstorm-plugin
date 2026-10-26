<?php
require \dirname(__DIR__, 3) . '/mainfile.php';
$helper = \XoopsModules\Demo\Helper::getInstance();
$helper->loadLanguage('main');
echo _MD_DEMO_THANKS;
