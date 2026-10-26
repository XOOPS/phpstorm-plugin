<?php
// findusers.php: the receiver is an untyped array element; the key is declared once under kernel/.
foreach ($foundusers as $j => $u) {
    echo "<input type='hidden' value='" . $foundusers[$j]->getVar('uname') . "' />";
    echo "<input type='hidden' value='" . $foundusers[$j]->getVar('user_sig') . "' />";
    echo "<input type='hidden' value='" . $foundusers[$j]->getVar('nowhere') . "' />";
}
