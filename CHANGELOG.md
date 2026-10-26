# XOOPS Support Changelog

Marketplace plugin `org.xoops.plugin.support`. Versions 0.4.0 to 0.7.2 below are the development line of this
rebuild; it ships as **1.0.0 Alpha 4**, the successor of the 1.0.0 Alpha 3 release of the previous code base
(https://github.com/XOOPS/phpstorm-plugin).

## 1.0.0-alpha.5 — 1.0.0 Alpha 5 — NOT RELEASED

False positives from a run over three newbb trees (`280test1/htdocs/modules/02/01inspections`, 2026-10-26), each
checked against the module source and pinned with a fixture.

- **DeprecatedCoreApi** no longer names `synchronization()`: it is the module handlers' own method (newbb reports 99
  calls), and the core's `XoopsPersistableObjectHandler::synchronization()` carries no `@deprecated` tag either.
- **InstallSql** and **SqlFileComment** look only at the file the installer runs: a module's `sql/mysql.sql`, or the
  file an active `sqlfile` entry names, resolved from the module root (so `sql/english/install.sql` counts, in both
  the `$modversion['sqlfile']['mysql']` and the array-manifest form, with every database entry of an array read).
  The manifest is read through the PHP parser, not as text: a commented-out declaration, a string or nowdoc that
  looks like one, a comparison against `sqlfile`, and a `sqlfile` key on any variable other than `$modversion` do
  not count, while a declaration behind an inline comment, after `?>`, with a multi-line `[XOOPS_DB_TYPE]` index,
  or joined from literals (`'sql/' . 'install.sql'`) still does. Adminer / phpMyAdmin dumps and old upgrade
  scripts beside it (`2510debug1.sql`, `xoops_mysql.sql`, `mysql.400.sql`) are never split by SqlUtility, so their
  MyISAM engine, `xoops_` prefixes and bare `--` lines were 237 + 111 findings about files nothing executes. A file a
  comment merely mentions is not an installer file. The core's own `install/sql/` schema has no module manifest and
  stays inspected; any other `sql/` folder outside a module does not.
- **Unlink** accepts an `SplFileInfo` check: `$info->isFile()` dominating `unlink($info->getRealPath())`, or a
  variable assigned from `$info->getPathname()`, is the is_file() check, and `$info->isLink()` guards
  `unlink($info->getPathname())` (the link itself, also when it dangles). `isFile()` before `getPath()` (the parent
  directory) and `isLink()` before `getRealPath()` (the link's target) are still reported.
- **UnregisteredTemplate** and **MissingRegisteredTemplate** skip test trees like every other rule; a `db:` include
  in `tests/example.tpl` was reported as an unregistered template.
- **MenuAutoload** gains fixtures for `<?php declare(strict_types=1);` on the open-tag line, a header-comment block,
  a `bootstrap.php` require that already loads the autoloader (no finding), and newbb's real file with two spaces after
  `<?php` and CRLF line endings. The Marketplace Alpha 4 jar put the require right after `<?php` on such a file
  (`<?phprequire_once …` ahead of `declare`); this code base inserts above the loading statement, and the fixtures hold
  it there on PhpStorm 2024.3 and 2026.3.
- **Build:** `gradlew test -PlocalIde=<PhpStorm install dir>` runs the suite against an installed IDE instead of the
  pinned 2024.3.5 platform, so a parser change in a newer PhpStorm is caught before a report comes in.
- **Docs:** `INSPECTIONS.md` lists all 52 inspections with default level, what each reports, what its quick-fix
  changes and when it stays silent. README and TUTORIAL link to it; the README's Alpha 3 era table is gone. The
  tutorial no longer claims that `assets/js/` is read by the security rules: that tree is excluded.
- **Tools:** `tools/gen_descriptions.py` is rebuilt from the shipped description pages and reproduces all 52 byte
  for byte. It had 35 entries, 12 of them shorter than the pages edited since and two under retired short names, so
  running it would have overwritten reviewed descriptions.

## 1.0.0-alpha.4 — 1.0.0 Alpha 4 — Marketplace release 2026-10-26

Alpha 4 as published: the rebuild below (2026-10-24) plus the refinement rounds that followed it, six full runs over
XoopsCore27 with every remaining finding checked against the source.

- **Build:** the Kotlin compiler runs with `-Xjvm-default=all`. Without it the compiled `XoopsToolWindowFactory`
  carried delegating stubs for every default method of the platform's `ToolWindowFactory` (`getAnchor`, `getIcon`,
  `manage`, `isApplicable`, …), which the Plugin Verifier reported as overrides of `@ApiStatus.Internal` API and
  failed CI on. The source never declared them.

False positives from the XoopsCore27 run (`01inspections`, 2,241 findings), each checked against the core
source, pinned with a fixture and re-run on copies of the real files.

- **Template rules skip test trees.** 1,150 findings came from `upgrade/tests/tmp/` (scratch copies the
  upgrade tests write) and `upgrade/tests/fixtures/` (broken on purpose). `tests/`, `test/`, `testdata/`
  and `fixtures/` are skipped by every template rule (and, since the second run below, by every PHP rule).
- **Smarty5Api is silent on a core older than 2.8.** Smarty 4 still has `assignByRef()` and `\Smarty`;
  the rename changed behaviour where the variable is written later. A standalone module is still checked.
- **Csrf** recognises a dominating `if (!$GLOBALS['xoopsSecurity']->check())` that exits or resets `$op`,
  scoped by `in_array($op, […])` / `$op === 'x'` (modulesadmin), inside a POST-only block whose `else` exits
  (comment_post), and a same-file helper that checks and stops (menus). A gate behind any other condition,
  in an `else` or a loop covers nothing. 10 of 10 findings were covered.
