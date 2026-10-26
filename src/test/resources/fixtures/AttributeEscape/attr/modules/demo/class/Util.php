<?php

namespace XoopsModules\Demo;

class Util
{
    public static function render($obj, array $images, $img): string
    {
        $html = '<a href="' . $obj->getVar('url') . '">' . $obj->getVar('title') . '</a>';
        $html .= "<input value='" . $images['main']->getVar('image_name') . "'>";
        $html .= '<img src="' . XOOPS_URL . '/thumb.php?src=' . $img->getVar('image_name') . '&w=100">';
        $html .= '<a href="' . htmlspecialchars((string) $obj->getVar('url'), ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8') . '">x</a>';
        $title = constant('CO_DEMO_FB') . $GLOBALS['xoopsModule']->getVar('dirname');
        $value = ['lat' => $obj->getVar('lat'), 'title' => $title];

        return $html . $value['title'];
    }
}
