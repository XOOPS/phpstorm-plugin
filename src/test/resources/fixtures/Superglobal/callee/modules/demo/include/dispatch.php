<?php
defined('XOOPS_ROOT_PATH') || exit('Restricted access');

/** @var \XoopsModule $xoopsModule */

$mod = new $_REQUEST['mod']();
$_GET['callback']($mod);
$mod->$_POST['method']();
echo $_GET['label'];
