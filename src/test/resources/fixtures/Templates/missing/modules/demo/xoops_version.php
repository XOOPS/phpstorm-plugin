<?php
$modversion['templates'][] = ['file' => 'demo_index.tpl', 'description' => ''];
$modversion['templates'][] = ['file' => 'partial_pagertop.tpl', 'type' => 'partial', 'description' => ''];
$modversion['templates'][] = ['file' => 'demo_admin_about.tpl', 'type' => 'admin', 'description' => ''];
$modversion['templates'][] = ['file' => 'demo_block_list.tpl', 'type' => 'block', 'description' => ''];
$modversion['templates'][] = ['file' => 'blocks/demo_block_ok.tpl', 'description' => ''];
$modversion['blocks'][] = ['file' => 'blocks.php', 'show_func' => 'b_demo', 'template' => 'blocks/demo_block_b.tpl'];
$modversion['blocks'][] = ['file' => 'blocks.php', 'show_func' => 'b_demo2', 'template' => 'demo_block_elsewhere.tpl'];
