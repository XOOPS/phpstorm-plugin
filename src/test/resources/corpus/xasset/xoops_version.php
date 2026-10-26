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

//include "header.php";
//require_once XOOPS_ROOT_PATH."/header.php";
//require_once __DIR__ . '/include/info.php';

use XoopsModules\Xasset\Helper;

require_once __DIR__ . '/preloads/autoloader.php';

$moduleDirName      = basename(__DIR__);
$moduleDirNameUpper = \mb_strtoupper($moduleDirName);

$modversion['version']             = '1.0.0';
$modversion['module_status']       = 'Alpha 2';
$modversion['release_date']        = '2022/12/15';
$modversion['name']                = _MI_XASSET_NAME;
$modversion['description']         = _MI_XASSET_DESC;
$modversion['credits']             = 'PSP, XOOPS Development Team';
$modversion['author']              = 'Nazar Aziz - nazar@panthersoftware.com';
$modversion['help']                = 'page=help';
$modversion['license']             = 'GNU GPL 2.0 or later';
$modversion['license_url']         = 'www.gnu.org/licenses/gpl-2.0.html';
$modversion['official']            = 0; //1 indicates supported by XOOPS Dev Team, 0 means 3rd party supported
$modversion['image']               = 'assets/images/xasset_slogo.png';
$modversion['dirname']             = basename(__DIR__);
$modversion['modicons16']          = 'assets/images/icons/16';
$modversion['modicons32']          = 'assets/images/icons/32';
$modversion['release_file']        = XOOPS_URL . '/modules/' . $modversion['dirname'] . '/docs/changelog.txt';
$modversion['module_website_url']  = 'www.xoops.org';
$modversion['module_website_name'] = 'XOOPS';
$modversion['min_php']             = '7.4';
$modversion['min_xoops']           = '2.5.10';
$modversion['min_admin']           = '1.2';
$modversion['min_db']              = ['mysql' => '5.5'];

// Admin things
$modversion['hasAdmin']    = 1;
$modversion['system_menu'] = 1;
$modversion['adminindex']  = 'admin/index.php';
$modversion['adminmenu']   = 'admin/menu.php';

// ------------------- Mysql ------------------- //
$modversion['sqlfile']['mysql'] = 'sql/mysql.sql';

// Tables created by sql file (without prefix!)
$modversion['tables'] = [
    $moduleDirName . '_' . 'app_prod_memb',
    $moduleDirName . '_' . 'app_product',
    $moduleDirName . '_' . 'application',
    $moduleDirName . '_' . 'application_groups',
    $moduleDirName . '_' . 'config',
    $moduleDirName . '_' . 'country',
    $moduleDirName . '_' . 'currency',
    $moduleDirName . '_' . 'gateway',
    $moduleDirName . '_' . 'gateway_detail',
    $moduleDirName . '_' . 'gateway_log',
    $moduleDirName . '_' . 'license',
    $moduleDirName . '_' . 'links',
    $moduleDirName . '_' . 'order_detail',
    $moduleDirName . '_' . 'order_index',
    $moduleDirName . '_' . 'package',
    $moduleDirName . '_' . 'packagegroup',
    $moduleDirName . '_' . 'region',
    $moduleDirName . '_' . 'tax_class',
    $moduleDirName . '_' . 'tax_rates',
    $moduleDirName . '_' . 'tax_zone',
    $moduleDirName . '_' . 'user_details',
    $moduleDirName . '_' . 'user_products',
    $moduleDirName . '_' . 'userpackagestats',
    $moduleDirName . '_' . 'zone',
];

// Menu
$modversion['hasMain'] = 1;

// ------------------- Help files ------------------- //
$modversion['helpsection'] = [
    ['name' => _MI_XASSET_OVERVIEW, 'link' => 'page=help'],
    ['name' => _MI_XASSET_DISCLAIMER, 'link' => 'page=disclaimer'],
    ['name' => _MI_XASSET_LICENSE, 'link' => 'page=license'],
    ['name' => _MI_XASSET_SUPPORT, 'link' => 'page=support'],
];