- **GuestUser** accepts `!isset($xoopsUser) || !is_object($xoopsUser)` (polarity-aware; 3 of 3).
- **PreloadGlobal**: `$xoopsModule = $GLOBALS['xoopsModule'] ?? null;` in a method without `global` is a read.
- **OrderBy**: `getSort()` passed to `buildOrderBy()` is allow-listed (3 of 5; findusers.php and
  profile.php remain, both real).
- **Setcookie** skips `xoops_setcookie()` itself and Protector, which runs before `common.php` defines it:
  the rename there would have been a fatal on every banned request.
- **WeakToken** ignores comparisons with a non-numeric literal (`$pass == ''`, `$addtoken != false`). New
  fix: two request strings compared loosely (`Request::getString()`, `getVar(…, 'string')`, assigned nowhere
  else) become `!==` / `===`. It applies to the password confirmation in edituser.php and profile/changepass.php;
  parameters (userutility.php) stay report-only.
- **FormLoader** skips the core's own `class/xoopsform/` (14 of 14).
- **DoubleCpHeader** names the real first call ("already called on line 41"), counts a first call only when
  it is a top-level statement, and offers the delete fix only for the admin_header.php case without a
  `$xoopsOption` change on the way: a page's own second call is not output-neutral to delete.
- **QueryExec** leaves `$force ? exec() : query()` alone (the deliberate GET gate).
- **DoubleEncodedAmp**: `redirect_header()` / `redirect()` undo `&amp;` themselves (verified on 2.7.4): weak
  note with an accurate message; the fix gives identical output. `header()` / `addItemButton()` unchanged.
- **PathContainment** skips literal needles, URLs, referers and URL helpers, and accepts a `realpath()`'d
  path against a separator-terminated base; a resolved path without the separator gets its own message.
- **LanguageLoad** checks only page scripts that boot `mainfile.php` (22 of 22 were included fragments).
- **LanguageRole** checks only a module's own catalogs directly under `language/<lang>/`, accepts the
  `_MD_AM_` / `_MD_A_` admin prefixes, and does not call an explicitly loaded catalog (`cpanel.php`) unloaded.
- **AllowlistAnchor** ignores a subject reassigned between the match and the comparison (class/preload.php).

Second run (`02inspections`, 1,136 findings, alpha.4.3), triaged with the Codex / Gemini / Grok reviews:

- **Security rules skip test trees too.** Only the convention and template rules did; ~230 findings were
  PHPUnit code saving `$_POST`, unlinking temp files and building stubs with `eval()`.
- **ResultSetGuard** recognises `isResultSet($r) && … ? fetch($r) : false`, `isResultSet($r) && … && fetch($r)`,
  a guard that ends in `break` / `continue`, and a handle the function receives as a parameter (8 of 8 core
  findings outside the legacy-check notes).
- **AttributeEscape** offers the `htmlspecialchars()` wrap only for a raw value (`OTHER`, `SOURCE`, format
  `'n'`). With the DTYPE unknown the finding is weak and has no fix: a TXTBOX is already escaped, and the wrap
  printed `&amp;#039;`. Keys ending in `id` (`groupid`, `from_userid`) count as numeric when the DTYPE is unknown.
- **PathContainment** follows `realpath()` through a failure ternary (`$root !== false ? realpath(…) : false`,
  `is_dir($d) ? realpath($d) . DIRECTORY_SEPARATOR : ''`): 10 upgrade and maintenance checks were containment.
- **LanguageRole** skips `Frameworks/` libraries (moduleadmin loads its `main.php` by path; 32 of 32).
- **UpdateCreatesTable** ignores an `sql/mysql.sql` the installer never runs: no active `sqlfile` in the
  manifest and no install callback reading it (pm 2.7 uses the core `priv_msgs` table).
- **InstallSql** leaves the core installer schema's engine alone (its legacy tables stay MyISAM on purpose);
  the message no longer claims InnoDB is "the 2.7 default".
- **Superglobal** skips Protector, the firewall that rewrites the raw superglobals (122 findings).
- **DoubleCpHeader** display name: "Second xoops_cp_header() on one request" (it no longer assumes
  admin_header.php).
- Checked and kept: the newbb `{$golast}` finding and its `<{$golast}>` fix. Smarty 4.5.8 renders the original
  as `{Go to last post}` and the fixed form correctly.

Third run (`03inspections`, 696 findings, alpha.4.4), triaged with the Codex / Gemini / Grok reviews:

- **PathContainment** follows `realpath()` through `str_replace()` calls that only swap one separator for
  another (`filelogger.php`). It treats a private method's parameter as resolved when every caller in the class passes
  a `realpath()` value (the upgrade delete helper). It skips a prefix check whose ternary only strips the prefix for
  display (`ScannerProcess::relativeToRoot()`). The 6 avatar checks remain.
- **ResultSetGuard**: a `!$result` exit that follows an `isResultSet()` exit is redundant, not a legacy guard
  (`install/page_tablesfill.php`).
- **Unlink**: an existence check inside a previous `try` counts when every `catch` exits too
  (`xoops_unlink_quietly()` in `include/file_safety.php`, which also removes broken symlinks).
- **Superglobal** skips the core installer and upgrader. Both run before the xoops_lib path is known, so the
  `Xmf\Request` fix would fatal there (`install/page_pathsettings.php`).
- **AttributeEscape**: when neither the receiver type nor the module declares the key, the single `initVar()`
  under core `kernel/` decides (`uname`, `com_email`, block `title`, user `url` are TXTBOX and already escaped).
- Checked and kept: newbb `{$golast}` (Grok again; the 4.5.8 render test stands), `field.php:595`
  `$force ? exec() : query()` (Codex again; deliberate GET gate). Also kept: `convertResultSet($result)` with no
  guard: it is deprecated, has no caller in core, and the handle is the caller's to check.

Fourth run (`04inspections`, 669 findings, alpha.4.5): exactly the 27 predicted rows gone, none new. From the reviews:

