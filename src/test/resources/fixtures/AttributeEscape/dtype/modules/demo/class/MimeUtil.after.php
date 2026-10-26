<?php

class MimeUtil
{
    public static function row(): string
    {
        $m = new Mime();
        $out = "<a href='mimetypes.php?op=edit&amp;id=" . $m->getVar('mime_id') . "'>edit</a>";
        $out .= "<input type='text' value='" . $m->getVar('mime_ext') . "'>";
        $out .= "<input type='text' value='" . htmlspecialchars((string) $m->getVar('mime_raw'), ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8') . "'>";
        $out .= "<input type='text' value='" . htmlspecialchars((string) $m->getVar('mime_ext', 'n'), ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8') . "'>";
        $out .= "<a href='x.php?ext=" . rawurlencode((string) $m->getVar('mime_ext', 'n')) . "'>ext</a>";
        $sql = "UPDATE t SET mime_ext='" . $m->getVar('mime_ext', 'n') . "', title='" . $m->getVar('mime_raw') . "'";

        return $out . $sql;
    }
}
