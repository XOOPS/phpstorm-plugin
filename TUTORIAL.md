# XOOPS Support Tutorial

A feature-by-feature walkthrough of the XOOPS Support plugin, with the exact
steps to try each feature and what you should see. Version covered: 0.6.1.

## 1. Install

1. Build the zip (`./gradlew buildPlugin`) or take
   `build/distributions/xoops-support-1.0.0-alpha.4.zip`.
2. PhpStorm → **Settings → Plugins** → gear icon → **Install Plugin from
   Disk…** → select the zip → **Restart IDE**.
3. After restart, **Settings → Plugins → Installed** shows "XOOPS Support
   ". Its **What's New** tab renders the changelog.

## 2. Project detection

**Try it:** Open a XOOPS tree — either a full install (`mainfile.php` at the
project root or under `htdocs/`) or a standalone module (a directory with
`xoops_version.php` at its root).

**You should see:** a balloon "XOOPS install detected / XOOPS 2.x.y". The
version is read from `include/version.php`. Click **Don't show again** to
silence it (stored in `.idea/xoopsSupport.xml`).

## 3. The XOOPS tool window

Click **XOOPS Support** in the right-hand sidebar: module overview (dirname,
manifests, template/language counts) and filesystem-level findings (registered
template missing on disk, standard Smarty `{ }` delimiters). Click a finding to
jump to it.

## 4. Inspections

All live under **Settings → Editor → Inspections → PHP → XOOPS**. The fastest
tour is the fixture tree the test-suite uses: copy
`src/test/resources/fixtures/*/*/modules/demo` into any project's `modules/`
and open the files — every `.php` next to a `.after.php` shows a highlighted
problem whose Alt+Enter fix produces exactly the `.after.php` content.

The complete list, with every rule's default level, what it reports, what its fix
changes and when it stays silent, is **[INSPECTIONS.md](INSPECTIONS.md)**. This
section walks through the rules that matter most, with what changed against the
old text-based plugin:

### 4.1 XOOPS_ROOT_PATH guard

**Fires on:** an include-only file (class, preload, include, block) with no
`defined('XOOPS_ROOT_PATH') || exit('Restricted access');`.

**Never fires on:** a file that bootstraps the core — any top-level `require`
of `mainfile.php`, `header.php`, `cp_header.php`, `admin_header.php`,
`print.php` …, even after `error_reporting()` or flag assignments;
`preloads/autoloader.php`; language files; 404 stubs; a file already guarded
with `\defined(...)`.

**Fix:** inserts the guard after `namespace` / `declare`, before `use` or the
class docblock — the docblock stays attached to its class.

### 4.2 Second xoops_cp_header()

**Fires on:** `xoops_cp_header()` after `require admin_header.php` **only when
that sibling file itself calls `xoops_cp_header()`**. Publisher-style modules
whose header file only loads `cp_header.php` keep their call. The delete fix
is not offered in "Fix all".

### 4.3 Deprecated database API

**Fires on:** `->queryF(…)`, `->quoteString(…)`.

**Fix:** follows the SQL. `queryF($sql)` with `$sql = 'UPDATE …'` two lines up
becomes `exec($sql)`; with a SELECT it becomes `query($sql)`; inside
`if ($force)` it is a write. A `$sql` that comes from a parameter gets the
warning and **no fix** — `query()` would refuse the write on GET, which is how
the old plugin killed the item counter.

### 4.4 Mutating SQL passed to query()

**Fires on:** `->query($sql)` whose SQL resolves to INSERT / UPDATE / DELETE /
DDL. An unresolved `$sql` is not a finding. **Fix:** `exec()`.

### 4.5 fetch*() without isResultSet()

**Fires on:** `fetchArray` / `fetchRow` / `fetchBoth` with no dominating
`isResultSet()`: a positive check in the enclosing `if`/`while` condition
counts, so does `if (!$db->isResultSet($r)) return …;` or `if (!$r) return …;`
in a preceding statement, until `$r` is reassigned.

**Reversed condition:** a fetch inside `if (!$db->isResultSet($r)) { … }` gets
its own message (that is a bug in the module, not a missing guard).

**Fix:** inserts, with the right indentation,

```php
if (!$this->db->isResultSet($result)) {
    return 0;          // from the function's return type or its existing failure return
}
```

### 4.6 Accessor in an HTML attribute

**Fires on:** `$obj->getVar('x')` concatenated after a string literal that
leaves an attribute open (`'<a href="' . $obj->getVar('url')`). PHP code such
as `$title = …`, array keys and link text are never attribute context. Inside
`'?src=' . …` the context is a URL.

