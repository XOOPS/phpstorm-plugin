"""Writes the golden fixtures under src/test/resources/fixtures.

Every case is a small module tree; `*.after.php` is the whole expected file once every
quick-fix of the family has been applied. Run from the plugin root:

    python -I tools/gen_fixtures.py
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "src/test/resources/fixtures"

F = {}

# --- RootPathGuard -------------------------------------------------------------------

F["RootPathGuard/namespaced/modules/demo/class/Foo.php"] = r"""<?php

declare(strict_types=1);

namespace XoopsModules\Demo;

use Xmf\Request;

/**
 * Class Foo
 */
class Foo
{
    public function run(): void
    {
    }
}
"""
F["RootPathGuard/namespaced/modules/demo/class/Foo.after.php"] = r"""<?php

declare(strict_types=1);

namespace XoopsModules\Demo;

defined('XOOPS_ROOT_PATH') || exit('Restricted access');

use Xmf\Request;

/**
 * Class Foo
 */
class Foo
{
    public function run(): void
    {
    }
}
"""

F["RootPathGuard/docblock/modules/demo/class/Baz.php"] = r"""<?php
/**
 * @copyright XOOPS Project
 * @license   GPL-2.0-or-later
 */

/**
 * Class Baz
 */
class Baz extends \XoopsObject
{
}
"""
F["RootPathGuard/docblock/modules/demo/class/Baz.after.php"] = r"""<?php
/**
 * @copyright XOOPS Project
 * @license   GPL-2.0-or-later
 */

defined('XOOPS_ROOT_PATH') || exit('Restricted access');

/**
 * Class Baz
 */
class Baz extends \XoopsObject
{
}
"""

F["RootPathGuard/entry/modules/demo/pop.php"] = r"""<?php

declare(strict_types=1);

$doNotStartPrint = true;
$noTitle = true;
require_once __DIR__ . '/print.php';
"""

F["RootPathGuard/global/modules/demo/include/functions.php"] = r"""<?php
/**
 * @copyright XOOPS Project
 * @license   GPL-2.0-or-later
 */
global $xoopsDB;

function demo_count(): int
{
    return 1;
}
"""
F["RootPathGuard/global/modules/demo/include/functions.after.php"] = r"""<?php
defined('XOOPS_ROOT_PATH') || exit('Restricted access');

/**
 * @copyright XOOPS Project
 * @license   GPL-2.0-or-later
 */
global $xoopsDB;

function demo_count(): int
{
    return 1;
}
"""

F["RootPathGuard/rootpage/modules/demo/index.php"] = r"""<?php
header('location: view.php');
"""
F["RootPathGuard/rootpage/modules/demo/verify.php"] = r"""<?php
define('XOOPS_XMLRPC', 1);
require __DIR__ . '/servicemain.php';
"""
F["RootPathGuard/rootpage/modules/demo/servicemain.php"] = r"""<?php
require dirname(__DIR__, 2) . '/mainfile.php';
"""
F["RootPathGuard/rootpage/modules/demo/functions.php"] = r"""<?php
function demo_helper(): int
{
    return 1;
}
"""
F["RootPathGuard/rootpage/modules/demo/run.php"] = r"""<?php
$boot = __DIR__ . '/servicemain.php';
require $boot;
"""
F["RootPathGuard/rootpage/modules/demo/api/ping.php"] = r"""<?php
echo '{"ok":true}';
"""
F["RootPathGuard/rootpage/modules/demo/footer.php"] = r"""<?php
global $xoopsTpl;
require_once __DIR__ . '/include/functions.php';
$xoopsTpl->assign('x', 1);
"""

F["RootPathGuard/sameline/modules/demo/include/strict.php"] = r"""<?php declare(strict_types=1); global $xoopsDB;

function demo_strict(): int
{
    return 1;
}
"""
F["RootPathGuard/sameline/modules/demo/include/strict.after.php"] = r"""<?php declare(strict_types=1); defined('XOOPS_ROOT_PATH') || exit('Restricted access');

global $xoopsDB;

function demo_strict(): int
{
    return 1;
}
"""

F["RootPathGuard/autoloader/modules/demo/preloads/autoloader.php"] = r"""<?php

spl_autoload_register(static function (string $class): void {
    require __DIR__ . '/../class/' . $class . '.php';
});
"""

F["RootPathGuard/qualified/modules/demo/class/Bar.php"] = r"""<?php

namespace XoopsModules\Demo;

\defined('XOOPS_ROOT_PATH') || exit('Restricted access');

class Bar
{
}
"""

F["RootPathGuard/stub/modules/demo/images/index.php"] = r"""<?php
header('HTTP/1.0 404 Not Found');
"""

# --- DoubleCpHeader ------------------------------------------------------------------

F["DoubleCpHeader/renders/modules/demo/admin/admin_header.php"] = r"""<?php
require \dirname(__DIR__, 3) . '/include/cp_header.php';
xoops_cp_header();
"""
F["DoubleCpHeader/renders/modules/demo/admin/index.php"] = r"""<?php
require_once __DIR__ . '/admin_header.php';

xoops_cp_header();
$adminObject->displayNavigation(basename(__FILE__));
xoops_cp_footer();
"""
F["DoubleCpHeader/renders/modules/demo/admin/index.after.php"] = r"""<?php
require_once __DIR__ . '/admin_header.php';

$adminObject->displayNavigation(basename(__FILE__));
xoops_cp_footer();
"""

F["DoubleCpHeader/norender/modules/demo/admin/admin_header.php"] = r"""<?php
require \dirname(__DIR__, 3) . '/include/cp_header.php';
require_once \dirname(__DIR__) . '/include/common.php';
"""
F["DoubleCpHeader/norender/modules/demo/admin/index.php"] = r"""<?php
require_once __DIR__ . '/admin_header.php';

xoops_cp_header();
$adminObject->displayNavigation(basename(__FILE__));
xoops_cp_footer();
"""

# --- AttributeEscape -----------------------------------------------------------------

F["AttributeEscape/attr/modules/demo/class/Util.php"] = r"""<?php

namespace XoopsModules\Demo;

