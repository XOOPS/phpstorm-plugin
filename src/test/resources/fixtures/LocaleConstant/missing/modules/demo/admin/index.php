<?php
require __DIR__ . '/admin_header.php';
echo _AM_DEMO_OK;
echo _AM_DEMO_MISSING;
echo _AM_SYSTEM_SOMETHING;
echo _MD_DEMO_ALSO_MISSING;
echo defined('_AM_DEMO_OPTIONAL') ? _AM_DEMO_OPTIONAL : 'fallback';
