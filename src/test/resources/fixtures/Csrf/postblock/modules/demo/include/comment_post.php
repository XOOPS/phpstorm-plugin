<?php
// Shape of include/comment_post.php: the gate sits in a POST-only block whose else exits.
use Xmf\Request;

$op = '';
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    if (Request::hasVar('com_dopost', 'POST')) {
        $op = 'post';
    } elseif (Request::hasVar('com_dopreview', 'POST')) {
        $op = 'preview';
    }
    if (Request::hasVar('com_dodelete', 'POST')) {
        $op = 'delete';
    }
    if ($op === 'preview' || $op === 'post') {
        if (!$GLOBALS['xoopsSecurity']->check()) {
            $op = '';
        }
    }
} else {
    exit();
}

switch ($op) {
    case 'post':
        $comment_handler->insert($comment);
        break;
    case 'delete':
        $comment_handler->delete($comment);
        break;
}