global $xoopsUser;

$helper = Helper::getInstance();
$module = $helper->getModule();

if ($module) {
    $hApps        = $helper->getHandler('Application'); // xoops_getModuleHandler('application', 'xasset');
    $hUserDetails = $helper->getHandler('UserDetails'); //new Xasset\UserDetailsHandler($GLOBALS['xoopsDB']);
    $hOrder       = $helper->getHandler('Order'); //new Xasset\OrderHandler($GLOBALS['xoopsDB']);
    $hMembers     = $helper->getHandler('ApplicationProductMemb'); //new Xasset\ApplicationProductMembHandler($GLOBALS['xoopsDB']);

    $oApps = $hApps->getApplicationMainMenuObjects();
    $i     = 1;
    foreach ($oApps as $key => $oApp) {
        $modversion['sub'][$i]['name'] = $oApp->getVar('menuItem');
        $modversion['sub'][$i]['url']  = 'index.php?op=product&id=' . $oApp->getVar('id') . '&key=' . $hApps->cryptID($oApp->getVar('id'));

        ++$i;
    }

    if ($xoopsUser) {
        $oUserDetails = $hUserDetails->getUserDetailByID($xoopsUser->uid());
        if ($oUserDetails) {
            $aDownloads = $oUserDetails->getUserDownloads();
            if (count($aDownloads) > 0) {
                //show only if we have downloads
                $modversion['sub'][$i + 1]['name'] = _MI_XASSET_SUBMENU_MY_DOWNLOADS;
                $modversion['sub'][$i + 1]['url']  = 'index.php?op=showMyDownloads';
            }
        }
        //show only if we have something in our cart
        if ($hOrder->userInCartOrders($xoopsUser->uid()) > 0) {
            $modversion['sub'][$i + 2]['name'] = _MI_XASSET_SUBMENU_MY_CART;
            $modversion['sub'][$i + 2]['url']  = 'order.php?op=showCart';
        }
        $modversion['sub'][$i + 3]['name'] = _MI_XASSET_SUBMENU_MY_DETAILS;
        $modversion['sub'][$i + 3]['url']  = 'order.php?op=showUserDetails';
        //now show my subscriptions
        if ($hMembers->getSubscriberCountByUser($xoopsUser)) {
            $modversion['sub'][$i + 4]['name'] = _MI_XASSET_SUBMENU_MY_SUBS;
            $modversion['sub'][$i + 4]['url']  = 'index.php?op=showUserSubs';
        }
    } else {
        $modversion['sub'][$i + 3]['name'] = _MI_XASSET_SUBMENU_MY_DETAILS;
        $modversion['sub'][$i + 3]['url']  = 'order.php?op=askUserDetails';
    }
}
// Smarty
$modversion['use_smarty'] = 1;

// Search
$modversion['hasSearch']      = 1;
$modversion['search']['file'] = 'include/search.php';
$modversion['search']['func'] = 'xasset_search';

// ------------------- Templates ------------------- //

