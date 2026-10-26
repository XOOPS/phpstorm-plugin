<?php
// Payment callback: runs without the core, so no request helper class is loadable here.
$raw = $_POST;
$txn = $_POST['txn_id'];
if (isset($_GET['debug'])) {
    echo $txn;
}
