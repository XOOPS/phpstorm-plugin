<?php
// One header per request: each branch renders once.
use Xmf\Request;

if (Request::hasVar('id')) {
    xoops_cp_header();
    echo 'edit';
} else {
    xoops_cp_header();
    echo 'list';
}
xoops_cp_footer();