$modversion['templates'] = [
    ['file' => 'xasset_admin_license_add.tpl', 'description' => _MI_XASSET_VERSION_ADMIN_ADD_LICENSE_BLOCK],
    ['file' => 'xasset_admin_packagegroup_add.tpl', 'description' => _MI_XASSET_VERSION_ADMIN_ADD_PACKAGE_GROUP],
    ['file' => 'xasset_admin_package_add.tpl', 'description' => _MI_XASSET_VERSION_ADMIN_ADD_PACKAGE_BLOCK],
    ['file' => 'xasset_admin_links_add.tpl', 'description' => _MI_XASSET_VERSION_ADMIN_ADD_LINKS],
    ['file' => 'xasset_admin_application_add.tpl', 'description' => _MI_XASSET_VERSION_ADMIN_ADDEDIT_APPLICATION],
    ['file' => 'xasset_admin_user_add.tpl', 'description' => _MI_XASSET_VERSION_ADMIN_ADDEDIT_USER],
    ['file' => 'xasset_admin_country_add.tpl', 'description' => _MI_XASSET_VERSION_COUNTRY_ADD],
    ['file' => 'xasset_admin_zone_add.tpl', 'description' => _MI_XASSET_VERSION_ZONE_ADD],
    ['file' => 'xasset_admin_tax_rate_add.tpl', 'description' => _MI_XASSET_VERSION_TAX_RATE_ADD],
    ['file' => 'xasset_admin_tax_class_add.tpl', 'description' => _MI_XASSET_VERSION_TAX_CLASS_ADD],
    ['file' => 'xasset_admin_application_product_add.tpl', 'description' => _MI_XASSET_VERSION_APPLICATION_PRODUCT_ADD],
    ['file' => 'xasset_admin_currency_add.tpl', 'description' => _MI_XASSET_VERSION_CURRENCY_ADD],
    ['file' => 'xasset_admin_region_add.tpl', 'description' => _MI_XASSET_VERSION_REGION_ADD],
    ['file' => 'xasset_admin_index.tpl', 'description' => _MI_XASSET_VERSION_ADMIN_INDEX],
    ['file' => 'xasset_admin_application_index.tpl', 'description' => _MI_XASSET_VERSION_ADMIN_APP_MAINTENANCE],
    ['file' => 'xasset_admin_license_index.tpl', 'description' => _MI_XASSET_VERSION_ADMIN_LICENCE_MAINTENANCE],
    ['file' => 'xasset_admin_package_index.tpl', 'description' => _MI_XASSET_VERSION_ADMIN_PACKAGE_MAINTENANCE],
    ['file' => 'xasset_admin_license_application.tpl', 'description' => _MI_XASSET_VERSION_ADMIN_LICENSES_BY_APPLICATION],
    ['file' => 'xasset_admin_license_client.tpl', 'description' => _MI_XASSET_VERSION_ADMIN_LICENSES_BY_CLIENTS],
    ['file' => 'xasset_admin_packages_index.tpl', 'description' => _MI_XASSET_VERSION_ADMIN_GROUPS_PACKAGES],
    ['file' => 'xasset_admin_links_index.tpl', 'description' => _MI_XASSET_VERSION_ADMIN_APPLICATION_LINKS],
    ['file' => 'xasset_admin_links_edit.tpl', 'description' => _MI_XASSET_VERSION_ADMIN_EDIT_LINKS],
    ['file' => 'xasset_admin_user_index.tpl', 'description' => _MI_XASSET_VERSION_ADMIN_USERS_INDEX],
    ['file' => 'xasset_admin_packagegroup_edit.tpl', 'description' => _MI_XASSET_VERSION_ADMIN_EDIT_PACKAGE_GROUP],
    ['file' => 'xasset_admin_package_edit.tpl', 'description' => _MI_XASSET_VERSION_ADMIN_PACKAGE_EDIT],
    ['file' => 'xasset_admin_download_stats_index.tpl', 'description' => _MI_XASSET_VERSION_ADMIN_DOWNLOAD_STATS_INDEX],
    ['file' => 'xasset_admin_country_index.tpl', 'description' => _MI_XASSET_VERSION_COUNTRY_INDEX],
    ['file' => 'xasset_admin_region_index.tpl', 'description' => _MI_XASSET_VERSION_REGION_INDEX],
    ['file' => 'xasset_admin_currency_index.tpl', 'description' => _MI_XASSET_VERSION_CURRENCY_INDEX],
    ['file' => 'xasset_admin_zone_index.tpl', 'description' => _MI_XASSET_VERSION_ZONE_INDEX],
    ['file' => 'xasset_admin_taxrates_index.tpl', 'description' => _MI_XASSET_VERSION_TAXRATES_INDEX],
    ['file' => 'xasset_admin_tax_region_zone.tpl', 'description' => _MI_XASSET_VERSION_REGIONZONE_INDEX],
    ['file' => 'xasset_admin_gateway_index.tpl', 'description' => _MI_XASSET_VERSION_GATEWAY_INDEX],
    ['file' => 'xasset_admin_config.tpl', 'description' => _MI_XASSET_VERSION_CONFIG],
    ['file' => 'xasset_admin_gateway_log_index.tpl', 'description' => _MI_XASSET_VERSION_GATEWAY_LOGS],
    ['file' => 'xasset_admin_gateway_log_detail.tpl', 'description' => _MI_XASSET_VERSION_GATEWAY_DET],
    ['file' => 'xasset_admin_order_tracking.tpl', 'description' => _MI_XASSET_VERSION_ORDER_TRACKING],
    ['file' => 'xasset_admin_support.tpl', 'description' => _MI_XASSET_VERSION_SUPPORT],
    ['file' => 'xasset_admin_order_details.tpl', 'description' => _MI_XASSET_VERSION_ORDER_DETAILS],
    ['file' => 'xasset_admin_membership_index.tpl', 'description' => _MI_XASSET_VERSION_ADMIN_MEMBERSHIP],
    ['file' => 'xasset_admin_header.tpl', 'description' => ''],
    ['file' => 'xasset_admin_footer.tpl', 'description' => ''],




    ['file' => 'xasset_order_user_details_add.tpl', 'description' => _MI_XASSET_VERSION_ORDER_USER_DETAILS_ADD],
    ['file' => 'xasset_index.tpl', 'description' => _MI_XASSET_VERSION_INDEX_PAGE],
    ['file' => 'xasset_license_index.tpl', 'description' => _MI_XASSET_VERSION_LICENSE_INDEX],
    ['file' => 'xasset_error.tpl', 'description' => _MI_XASSET_VERSION_ERROR_PAGE],
    ['file' => 'xasset_package_index.tpl', 'description' => _MI_XASSET_VERSION_PACKAGE_INDEX],
    ['file' => 'xasset_evaluation_index.tpl', 'description' => _MI_XASSET_VERSION_EVALUATION_INDEX],
    ['file' => 'xasset_order_index.tpl', 'description' => _MI_XASSET_VERSION_ORDER_INDEX],
    ['file' => 'xasset_order_user_details.tpl', 'description' => _MI_XASSET_VERSION_ORDER_USER_DETAILS],
    ['file' => 'xasset_order_cart.tpl', 'description' => _MI_XASSET_VERSION_ORDER_USER_DETAILS_ADD],
    ['file' => 'xasset_order_checkout.tpl', 'description' => _MI_XASSET_VERSION_ORDER_CHECKOUT],
    ['file' => 'xasset_product.tpl', 'description' => _MI_XASSET_VERSION_PRODUCT],
    ['file' => 'xasset_downloads.tpl', 'description' => _MI_XASSET_VERSION_DOWNLOADS],
    ['file' => 'xasset_order_extra.tpl', 'description' => _MI_XASSET_VERSION_OEXTRA],
    ['file' => 'xasset_my_subscriptions.tpl', 'description' => _MI_XASSET_VERSION_SUBS],
    ['file' => 'xasset_player_code.tpl', 'description' => _MI_XASSET_VERSION_PLAYER],
    ['file' => 'xasset_video_index.tpl', 'description' => _MI_XASSET_VERSION_VIDEO],
    //block templates
    ['file' => 'xasset_block_download_option.tpl', 'description' => _MI_XASSET_BLOCK_DOWNOPT],
    ['file' => 'xasset_block_pics_option.tpl', 'description' => _MI_XASSET_BLOCK_PICTOPT],
];

