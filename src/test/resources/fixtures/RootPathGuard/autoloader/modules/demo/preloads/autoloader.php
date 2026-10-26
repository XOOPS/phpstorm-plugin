<?php

spl_autoload_register(static function (string $class): void {
    require __DIR__ . '/../class/' . $class . '.php';
});
