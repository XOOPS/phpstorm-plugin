<?php declare(strict_types=1);

/*
 * You may not change or alter any portion of this comment or credits
 * of supporting developers from this source code or any supporting source code
 * which is considered copyrighted (c) material of the original comment or credit authors.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

/**
 * @copyright    2000-2026 XOOPS Project (https://xoops.org)
 * @license      GNU GPL 2.0 or later (https://www.gnu.org/licenses/gpl-2.0.html)
 * @author       Nazar Aziz (www.panthersoftware.com)
 * @author       XOOPS Development Team
 */

use XoopsModules\Xasset;

define('XOOPS_XMLRPC', 1);
require_once __DIR__ . '/servicemain.php';

if (count($_POST) > 0) {
    $hGateway = new Xasset\GatewayHandler($GLOBALS['xoopsDB']);
    $hOrder   = new Xasset\OrderHandler($GLOBALS['xoopsDB']);
    $hLog     = new Xasset\GatewayLogHandler($GLOBALS['xoopsDB']);
    //determine the gateway and order id from $_POST
    $orderID = $hGateway->getGatewayFromPost($_POST, $gateway);
    if ($orderID) {
        $order = $hOrder->get($orderID);
        //check if this order is open;
        if ($order->getVar('status') == $order->orderStatusGateway()) {
            //log post
            $hLog->addLog($order->getVar('id'), $order->getVar('gateway'), $order->orderStatusValidate(), $_POST);

            if (isset($gateway)) {
                if ($gateway->validates()) {
                    if ($gateway->validateTransaction($order->getVar('id'), $_POST)) {
                        $hLog->addLog($order->getVar('id'), $order->getVar('gateway'), $order->orderStatusValidate(), 'validated');

                        $order->setVar('status', $order->orderStatusValidate());
                        $hOrder->insert($order);
                        //now process and update order and ticket
                        if ($gateway->processReturn($order, $_POST)) {
                            $hLog->addLog($order->getVar('id'), $order->getVar('gateway'), $order->orderStatusValidate(), 'processed');
                            $order->setVar('status', $order->orderStatusComplete());
                            if ($hOrder->insert($order)) {
                                $order->processPurchase();
                            } else {
                                $post = print_r($hOrder, true);
                                $hLog->addLog($order->getVar('id'), $order->getVar('gateway'), $order->orderStatusValidate(), "Failed to save order: $post");
                            }
                        } else {
                            $post = print_r($_POST, true);
                            $hLog->addLog($order->getVar('id'), $order->getVar('gateway'), $order->orderStatusValidate(), "Did not process return from post: $post");
                        }
                    } else {
                        $post = print_r($_POST, true);
                        $hLog->addLog($order->getVar('id'), $order->getVar('gateway'), $order->orderStatusValidate(), "Did not validate from post: $post");
                    }
                }
            } else {
                $hLog->addLog($order->getVar('id'), $order->getVar('gateway'), $order->orderStatusValidate(), 'Gateway does not validate');
            }
        } else {
            $hLog->addLog($order->getVar('id'), $order->getVar('gateway'), $order->orderStatusValidate(), 'Gateway not found');
        }
    }
}