// ------------------- Blocks ------------------- //
$modversion['blocks'][] = [
    'file'        => 'xasset_blocks.php',
    'name'        => _MI_XASSET_CURRENCY,
    'description' => _MI_XASSET_CURRENCYD,
    'show_func'   => 'b_xasset_currencies',
    'template'    => 'xasset_currencies.tpl',
];

$modversion['blocks'][] = [
    'file'        => 'xasset_blocks.php',
    'name'        => _MI_XASSET_TOP,
    'description' => _MI_XASSET_TOPD,
    'show_func'   => 'b_xasset_downloads',
    'edit_func'   => 'b_xasset_downloads_opt',
    'template'    => 'xasset_top_download.tpl',
];

$modversion['blocks'][] = [
    'file'        => 'xasset_blocks.php',
    'name'        => _MI_XASSET_APP_PICS,
    'description' => _MI_XASSET_APP_PICSD,
    'show_func'   => 'b_xasset_pics',
    'edit_func'   => 'b_xasset_pics_opt',
    'template'    => 'xasset_app_pics.tpl',
];

$modversion['blocks'][] = [
    'file'        => 'xasset_blocks.php',
    'name'        => _MI_XASSET_APP_APPS,
    'description' => _MI_XASSET_APP_APPSD,
    'show_func'   => 'b_xasset_apps',
    'template'    => 'xasset_apps.tpl',
];