- **Unlink** accepts a same-class helper that checks the same `$this->…` path (`File::delete()` via `exists()`),
  a check on `$x` when the path is `realpath($x)` (`install/page_langselect.php`), `if (false === file_put_contents($p, …))
  return;` before deleting a probe the code just wrote (`maintenance.php`), and `break` / `continue` as exits. A check
  behind an unrelated `&&` no longer counts: `if ($a && !is_file($p)) return;` falls through when `$a` is false.
- **Superglobal**: an assignment to `$_POST` gets its own message instead of "read it through Xmf\Request"
  (`profile/register.php` merges the session copy into `$_POST` on purpose).
- **WeakToken**: `md5($pass)` compared or passed as a `Criteria` value is a legacy-hash lookup (the 2.2 login
  fallback in `upgrade/login.php`), a weak note; a stored `md5()` stays a warning. There never was a fix.
- **UpdateCreatesTable** is a weak note with an honest message: whether a released version lacked the table
  (Protector `access`) cannot be told from the tree.
- Checked and kept: `{$golast}` (Grok, fourth time; the render prints `{Go last}` with literal braces, the fixed
  `<{$golast}>` prints `Go last` inside the assign), `field.php:595` and `convertResultSet()` (Codex, as before).

Fifth run (`05inspections`, 666 findings, alpha.4.6): as predicted, none new. **WeakToken**: the legacy-hash note
says to rehash only once the schema holds modern hashes; before `upd-2.5.7-to-2.5.8` widens `pass` to 255, a bcrypt
hash would not fit (Codex).

Sixth run (`06inspections`, 666 findings, alpha.4.7): byte-identical to the fifth except the reworded WeakToken
note; no plugin change. `{$golast}` was rendered through the core's own Smarty 4.5.8 with `<{ }>` delimiters: the
original line prints `title='{Go to last post}'` (literal braces in the attribute), the quick-fix form prints the
text alone, so the finding and its fix stand (Grok, sixth time). `field.php:595` and `convertResultSet()` stay
deliberate keeps (Codex).

## 1.0.0-alpha.4 — first cut of Alpha 4 — 2026-10-24

