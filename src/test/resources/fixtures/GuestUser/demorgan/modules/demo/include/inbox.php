<?php
defined('XOOPS_ROOT_PATH') || exit('Restricted access');

function demo_inbox_count()
{
    global $xoopsUser;
    // The core's form (function.xoInboxCount.php): safe, no finding.
    if (!isset($xoopsUser) || !is_object($xoopsUser)) {
        return null;
    }
    // Mixed polarity: a guest ('') passes the isset half of the ||, reported.
    if (isset($xoopsUser) || !is_object($xoopsUser)) {
        return 0;
    }

    return 1;
}
