<?php

class DemoFieldHandler
{
    public $db;

    // Shape of profile/class/field.php: the non-forced write goes through query() on purpose.
    public function alter($table, $name, $force = false)
    {
        $sql    = 'ALTER TABLE `' . $table . '` ADD `' . $name . '` TEXT NULL';
        $result = $force ? $this->db->exec($sql) : $this->db->query($sql);

        return $result;
    }

    public function drop($table, $name)
    {
        $sql = 'ALTER TABLE `' . $table . '` DROP `' . $name . '`';

        return $this->db->query($sql);
    }
}
