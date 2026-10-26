<?php
$modversion['name']    = 'Demo';
$modversion['dirname'] = 'demo';
// The 1.x installer ran sql/upgrade.sql; it is kept for reference only.
/*
$modversion['sqlfile']['mysql'] = 'sql/upgrade.sql';
*/
$legacy = false; // $modversion['sqlfile']['mysql'] = 'sql/upgrade.sql';
/* schema */ $modversion['sqlfile']['mysql'] = /* english */ 'sql/english/install.sql';
