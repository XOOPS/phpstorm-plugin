# XOOPS Support inspections

Every inspection the plugin registers (52 in 1.0.0 Alpha 5), what it reports, what its quick-fix changes, and when it
stays silent. All of them live under **Settings → Editor → Inspections → PHP → XOOPS** and can be switched off or
re-levelled there; the levels below are the defaults. The same text, one page per inspection, is what PhpStorm shows
in the inspection's description pane.

Conventions used throughout:

- **Report only** means the finding has no quick-fix, because the right change depends on a decision the author must
  make (which column to allow-list, whether a table is old enough to need no update path).
- A fix that is **Alt+Enter only** is offered in the editor but excluded from "Fix all" and the batch Inspect Code
  run, because applying it blindly can change behaviour.
- **Never inspected** by any rule: test trees (`tests/`, `test/`, `testdata/`, `fixtures/`) and vendored,
  generated or scratch trees (`vendor/`, `templates_c/`, `cache/`, `uploads/`, `xoops_data/`, `node_modules/`,
  `assets/js/`, `jpgraph`, `00/`).
- **Bundled third-party code** (`assets/`, `libraries/`, `lib/`, `js/`, `jquery/`, `ckeditor/`, `tinymce/`,
  `pear/`) is read by the security rules and skipped by the convention rules (file header, root-path guard,
  `xoops_setcookie()`, status line).
- **Core version** gating reads `include/version.php` of the project, or the Core Version setting when the module is
  opened standalone. Rules that only matter on core 2.8 (Smarty 5) stay silent on a 2.5 / 2.7 tree.

## Index

