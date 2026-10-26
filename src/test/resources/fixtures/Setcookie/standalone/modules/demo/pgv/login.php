<?php
// Bundled application page: never loads the core, so the site cookie helper does not exist here.
if (isset($_POST['remember'])) {
    setcookie('pgv_rem', $_POST['username'], time() + 60 * 60 * 24 * 7);
}
