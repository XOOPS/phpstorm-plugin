<?php

class MimeUtil
{
    public static function row(): string
    {
        $m = new Mime();
        $out = "<a href='mimetypes.php?op=edit&amp;id=" . $m->getVar('mime_id') . "'>edit</a>";
        $out .= "<input type='text' value='" . $m->getVar('mime_ext') . "'>";
        $out .= "<input type='text' value='" . $m->getVar('mime_raw') . "'>";
        $out .= "<input type='text' value='" . $m->getVar('mime_ext', 'n') . "'>";
        $out .= "<a href='x.php?ext=" . $m->getVar('mime_ext') . "'>ext</a>";
        $sql = "UPDATE t SET mime_ext='" . $m->getVar('mime_ext', 'n') . "', title='" . $m->getVar('mime_raw') . "'";

        return $out . $sql;
    }
}
