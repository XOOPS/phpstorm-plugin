<?php
use Xmf\Request;

require_once __DIR__ . '/admin_header.php';

$op = Request::getString('op', 'default', 'GET');
if ('POST' === Request::getMethod() && !$GLOBALS['xoopsSecurity']->check()) {
    redirect_header('mimetypes.php', 3, implode('<br>', $GLOBALS['xoopsSecurity']->getErrors()));
}

switch ($op) {
    case 'delete':
        $mimetypeHandler->delete($mimeType, true);
        break;
    case 'clone':
        $mimeType->setVar('mime_id', 0);
        break;
    case 'save':
        if (!$GLOBALS['xoopsSecurity']->check()) {
            redirect_header('mimetypes.php', 3, 'token');
        }
        $mimetypeHandler->insert($mimeType);
        break;
    default:
        break;
}