class Util
{
    public static function render($obj, array $images, $img): string
    {
        $html = '<a href="' . $obj->getVar('url') . '">' . $obj->getVar('title') . '</a>';
        $html .= "<input value='" . $images['main']->getVar('image_name') . "'>";
        $html .= '<img src="' . XOOPS_URL . '/thumb.php?src=' . $img->getVar('image_name') . '&w=100">';
        $html .= '<a href="' . htmlspecialchars((string) $obj->getVar('url'), ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8') . '">x</a>';
        $title = constant('CO_DEMO_FB') . $GLOBALS['xoopsModule']->getVar('dirname');
        $value = ['lat' => $obj->getVar('lat'), 'title' => $title];

        return $html . $value['title'];
    }
}
"""
F["AttributeEscape/attr/modules/demo/class/Util.after.php"] = r"""<?php

namespace XoopsModules\Demo;

class Util
{
    public static function render($obj, array $images, $img): string
    {
        $html = '<a href="' . htmlspecialchars((string) $obj->getVar('url'), ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8') . '">' . $obj->getVar('title') . '</a>';
        $html .= "<input value='" . htmlspecialchars((string) $images['main']->getVar('image_name'), ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8') . "'>";
        $html .= '<img src="' . XOOPS_URL . '/thumb.php?src=' . rawurlencode((string) $img->getVar('image_name', 'n')) . '&w=100">';
        $html .= '<a href="' . htmlspecialchars((string) $obj->getVar('url'), ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8') . '">x</a>';
        $title = constant('CO_DEMO_FB') . $GLOBALS['xoopsModule']->getVar('dirname');
        $value = ['lat' => $obj->getVar('lat'), 'title' => $title];

        return $html . $value['title'];
    }
}
"""

# --- DeprecatedDbApi -----------------------------------------------------------------

F["DeprecatedDbApi/queryf/modules/demo/class/Handler.php"] = r"""<?php

namespace XoopsModules\Demo;

class Handler
{
    public $db;

    public function updateCounter(int $id): bool
    {
        $sql = 'UPDATE ' . $this->db->prefix('demo_items') . ' SET counter=counter+1 WHERE itemid = ' . $id;
        if ($this->db->queryF($sql)) {
            return true;
        }

        return false;
    }

    public function count(): int
    {
        $sql = 'SELECT COUNT(*) FROM ' . $this->db->prefix('demo_items');
        $result = $this->db->queryF($sql);
        [$count] = $this->db->fetchRow($result);

        return (int) $count;
    }

    public function insert($obj, bool $force = false)
    {
        $sql2 = 'INSERT INTO ' . $this->db->prefix('demo_items') . ' (title) VALUES (' . $this->db->quoteString($obj->title) . ')';
        if ($force) {
            $result = $this->db->queryF($sql2);
        } else {
            $result = $this->db->query($sql2);
        }

        return $result;
    }

    public function raw(string $unknown)
    {
        return $this->db->queryF($unknown);
    }
}
"""
F["DeprecatedDbApi/queryf/modules/demo/class/Handler.after.php"] = r"""<?php

namespace XoopsModules\Demo;

class Handler
{
    public $db;

    public function updateCounter(int $id): bool
    {
        $sql = 'UPDATE ' . $this->db->prefix('demo_items') . ' SET counter=counter+1 WHERE itemid = ' . $id;
        if ($this->db->exec($sql)) {
            return true;
        }

        return false;
    }

    public function count(): int
    {
        $sql = 'SELECT COUNT(*) FROM ' . $this->db->prefix('demo_items');
        $result = $this->db->query($sql);
        [$count] = $this->db->fetchRow($result);

        return (int) $count;
    }

    public function insert($obj, bool $force = false)
    {
        $sql2 = 'INSERT INTO ' . $this->db->prefix('demo_items') . ' (title) VALUES (' . $this->db->quote($obj->title) . ')';
        if ($force) {
            $result = $this->db->exec($sql2);
        } else {
            $result = $this->db->query($sql2);
        }

        return $result;
    }

    public function raw(string $unknown)
    {
        return $this->db->queryF($unknown);
    }
}
"""

F["MutatingQuery/write/modules/demo/class/H.php"] = r"""<?php

class H
{
    public function run($db, string $arg): void
    {
        $sql = 'DELETE FROM ' . $db->prefix('demo') . ' WHERE id = 1';
        $db->query($sql);
        $db->query('SELECT 1');
        $db->query($arg);
    }
}
"""
F["MutatingQuery/write/modules/demo/class/H.after.php"] = r"""<?php

class H
{
    public function run($db, string $arg): void
    {
        $sql = 'DELETE FROM ' . $db->prefix('demo') . ' WHERE id = 1';
        $db->exec($sql);
        $db->query('SELECT 1');
        $db->query($arg);
    }
}
"""

# --- FileHeader ----------------------------------------------------------------------

F["FileHeader/noheader/modules/demo/class/A.php"] = r"""<?php declare(strict_types=1);

namespace XoopsModules\Demo;

class A
{
}
"""
F["FileHeader/noheader/modules/demo/class/A.after.php"] = r"""<?php declare(strict_types=1);
/**
 * @copyright   XOOPS Project (https://xoops.org)
 * @license     GPL-2.0-or-later (https://www.gnu.org/licenses/gpl-2.0.html)
 */

namespace XoopsModules\Demo;

class A
{
}
"""

F["FileHeader/thumb/modules/demo/class/T.php"] = r"""<?php
/**
 * @copyright   XOOPS Project
 * @license     GPL-2.0-or-later
 */

class T
{
    protected $filePrependSecurityBlock = "<?php exit('Execution denied!'); //";
}
"""

F["FileHeader/thirdparty/modules/demo/class/V.php"] = r"""<?php
/*
 * Copyright (c) 2010 Some Vendor
 * Permission is hereby granted, free of charge, to any person obtaining a copy (MIT)
 */

class V
{
}
"""

# --- ResultSetGuard ------------------------------------------------------------------

F["ResultSetGuard/guard/modules/demo/class/H.php"] = r"""<?php

class H
{
    public $db;

    public function getCount(string $sql): int
    {
        $result = $this->db->query($sql);
        [$count] = $this->db->fetchRow($result);

        return (int) $count;
    }

    public function getObjects(string $sql)
    {
        $ret = [];
        $result = $this->db->query($sql);
        while (false !== ($row = $this->db->fetchArray($result))) {
            $ret[] = $row;
        }

        return $ret;
    }

    public function guardedInCondition($db, $r): array
    {
        $ret = [];
        while ($db->isResultSet($r) && false !== ($row = $db->fetchArray($r))) {
            $ret[] = $row;
        }

        return $ret;
    }

    public function guardedByTruthiness($db, $result)
    {
        if (!$result) {
            return false;
        }

        return $db->fetchRow($result);
    }

    public function guardedByEarlyExit($db, $r): array
    {
        if (!$db->isResultSet($r)) {
            return [];
        }

        return $db->fetchArray($r);
    }

    public function reversed($db, $r): array
    {
        $rows = [];
        if (!$db->isResultSet($r)) {
            while ($row = $db->fetchBoth($r)) {
                $rows[] = $row;
            }
        }

        return $rows;
    }
}
"""
F["ResultSetGuard/guard/modules/demo/class/H.after.php"] = r"""<?php

class H
{
    public $db;

    public function getCount(string $sql): int
    {
        $result = $this->db->query($sql);
        if (!$this->db->isResultSet($result)) {
            return 0;
        }
        [$count] = $this->db->fetchRow($result);

        return (int) $count;
    }

    public function getObjects(string $sql)
    {
        $ret = [];
        $result = $this->db->query($sql);
        if (!$this->db->isResultSet($result)) {
            return $ret;
        }
        while (false !== ($row = $this->db->fetchArray($result))) {
            $ret[] = $row;
        }

        return $ret;
    }

    public function guardedInCondition($db, $r): array
    {
        $ret = [];
        while ($db->isResultSet($r) && false !== ($row = $db->fetchArray($r))) {
            $ret[] = $row;
        }

        return $ret;
    }

    public function guardedByTruthiness($db, $result)
    {
        if (!$result) {
            return false;
        }

        return $db->fetchRow($result);
    }

    public function guardedByEarlyExit($db, $r): array
    {
        if (!$db->isResultSet($r)) {
            return [];
        }

        return $db->fetchArray($r);
    }

    public function reversed($db, $r): array
    {
        $rows = [];
        if (!$db->isResultSet($r)) {
            while ($row = $db->fetchBoth($r)) {
                $rows[] = $row;
            }
        }

        return $rows;
    }
}
"""

# --- FormLoader ----------------------------------------------------------------------

F["FormLoader/tray/modules/demo/class/Form/Tray.php"] = r"""<?php

namespace XoopsModules\Demo\Form;

use XoopsModules\Demo\Helper;

//xoops_load('formxoopsfilemanager', 'xaddresses'); // load custom form class
/**
 * Class Tray
 */
class Tray extends \XoopsFormElementTray
{
}
"""
F["FormLoader/tray/modules/demo/class/Form/Tray.after.php"] = r"""<?php

namespace XoopsModules\Demo\Form;

use XoopsModules\Demo\Helper;

\xoops_load('XoopsFormLoader');

//xoops_load('formxoopsfilemanager', 'xaddresses'); // load custom form class
/**
 * Class Tray
 */
class Tray extends \XoopsFormElementTray
{
}
"""

F["FormLoader/parentload/modules/demo/class/BlockForm.php"] = r"""<?php

namespace XoopsModules\Demo;

\xoops_load('XoopsForm');

class BlockForm extends \XoopsForm
{
}
"""

# --- Superglobal ---------------------------------------------------------------------

F["Superglobal/keyed/modules/demo/include/ajax.php"] = r"""<?php

use Xmf\Request;

require \dirname(__DIR__, 3) . '/mainfile.php';

$id = $_POST['id'];
echo $id;
if (isset($_POST['new'])) {
    $new = true;
}
if (Request::hasVar('_method', 'POST') && null !== $_POST['_method']) {
    $method = 'x';
}
$_GET['categoryid'] = 5;
foreach ($_POST as $k => $v) {
    $all[$k] = $v;
}
if (empty($_GET['x'])) {
    $x = 1;
}
$a = $_REQUEST['a'];
"""
F["Superglobal/keyed/modules/demo/include/ajax.after.php"] = r"""<?php

use Xmf\Request;

require \dirname(__DIR__, 3) . '/mainfile.php';

$id = Request::getText('id', '', 'POST');
echo $id;
if (Request::hasVar('new', 'POST')) {
    $new = true;
}
if (Request::hasVar('_method', 'POST') && Request::hasVar('_method', 'POST')) {
    $method = 'x';
}
$_GET['categoryid'] = 5;
foreach (Request::get('POST', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM) as $k => $v) {
    $all[$k] = $v;
}
if (empty(Request::getVar('x', null, 'GET', 'none', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM))) {
    $x = 1;
}
$a = Request::getVar('a', null, 'default', 'none', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM);
"""

F["Superglobal/typed/modules/demo/xoops_version.php"] = "<?php\n$modversion['name'] = 'Demo';\n"
F["Superglobal/typed/modules/demo/class/Item.php"] = r"""<?php

class Item extends XoopsObject
{
    public function __construct()
    {
        $this->initVar('tags', XOBJ_DTYPE_ARRAY, null, false);
        $this->initVar('title2', XOBJ_DTYPE_TXTBOX, null, true, 255);
    }
}
"""
F["Superglobal/typed/modules/demo/admin/perms.php"] = r"""<?php

use Xmf\Request;

require \dirname(__DIR__, 3) . '/mainfile.php';

foreach ($_POST['groups'] as $gid) {
    $perm->addRight('view', $gid);
}
$n = count($_POST['ids']);
$id = (int)$_GET['id'];
$start = intval($_GET['start']);
$op = isset($_POST['op']) ? $_POST['op'] : 'list';
switch ($op) {
    case 'save':
        break;
}
$limit = $_GET['limit'] ?? 20;
$criteria->setLimit($limit);
if ($_GET['type'] === 'rss') {
    $x = 1;
}
echo 'Hello ' . $_GET['name'];
$title = $_POST['title'];
echo $title . '!';
$body = trim($_POST['body']);
$cats = $_POST['cats'];
$out = implode(',', $cats);
$x = $_POST['unknown'];
doSomething($x);
$obj->setVar('description', $_POST['description']);
$item = new Item();
$item->setVar('tags', $_POST['tags']);
$item->setVar('title2', $_POST['title2']);
echo "Hi {$_POST['who']}";
if (!$_POST['ids2']) {
    $e = 1;
}
$s = implode($_POST['parts']);
$m = max($_POST['n'], 10);
if (isset($_POST['a']['b'])) {
    $f = 1;
}
$_POST['c']['d'] = 1;
reset($_POST['r']);
$v0 = $_POST['v'][0];
$c = $_POST['cv'];
$packed = compact('c');
echo $c;
$off = array_slice($all, $_GET['offset']);
"""
F["Superglobal/typed/modules/demo/admin/perms.after.php"] = r"""<?php

use Xmf\Request;

require \dirname(__DIR__, 3) . '/mainfile.php';

foreach (Request::getArray('groups', [], 'POST') as $gid) {
    $perm->addRight('view', $gid);
}
$n = count(Request::getArray('ids', [], 'POST'));
$id = Request::getInt('id', 0, 'GET');
$start = Request::getInt('start', 0, 'GET');
$op = Request::getString('op', 'list', 'POST');
switch ($op) {
    case 'save':
        break;
}
$limit = Request::getVar('limit', null, 'GET', 'none', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM) ?? 20;
$criteria->setLimit($limit);
if (Request::getString('type', '', 'GET') === 'rss') {
    $x = 1;
}
echo 'Hello ' . Request::getText('name', '', 'GET');
$title = Request::getText('title', '', 'POST');
echo $title . '!';
$body = trim(Request::getText('body', '', 'POST'));
$cats = Request::getArray('cats', [], 'POST');
$out = implode(',', $cats);
$x = Request::getVar('unknown', null, 'POST', 'none', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM);
doSomething($x);
$obj->setVar('description', Request::getVar('description', null, 'POST', 'none', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM));
$item = new Item();
$item->setVar('tags', Request::getArray('tags', [], 'POST'));
$item->setVar('title2', Request::getText('title2', '', 'POST'));
echo "Hi {$_POST['who']}";
if (!Request::getVar('ids2', null, 'POST', 'none', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM)) {
    $e = 1;
}
$s = implode(Request::getArray('parts', [], 'POST'));
$m = max(Request::getVar('n', null, 'POST', 'none', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM), 10);
if (isset($_POST['a']['b'])) {
    $f = 1;
}
$_POST['c']['d'] = 1;
reset($_POST['r']);
$v0 = Request::getVar('v', null, 'POST', 'none', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM)[0];
$c = Request::getVar('cv', null, 'POST', 'none', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM);
$packed = compact('c');
echo $c;
$off = array_slice($all, Request::getVar('offset', null, 'GET', 'none', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM));
"""

F["Superglobal/raw/modules/demo/include/raw.php"] = r"""<?php

use Xmf\Request;

$x = $_POST['unknown'];
doSomething($x);
if (empty($_GET['q'])) {
    $e = 1;
}
if (null !== $_POST['m']) {
    $m = 1;
}
if ($_POST['m2'] === null) {
    $m = 2;
}
if (null != $_GET['l']) {
    $m = 3;
}
$all = $_POST;
foreach ($_REQUEST as $k => $v) {
    $copy[$k] = $v;
}
$r = $_REQUEST['op'];
switch ($r) {
    case 'a':
        break;
}
$v0 = $_POST['v'][0];
echo "Hi {$_POST['who']}";
$_POST['w'] = 1;
sort($_POST['s']);
$dyn = $_GET[$k];
"""
F["Superglobal/raw/modules/demo/include/raw.after.php"] = r"""<?php

use Xmf\Request;

$x = Request::getVar('unknown', null, 'POST', 'none', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM);
doSomething($x);
if (empty(Request::getVar('q', null, 'GET', 'none', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM))) {
    $e = 1;
}
if (Request::hasVar('m', 'POST')) {
    $m = 1;
}
if (!Request::hasVar('m2', 'POST')) {
    $m = 2;
}
if (null != Request::getVar('l', null, 'GET', 'none', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM)) {
    $m = 3;
}
$all = Request::get('POST', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM);
foreach (Request::get('default', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM) as $k => $v) {
    $copy[$k] = $v;
}
$r = Request::getString('op', '', 'default');
switch ($r) {
    case 'a':
        break;
}
$v0 = Request::getVar('v', null, 'POST', 'none', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM)[0];
echo "Hi {$_POST['who']}";
$_POST['w'] = 1;
sort($_POST['s']);
$dyn = Request::getVar($k, null, 'GET', 'none', Request::MASK_ALLOW_RAW | Request::MASK_NO_TRIM);
"""

F["Superglobal/assets/modules/demo/assets/js/handler.php"] = r"""<?php
$uuid = $_POST['qquuid'];
"""

# --- Small fixes ---------------------------------------------------------------------

F["Unserialize/unser/modules/demo/class/U.php"] = r"""<?php

class U
{
    public function defaults($field, string $x): array
    {
        $a = unserialize($field->getVar('field_default', 'n'));
        $b = unserialize($x, ['allowed_classes' => false]);

        return [$a, $b];
    }
}
"""
F["Unserialize/unser/modules/demo/class/U.after.php"] = r"""<?php

class U
{
    public function defaults($field, string $x): array
    {
        $a = unserialize($field->getVar('field_default', 'n'), ['allowed_classes' => false]);
        $b = unserialize($x, ['allowed_classes' => false]);

        return [$a, $b];
    }
}
"""

F["HttpStatus/status/modules/demo/images/index.php"] = r"""<?php
header('HTTP/1.0 404 Not Found');
"""
F["HttpStatus/status/modules/demo/images/index.after.php"] = r"""<?php
http_response_code(404);
"""

F["Setcookie/cookie/modules/demo/class/U.php"] = r"""<?php

class U
{
    public static function cookie(string $name, string $value, int $expire): void
    {
        setcookie($name, $value, $expire, '/', '', false, true);
    }
}
"""
F["Setcookie/cookie/modules/demo/class/U.after.php"] = r"""<?php

class U
{
    public static function cookie(string $name, string $value, int $expire): void
    {
        xoops_setcookie($name, $value, $expire, '/', '', false, true);
    }
}
"""

F["GuestUser/guest/modules/demo/vote.php"] = r"""<?php
require_once __DIR__ . '/header.php';

if (isset($xoopsUser) && is_object($xoopsUser)) {
    $groups = $xoopsUser->getGroups();
}
if (isset($xoopsUser)) {
    $uid = $xoopsUser->uid();
}
"""
F["GuestUser/guest/modules/demo/vote.after.php"] = r"""<?php
require_once __DIR__ . '/header.php';

if (isset($xoopsUser) && is_object($xoopsUser)) {
    $groups = $xoopsUser->getGroups();
}
if ($xoopsUser instanceof \XoopsUser) {
    $uid = $xoopsUser->uid();
}
"""

F["Unlink/unlink/modules/demo/include/onupdate.php"] = r"""<?php

function xoops_module_update_demo($module, $previousVersion, string $templateFolder, array $files, string $other): bool
{
    foreach ($files as $v) {
        if (\is_file($templateFolder . $v)) {
            unlink($templateFolder . $v);
        }
    }
    unlink($other);

    return true;
}
"""

F["ByRefArgument/byref/modules/demo/class/XoopsForm.php"] = r"""<?php

class XoopsForm
{
    public function __construct($title, $name, $action, $method = 'post', $addtoken = true, $summary = '')
    {
    }
}

function demo_takes_ref(&$a)
{
    $a = 1;
}
"""
F["ByRefArgument/byref/modules/demo/class/Form/CategoryForm.php"] = r"""<?php

class CategoryForm extends XoopsForm
{
    public function __construct(&$target)
    {
        parent::__construct($target->getVar('title'), 'form', \xoops_getenv('SCRIPT_NAME'));
        demo_takes_ref(strtolower('x'));
    }
}
"""

# --- Language ------------------------------------------------------------------------

F["LanguageLoad/helper/modules/demo/include/ajax_rating.php"] = r"""<?php
require \dirname(__DIR__, 3) . '/mainfile.php';
$helper = \XoopsModules\Demo\Helper::getInstance();
$helper->loadLanguage('main');
echo _MD_DEMO_THANKS;
"""

F["LanguageLoad/missing/modules/demo/include/ajax2.php"] = r"""<?php
require \dirname(__DIR__, 3) . '/mainfile.php';
echo _MD_DEMO_THANKS;
"""

F["LanguageRole/copy/modules/demo/language/english/modinfo1.php"] = r"""<?php
define('_MI_DEMO_NAME', 'Demo');
define('_MI_DEMO_DESC', 'Demo module');
define('_MI_DEMO_ADMENU1', 'Index');
"""

F["LanguageRole/prefix/modules/demo/language/english/admin.php"] = r"""<?php
define('_AM_DEMO_SAVE', 'Save');
define('_MI_DEMO_NAME', 'Demo');
"""

# --- CSRF ----------------------------------------------------------------------------

F["Csrf/getop/modules/demo/admin/mimetypes.php"] = r"""<?php
use Xmf\Request;

require_once __DIR__ . '/admin_header.php';

$op = Request::getString('op', 'default', 'GET');
if ('POST' === Request::getMethod() && !$GLOBALS['xoopsSecurity']->check()) {
    redirect_header('mimetypes.php', 3, implode('<br>', $GLOBALS['xoopsSecurity']->getErrors()));
}

switch ($op) {
    case 'delete':
        $mimetypeHandler->delete($mimeType, true);
        break;
    case 'clone':
        $mimeType->setVar('mime_id', 0);
        break;
    case 'save':
        if (!$GLOBALS['xoopsSecurity']->check()) {
            redirect_header('mimetypes.php', 3, 'token');
        }
        $mimetypeHandler->insert($mimeType);
        break;
    default:
        break;
}
"""

F["Csrf/postop/modules/demo/admin/item.php"] = r"""<?php
use Xmf\Request;

require_once __DIR__ . '/admin_header.php';

$op = Request::getString('op', 'list', 'POST');
if ('POST' === Request::getMethod() && !$GLOBALS['xoopsSecurity']->check()) {
    redirect_header('item.php', 3, 'token');
}

switch ($op) {
    case 'delete':
        $itemHandler->delete($item, true);
        break;
    default:
        break;
}
"""

# --- Templates -----------------------------------------------------------------------

F["Templates/missing/modules/demo/xoops_version.php"] = r"""<?php
$modversion['templates'][] = ['file' => 'demo_index.tpl', 'description' => ''];
$modversion['templates'][] = ['file' => 'partial_pagertop.tpl', 'type' => 'partial', 'description' => ''];
$modversion['templates'][] = ['file' => 'demo_admin_about.tpl', 'type' => 'admin', 'description' => ''];
$modversion['templates'][] = ['file' => 'demo_block_list.tpl', 'type' => 'block', 'description' => ''];
$modversion['templates'][] = ['file' => 'blocks/demo_block_ok.tpl', 'description' => ''];
$modversion['blocks'][] = ['file' => 'blocks.php', 'show_func' => 'b_demo', 'template' => 'blocks/demo_block_b.tpl'];
$modversion['blocks'][] = ['file' => 'blocks.php', 'show_func' => 'b_demo2', 'template' => 'demo_block_elsewhere.tpl'];
"""
F["Templates/missing/modules/demo/xoops_version.after.php"] = r"""<?php
$modversion['templates'][] = ['file' => 'demo_index.tpl', 'description' => ''];
$modversion['templates'][] = ['file' => 'partials/partial_pagertop.tpl', 'type' => 'partial', 'description' => ''];
$modversion['templates'][] = ['file' => 'demo_admin_about.tpl', 'type' => 'admin', 'description' => ''];
$modversion['templates'][] = ['file' => 'blocks/demo_block_list.tpl', 'type' => 'block', 'description' => ''];
$modversion['templates'][] = ['file' => 'blocks/demo_block_ok.tpl', 'description' => ''];
$modversion['blocks'][] = ['file' => 'blocks.php', 'show_func' => 'b_demo', 'template' => 'demo_block_b.tpl'];
$modversion['blocks'][] = ['file' => 'blocks.php', 'show_func' => 'b_demo2', 'template' => 'demo_block_elsewhere.tpl'];
"""
F["Templates/missing/modules/demo/templates/demo_index.tpl"] = "<div>index</div>\n"
F["Templates/missing/modules/demo/templates/partials/partial_pagertop.tpl"] = "<div>pager</div>\n"
F["Templates/missing/modules/demo/templates/blocks/demo_block_list.tpl"] = "<div>list</div>\n"
F["Templates/missing/modules/demo/templates/blocks/demo_block_ok.tpl"] = "<div>ok</div>\n"
F["Templates/missing/modules/demo/templates/blocks/demo_block_b.tpl"] = "<div>b</div>\n"
F["Templates/missing/modules/demo/templates/demo_block_elsewhere.tpl"] = "<div>elsewhere</div>\n"

F["Templates/unregistered/modules/demo/xoops_version.php"] = r"""<?php
$modversion['name'] = 'Demo';
$modversion['templates'][] = ['file' => 'demo_index.tpl', 'description' => ''];
"""
F["Templates/unregistered/modules/demo/xoops_version.after.php"] = r"""<?php
$modversion['name'] = 'Demo';
$modversion['templates'][] = ['file' => 'demo_index.tpl', 'description' => ''];
$modversion['templates'][] = ['file' => 'demo_item.tpl', 'description' => ''];
"""
F["Templates/unregistered/modules/demo/templates/demo_index.tpl"] = "<div>index</div>\n"
F["Templates/unregistered/modules/demo/templates/demo_item.tpl"] = r"""<div class="item">
    <{include file='db:system_comments_flat.tpl'}>
    <{include file="db:tag_bar.tpl"}>
    <{* <{include file='db:demo_ghost.tpl'}> *}>
    <{include file='db:demo_header.tpl'}>
</div>
"""
F["Templates/unregistered/modules/tag/xoops_version.php"] = r"""<?php
$modversion['templates'][] = ['file' => 'tag_bar.tpl', 'description' => ''];
"""

# --- 0.5.0: receiver proof, legacy guards, trailing comments ------------------------------

F["MutatingQuery/wrapper/modules/demo/class/VersionControlMySQL.php"] = r"""<?php

class VersionControlMySQL
{
    public $xoopsDB;

    public function query($sql)
    {
        return $this->xoopsDB->query($sql);
    }

    public function bump(int $id)
    {
        $sql = 'UPDATE versions SET n = n + 1 WHERE id = ' . $id;
        return $this->query($sql);
    }

    public function pdo(int $id)
    {
        $db = new \PDO('sqlite::memory:');
        $sql = 'DELETE FROM versions WHERE id = ' . $id;
        return $db->query($sql);
    }
}
"""

F["DeprecatedDbApi/field/modules/demo/class/VersionControlMySQL.php"] = r"""<?php

class VersionControlMySQL
{
    public $xoopsDB;

    public function purge(int $id)
    {
        $sql = 'DELETE FROM versions WHERE id = ' . $id;
        $this->xoopsDB->queryF($sql);
        $this->queryF($sql);
    }

    public function queryF($sql)
    {
        return $this->xoopsDB->exec($sql);
    }
}
"""
F["DeprecatedDbApi/field/modules/demo/class/VersionControlMySQL.after.php"] = r"""<?php

class VersionControlMySQL
{
    public $xoopsDB;

    public function purge(int $id)
    {
        $sql = 'DELETE FROM versions WHERE id = ' . $id;
        $this->xoopsDB->exec($sql);
        $this->queryF($sql);
    }

    public function queryF($sql)
    {
        return $this->xoopsDB->exec($sql);
    }
}
"""

F["ResultSetGuard/legacy/modules/demo/class/L.php"] = r"""<?php

class L
{
    public $db;

    public function getObjects(string $sql)
    {
        $ret = [];
        $result = $this->db->query($sql);
        if (!$result) {
            return $ret;
        }
        while (false !== ($row = $this->db->fetchArray($result))) {
            $ret[] = $row;
        }

        return $ret;
    }

    public function getCount(string $sql): int
    {
        if (!$result = $this->db->query($sql)) {
            return 0;
        }
        [$count] = $this->db->fetchRow($result);

        return (int) $count;
    }

    public function nested(string $sql)
    {
        return $this->db->fetchArray($this->db->query($sql));
    }

    public function unbraced(string $sql)
    {
        $row = null;
        if ($result = $this->db->query($sql)) $row = $this->db->fetchArray($result);

        return $row;
    }

    public function elseBranch($result): array
    {
        if (!$this->db->isResultSet($result)) {
            return [];
        } else {
            return $this->db->fetchArray($result);
        }
    }
}
"""
F["ResultSetGuard/legacy/modules/demo/class/L.after.php"] = r"""<?php

class L
{
    public $db;

    public function getObjects(string $sql)
    {
        $ret = [];
        $result = $this->db->query($sql);
        if (!$this->db->isResultSet($result)) {
            return $ret;
        }
        while (false !== ($row = $this->db->fetchArray($result))) {
            $ret[] = $row;
        }

        return $ret;
    }

    public function getCount(string $sql): int
    {
        if (!$this->db->isResultSet($result = $this->db->query($sql))) {
            return 0;
        }
        [$count] = $this->db->fetchRow($result);

        return (int) $count;
    }

    public function nested(string $sql)
    {
        return $this->db->fetchArray($this->db->query($sql));
    }

    public function unbraced(string $sql)
    {
        $row = null;
        if ($this->db->isResultSet($result = $this->db->query($sql))) $row = $this->db->fetchArray($result);

        return $row;
    }

    public function elseBranch($result): array
    {
        if (!$this->db->isResultSet($result)) {
            return [];
        } else {
            return $this->db->fetchArray($result);
        }
    }
}
"""

F["ResultSetGuard/euser/modules/demo/include/count.php"] = r"""<?php

function demo_count(): int
{
    global $xoopsDB;
    $sql    = 'SELECT COUNT(*) FROM ' . $xoopsDB->prefix('demo_items');
    $result = $xoopsDB->query($sql);
    if (!$xoopsDB->isResultSet($result)) {
        \trigger_error("Query Failed! SQL: $sql- Error: " . $xoopsDB->error(), E_USER_ERROR);
    }
    [$count] = $xoopsDB->fetchRow($result);

    return (int)$count;
}
"""

F["ResultSetGuard/nofix/modules/demo/class/N.php"] = r"""<?php

class N
{
    public $db;

    public function row(string $sql): Row
    {
        $result = $this->db->query($sql);
        $data = $this->db->fetchArray($result);

        return new Row($data);
    }

    public function later(string $sql)
    {
        $result = $this->db->query($sql);
        $row = $this->db->fetchArray($result);

        return $row;
    }
}
"""
F["ResultSetGuard/nofix/modules/demo/class/N.after.php"] = r"""<?php

class N
{
    public $db;

    public function row(string $sql): Row
    {
        $result = $this->db->query($sql);
        $data = $this->db->fetchArray($result);

        return new Row($data);
    }

    public function later(string $sql)
    {
        $result = $this->db->query($sql);
        if (!$this->db->isResultSet($result)) {
            return null;
        }
        $row = $this->db->fetchArray($result);

        return $row;
    }
}
"""

F["ResultSetGuard/comment/modules/demo/include/notification.inc.php"] = r"""<?php

function demo_notify_iteminfo($category, $item_id)
{
    $sql = 'SELECT name FROM ' . $GLOBALS['xoopsDB']->prefix('demo_items') . ' WHERE id = ' . (int) $item_id;
    $result       = $GLOBALS['xoopsDB']->query($sql); // TODO: error check
    $resultArray  = $GLOBALS['xoopsDB']->fetchArray($result);
    $item['name'] = $resultArray['name'];

    return $item;
}
"""
F["ResultSetGuard/comment/modules/demo/include/notification.inc.after.php"] = r"""<?php

function demo_notify_iteminfo($category, $item_id)
{
    $sql = 'SELECT name FROM ' . $GLOBALS['xoopsDB']->prefix('demo_items') . ' WHERE id = ' . (int) $item_id;
    $result       = $GLOBALS['xoopsDB']->query($sql); // TODO: error check
    if (!$GLOBALS['xoopsDB']->isResultSet($result)) {
        return null;
    }
    $resultArray  = $GLOBALS['xoopsDB']->fetchArray($result);
    $item['name'] = $resultArray['name'];

    return $item;
}
"""

# --- 0.5.0: file header reads the whole head ---------------------------------------------

F["FileHeader/secondblock/modules/demo/class/Helper.php"] = r"""<?php declare(strict_types=1);

namespace XoopsModules\Demo;

/*
 You may not change or alter any portion of this comment or credits
 of supporting developers from this source code or any supporting source code
 which is considered copyrighted (c) material of the original comment or credit authors.
 */
/**
 * Demo helper
 *
 * @copyright       2000-2026 XOOPS Project (https://xoops.org)
 * @license         https://www.fsf.org/copyleft/gpl.html GNU public license
 */
class Helper
{
}
"""
F["FileHeader/inlinehtml/modules/demo/class/Snippet.php"] = r"""<?php $title = 'x'; ?>
<h1><?= $title ?></h1>
"""
F["FileHeader/inlinehtml/modules/demo/class/Snippet.after.php"] = r"""<?php
/**
 * @copyright   XOOPS Project (https://xoops.org)
 * @license     GPL-2.0-or-later (https://www.gnu.org/licenses/gpl-2.0.html)
 */ $title = 'x'; ?>
<h1><?= $title ?></h1>
"""
F["FileHeader/ccby/modules/demo/include/ajax_rating.php"] = r"""<?php
// Ajax Rating script by Example Author
// Licensed under Creative Commons Attribution 3.0
echo 'ok';
"""
F["FileHeader/assets/modules/demo/assets/js/uploader.php"] = r"""<?php
echo 'third party';
"""

# --- 0.5.0: DTYPE-aware attribute escape ---------------------------------------------------

F["AttributeEscape/dtype/modules/demo/class/Mime.php"] = r"""<?php

class Mime extends XoopsObject
{
    public function __construct()
    {
        $this->initVar('mime_id', XOBJ_DTYPE_INT, null, false);
        $this->initVar('mime_ext', XOBJ_DTYPE_TXTBOX, null, true, 60);
        $this->initVar('mime_raw', XOBJ_DTYPE_OTHER, null, false);
    }
}
"""
F["AttributeEscape/dtype/modules/demo/class/MimeUtil.php"] = r"""<?php

class MimeUtil
{
    public static function row(): string
    {
        $m = new Mime();
        $out = "<a href='mimetypes.php?op=edit&amp;id=" . $m->getVar('mime_id') . "'>edit</a>";
        $out .= "<input type='text' value='" . $m->getVar('mime_ext') . "'>";
        $out .= "<input type='text' value='" . $m->getVar('mime_raw') . "'>";
        $out .= "<input type='text' value='" . $m->getVar('mime_ext', 'n') . "'>";
        $out .= "<a href='x.php?ext=" . $m->getVar('mime_ext') . "'>ext</a>";
        $sql = "UPDATE t SET mime_ext='" . $m->getVar('mime_ext', 'n') . "', title='" . $m->getVar('mime_raw') . "'";

        return $out . $sql;
    }
}
"""
F["AttributeEscape/dtype/modules/demo/class/MimeUtil.after.php"] = r"""<?php

class MimeUtil
{
    public static function row(): string
    {
        $m = new Mime();
        $out = "<a href='mimetypes.php?op=edit&amp;id=" . $m->getVar('mime_id') . "'>edit</a>";
        $out .= "<input type='text' value='" . $m->getVar('mime_ext') . "'>";
        $out .= "<input type='text' value='" . htmlspecialchars((string) $m->getVar('mime_raw'), ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8') . "'>";
        $out .= "<input type='text' value='" . htmlspecialchars((string) $m->getVar('mime_ext', 'n'), ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8') . "'>";
        $out .= "<a href='x.php?ext=" . rawurlencode((string) $m->getVar('mime_ext', 'n')) . "'>ext</a>";
        $sql = "UPDATE t SET mime_ext='" . $m->getVar('mime_ext', 'n') . "', title='" . $m->getVar('mime_raw') . "'";

        return $out . $sql;
    }
}
"""

# --- 0.5.0: foreign db: templates ----------------------------------------------------------

F["Templates/foreign/modules/publisher-copy/xoops_version.php"] = r"""<?php
$modversion['dirname'] = 'publisher';
$modversion['templates'][] = ['file' => 'publisher_item.tpl', 'description' => ''];
$modversion['templates'][] = ['file' => 'publisher_index.tpl', 'description' => ''];
"""
F["Templates/foreign/modules/publisher-copy/templates/publisher_item.tpl"] = r"""<div class="item">
    <{include file="db:tag_bar.tpl"}>
    <{include file="db:publisher_ghost.tpl"}>
</div>
"""
F["Templates/foreign/modules/publisher-copy/templates/publisher_index.tpl"] = "<div>index</div>\n"

# --- 0.5.0: new rules ----------------------------------------------------------------------

F["DynamicCode/dyn/modules/demo/admin/preferences.php"] = r"""<?php
require __DIR__ . '/admin_header.php';
foreach ($_POST as $k => $v) {
    ${$k} = $v;
}
$name = 'x';
$$name = 1;
extract($_POST);
$ok = "{$name}";
"""

F["OrderBy/sort/modules/demo/class/H.php"] = r"""<?php

use Xmf\Request;

class H
{
    public $db;

    public function sql($criteria): string
    {
        $sql = 'SELECT * FROM t';
        $sql .= ' ORDER BY ' . $criteria->getSort() . ' ' . $criteria->getOrder();
        $criteria->setSort(Request::getString('sort', 'id'));
        $criteria->setOrder('ASC');

        return $sql;
    }
}
"""

F["LegacySuperglobal/old/modules/demo/index.php"] = r"""<?php
include '../../mainfile.php';
$id = (int) $HTTP_GET_VARS['id'];
"""
F["LegacySuperglobal/old/modules/demo/index.after.php"] = r"""<?php
include '../../mainfile.php';
$id = (int) $_GET['id'];
"""

F["DeprecatedCoreApi/old/modules/demo/class/Old.php"] = r"""<?php

class Old extends XoopsObject
{
    public function __construct()
    {
        parent::XoopsObject();
        $this->initVar('title', XOBJ_DTYPE_UNICODE_TXTBOX, null, true, 255);
        $this->destoryVars('junk');
    }

    public function render($tpl)
    {
        $tree = new XoopsTree('t', 'id', 'pid');
        $tpl->assign_by_ref('tree', $tree);
    }
}
"""
F["DeprecatedCoreApi/old/modules/demo/class/Old.after.php"] = r"""<?php

class Old extends XoopsObject
{
    public function __construct()
    {
        parent::__construct();
        $this->initVar('title', XOBJ_DTYPE_UNICODE_TXTBOX, null, true, 255);
        $this->destroyVars('junk');
    }

    public function render($tpl)
    {
        $tree = new XoopsTree('t', 'id', 'pid');
        $tpl->assign('tree', $tree);
    }
}
"""

F["Smarty/delims/modules/demo/xoops_version.php"] = "<?php\n$modversion['name'] = 'Demo';\n"
F["Smarty/delims/modules/demo/templates/demo_x.tpl"] = r"""<style>a{color:red} .x {display:none}</style>
{if $x}
<{$y}>
<p>{$z|escape}</p>
<{* {if $hidden} *}>
<{literal}>{if}<{/literal}>
<script>if (a) { b(); }</script>
<script>function f(){if(x){run();}}</script>
<p>{if(x)}</p>
<input type="checkbox" <{if $on}>checked{/if}>>
"""
F["Smarty/delims/modules/demo/templates/demo_x.after.tpl"] = r"""<style>a{color:red} .x {display:none}</style>
<{if $x}>
<{$y}>
<p><{$z|escape}></p>
<{* {if $hidden} *}>
<{literal}>{if}<{/literal}>
<script>if (a) { b(); }</script>
<script>function f(){if(x){run();}}</script>
<p>{if(x)}</p>
<input type="checkbox" <{if $on}>checked<{/if}>>>
"""

F["Smarty/escape/modules/demo/xoops_version.php"] = "<?php\n$modversion['name'] = 'Demo';\n"
F["Smarty/escape/modules/demo/templates/demo_y.tpl"] = r"""<input name="op" value="<{$smarty.get.op}>">
<input name="id" value="<{$smarty.get.id|escape}>">
<p><{$smarty.request.q|default:''}></p>
<script>var q = <{$smarty.get.q|json_encode}>;</script>
"""
F["Smarty/escape/modules/demo/templates/demo_y.after.tpl"] = r"""<input name="op" value="<{$smarty.get.op|escape}>">
<input name="id" value="<{$smarty.get.id|escape}>">
<p><{$smarty.request.q|default:''|escape}></p>
<script>var q = <{$smarty.get.q|json_encode}>;</script>
"""


# --- Ported after the 16-module run ---------------------------------------------------

F["DoubleEncodedAmp/redirect/modules/demo/admin/main.php"] = r"""<?php
require __DIR__ . '/admin_header.php';
$adminObject->addItemButton('Add', 'main.php?op=new&amp;type=1', 'add');
redirect_header('index.php?op=list&amp;id=' . $id, 2, 'Saved');
echo '<a href="main.php?op=list&amp;id=1">list</a>';
$adminObject->addItemButton('Fish &amp; Chips', 'index.php', 'list');
header('X-Label: Fish &amp; Chips');
header('Location: view.php?id=1&amp;page=2');
"""
F["DoubleEncodedAmp/redirect/modules/demo/admin/main.after.php"] = r"""<?php
require __DIR__ . '/admin_header.php';
$adminObject->addItemButton('Add', 'main.php?op=new&type=1', 'add');
redirect_header('index.php?op=list&id=' . $id, 2, 'Saved');
echo '<a href="main.php?op=list&amp;id=1">list</a>';
$adminObject->addItemButton('Fish &amp; Chips', 'index.php', 'list');
header('X-Label: Fish &amp; Chips');
header('Location: view.php?id=1&page=2');
"""

F["StrictCalendarCast/strict/modules/demo/class/Utility.php"] = r"""<?php

declare(strict_types=1);

class Utility
{
    public static function monthStart(): int
    {
        return mktime(0, 0, 0, date('n'), (int) date('j'), date('Y')) + idate(substr('Y', 0, 1), 0);
    }
}
"""
F["StrictCalendarCast/strict/modules/demo/class/Utility.after.php"] = r"""<?php

declare(strict_types=1);

class Utility
{
    public static function monthStart(): int
    {
        return mktime(0, 0, 0, (int) date('n'), (int) date('j'), (int) date('Y')) + idate(substr('Y', 0, 1), 0);
    }
}
"""
F["StrictCalendarCast/loose/modules/demo/class/Loose.php"] = r"""<?php

class Loose
{
    public static function monthStart(): int
    {
        return mktime(0, 0, 0, date('n'), 1, date('Y'));
    }
}
"""

F["LocaleConstant/missing/modules/demo/xoops_version.php"] = "<?php\n$modversion['name'] = 'Demo';\n"
F["LocaleConstant/missing/modules/demo/language/english/admin.php"] = r"""<?php
define('_AM_DEMO_OK', 'Fine');
"""
F["LocaleConstant/missing/modules/demo/admin/index.php"] = r"""<?php
require __DIR__ . '/admin_header.php';
echo _AM_DEMO_OK;
echo _AM_DEMO_MISSING;
echo _AM_SYSTEM_SOMETHING;
echo _MD_DEMO_ALSO_MISSING;
echo defined('_AM_DEMO_OPTIONAL') ? _AM_DEMO_OPTIONAL : 'fallback';
"""

F["DuplicateDefine/dup/modules/demo/language/english/admin.php"] = r"""<?php
define('_AM_DEMO_A', 'a');
define('_AM_DEMO_A', 'a');
define('_AM_DEMO_B', 'b');
define('_AM_DEMO_B', 'c');
if ($rtl) {
    define('_AM_DEMO_C', 'c');
} else {
    define('_AM_DEMO_C', 'c');
}
"""
F["DuplicateDefine/dup/modules/demo/language/english/admin.after.php"] = r"""<?php
define('_AM_DEMO_A', 'a');
define('_AM_DEMO_B', 'b');
define('_AM_DEMO_B', 'c');
if ($rtl) {
    define('_AM_DEMO_C', 'c');
} else {
    define('_AM_DEMO_C', 'c');
}
"""

F["ThemeConstant/theme/modules/demo/language/english/main.php"] = r"""<?php
define('_MD_DEMO_HELLO', 'Hello');
define('THEME_LIKE', 'Like');
defined('THEME_INFO') || define('THEME_INFO', 'Info');
"""

F["MenuAutoload/menu/modules/demo/xoops_version.php"] = "<?php\n$modversion['name'] = 'Demo';\n"
F["MenuAutoload/menu/modules/demo/preloads/autoloader.php"] = "<?php\nspl_autoload_register(static function (string $class): void {});\n"
F["MenuAutoload/menu/modules/demo/class/Helper.php"] = r"""<?php

namespace XoopsModules\Demo;

class Helper
{
    public static function getInstance(): self
    {
        return new self();
    }
}
"""
F["MenuAutoload/menu/modules/demo/admin/menu.php"] = r"""<?php

use XoopsModules\Demo\Helper;

$helper = Helper::getInstance();
$adminmenu   = [];
$adminmenu[] = ['title' => 'Home', 'link' => 'admin/index.php'];
"""
F["MenuAutoload/menu/modules/demo/admin/menu.after.php"] = r"""<?php

use XoopsModules\Demo\Helper;

require_once \dirname(__DIR__) . '/preloads/autoloader.php';
$helper = Helper::getInstance();
$adminmenu   = [];
$adminmenu[] = ['title' => 'Home', 'link' => 'admin/index.php'];
"""
F["MenuAutoload/classconst/modules/demo/xoops_version.php"] = "<?php\n$modversion['name'] = 'Demo';\n"
F["MenuAutoload/classconst/modules/demo/preloads/autoloader.php"] = "<?php\nspl_autoload_register(static function (string $class): void {});\n"
F["MenuAutoload/classconst/modules/demo/admin/menu.php"] = r"""<?php

$name = \XoopsModules\Demo\Helper::class;
$adminmenu = [];
"""
F["MenuAutoload/ok/modules/demo/xoops_version.php"] = "<?php\n$modversion['name'] = 'Demo';\n"
F["MenuAutoload/ok/modules/demo/preloads/autoloader.php"] = "<?php\nspl_autoload_register(static function (string $class): void {});\n"
F["MenuAutoload/ok/modules/demo/admin/menu.php"] = r"""<?php

use XoopsModules\Demo\Helper;

require_once \dirname(__DIR__) . '/preloads/autoloader.php';
$helper = Helper::getInstance();
"""

F["HandlerSignature/handler/modules/demo/class/ItemHandler.php"] = r"""<?php

class ItemHandler extends XoopsPersistableObjectHandler
{
    public function getObjects(?CriteriaElement $criteria = null, $id_as_key = false, $as_object = true)
    {
        $ret = [];
        return $ret;
    }

    public function &getAll(?CriteriaElement $criteria = null, $fields = null, $asObject = true, $id_as_key = true)
    {
        $ret = [];
        return $ret;
    }
}

class PlainHandler extends XoopsObjectHandler
{
    public function getAll()
    {
        return [];
    }
}
"""

F["SqlConcat/concat/modules/demo/include/search.php"] = r"""<?php

use Xmf\Request;

$sql    = 'SELECT * FROM ' . $xoopsDB->prefix('demo_items') . ' WHERE id=' . $_GET['id'];
$result = $xoopsDB->query($sql);
$xoopsDB->exec('DELETE FROM ' . $xoopsDB->prefix('demo_items') . ' WHERE id=' . (int)$_GET['id']);
$xoopsDB->query('SELECT 1 FROM x WHERE name=' . $xoopsDB->quote(Request::getString('n', '', 'GET')));
$xoopsDB->query("SELECT 1 FROM x WHERE name='{$_POST['name']}'");
$xoopsDB->query('SELECT * FROM t LIMIT ' . count($_POST['ids']));
"""

F["SqlComment/sql/modules/demo/xoops_version.php"] = "<?php\n$modversion['name'] = 'Demo';\n$modversion['sqlfile']['mysql'] = 'sql/mysql.sql';\n"
F["SqlComment/sql/modules/demo/sql/mysql.sql"] = """-- ok comment
--second bad
--bad comment
# hash comment
CREATE TABLE demo_items (
  id INT NOT NULL
);
"""
F["SqlComment/sql/modules/demo/sql/mysql.after.sql"] = """-- ok comment
-- second bad
-- bad comment
# hash comment
CREATE TABLE demo_items (
  id INT NOT NULL
);
"""

for rel, content in F.items():
    path = ROOT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8", newline="\n")
print(f"wrote {len(F)} fixture files under {ROOT}")
