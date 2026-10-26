<?php
if (!defined('XOOPS_ROOT_PATH')) {
    http_response_code(404);
    exit;
}

function demo_share_icons(): array
{
    return ['facebook', 'x'];
}
