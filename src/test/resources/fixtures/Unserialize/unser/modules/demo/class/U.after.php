<?php

class U
{
    public function defaults($field, string $x, array $row): array
    {
        $out = [];
        $a = unserialize($field->getVar('field_default', 'n'), ['allowed_classes' => false]);
        foreach ($a as $k => $v) {
            $out[$k] = $v;
        }
        $b = unserialize($x, ['allowed_classes' => false]);
        $search = unserialize($row['search']);
        $search->add(new \Criteria('x', 1));
        $ret['search'] = unserialize($row['search']);
        if (is_array(unserialize($x, ['allowed_classes' => false]))) {
            $out[] = count(unserialize($x, ['allowed_classes' => false]));
        }

        return [$out, $b, $search, $ret];
    }
}