**Fix:** `htmlspecialchars((string) $images['main']->getVar('x'), ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8')`
— the whole call, receiver included — or `rawurlencode((string) …)` in a URL.
Applying it twice changes nothing.

### 4.7 Superglobals

`$_POST['id']` → `Request::getString('id', '', 'POST')`;
`isset($_POST['new'])` → `Request::hasVar('new', 'POST')` (the `use Xmf\Request`
alias is reused when present). A write (`$_GET['x'] = …`), `empty()`,
`null !== $_POST['x']`, a dynamic key, `foreach ($_POST …)` and `$_REQUEST` are
reported without a fix. Vendored code under `assets/` is skipped.

### 4.8 The rest

Form loader (statement before the class docblock, parent-class load accepted),
file header (host file only; third-party banners noted, not rewritten),
`unserialize()` (`allowed_classes`), `isset($xoopsUser)`, `unlink()` without
`is_file()`, `setcookie()`, `header('HTTP/1.0 404')`, by-reference arguments
(resolved through the real parent), CSRF switch branches (content, not label;
a POST-only gate does not cover a GET `$op`), language catalog roles (one note
per stray `modinfo1.php`), language loading (helper `loadLanguage()` counts),
and the two template checks (re-point a misplaced registration instead of
creating a stub; skip `system_*`, other modules and Smarty comments).

### 4.9 New in 0.5.0 — try these on an old module

1. **Receiver proof.** Open a class with its own `query()` wrapper (Publisher's
   `class/Versioncontrol/VersionControlMySQL.php`): `$this->query('UPDATE …')` is
   not reported; `$this->xoopsDB->queryF($sql)` is, with the `exec()` fix.
2. **Legacy guards.** `if (!$result) { return $ret; }` before a fetch gets a weak
   note; Alt+Enter rewrites it in place to `if (!$this->db->isResultSet($result))`.
   `$db->fetchArray($db->query($sql))` (query nested in the fetch) is reported
   without a fix.
3. **DTYPE-aware escaping.** `value='" . $obj->getVar('mime_id')` (an
   `XOBJ_DTYPE_INT` field) is silent; `getVar('x', 'n')` or an `XOBJ_DTYPE_OTHER`
   field in an attribute is a warning with the `htmlspecialchars()` fix.
4. **Dynamic code and ORDER BY.** `${$k} = $v`, `extract($_POST)`, `eval()` are
   errors; `' ORDER BY ' . $criteria->getSort()` and `setSort(Request::…)` are
   warnings. Neither has a fix on purpose.
5. **Deprecated core API.** `parent::XoopsObject()` → `parent::__construct()`,
   `destoryVars()` → `destroyVars()`, `$tpl->assign_by_ref()` → `assignByRef()`
   with Alt+Enter; `new XoopsTree`, `XOBJ_DTYPE_UNICODE_*`,
   `xoops_getModuleOption()` and the `makeTboxData4*` family are explained,
   not rewritten.
6. **`$HTTP_POST_VARS`** is an error with a rename to `$_POST`.
7. **Templates.** `{if $x}` or `{$var}` in a `.tpl` is an error with a
   "Wrap in <{ }>" fix; `<{$smarty.get.op}>` gets "Add |escape".
8. **Scope.** `assets/uploader.php` reading `$_POST` is reported (security
   rules read bundled code); it gets no header, guard or status-line note
   (convention rules skip it). Anything under `assets/js/` is not inspected
   at all, nor are `tests/` and `vendor/`. `db:tag_bar.tpl` inside Publisher
   is silent.

## 5. Language-constant completion, 6. Live templates

Unchanged from 0.3: type `_MI_` / `_AM_` / `_MD_` in a PHP file for completion
from every `language/` directory; `xoguard`, `xofetch`, `xolang`, `xocriteria`,
`xorequest` + Tab.

## 7. Running the tests

```
./gradlew test
```

`XoopsQuickFixTest` copies each fixture tree into a light project, highlights
the target file, asserts the problem count, applies every quick-fix of the
family through the IDE (re-computed after each one, as a batch run does),
compares the whole file with `*.after.php`, and asserts that a second pass
offers no further fix. `PluginDescriptorTest` checks that every registered
inspection instantiates, matches its `shortName` and has a description page.

## 8. Troubleshooting

- **Inspections not firing** — the file must be a PHP file inside the project
  content root and not under `vendor/`, `assets/`, `cache/`, `templates_c/`.
- **A fix does nothing** — every fix re-checks its precondition at apply time
  and declines silently when an earlier edit already satisfied it; re-run
  highlighting.
- **After editing inspection code:** `shortName` in `plugin.xml` must equal the
  class's simple name minus `Inspection`, and `inspectionDescriptions/<shortName>.html`
  must exist — `PluginDescriptorTest` catches both.