//config
$modversion['config'][] = [
    'name'        => 'prodShowMinLicence',
    'title'       => '_MI_XASSET_SHOW_MIN_LICENSE',
    'description' => '_MI_XASSET_SHOW_MIN_L_DESC',
    'formtype'    => 'yesno',
    'valuetype'   => 'int',
    'default'     => 0,
];

$modversion['config'][] = [
    'name'        => 'prodShowMaxDownloads',
    'title'       => '_MI_XASSET_SHOW_MAX_DOWNLOADS',
    'description' => '_MI_XASSET_SHOW_MAX_D_DESC',
    'formtype'    => 'yesno',
    'valuetype'   => 'int',
    'default'     => 0,
];

$modversion['config'][] = [
    'name'        => 'prodShowMaxDays',
    'title'       => '_MI_XASSET_SHOW_MAX_DAYS',
    'description' => '_MI_XASSET_SHOW_MAX_DAYS_DESC',
    'formtype'    => 'yesno',
    'valuetype'   => 'int',
    'default'     => 0,
];

$modversion['config'][] = [
    'name'        => 'prodShowExpires',
    'title'       => '_MI_XASSET_SHOW_EXPIRES',
    'description' => '_MI_XASSET_SHOW_EXPIRES',
    'formtype'    => 'yesno',
    'valuetype'   => 'int',
    'default'     => 0,
];

$modversion['config'][] = [
    'name'        => 'memExpireDaysWarn',
    'title'       => '_MI_XASSET_EXPIRE_WARND',
    'description' => '_MI_XASSET_EXPIRE_WARNDE',
    'formtype'    => 'text',
    'valuetype'   => 'int',
    'default'     => 7,
];

$modversion['config'][] = [
    'name'        => 'orderCompleteWait',
    'title'       => '_MI_XASSET_ORDERC_CAP',
    'description' => '_MI_XASSET_ORDERC_CAPDE',
    'formtype'    => 'text',
    'valuetype'   => 'int',
    'default'     => 10,
];

$modversion['config'][] = [
    'name'        => 'orderCompleteRedirect',
    'title'       => '_MI_XASSET_ORDERC_RED',
    'description' => '_MI_XASSET_ORDERC_REDDE',
    'formtype'    => 'text',
    'valuetype'   => 'string',
    'default'     => '',
];

$modversion['config'][] = [
    'name'        => 'encryptKey',
    'title'       => '_MI_XASSET_ENCRYPT_KEY',
    'description' => '_MI_XASSET_ENCRYPT_KEYD',
    'formtype'    => 'text',
    'valuetype'   => 'string',
    'default'     => base64_encode((string)time()),
];

$modversion['config'][] = [
    'name'        => 'Enablebandwidth',
    'title'       => '_MI_XASSET_BANDWIDTHENABLE',
    'description' => '_MI_XASSET_BANDWIDTHENABLED',
    'formtype'    => 'yesno',
    'valuetype'   => 'int',
    'default'     => 0,
];

$modversion['config'][] = [
    'name'        => 'bandwidth',
    'title'       => '_MI_XASSET_BANDWIDTH',
    'description' => '_MI_XASSET_BANDWIDTHD',
    'formtype'    => 'int',
    'valuetype'   => 'string',
    'default'     => 128,
];

