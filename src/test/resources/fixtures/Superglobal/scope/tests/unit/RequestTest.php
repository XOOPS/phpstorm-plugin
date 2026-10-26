<?php
// PHPUnit for a XOOPS_ROOT_PATH page: saves and restores the request state.

final class RequestTest
{
    private $saved;

    public function setUp(): void
    {
        $this->saved = $_POST;
        $_POST['op'] = 'save';
        $id = $_GET['id'];
    }

    public function tearDown(): void
    {
        $_POST = $this->saved;
    }
}
