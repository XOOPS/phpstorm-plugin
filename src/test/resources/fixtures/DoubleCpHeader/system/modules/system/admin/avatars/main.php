<?php
// Shape of system/admin/avatars/main.php: no admin_header.php, one header on top, more in branches.
use Xmf\Request;

$op = Request::getString('op', 'list');
$GLOBALS['xoopsOption']['template_main'] = 'system_avatars.tpl';
xoops_cp_header();

switch ($op) {
    case 'save':
        if (count($err) > 0) {
            $GLOBALS['xoopsOption']['template_main'] = 'system_header.tpl';
            xoops_cp_header();
            echo implode('<br>', $err);
            exit();
        }
        break;
    case 'show':
        xoops_cp_header();
        echo 'shown';
        break;
}
xoops_cp_footer();