$modversion['config'][] = [
    'name'        => 'prodwin_width',
    'title'       => '_MI_XASSET_PRODWIN_WIDTH',
    'description' => '_MI_XASSET_PRODWIN_WIDTHD',
    'formtype'    => 'int',
    'valuetype'   => 'int',
    'default'     => 450,
];

$modversion['config'][] = [
    'name'        => 'prodwin_height',
    'title'       => '_MI_XASSET_PRODWIN_HEIGHT',
    'description' => '_MI_XASSET_PRODWIN_HEIGHTD',
    'formtype'    => 'int',
    'valuetype'   => 'int',
    'default'     => 380,
];

// default admin editor
xoops_load('XoopsEditorHandler');
$editorHandler = \XoopsEditorHandler::getInstance();
$editorList    = array_flip($editorHandler->getList());

//$modversion['config'][] = [
//    'name'        => 'editor_options',
//    'title'       => '_MI_XASSET_EDITOR_OPTIONS',
//    'description' => '',
//    'formtype'    => 'select',
//    'valuetype'   => 'text',
//    'default'     => 'dhtml',
//    'options'     => $editorList,
//];

$modversion['config'][] = [
    'name'        => 'editorAdmin',
    'title'       => '_MI_XASSET_EDITOR_ADMIN',
    'description' => '_MI_XASSET_EDITOR_ADMIN_DESC',
    'formtype'    => 'select',
    'valuetype'   => 'text',
    'default'     => 'dhtmltextarea',
    'options'     => $editorList,
];

$modversion['config'][] = [
    'name'        => 'editorUser',
    'title'       => '_MI_XASSET_EDITOR_USER',
    'description' => '_MI_XASSET_EDITOR_USER_DESC',
    'formtype'    => 'select',
    'valuetype'   => 'text',
    'default'     => 'dhtmltextarea',
    'options'     => $editorList,
];

/**
 * Make Sample button visible?
 */
$modversion['config'][] = [
    'name'        => 'displaySampleButton',
    'title'       => '_CO_XASSET_SHOW_SAMPLE_BUTTON',
    'description' => '_CO_XASSET_SHOW_SAMPLE_BUTTON_DESC',
    'formtype'    => 'yesno',
    'valuetype'   => 'int',
    'default'     => 1,
];

/**
 * Show Developer Tools?
 */
$modversion['config'][] = [
    'name'        => 'displayDeveloperTools',
    'title'       => '_CO_XASSET_SHOW_DEV_TOOLS',
    'description' => '_CO_XASSET_SHOW_DEV_TOOLS_DESC',
    'formtype'    => 'yesno',
    'valuetype'   => 'int',
    'default'     => 0,
];
//notification
//$modversion['hasNotification'] = 0;

// Email templates
$modversion['_email_tpl'][1]['name']          = 'new_client_purchase';
$modversion['_email_tpl'][1]['category']      = 'client';
$modversion['_email_tpl'][1]['mail_template'] = 'client_newpurchase_notify';
$modversion['_email_tpl'][1]['mail_subject']  = _MI_XASSET_APP_NEW_PURCHASE_NOTIFYSBJ;
$modversion['_email_tpl'][1]['bit_value']     = 0;
$modversion['_email_tpl'][1]['title']         = _MI_XASSET_APP_NEW_PURCHASE_NOTIFY;
$modversion['_email_tpl'][1]['caption']       = _MI_XASSET_APP_NEW_PURCHASE_NOTIFYCAP;
$modversion['_email_tpl'][1]['description']   = _MI_XASSET_APP_NEW_PURCHASE_NOTIFYDSC;

