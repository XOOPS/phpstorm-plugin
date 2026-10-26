<?php
// Shape of system/admin/menus/main.php: the token check lives in a helper of the same file.
use Xmf\Request;

function demo_require_token(bool $isAjax): void
{
    if (!$GLOBALS['xoopsSecurity']->check()) {
        $message = 'Security token mismatch';
        if ($isAjax) {
            echo $message;
        }
        redirect_header('menus.php', 3, $message);
    }
}

function demo_log(string $text): void
{
    echo $text;
}

$op = Request::getString('op', 'list');
switch ($op) {
    case 'savecat':
        demo_require_token(false);
        $handler->insert($cat);
        break;
    case 'delcat':
        demo_log('deleting');
        $handler->delete($cat);
        break;
}