| Short name | Level | Default | Quick-fix | Section |
|---|---|---|---|---|
| XoopsAllowlistAnchor | Warning | on | yes | [Security](#security) |
| XoopsAttributeEscape | Warning | on | yes | [Security](#security) |
| XoopsCsrf | Warning | on | yes | [Security](#security) |
| XoopsDynamicCode | Error | on | no | [Security](#security) |
| XoopsGuestUser | Warning | on | yes | [Security](#security) |
| XoopsLegacySuperglobal | Error | on | yes | [Security](#security) |
| XoopsOrderBy | Warning | on | no | [Security](#security) |
| XoopsPathContainment | Warning | on | no | [Security](#security) |
| XoopsSmartyRequestEscape | Warning | on | yes | [Security](#security) |
| XoopsSqlConcat | Warning | on | no | [Security](#security) |
| XoopsSuperglobal | Weak warning | on | yes | [Security](#security) |
| XoopsUnserialize | Warning | on | yes | [Security](#security) |
| XoopsWeakToken | Warning | on | yes | [Security](#security) |
| XoopsDeprecatedDbApi | Warning | on | yes | [Database](#database) |
| XoopsInstallSql | Warning | on | no | [Database](#database) |
| XoopsQueryExec | Warning | on | yes | [Database](#database) |
| XoopsResultSetGuard | Weak warning | on | yes | [Database](#database) |
| XoopsSqlComment | Error | on | yes | [Database](#database) |
| XoopsSqlFileComment | Error | on | yes | [Database](#database) |
| XoopsUpdateCreatesTable | Warning | on | no | [Database](#database) |
| XoopsByRefArgument | Warning | on | no | [Module files and bootstrap](#module-files-and-bootstrap) |
| XoopsDoubleCpHeader | Error | on | Alt+Enter only | [Module files and bootstrap](#module-files-and-bootstrap) |
| XoopsFileHeader | Weak warning | on | yes | [Module files and bootstrap](#module-files-and-bootstrap) |
| XoopsFormLoader | Warning | on | yes | [Module files and bootstrap](#module-files-and-bootstrap) |
| XoopsHandlerSignature | Error | on | no | [Module files and bootstrap](#module-files-and-bootstrap) |
| XoopsHttpStatus | Weak warning | on | yes | [Module files and bootstrap](#module-files-and-bootstrap) |
| XoopsIncludeOnceHeader | Weak warning | on | yes | [Module files and bootstrap](#module-files-and-bootstrap) |
| XoopsIsFileAsset | Weak warning | on | yes | [Module files and bootstrap](#module-files-and-bootstrap) |
| XoopsLocalXoopsOption | Warning | on | yes | [Module files and bootstrap](#module-files-and-bootstrap) |
| XoopsMenuAutoload | Error | on | yes | [Module files and bootstrap](#module-files-and-bootstrap) |
| XoopsPreloadGlobal | Warning | on | no | [Module files and bootstrap](#module-files-and-bootstrap) |
| XoopsRootPathGuard | Weak warning | on | yes | [Module files and bootstrap](#module-files-and-bootstrap) |
| XoopsSetcookie | Weak warning | on | yes | [Module files and bootstrap](#module-files-and-bootstrap) |
| XoopsStrictCalendarCast | Warning | on | yes | [Module files and bootstrap](#module-files-and-bootstrap) |
| XoopsUnlink | Weak warning | on | no | [Module files and bootstrap](#module-files-and-bootstrap) |
| XoopsMissingRegisteredTemplate | Warning | on | yes | [Templates and Smarty](#templates-and-smarty) |
| XoopsSmarty5Api | Warning | on | yes | [Templates and Smarty](#templates-and-smarty) |
| XoopsSmarty5TemplatePrep | Weak warning | **off** | no | [Templates and Smarty](#templates-and-smarty) |
| XoopsSmartyBlockShorthand | Warning | on | yes | [Templates and Smarty](#templates-and-smarty) |
| XoopsSmartyDateFormatStrftime | Warning | on | yes | [Templates and Smarty](#templates-and-smarty) |
| XoopsSmartyForeachItemShadowsFrom | Error | on | yes | [Templates and Smarty](#templates-and-smarty) |
| XoopsSmartyRemovedTag | Error | on | yes | [Templates and Smarty](#templates-and-smarty) |
| XoopsSmartyUnquotedPluginArg | Error | on | yes | [Templates and Smarty](#templates-and-smarty) |
| XoopsUnregisteredTemplate | Weak warning | on | Alt+Enter only | [Templates and Smarty](#templates-and-smarty) |
| XoopsWrongSmartyDelimiter | Error | on | yes | [Templates and Smarty](#templates-and-smarty) |
| XoopsDuplicateDefine | Warning | on | yes | [Language files](#language-files) |
| XoopsLanguageLoad | Weak warning | **off** | no | [Language files](#language-files) |
| XoopsLanguageRole | Weak warning | on | no | [Language files](#language-files) |
| XoopsLocaleConstant | Warning | on | no | [Language files](#language-files) |
| XoopsThemeConstant | Weak warning | on | no | [Language files](#language-files) |
| XoopsDeprecatedCoreApi | Warning | on | yes | [Deprecated APIs](#deprecated-apis) |
| XoopsDoubleEncodedAmp | Error | on | yes | [Deprecated APIs](#deprecated-apis) |

## Security

### XoopsCsrf — Mutating switch branch without CSRF token check
A `switch ($op)` branch that writes (handler insert or delete, `exec()`, file writes) without a CSRF token check. The
branch content decides, never the case label. A branch is covered by its own `$GLOBALS['xoopsSecurity']->check()` or
`xoops_confirm()` (directly, or through a function in the same file that checks and stops), by a top-level gate
before the switch, or by an `if (!$GLOBALS['xoopsSecurity']->check())` before it that exits or resets `$op` for the
branch's value. A gate behind any other condition, in an `else` or a loop covers nothing; a gate that only runs for
POST covers nothing when `$op` is read from GET and the GET path still reaches the switch.
**Fix:** inserts the token check at the top of the branch.

### XoopsSuperglobal — Raw superglobal instead of Xmf\Request
Raw `$_GET` / `$_POST` / `$_COOKIE` reads go through `Xmf\Request`; `$_REQUEST` is banned outright. Checked in any
file that boots XOOPS (it names `xoops`, `mainfile.php` or `Xmf`), `assets/` endpoints included; a bundled script
that never loads the core is skipped. A write, `unset()`, `empty()`, a `=== null` comparison, a dynamic key, a bare
`$_POST` and a dynamic callee (`new $_REQUEST['c']()`) are reported without a fix. An assignment to `$_POST` itself
gets its own message: it changes what every later reader sees, `Xmf\Request` included. Not inspected: Protector (it
rewrites the superglobals before XOOPS boots), the core installer and upgrader (they run before `Xmf\Request` can
load), and test trees.
**Fix:** the accessor is chosen from the use: `getArray()` for `foreach`, `count()`, `[…]`, an array-typed parameter
or an `XOBJ_DTYPE_ARRAY` field; `getInt()` for `(int)`, `intval()` or arithmetic; `getString()` for a literal
comparison, `switch` or array key; `getText()` for concatenation, `echo`, string functions and text fields (it keeps
HTML). `isset()` and a strict null comparison become `hasVar()`; `isset ? : 'd'` and `?? 'd'` keep the default. Every
other read gets the byte-identical raw accessor `getVar(key, null, hash, 'none', MASK_ALLOW_RAW | MASK_NO_TRIM)`.
`$_REQUEST` maps to the library's `'default'` hash. The batch run offers three buttons: reads only, reads and
`isset()`, everything.

### XoopsLegacySuperglobal — $HTTP_POST_VARS and other removed superglobals
`$HTTP_POST_VARS`, `$HTTP_GET_VARS` and the other long arrays were removed in PHP 5.4: the page reads null.
**Fix:** renames the variable to its `$_POST` / `$_GET` counterpart; XoopsSuperglobal then takes over.

### XoopsSqlConcat — Request value concatenated into SQL
`$_GET['id']`, `$_POST[…]`, `$_REQUEST[…]` or `Request::getString()` concatenated into the SQL that reaches `query()`,
`exec()` or `queryF()`, with no `quote()`, `(int)` or `intval()` around it. The `$sql` variable is followed back to
its assignment in the same block. Report only: the right fix (quote, cast or allow-list) depends on the column.

### XoopsOrderBy — ORDER BY built from getSort()/getOrder() or the request
`' ORDER BY ' . $criteria->getSort()` concatenates whatever `setSort()` received; `setSort(Request::getString('sort'))`
hands the column to the client. Quoting does not help: a column is an identifier, not a string. Allow-list the column
or use `XoopsObject::buildOrderBy()`. `getSort()` passed to `buildOrderBy()` is allow-listed and not reported.
Report only.

### XoopsUnserialize — unserialize() without allowed_classes
`unserialize()` with one argument can instantiate arbitrary classes.
**Fix:** adds `['allowed_classes' => false]`; the argument count comes from the PSI, so an inner call with commas is
handled.

### XoopsDynamicCode — Dynamic code: $$name, extract(), eval()
`$$name`, `${$name}`, `extract($_POST)`, `eval()`, `create_function()`. A request array copied into the symbol table
lets the client define any variable. There is no local rewrite, so nothing is offered as a quick-fix.

### XoopsPathContainment — Prefix check used as path containment
`strpos($path, $base) === 0` or `str_starts_with($path, $base)` on a filesystem path is a prefix test, not
containment: `/safe/../outside/secret` starts with `/safe`, and `/uploads2/x` starts with `/uploads`. Not reported: a
`realpath()`'d path (directly, through a local, behind a failure ternary, or through a separator-only
`str_replace()`) against a base ending in a separator; a literal needle (`'/'`, `'www.'`, `'ldaps://'`); a prefix
strip for display; a URL or referer, or a prefix test inside a function that handles URLs. Report only: the fix is
`realpath()` on both sides and a comparison against the directory plus a separator, placed where the path is final.

### XoopsWeakToken — Weak token generation, password hash or secret comparison
Three shapes. A variable named `*token*`, `*nonce*` or `*csrf*` assigned from `rand()`, `mt_rand()` or `uniqid()`:
predictable, use `bin2hex(random_bytes(n))` or the core token API. `md5()` / `sha1()` of a `*pass*` / `*secret*`
variable: not a password hash, use `password_hash()` / `password_verify()` (compared with a stored value it is the
2.2 login fallback and only a weak note). `==`, `!=` or `strcmp()` on a token, secret or password: `'0e123' == '0e456'`
is true and the comparison is not constant-time, use `hash_equals()`. A comparison with a non-numeric literal
(`$pass == ''`) is an emptiness test and is not reported.
**Fix:** only for two request-text operands assigned nowhere else, `!=` becomes `!==` and `==` becomes `===`.
Everything else is report only.

### XoopsAllowlistAnchor — Allowlist regex anchored with $ before an exact comparison
In PCRE, `$` matches before a final newline: `preg_match('/^[a-z]+$/', $name)` accepts `"script\n"`, and the
`in_array()` / `===` comparison that follows does not recognise it. Reported when the pattern is a single-quoted literal
ending in an unescaped `$` without the `D` or `m` modifier, and the subject is compared exactly later in the same
scope with no reassignment in between.
**Fix:** replaces that `$` with `\z`.

### XoopsAttributeEscape — Accessor interpolated into an HTML attribute or URL
`getVar()` is not an escaper: `XOBJ_DTYPE_OTHER` and `SOURCE` fields come back raw, and attribute context needs
`ENT_QUOTES` regardless. Context is decided from the string literal that precedes the accessor in the same
concatenation (`'<a href="' . $obj->getVar('url')`). The DTYPE comes from the class's `initVar()`, or for an untyped
receiver from the one declaration of the key in the module's `class/` or `src/`, else in the core `kernel/`. Numeric
DTYPEs are never reported; TXTBOX, TXTAREA, URL and EMAIL are already escaped by the default format.
**Fix:** `htmlspecialchars(…, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8')` around the whole call, `rawurlencode()` inside
a URL query. Offered only for a raw value (OTHER, SOURCE, format `'n'`); an unresolved DTYPE is a weak note without a
fix, because wrapping an already-escaped value prints `&amp;#039;`.

### XoopsSmartyRequestEscape — $smarty.get / $smarty.request printed without |escape
`<{$smarty.get.x}>`, `$smarty.request`, `$smarty.post`, `$smarty.cookie` and `$smarty.server` print the request into
the page. Without `|escape` that is reflected XSS.
**Fix:** appends `|escape` after any other modifier.

### XoopsGuestUser — isset($xoopsUser) is true for guests
`isset($xoopsUser)` is true for a guest, because the global is `''`. A condition that already joins the `isset` with
`is_object($xoopsUser)` or `instanceof` (or its De Morgan form) is not reported.
**Fix:** `$xoopsUser instanceof \XoopsUser`.

## Database

### XoopsDeprecatedDbApi — Deprecated database API (queryF, quoteString)
`queryF()` and `quoteString()` are deprecated. `queryF()` is the forced-write alias and delegates to `exec()`;
`query()` refuses a mutation on a GET request. Only a XOOPS database receiver is checked, never an application wrapper
and never inside a database class.
**Fix:** follows the SQL, resolved through a single local `$sql = …` assignment when needed: `exec()` for a write,
`query()` for a read, no rewrite when the SQL cannot be resolved. A call inside `if ($force)` is a write by intent.

### XoopsQueryExec — Mutating SQL passed to query() instead of exec()
`query()` with an INSERT / UPDATE / DELETE / DDL statement: the safe database class refuses it on GET and strict mode
warns on POST. Only a statement whose SQL resolves to a write is reported. `$force ? $db->exec($sql) : $db->query($sql)`
is the deliberate GET gate of a non-forced write and is left alone. The same SQL resolution is what makes such a
`query()` count as a write for XoopsCsrf.
**Fix:** `exec()`; `queryF()` when the nearest `include/version.php` says the core is older than 2.5.12, where
`exec()` does not exist yet (the same gate keeps `queryF()` unreported there).

### XoopsResultSetGuard — fetch*() without a dominating isResultSet() guard
`fetchArray()` / `fetchRow()` / `fetchBoth()` on a handle that no `isResultSet()` check dominates. Recognised guards: a
positive check in the enclosing `if` / `while` condition, an early-exit `if (!$db->isResultSet($r))` or `if (!$r)` in a
preceding statement (until the handle is reassigned; `break` / `continue` count), a check in the same expression
(`isResultSet($r) && … ? fetch($r) : false`), and a handle the function receives as a parameter. A fetch inside
`if (!$db->isResultSet($r)) { … }` is a reversed condition and gets its own message, as does `fetchArray($db->query())`.
A legacy guard (`if (!$r)`, `if (!$r = query())`, `false === $r`) is a weak note.
**Fix:** inserts an early return that keeps the enclosing function's failure contract (`[]`, `0`, `false`, `null`,
`return;` for void), never a throw; a legacy guard is rewritten in place to `isResultSet()`. On a core older than
2.5.11, which has no `isResultSet()`, the inserted guard is `if (!$result)`.

### XoopsSqlComment — Bare -- comment in an install SQL file
Checked from the `$modversion['sqlfile']` entry in `xoops_version.php`: the installer treats a line of the SQL file as
a comment only when it starts with `-- ` (dash, dash, space) or `#`. A bare `--` or `--text` stays glued to the next
statement, the CREATE TABLE prefixer fails, and that table is silently never created.
**Fix:** inserts the space in the SQL file.

### XoopsSqlFileComment — Bare '--' comment line in installer SQL
The same defect reported on the SQL file itself, one finding per line, for the file the installer runs: the file an
active `sqlfile` entry names (resolved from the module root, so `sql/english/install.sql` counts; `sql/mysql.sql`
when the manifest names none), or the core's own `install/sql/` schema. Dumps and old upgrade scripts in a
module's `sql/` folder are skipped, as is a file only a comment mentions.
**Fix:** inserts the space on that line only, re-checking the live line text, so it is safe in any batch order. A line
that is exactly `--` is blanked rather than given a trailing space, which "strip trailing spaces on save" would remove
again.

### XoopsInstallSql — Installer SQL the installer cannot run
Rules on the file the installer runs, chosen as for XoopsSqlFileComment above. `FOREIGN KEY … REFERENCES`: `SqlUtility::prefixQuery()` prefixes only the
table being created, never the REFERENCES target, so the key dangles; a plain INSERT fails with 1452, an INSERT IGNORE
silently writes nothing. Hard-coded prefix: `xoops_`, `{prefix}` and `%s` are not installer tokens. `ENGINE=MyISAM`
(weak): no transactions, no row locks; InnoDB has supported FULLTEXT since MySQL 5.6. Not reported on the core
installer schema under `install/sql/`. Report only.

### XoopsUpdateCreatesTable — CREATE TABLE that no module update path creates
XOOPS runs `sql/mysql.sql` only on a fresh install. When a site updates a module it already has, that file is not
run again, so a table added in a later version must also be created by the module's update code.

Example: version 1.0 ships tables `a` and `b`, and version 1.1 adds table `c` to `mysql.sql`. A new install of 1.1
gets all three. A site updating from 1.0 still has only `a` and `b`, and the first page that uses `c` fails with MySQL
error 1146 (table doesn't exist). xnewsletter's `include/onupdate.php` shows the fix: one update step per version that
added a table, each creating it if it is missing.

This note marks every `CREATE TABLE` that no update code mentions. The plugin cannot tell whether the table is new.
If it has existed since the module's first release, every site already has it and the note can be ignored. If it was
added later, add a create-if-missing step to the update code. That is why it is only a weak note.

Update paths searched:
`onupdate.php`, `include/onupdate.php`, `include/update.php`, `include/install.php` and the YAML files directly in `sql/`. An
update that re-runs the SQL file or calls `createMissingTables()` / `synchronizeSchema()` covers every table. A module
with none of these files is not reported, nor is an SQL file the installer never runs (`sqlfile` commented out or
`false`). A weak note, report only.

## Module files and bootstrap

### XoopsRootPathGuard — Include-only file without XOOPS_ROOT_PATH guard
Include-only PHP files (classes, preloads, includes, blocks) start with
`defined('XOOPS_ROOT_PATH') || exit('Restricted access');`. A file that bootstraps the core itself (any top-level
require of `mainfile.php`, `header.php`, `cp_header.php`, `admin_header.php`, `print.php`) is a public entry point and
is never reported. `preloads/autoloader.php`, language files, tests, 404 stubs and `\defined()` guards are recognised.
**Fix:** inserts the guard after `namespace` / `declare` and before `use` or the class docblock, on its own line.

### XoopsMenuAutoload — admin/menu.php uses a module class before the autoloader
`admin/menu.php` is read by the system module's menu builder before the module's own bootstrap: a
`XoopsModules\Module\Helper::getInstance()` there is "Class not found" unless the file requires
`preloads/autoloader.php` first. `use` imports, `::class` and type hints do not load the class and stay silent.
**Fix:** inserts `require_once \dirname(__DIR__) . '/preloads/autoloader.php';` on its own line directly above the
first statement that uses the class (after `declare(strict_types=1)`, the header comments and the `use` block), when
that autoloader exists.

### XoopsFormLoader — XoopsForm subclass without the form loader
`XoopsForm*` classes are not autoloaded; a file whose class extends one of them needs `xoops_load('XoopsFormLoader')`
(or a load of exactly the parent class) before the `class` keyword. The core's own `class/xoopsform/` is not reported.
**Fix:** inserts the statement in front of the class and its docblock.

### XoopsDoubleCpHeader — Second xoops_cp_header() on one request
A second `xoops_cp_header()` on one request breaks the admin menu. The sibling `admin_header.php` is read: only when
that file really calls `xoops_cp_header()` is the page's own call a duplicate. Without an `admin_header.php`, a later
call duplicates the page's own first call only when that first call is a top-level statement; one call in each branch
of an `if` / `else` renders once.
**Fix:** deletes the statement, Alt+Enter only, and only for the `admin_header.php` case when `$xoopsOption` does
not change on the way to the second call.

### XoopsIncludeOnceHeader — include of header.php / footer.php instead of include_once
`include` or `require` of `header.php` / `footer.php` without the `_once` form. When the same request reaches the
core header twice through different include paths, PHP stops with a "cannot redeclare" fatal.
**Fix:** renames the keyword to `include_once` / `require_once`.

### XoopsLocalXoopsOption — $xoopsOption used as a local inside a function
`$xoopsOption['template_main'] = 'x.tpl'` inside a function or method writes a local array unless the function
declares `global $xoopsOption`; the page then renders without its template. Script-level code, a function that
declares the global, and a function that takes `$xoopsOption` as a parameter are not reported.
**Fix:** `$GLOBALS['xoopsOption']`.

### XoopsPreloadGlobal — Preload assigns $xoopsModule or $xoopsModuleConfig
A file under `preloads/` runs inside every module's request. Assigning `$xoopsModule` or `$xoopsModuleConfig` there
(or declaring them global) repoints every later reader on the page to the wrong module: "Division by zero" and null
config keys in a module that has nothing to do with the preload. `$xoopsModule = $GLOBALS['xoopsModule'] ?? null;` is a
read and is not reported. Report only: load the preload's own config into a local with
`xoops_getHandler('config')->getConfigsByCat(0, $mid)`.

### XoopsHandlerSignature — Handler getObjects() not declared by reference
A handler extending `XoopsPersistableObjectHandler` / `XoopsObjectHandler` must declare `&getObjects(…)` and
`&getAll(…)`: the kernel returns by reference and PHP refuses an incompatible override at class load. Report only,
because adding `&` also requires `return $var;` instead of `return [];`.

### XoopsFileHeader — File header docblock without @license
Module PHP files open with a docblock carrying `@copyright` and `@license`. A file that already has a leading comment
without the tag (third-party MIT / GPL prose) is only noted, never rewritten.
**Fix:** offered only for a file with no leading comment at all, and never for a `"<?php …"` string literal.

### XoopsSetcookie — setcookie() instead of xoops_setcookie()
`xoops_setcookie()` takes the same arguments and applies the site-wide SameSite / domain policy. Not reported inside
`xoops_setcookie()` itself, nor in Protector, which runs before the wrapper is defined.
**Fix:** renames the call.

### XoopsHttpStatus — Hard-coded HTTP status line
`header('HTTP/1.0 404 Not Found')` hard-codes the protocol version.
**Fix:** `http_response_code(404)`.

### XoopsUnlink — unlink() without is_file() check
`unlink($path)` with no `is_file()` / `file_exists()` on the same expression dominating it. A check in the enclosing
`if`, a preceding early exit at any block level up to the function, a previous `try` whose every catch exits, a
`$this->exists()`-style helper that checks the same `$this->…` path, a check on `$x` when the unlinked path is
`realpath($x)`, an `SplFileInfo` object's `isFile()` when its `getRealPath()` or `getPathname()` is unlinked
(directly or through a variable), its `isLink()` when `getPathname()` (the link itself) is unlinked, and
`if (false === file_put_contents($p, …)) return;` all count. A check behind an unrelated `&&` does not, nor does
`isFile()` before `getPath()` (the containing directory) or `isLink()` before `getRealPath()` (the link's target).
Report only.

### XoopsIsFileAsset — file_exists() / is_readable() accepts a directory before a file read
`file_exists($x)` and `is_readable($x)` are true for a directory. When the condition's body then includes `$x` or reads
it with `readfile()`, `file_get_contents()`, `fopen()` or similar, a directory name reaches the reader.
**Fix:** appends `&& is_file($x)`; a negated check is reported without a fix, because `!a && b` would invert it.

### XoopsByRefArgument — Temporary passed to a by-reference parameter
A call passes a temporary (a call result, a literal) where the resolved declaration takes a reference. Resolution goes
through the PSI, so `parent::__construct()` is checked against the parent's signature. Report only.

### XoopsStrictCalendarCast — strict_types: string passed to mktime()/checkdate()
Under `declare(strict_types=1)`, `mktime(0, 0, 0, date('n'), 1, date('Y'))` throws a TypeError on every call: `date()`
returns a string and the calendar functions take `int`.
**Fix:** wraps the argument in `(int)`; on a numeric string the cast is exact.

## Templates and Smarty

### XoopsWrongSmartyDelimiter — Smarty tag without the XOOPS <{ }> delimiters
XOOPS compiles Smarty with `<{ }>` delimiters: a bare `{if $x}` or `{$var}` is printed literally. Comments and
`<{literal}>` blocks are masked; CSS and JavaScript braces do not match because only Smarty keywords and `{$…}` are
looked for.
**Fix:** wraps the tag in `<{ }>`.

### XoopsUnregisteredTemplate — Template or db: include not registered
A module `.tpl` the manifest does not know, or a `db:` include nobody registers. Core `system_*` templates, templates
whose prefix belongs to another module (`db:tag_bar.tpl` inside Publisher) and includes inside a Smarty comment are not
reported.
**Fix:** registers the template in `xoops_version.php`, Alt+Enter only, because it changes what the installer stores.

### XoopsMissingRegisteredTemplate — Registered template missing where the installer reads it
A template registered in `xoops_version.php` is not where `xoops_module_gettemplate()` reads it: `type => admin|blocks`
adds that folder, every other type is `templates/{file}` as written.
**Fix:** when a file with that name exists elsewhere under `templates/`, points the registration at it. No empty stub
is ever created.

### XoopsSmartyUnquotedPluginArg — Unquoted argument to an xo* Smarty plugin
`<{xoAdminIcons home.png}>`: since Smarty 3 (XOOPS 2.5.11) a bare plugin argument is parsed as an expression and the
template does not compile. Not reported on cores older than 2.5.11, where the argument had to be bare.
**Fix:** quotes it with single quotes, also inside a single-quoted HTML attribute, because Smarty removes the whole
tag before the browser sees it.

### XoopsSmartyForeachItemShadowsFrom — foreach item named like its from array
`<{foreach item=story from=$story}>`: Smarty 3 and later refuse an item variable with the same name as the array it
loops over.
**Fix:** renames the item to `story_item` in the tag and in every `$story` of the loop body, up to `<{foreachelse}>`
or `<{/foreach}>`; a nested loop that declares `item=story` again keeps its own variable.

### XoopsSmartyRemovedTag — Removed Smarty tag (includeq, foreachq, php, include_php)
`<{includeq}>` and `<{foreachq}>` were XOOPS Smarty 2 plugins and no longer exist; `<{php}>` and `<{include_php}>` do
not compile on Smarty 3 and later. Not reported on cores older than 2.5.11.
**Fix:** renames `includeq` / `foreachq` to `include` / `foreach`. The PHP tags have no fix: move the code into the PHP
page and assign the result.

### XoopsSmartyBlockShorthand — block_parent / block_child shorthand
`<{block_parent}>` and `<{block_child}>` are not Smarty tags.
**Fix:** `<{$smarty.block.parent}>` / `<{$smarty.block.child}>`, which work on Smarty 3, 4 and 5.

### XoopsSmartyDateFormatStrftime — date_format with strftime() codes
`|date_format:'%d.%m.%Y'`: a format with `%` codes runs `strftime()`, deprecated since PHP 8.1 and removed from
Smarty 5. Not reported on cores older than 2.5.11.
**Fix:** rewrites `%Y %y %m %d %e %H %M %S %p %%` to `Y y m d j H i s A %` (`%M` is minutes, so it becomes `i`). A
format with a locale token (`%A`, `%B`, `%j`) or literal letters is reported without a fix.

### XoopsSmarty5Api — Smarty API removed in Smarty 5 (core 2.8)
Smarty 5 removed `assignByRef()`, `appendByRef()`, the `register_*()` family and the public `template_dir` /
`compile_dir` properties, and has no global `\Smarty` class (core 2.8 templates are `\XoopsTpl`, which extends
`\Smarty\Smarty`). On a 2.5 / 2.7 tree these calls are the current API and nothing is reported; a standalone module
without a core is checked.
**Fix:** `assignByRef($k, $v)` becomes `assign($k, $v)` only when `$v` is never written again in its scope. Everything
else is reported for a hand rewrite.

### XoopsSmarty5TemplatePrep — Template construct that needs rework for Smarty 5
Constructs that work on Smarty 4 but need rework before a Smarty 5 core: bare `<{parent}>` / `<{child}>`,
`<{insert}>`, `<{make_nocache}>`, `config_load scope=`, and PHP functions used as modifiers (`|substr`, `|trim`,
`|strtolower`), which Smarty 5 only accepts when registered. **Off by default**: enable it for a tree that is being
prepared for Smarty 5. No quick-fix.

## Language files

### XoopsLanguageRole — Language constant in the wrong catalog
`_MI_` belongs in `modinfo.php`, `_AM_` in `admin.php`, `_MD_` in `main.php`, `_MB_` in `blocks.php`, `_CO_` in
`common.php`. A canonical file with a foreign prefix gets one note per constant; a non-canonical file (`modinfo1.php`,
a backup copy) gets one file-level note unless some code loads it by name. Only a module's own catalogs directly under
`language/<lang>/` are checked. `_MD_AM_` (system) and `_MD_A_` (Protector, D3) count as admin constants. Report only.

### XoopsLanguageLoad — Language constant used without a loaded catalog
A page script (it boots `mainfile.php`) uses `_MD_` / `_CO_` / `_AM_` constants but loads no catalog: neither
`xoops_loadLanguage()`, nor the XMF helper's `loadLanguage()`, nor an include of `header.php` / `admin_header.php` /
`common.php`. A file that does not boot `mainfile.php` is included by one that did and is not reported. **Off by
default.** Report only.

### XoopsLocaleConstant — Module language constant defined nowhere
`_AM_<DIRNAME>_X`, `_MD_…`, `_MI_…`, `_MB_…`, `_CO_…` referenced in the module but `define()`d in none of its language
files: PHP 8 throws "Undefined constant" where PHP 7 printed the name. Only constants carrying the module's own dirname
are checked, against PhpStorm's index of every `define()` in the project. Report only.

### XoopsDuplicateDefine — Constant defined twice in a language file
The same constant `define()`d twice in one language file: PHP warns "Constant already defined" on every request and
keeps the first value.
**Fix:** deletes an identical duplicate; two different values are reported for a human to choose.

### XoopsThemeConstant — THEME_* constant defined by a module
`define('THEME_…')` in a module language file. The `THEME_*` constants belong to the theme's own language pack;
whichever file loads second warns and the theme's text loses. Report only.

## Deprecated APIs

### XoopsDeprecatedCoreApi — Deprecated core API
Core APIs the 2.7.4 tree marks `@deprecated` (list generated from the real `class/`, `kernel/` and `include/`
docblocks) plus pre-2.5 idioms that still surface in old modules: `XoopsTree`, `XoopsComments`, Snoopy,
`XOBJ_DTYPE_UNICODE_*`, `xoops_getModuleOption()`, the sanitizer's `makeTboxData4*()` family, Smarty 2 method names on
`XoopsTpl`, and PHP 4 constructor calls (`parent::XoopsObject()`). It works on a standalone module and says what to
use instead. The UNICODE dtypes are never renamed: they decide how stored rows are decoded.
**Fix:** a rename, only where the replacement takes the same arguments (`destoryVars` → `destroyVars`,
`parent::XoopsObject()` → `parent::__construct()`, `assign_by_ref` → `assignByRef`).

### XoopsDoubleEncodedAmp — &amp;amp; inside a URL passed to header() / addItemButton()
`header('Location: …')` and `ModuleAdmin::addItemButton()` take a raw URL: `renderButton()` escapes it and `header()`
sends it as is. A literal `&amp;` in that string reaches the server as a parameter named `amp;…`, so the token check
fails. A URL printed into HTML is left alone. `redirect_header()` and the helper's `redirect()` also take a raw URL but
turn `&amp;` back into `&` for their meta refresh, so there it is only a weak note.
**Fix:** writes `&`.

## Reading a finding

Every message starts with `XOOPS:` so the Problems view can be filtered to this plugin, and names the construct it
saw, not only the rule. A message that ends in "report only", "no quick-fix" or "kept as is" is deliberate: the
inspection can prove the defect but not the correct replacement. When a fix is offered it is idempotent (applying it
twice changes nothing), recomputes its ranges at apply time (so a batch run over a file applies every fix), and keeps
the file parsing; the test suite asserts all three for every fixture.
