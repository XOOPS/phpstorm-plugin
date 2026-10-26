<?php

$rows = [1, 2];
$xoopsTpl->assignByRef('rows', $rows);
$late = [];
$xoopsTpl->appendByRef('late', $late);
$late[] = 3;
$xoopsTpl->register_function('demo', 'demo_fn');
$xoopsTpl->template_dir = XOOPS_ROOT_PATH . '/modules/demo/templates';
$tpl = new \Smarty();
$xoopsTpl->assign('ok', $rows);
