<?php

class H
{
    public function run($db, string $arg): void
    {
        $sql = 'DELETE FROM ' . $db->prefix('demo') . ' WHERE id = 1';
        $db->query($sql);
        $db->query('SELECT 1');
        $db->query($arg);
    }
}