Everything from 0.7.2 and below, under the published plugin id, name, vendor and icons. Added for continuity with
Alpha 3: the `XoopsIncludeOnceHeader` inspection (`include` of header/footer.php → `include_once`), the live
templates `xofetchdb`, `xohead` and `xoexec`, and the Alpha 3 short names for the inspections both have
(`XoopsQueryExec`, `XoopsWrongSmartyDelimiter`, …) so profile severities carry over. Also rebuilt from Alpha 3:
the Settings page (enable switch, startup balloon, tool-window auto-scan, Core Version override that the
version-gated quick-fixes honour, with the detected version shown), **New → XOOPS Module…** (dialog with validation;
legacy stub or hybrid layout with `class/Helper.php`, `preloads/autoloader.php`, `include/common.php` and an
`Xmf\Module\Admin` admin side; every generated file passes this plugin's inspections, which a test asserts), the
Tools → XOOPS Support menu (project info, refresh overview, new module), and Ctrl+B / Find Usages on language
constants written as strings or as `$smarty.const._X` in templates (poly-variant: same module and English first).
The Overview tool window shows the Smarty major and, per module, version, language packs, preloads, classes
(`class/` + `src/`), block entries, admin / SQL / tests flags, `min_php` / `min_xoops`, PSR-4 autoloader, `Xmf\Module\Admin`
use, `strict_types`, findings per module and PHP line count, with totals above the table. Every column is a checkbox in
Settings → XOOPS Support; all are shown on a fresh install, an update keeps what was hidden.

## 0.7.2 — 2026-10-24

Findings from the 224-module review (`0_pluginTests/htdocs04`, 46,634 findings, 10,694 files rewritten,
one parse error, zero blocked entry points).

- **Superglobal: dynamic callees are never rewritten.** `new $_REQUEST['mod']()`, `$_GET['f']()` and
  `$o->$_POST['m']()` name a class, function or method; `new \Xmf\Request::getVar(…)()` does not parse
  (pgv/module.php). Reported without a fix.
- **Superglobal: scripts that never boot XOOPS are skipped.** 102 files - PayPal / Alipay callbacks, a
  TinyMCE compressor, thumbnailers, the bundled PhpGedView, PEAR examples - were rewritten to `Xmf\Request`
  although nothing in them loads the core, a fatal on the first request. A file is XOOPS code when its text
  names `xoops`, `mainfile.php` or `Xmf\`; anything else is left alone.
  The same gate now sits under every convention rule (`skipConvention()`): Setcookie had written
  `xoops_setcookie()` into the bundled PhpGedView and Facebook SDK, and that word alone re-qualified eight scripts
  for the superglobal rewrite on the next pass. The guard line is not evidence either. Language packs,
  `xoops_version.php` and `index.php` stubs count as module code by path.
- **RootPathGuard** also accepts `{ return; }` as the guard body (newbb's welcome include).
- **SqlFileComment / SqlComment: a bare `--` line is blanked, not turned into `-- `.** `-- ` ends in a space, and PhpStorm's
  "Remove trailing spaces on save" stripped it again on every save (`TrailingSpacesStripper.beforeDocumentSaving`,
  caught with a document listener after five instrumented builds): the edit was visible for 130 ms and the file
  never changed on disk, in the editor and in batch alike. A bare `--` is a phpMyAdmin separator with no comment
  in it, so the line is emptied (kept, so batch line numbers stay valid); `--text` still gets its space. The batch path also takes the
  document from the file captured at report time (the SQL leaf is stale by then) and saves it, since no editor holds
  an SQL file in a batch run.
- **RootPathGuard recognises `throw` and compound guards.** `if (!defined('XOOPS_ROOT_PATH')) { throw new
  \RuntimeException(…); }` and `if (!defined(…) || !($GLOBALS['xoopsUser'] instanceof \XoopsUser)) { exit; }`
  are guards, as is `{ http_response_code(404); exit; }`; about 400 files had received a second one.
- **Csrf counts `query()` with write SQL as a write**, so the eight token-less branches that only surfaced
  after MutatingQuery renamed them to `exec()` are reported up front.
- `/docs/`, `/examples/` and `/tests-src/` are excluded trees; `/pear/` is third-party.
- Six new golden fixtures.

## 0.7.1 — 2026-10-24

### Core version gate for the database QuickFixes

Found in the four-plugin bake-off on a XOOPS 2.5.9 tree (`0_pluginTests/htdocs02`): the ResultSetGuard
and MutatingQuery fixes wrote 177 `isResultSet()` and 155 `exec()` calls into a core whose database class
has neither. Call to undefined method on every page, invisible to `php -l`.

- New `XoopsCore` reads the nearest ancestor `include/version.php` (cached per file stamp). The version file is
  the marker, not `mainfile.php`: an unpacked distribution has only `mainfile.dist.php`, and the first re-run on
  the 2.5.9 tree (`03inspections`) showed the gate silent for exactly that reason.
  No web root (a standalone module checkout) means "current core": every rewrite stays on.
- **ResultSetGuard**: below 2.5.11 the inserted guard is `if (!$result)` and the legacy `if (!$r)` /
  `if (!$r = query())` / `false === $r` checks are not findings - there they are the correct test.
  The nested `fetch*($db->query())` message says "test it before fetching" there instead of naming `isResultSet()`.
- **MutatingQuery**: below 2.5.12 the fix renames `query()` to `queryF()`, the forced write that core has.
- **DeprecatedDbApi**: below 2.5.12 `queryF()` is not reported (nothing to rename it to); `quoteString()` still is.
- **Dynamic unload**: the language-constant completion cache moved from `CachedValuesManager.getCachedValue(project)`
  (a plugin-classloader lambda parked in project user data, which survives an unload) into a project-level light
  service that is disposed with the plugin.
- Four golden fixtures with a 2.5.9 `include/version.php` plus a parser test (`2.5.9`, `2.8.0-Alpha1`, `2.7`).
  Thresholds verified against 80 local core trees: `isResultSet()` from 2.5.11-Stable, `exec()` from 2.5.12-RC1.

## 0.7.0 — 2026-10-23

Answer to the four reviews of round 4 (`C:\wamp64\www\280test1\04inspections`, feedback 1–4). The brief:
more real findings, every QuickFix provably safe, vendored folders out. Nine rules harvested from the
Gemini / Grok / Codex plugins and rebuilt on the PSI, two own defects closed, two new test gates.

### Own defects named by the reviews

- **`unserialize()` fix no longer breaks stored objects** (feedback 2). xhelp stores a serialized
  `CriteriaCompo` (`class/Utility.php:1043`) and oledrion an attribute object; `['allowed_classes' => false]`
  handed them back as `__PHP_Incomplete_Class` and the next `->` call failed. The fix is now offered only
  where every use of the result is array-shaped (`foreach`, `[...]`, `(array)`, `count()`, `in_array()`,
  `array_*`, …); a result that reaches `->`, `instanceof`, a return, a field, an array element or another
  call is reported with the class list left to the author. Golden fixture with both shapes.
- **By-reference functions get a variable return** (feedback 3). `InsertResultSetGuardFix` wrote
  `return [];` into six `function &…(): array` methods of xhelp (PHP notice "Only variable references should
  be returned by reference"). It now emits `$ret = []; return $ret;` there; `: void` keeps the bare `return;`.
- **Vendored and scratch folders are excluded from every rule**: `assets/js`, `jpgraph*`, `jpspan*`,
  `0oldstuff`, `GUMP_examples`, `00/`, `vendor`, `node_modules`. Roughly 2,200 of the 3,824 files the 0.6.2
  batch touched were in those trees.

### Rules harvested from the other plugins (all PSI-based, no regex over file text)

| Rule | From | Fix |
|---|---|---|
| Installer SQL: `FOREIGN KEY … REFERENCES`, hard-coded `xoops_` / `{prefix}` / `%s` prefix, `ENGINE=MyISAM` | Gemini, Codex | report |
| `CREATE TABLE` that no update path (onupdate.php, include/update.php, sql/*.yml) mentions — error 1146 on upgrade | Gemini | report |
| Bare `--` line in the `.sql` file itself, one finding per line | Gemini | inserts the space on that line only, re-checked at apply time |
| `strpos($p, $base) === 0` / `str_starts_with()` as path containment | Gemini | report |
| `file_exists($x)` / `is_readable($x)` before an include or file read of `$x` | Gemini | `&& is_file($x)` — only on a positive check, so it can only narrow |
| Allowlist `preg_match('/^…$/', $v)` followed by an exact comparison of `$v` | Gemini | `$` → `\z` (single-quoted pattern, no `D`/`m` modifier) |
| Preload that `global`s or assigns `$xoopsModule` / `$xoopsModuleConfig` | Gemini | report |
| Token from `rand()` / `mt_rand()` / `uniqid()`, `md5()`/`sha1()` of a password, `==` / `strcmp()` on a secret | Gemini | report (storage width of a token is unknown) |
| Smarty 5: `assignByRef()`, `appendByRef()`, `register_*()`, public `template_dir`, global `\Smarty` | Gemini | `assignByRef` → `assign` only when the variable is never written again in scope |

Not copied, on purpose: FetchFalse (duplicates the result-set guard; `while ([$a] = fetchRow())` is correct
code), CriteriaOr / ElvisZero / NumericCast / MbEreg (name heuristics, not provable), InstanceofParen
(`!$a instanceof B` already parses as `!($a instanceof B)`), and the locale-constant ternary fix (it hides
a missing translation, and it is what broke Gemini's namespaced files).

### Test gates

- Every golden fixture now asserts that the fixed PHP file has **no parse error element** — the gate the
  other plugins lacked (Gemini 11, Grok 8 files that no longer parsed).
- `NeverBreakTest`: the five shapes that broke the other plugins, verbatim from the original modules, run
  through **every** registered rule with **every** XOOPS fix applied in IDE order: multi-variable `isset()`
  (suico), the `if (!isset($GLOBALS['xoTheme'])…)` header (xoopstube), the namespaced `\_AM_CONST`
  (oledrion), `function index(): void` with a fetch (adslight), the by-reference handler (xhelp).
  Afterwards the file must parse and the specific trap must not have been sprung.
- SQL-file rules are checked by descriptor, the way the batch "Run inspection" collects them.

Tests: 104 (87 golden fixtures + 11 corpus + 5 never-break + 1 descriptor). 44 inspections.

## 0.6.2 — 2026-10-23

Round 4 re-run (`C:\wamp64\www\280test1\04inspections\02`): the batch button for
"Replace isset($_X['key']) / null comparison with Request::hasVar()" left 149 superglobal findings unchanged,
run after run. None of them was a failed edit: 32 were `isset()` with a dynamic key (`$_POST[$name]`,
`$_REQUEST[$field . '_larger']`) or a multi-variable `isset($_POST['a'], $_POST['b'])`, which the fix declined
at apply time; 81 were a bare `$_POST` in `isset()`, `empty()`, `global`, `reset()` or an assignment, which had no
fix; 36 were writes, which never get one. Now:

- **`hasVar()` takes any key expression.** `isset($_POST[$name])` → `Request::hasVar($name, 'POST')`; the index
  goes in verbatim, so a concatenation or a constant works too. The same for `=== null` / `!== null`.
- **Multi-variable `isset()` is a conjunction.** `isset($_POST['submit'], $_POST['passwd'])` →
  `Request::hasVar('submit', 'POST') && Request::hasVar('passwd', 'POST')`; a term that is not a superglobal keeps
  its own `isset()`. The result is parenthesised unless it is a whole `if` / `elseif` / `while` condition, so a
  negated `!isset(a, b)` stays `!(A && B)`.
- **`empty($_POST)` joins the raw family:** `empty(Request::get('POST', MASK_ALLOW_RAW | MASK_NO_TRIM))` is the
  same test (PHP 5.5+ accepts an expression in `empty()`).
- **`global $_POST` has its own batch fix** ("Remove the superglobal from the global statement"): the superglobal
  leaves the list, a list naming only superglobals leaves with its line. A superglobal is in scope everywhere.
- **`isset($_POST)` is reported as always true** and gets no fix — removing the check changes control flow and is
  a hand edit.

Still report-only, by design: writes (`$_POST['x'] = …`, `$_POST = […]`), `unset()`, `reset($_GET)`,
`parse_str(…, $_POST)` and by-reference use. Of the 149, 68 now have a fix and 81 remain reported.

Tests: 86 (74 golden fixtures + 1 descriptor + 11 corpus).

## 0.6.1 — 2026-10-23

Round 4 (`C:\wamp64\www\280test1\04inspections`): 1,624 superglobal findings, 1,247 left after the batch —
the typed tiers fixed what they claimed and the rest had no fix at all. Two changes:

- **Every superglobal read now has a fix.** `Xmf\Request::getVar($key, null, $hash, 'none', MASK_ALLOW_RAW | MASK_NO_TRIM)`
  is byte-identical to the raw read (verified against the installed library: HTML kept, arrays kept, whitespace
  kept, `'0'` kept, a missing key is `null`), and `Request::get($hash, MASK_ALLOW_RAW | MASK_NO_TRIM)` is the whole
  array as is. They are the third batch family: the reads the typed tiers decline, `empty()`, a loose `== null`,
  a dynamic key, a string offset, a bare `$_POST` / `$_REQUEST`. A strict `=== null` / `!== null` is `isset()` and
  joins the `hasVar()` family. `$_REQUEST` is no longer "banned": it is the library's `'default'` hash and gets
  the same tiers. Only a write, `unset()`, nested `isset()`, interpolation and by-reference use stay report-only.
- **Template registrations follow `xoops_module_gettemplate()` exactly.** The installer prepends
  `templates/<type>/` only for type `admin` / `blocks` (and every `$modversion['blocks']` entry) and reads
  `templates/<file>` as written for everything else. The old rule short-circuited a file name starting with
  `blocks/`, so wgevents' `'type' => 'block'` entries were repointed to `blocks/x.tpl` (correct) and then reported
  again, and suico's `'file' => 'blocks/suico_block_friends.tpl'` (correct as written) was a false positive. The
  fix is now relative to the folder the installer uses; a file outside that folder is reported without a fix.

Tests: 85 (73 golden fixtures + 1 descriptor + 11 corpus, wgevents and suico manifests added verbatim).


## 0.6.0 — 2026-10-23

Answer to the four round-3 reviews of the 16-module run. Everything below ships with a golden fixture; the
real-file facts are pinned in `CorpusSmokeTest` on verbatim files from the corpus (news, newbb, pm, profile,
xasset, adslight, xhelp).

Quick-fix safety:
- **Superglobal rewrite follows the use.** `Request::getString()` on a POST array is the string `"Array"`:
  News rebuilds its permissions from `foreach ($_POST['groups_…'] as …)` and would have saved no group
  (Codex, round 3). The accessor is now derived from how the value is consumed — `getArray()` for foreach /
  `count()` / `[…]` / an `array`-typed parameter / an `XOBJ_DTYPE_ARRAY` field, `getInt()` for `(int)` /
  `intval()` / arithmetic (the cast is replaced), `getString()` only where tags cannot matter (literal
  comparison, `switch`, array key, `in_array()` needle), `getText()` — which keeps HTML — for concatenation,
  echo, string functions and text fields. `isset($_X['k']) ? $_X['k'] : 'd'` and `$_X['k'] ?? 'd'` become
  one call with `'d'` as the default. A variable assigned from the read is traced through its later uses;
  one array use is decisive, one unreadable use (untyped argument, `compact()`, hand-over) leaves the read
  without a fix. Two batch families: the keyed read and `hasVar()`.
- **`trigger_error(…, E_USER_ERROR)` is a guard.** `if (!isResultSet($r)) { trigger_error(…, E_USER_ERROR); }`
  is the 2.5.11 convention; PHP halts and the XOOPS logger exits. 0.5.x inserted a second guard behind 142 of
  them (dead code, all three plugins did it).
- **Root pages that include a library are still guarded.** The 0.5.1 entry-page rule skipped any root file
  with an include; only an include of a sibling page (`user.php`, `servicemain.php`) or a `header()` call
  marks an entry now, so `footer.php` with `require …/include/functions.php` keeps its guard.
- **Smarty 5.** `assignByRef()` / `appendByRef()` (removed in Smarty 5) → `assign()` / `append()`; the
  Smarty 2 names go straight there.

Rules ported from the Grok / Gemini catalogue, limited to the form where the finding is a defect and the fix
is a pure text edit: DoubleEncodedAmp (fix), StrictCalendarCast (fix), LocaleConstant, DuplicateDefine (fix for
an identical duplicate), ThemeConstant, MenuAutoload (fix), HandlerSignature, SqlConcat, SqlComment (fix in
the SQL file, reported on the `xoops_version.php` entry that names it). Not ported, on purpose: FetchFalse
(`while ([$a] = fetchRow())` is correct PHP), InstanceofParen (cosmetic), IncludeOnceHeader, ResultName.

Second-opinion round (Codex, 25 findings, all addressed): no `getInt()` without an existing `(int)` / `intval()`;
`getString()` only against identifier literals; `getText()` never for digests, lengths or (de)serialisation;
string offsets, `!$_POST[…]`, interpolated strings, interpolated or escaped keys, by-reference iteration and
by-reference array functions, `compact()`, `global`, rebinding and use-before-assignment all withhold the fix;
per-parameter array positions (`array_slice($a, $_GET['offset'])` is no longer an array); the `?? 0` / `?? []`
default only has to agree with the use, it never decides; the `setVar()` DTYPE comes from the resolved receiver
only; `use Xmf\Request` is honoured per namespace; the fix re-derives key and source from the live tree.
Ported rules: `&amp;` only in the URL parameter (and `header()` only for `Location:`), a duplicate `define()` is
deleted only when unconditional and literal, `X::class` does not trigger MenuAutoload, `idate()` is not a calendar
function, `defined()` guards silence LocaleConstant / ThemeConstant, HandlerSignature checks
`XoopsPersistableObjectHandler` only, `count()` / `strlen()` make a SQL operand safe. Root-path guard: a root page
or a file outside the include directories is guarded only on positive evidence (declarations only, or a known
include-only name), a variable `require` or a top-level `echo` marks an entry, and the anchor absorbs the
`global` / `static` keyword leaf without crossing a same-line `declare`.

Tests: 82 (72 golden fixtures + 1 descriptor + 9 corpus).


## 0.5.1 — 2026-10-22

Response to the 16-module run (`C:\wamp64\www\280test1\03inspections`): 10,896 findings, 3,842 files changed, two broken files, five entry pages guarded, and a batch-fix list the user could not work through.

- **One batch button per fix, not per key.** Every quick-fix had a family name that embedded the key or the call (`Replace with Request::getString('op', '', 'GET')`, `Replace quoteString() with quote()`), so the Inspection Results window offered one "partial fix" per distinct name — hundreds for the superglobal rule. Family names are now constant per fix kind; the detail moved to the fix's own name (what Alt+Enter shows). Superglobal: two families (`getString()`, `hasVar()`). Deprecated calls: one. PHP 4 superglobals: one.
- **Guard above a `global` statement.** `global $x;` is parsed as a keyword leaf followed by the statement, so the anchor sat mid-line and the guard was inserted inside the statement (parse error in newbb's `footer.php` and xoopstube's `include/update.php`). The anchor now moves to the first element on the statement's line.
- **Root pages that boot through a sibling are entry points.** `modules/pm/index.php` (`header('location: …')`), `modules/profile/index.php` (`require 'user.php'`) and the xasset / xhelp service endpoints (`require 'servicemain.php'`) got a guard and became "Restricted access". A file directly under `modules/<dir>/` that includes any file or calls `header()` is no longer guarded; a root `functions.php` that only declares still is.
- **`checked{/if}>` is a bare Smarty tag.** The delimiter rule refused a `{` glued to a word and a `}` followed by `>`; both are legal positions for a tag the module author forgot to wrap.

Tests: 59 (54 golden fixtures + 1 descriptor + 4 corpus).


## 0.5.0 — 2026-10-22

Answer to the four-plugin Publisher bake-off (`../00/inspections02/`). Every reviewer-confirmed defect in 0.4.1 is fixed with a golden fixture, and the rules the other plugins had and this one lacked are added — as warnings where no local rewrite is safe.

Fixed (defects found by the Codex, Grok and Gemini reviews):
- **Receiver proof for every database rule.** `$this->query()` on an application wrapper (Publisher's `VersionControlMySQL`) was renamed to a non-existent `exec()`. `queryF()`, `quoteString()`, mutating `query()` and the fetch guard now act only on a receiver that is the XOOPS database (`$GLOBALS['xoopsDB']`, `$xoopsDB`, `$db`, `$this->db`, `$handler->db`, or a `*Database*`-typed expression) and never inside a database class.
- **Trailing comments.** A guard inserted before `$r = $db->query($sql); // TODO` landed on the same line as the assignment; a comment that shares its line with code is no longer absorbed as the next statement's leading comment.
- **File header reads the whole head.** The licence of XOOPS class files sits in the second docblock after the "You may not change or alter" banner; 39 Publisher files got a duplicate `@license` block. Any comment in the first 4000 characters mentioning `@license`, `license:`, `licensed under` or a copyright ends the check; third-party and test trees, `xoops_version.php` and 404 stubs are skipped; the fix is offered only when the head has no comment at all.
- **DTYPE-aware attribute escaping.** 13 `rawurlencode()` wrappers on an integer `mime_id` were noise. The field's `initVar()` DTYPE is resolved through PhpStorm's type of the receiver, with a fallback scan of the module's `class/` and `src/` trees when the receiver cannot be typed (`foreach ($handler->getObjects() as $obj)`): numeric DTYPEs are never reported, `TXTBOX`/`TXTAREA`/`URL`/`EMAIL` with the default or `'e'` format already escape, `OTHER`/`SOURCE` and format `'n'` are raw and reported as warnings; an unresolved id-like key is skipped. A `getVar()` used as a ternary condition inside the concatenation (`($o->getVar('x') ? $a : $b)`) prints nothing and is no longer a finding. On Publisher's `MimetypesUtility.php` the count goes from 15 to 0.
- **Real-corpus smoke test.** `CorpusSmokeTest` pins four verbatim Publisher files to the facts the review established (wrapper never renamed, reversed condition reported once, guard on its own line after a trailing comment, zero escape findings on integer fields).
- **Scope by file role.** `assets/` is no longer excluded wholesale: an upload endpoint shipped under `assets/js/` is read by the security rules (superglobals, CSRF, fetch guards, unserialize, escaping, dynamic code), while the convention rules (header, guard, status line, form loader, deprecations) skip third-party trees.
- **Cross-module `db:` templates.** `db:tag_bar.tpl` inside Publisher is the tag module's template: a `db:` name whose prefix is not this module's (from `$modversion['dirname']` or the registered templates) is never reported. Registering a template is now an explicit Alt+Enter, excluded from "Fix all".
- **Legacy result checks.** `if (!$result)`, `if (!$result = $db->query(…))` and `if (false === $result)` remain guards (no second check is inserted) and get their own weak finding with an in-place rewrite to `isResultSet()`; `fetch*($db->query(…))` with the query nested in the fetch is reported without a fix; the failure return prefers a `return` that precedes the fetch and falls back to a bare `return;` only in a function that returns nothing.

New inspections (27 in total):
- **DynamicCode** — `$$name`, `${$name}`, `extract($_POST)`, `eval()`, `create_function()`; error, no fix.
- **OrderBy** — `' ORDER BY ' . $criteria->getSort()` and `setSort(Request::getString(…))`; warning, no fix.
- **LegacySuperglobal** — `$HTTP_POST_VARS` and friends (removed in PHP 5.4); error with rename fix.
- **DeprecatedCoreApi** — list generated from the real 2.7.4 core `@deprecated` tags: `XoopsTree`, `XoopsComments`, `Snoopy`, `XoopsErrorHandler`, `XOBJ_DTYPE_UNICODE_*`, `xoops_getModuleOption()` and the other legacy functions, the sanitizer's `makeTboxData4*` family, Smarty 2 method names on `XoopsTpl`, PHP 4 constructor calls (`parent::XoopsObject()`); rename fix only where the replacement takes the same arguments.
- **SmartyDelimiter** — `{if …}` / `{$var}` without the XOOPS `<{ }>` delimiters; fix wraps the tag.
- **SmartyRequestEscape** — `<{$smarty.get.x}>` and other request variables printed without `|escape`; fix appends it.

Severity tiers: conventions (header, guard, status line, result naming) are weak warnings; security findings are warnings; dynamic code, removed superglobals and bare Smarty tags are errors.

Hardened after an adversarial second-opinion review of the 0.5.0 sources (15 findings, all addressed, each with a fixture):
- The guard fix is withheld when no type-compatible failure return exists (non-nullable object, `never`, `static`), and a `$row` that is only assigned after the fetch is never reused as the early return.
- `if ($r = $db->query($sql)) $row = $db->fetchArray($r);` (unbraced, truthiness in the condition) is a legacy guard with the in-place rewrite, not a site for a second guard in front of the `if`; a fetch in the `else` branch of `if (!isResultSet($r))` is guarded, not reversed.
- `"UPDATE t SET title='" . $o->getVar('title', 'n')` is SQL, not an HTML attribute: attribute context now requires an open tag before the attribute in the same literal.
- The URL fix re-reads the field with format `'n'` (`rawurlencode((string) $o->getVar('x', 'n'))`): the default format already holds `&amp;`, which would have become `%26amp%3B`.
- `XOBJ_DTYPE_SOURCE` is raw only for the show/preview formats; the core escapes it for `'e'`.
- `<{$smarty.get.q|json_encode}>` (and `|intval`, `|urlencode`, `|number_format` …) is already encoded and gets no `|escape`.
- `{if(x)` inside JavaScript no longer matches the bare-tag rule (a keyword tag needs a space or `}` after the keyword) and `<script>` / `<style>` blocks are masked.
- A `$db` PhpStorm types as PDO / mysqli / Doctrine is not the XOOPS database, whatever its name.
- `showbanner()`, `makeTareaData4Show()` and `makeTareaData4Preview()` lost their rename fixes (the first echoes, the other two default `$html` to 1 where the replacements default to 0); `registerFilter` / `loadFilters` were dropped from the deprecated-method list (they collide with Smarty's `registerFilter()`); `getOrder()` / `setOrder()` are no longer reported (Criteria normalises them to ASC/DESC).
- The header fix stays inside PHP when the first line closes the tag (`<?php … ?>` followed by HTML).

## 0.4.1 — 2026-10-22

- Fixed: with the sibling prototype plugin installed, nineteen of the twenty inspections were silently dropped at IDE start ("Short name 'XoopsHttpStatus' is not unique" in idea.log) because both plugins registered the same inspection shortNames; only `XoopsMutatingQuery` survived. Every shortName got a plugin-specific prefix, the inspections sit in their own group (Inspections → PHP), and the notification group id is the plugin's own. Existing profile overrides keyed on the old shortNames are reset to the defaults.

## 0.4.0 — 2026-10-21

PSI-first rebuild after the Publisher field review (`../xoops-support/00_docs/`). Every quick-fix is now verified by a whole-file golden fixture, re-applied for idempotence, and computes its edit from the live PSI at apply time — no stored offsets, no regex over file text for code structure.

- Twenty inspections under Inspections → PHP → XOOPS (was five): RootPathGuard, DoubleCpHeader, FormLoader, FileHeader, LanguageLoad, LanguageRole, DeprecatedDbApi, MutatingQuery, ResultSetGuard, Superglobal, AttributeEscape, Csrf, Unserialize, GuestUser, Unlink, Setcookie, HttpStatus, ByRefArgument, MissingRegisteredTemplate, UnregisteredTemplate.
- `XOOPS_ROOT_PATH` guard: a file that bootstraps the core (any top-level require of `mainfile.php`, `header.php`, `cp_header.php`, `admin_header.php`, `print.php` …) is an entry point and is never guarded, even with `error_reporting()` or flag assignments before the require; `preloads/autoloader.php`, 404 stubs, language files and `\defined()` guards are recognised; the guard lands after `namespace`/`declare` and before `use` or the class docblock.
- `xoops_cp_header()` duplicate: reported only when the sibling `admin_header.php` really calls it; the delete fix is excluded from "Fix all".
- `queryF()` migration follows the SQL: `exec()` for a write, `query()` for a read (resolved through a single local `$sql = …` assignment), no rewrite when unknown, `exec()` inside `if ($force)`; `quoteString()` → `quote()` unchanged.
- Attribute escaping wraps the whole accessor expression (`$images['main']->getVar(…)`, `$GLOBALS['x']->getVar(…)`), decides context from the preceding string literal only, uses `rawurlencode()` inside a URL query, and never re-wraps an escaped call.
- Result-set guard understands `while ($db->isResultSet($r) && …)`, `if (!$r) return …` and reassignment, reports a reversed `if (!isResultSet)` as its own finding, and inserts an early return that keeps the function's failure contract — never a `throw`, never `instanceof \mysqli_result`.
- File header fix skips injected PHP fragments (a `"<?php …"` string literal is not a file) and never stamps a file that already has a leading comment; the docblock follows a same-line `declare(strict_types=1);`.
- Form loader fix inserts a real statement in front of the class docblock, never on an existing line or inside a `//` comment; `xoops_load('XoopsForm')` satisfies a class extending `XoopsForm`.
- Superglobal fix is offered only where it preserves meaning (keyed read → `getString`, keyed `isset` → `hasVar`); writes, `empty()`, `=== null`, dynamic keys and bare `$_POST` are reported without a fix; `assets/` is excluded; the imported `Request` alias is reused.
- Templates: a registration whose file lives elsewhere under `templates/` is re-pointed (never stubbed); `db:system_*` includes, templates owned by another installed module and includes inside `<{* *}>` are not reported.
- CSRF: the branch content decides (handler insert/delete, `exec()`, file writes), and a POST-only gate does not cover a `$op` read from GET.
- Noise: one finding per non-canonical language catalog instead of one per constant; unresolved `$sql` is never a "mutating query" finding.
- Build targets PhpStorm 2024.3+ (since-build 243, no upper cap); Gradle wrapper committed; `gradlew test` runs the golden-file suite.

## 0.3.0 — 2026-08-10

- Quick fix on raw superglobal reads: `$_POST['key']` (and `$_GET`/`$_COOKIE`) can be replaced in place with `\Xmf\Request::getString('key', '', 'POST')`; swap in a typed getter (`getInt`, `getBool`, …) afterwards if the value isn't a string. `$_REQUEST` stays fix-less on purpose — a human has to pick the real source.
- Quick fix on unguarded `fetch*()` calls: inserts the two-part guard `if (!$db->isResultSet($result) || !$result instanceof \mysqli_result) { return null; }` before the fetching statement, using the actual receiver and result variable from the call.
- The Plugins UI "What's New" tab now renders this changelog (`CHANGELOG.md` is converted to HTML at build time and embedded as the plugin's change-notes).
- Added `TUTORIAL.md` — a feature-by-feature walkthrough with expected results.
- Tool window got a plugin-specific id — the plain "XOOPS" id collided with the sibling prototype when both plugins are installed, killing this plugin's tool window at IDE startup.

## 0.2.0 — 2026-08-10

Peer-review synthesis release — ideas adopted from the sibling prototypes in `../codex` and `../grok`:

- Language-constant completion: `_MI_`/`_AM_`/`_MD_`/`_MB_`/`_CO_` constants scanned from every `language/` directory, with the owning module shown in the popup; project-level cache invalidated on PSI change.
- XOOPS tool window (right sidebar): module overview (dirname, manifests, template/language counts) plus findings with click-to-navigate.
- Registered-template check: templates declared in `xoops_version.php` but missing on disk (searches `templates/`, `templates/blocks/`, `templates/admin/`, `blocks/`). Validated against 93 real modules.
- Wrong-delimiter check: standard Smarty `{ }` in `.tpl` files where XOOPS needs `<{ }>`.
- "Insert XOOPS_ROOT_PATH guard" quick fix on the direct-access-guard inspection.
- Startup notification gained "Don't show again" (persisted per project in `.idea/xoopsSupport.xml`).

## 0.1.0 — 2026-08-10

- XOOPS project detection: finds `mainfile.php` (root or `htdocs/`) or a standalone module's `xoops_version.php`, reports the core version from `include/version.php` in a notification.
- Five inspections under Inspections → PHP → XOOPS: deprecated `queryF()`/`quoteString()` (with `query()`/`exec()`/`quote()` rename fixes), mutating SQL passed to `query()` (fix to `exec()`), `fetch*()` without an `isResultSet()` guard, raw superglobal reads, missing `XOOPS_ROOT_PATH` guard in class files.
- Five live templates: `xoguard`, `xofetch`, `xolang`, `xocriteria`, `xorequest`.