$modversion['_email_tpl'][2]['name']          = 'new_client_purchase';
$modversion['_email_tpl'][2]['category']      = 'admin';
$modversion['_email_tpl'][2]['html']          = true;
$modversion['_email_tpl'][2]['mail_template'] = 'newpurchase_notify';
$modversion['_email_tpl'][2]['mail_subject']  = _MI_XASSET_APP_NEW_PURCHASE_ADMIN_NOTIFYSBJ;
$modversion['_email_tpl'][2]['bit_value']     = 1;
$modversion['_email_tpl'][2]['title']         = _MI_XASSET_APP_NEW_PURCHASE_ADMIN_NOTIFY;
$modversion['_email_tpl'][2]['caption']       = _MI_XASSET_APP_NEW_PURCHASE_ADMIN_NOTIFYCAP;
$modversion['_email_tpl'][2]['description']   = _MI_XASSET_APP_NEW_PURCHASE_ADMIN_NOTIFYDSC;

$modversion['_email_tpl'][3]['name']          = 'new_user';
$modversion['_email_tpl'][3]['category']      = 'client';
$modversion['_email_tpl'][3]['html']          = true;
$modversion['_email_tpl'][3]['mail_template'] = 'new_user';
$modversion['_email_tpl'][3]['mail_subject']  = _MI_XASSET_APP_NEW_USER_NOTIFYSBJ;
$modversion['_email_tpl'][3]['bit_value']     = 2;
$modversion['_email_tpl'][3]['title']         = _MI_XASSET_APP_NEW_USER_NOTIFY;
$modversion['_email_tpl'][3]['caption']       = _MI_XASSET_APP_NEW_USER_NOTIFYCAP;
$modversion['_email_tpl'][3]['description']   = _MI_XASSET_APP_NEW_USER_NOTIFYCAPDSC;

$modversion['_email_tpl'][4]['name']          = 'expire_warning';
$modversion['_email_tpl'][4]['category']      = 'client';
$modversion['_email_tpl'][4]['html']          = true;
$modversion['_email_tpl'][4]['mail_template'] = 'expire_warning';
$modversion['_email_tpl'][4]['mail_subject']  = _MI_XASSET_APP_EXPIRE_WARN_NOTIFYSBJ;
$modversion['_email_tpl'][4]['bit_value']     = 3;
$modversion['_email_tpl'][4]['title']         = _MI_XASSET_APP_EXPIRE_WARN_NOTIFY;
$modversion['_email_tpl'][4]['caption']       = _MI_XASSET_APP_EXPIRE_WARN_NOTIFYCAP;
$modversion['_email_tpl'][4]['description']   = _MI_XASSET_APP_EXPIRE_WARN_NOTIFYDSC;

$modversion['_email_tpl'][5]['name']          = 'expire_membership';
$modversion['_email_tpl'][5]['category']      = 'client';
$modversion['_email_tpl'][5]['html']          = true;
$modversion['_email_tpl'][5]['mail_template'] = 'expire_membership';
$modversion['_email_tpl'][5]['mail_subject']  = _MI_XASSET_APP_EXPIRE_MEMBER_NOTIFYSBJ;
$modversion['_email_tpl'][5]['bit_value']     = 3;
$modversion['_email_tpl'][5]['title']         = _MI_XASSET_APP_EXPIRE_MEMBER_NOTIFY;
$modversion['_email_tpl'][5]['caption']       = _MI_XASSET_APP_EXPIRE_MEMBER_NOTIFYCAP;
$modversion['_email_tpl'][5]['description']   = _MI_XASSET_APP_EXPIRE_MEMBER_NOTIFYDSC;

$modversion['_email_tpl'][6]['name']          = 'order_complete';
$modversion['_email_tpl'][6]['category']      = 'client';
$modversion['_email_tpl'][6]['html']          = true;
$modversion['_email_tpl'][6]['mail_template'] = 'order_complete';
$modversion['_email_tpl'][6]['mail_subject']  = _MI_XASSET_ORDER_COMPLETE_NOTIFYSBJ;
$modversion['_email_tpl'][6]['bit_value']     = 3;
$modversion['_email_tpl'][6]['title']         = _MI_XASSET_ORDER_COMPLETE_NOTIFY;
$modversion['_email_tpl'][6]['caption']       = _MI_XASSET_ORDER_COMPLETE_NOTIFYCAP;
$modversion['_email_tpl'][6]['description']   = _MI_XASSET_ORDER_COMPLETE_NOTIFYDSC;
