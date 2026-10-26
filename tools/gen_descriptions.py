"""Generates src/main/resources/inspectionDescriptions/<ShortName>.html for every inspection.

Run from the plugin root:  python -I tools/gen_descriptions.py
"""
import re
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/inspectionDescriptions"

DESCRIPTIONS = {
    "XoopsRootPathGuard": r"""
Include-only PHP files (classes, preloads, includes, blocks) start with
<pre>defined('XOOPS_ROOT_PATH') || exit('Restricted access');</pre>
<p>A file that bootstraps the core itself &mdash; any top-level <code>require</code> of
<code>mainfile.php</code>, <code>header.php</code>, <code>cp_header.php</code>,
<code>admin_header.php</code>, <code>print.php</code> &hellip; &mdash; is a public entry point and is
never reported, even when <code>error_reporting()</code> or flag assignments precede that require.
<code>preloads/autoloader.php</code>, language files, tests, 404 stubs and fully qualified
<code>\defined()</code> guards are recognised.</p>
<p>The quick-fix inserts the guard after <code>namespace</code> / <code>declare</code> and before
<code>use</code> or the class docblock, on its own line.</p>
""",
    "XoopsDoubleCpHeader": r"""
A second <code>xoops_cp_header()</code> on one request breaks the admin menu.
<p>The inspection reads the sibling <code>admin_header.php</code>: only when that file really calls
<code>xoops_cp_header()</code> is the page's own call a duplicate. Modules whose header file only
loads <code>cp_header.php</code> keep their call. The delete fix is deliberately not offered in
"Fix all".</p>
""",
    "XoopsAttributeEscape": r"""
<code>getVar()</code> is not an escaper &mdash; <code>XOBJ_DTYPE_OTHER</code> / <code>SOURCE</code> fields
come back raw and attribute context needs <code>ENT_QUOTES</code> regardless.
<p>Context is decided from the string literal that precedes the accessor in the same
concatenation (<code>'&lt;a href="' . $obj-&gt;getVar('url')</code>). PHP code such as
<code>$title = &hellip;</code>, array keys and link text are never attribute context. Inside a URL
query (<code>'?src=' . &hellip;</code>) the fix uses <code>rawurlencode()</code>. The fix wraps the complete
call expression, receiver included, and is idempotent.</p>
""",
    "XoopsDeprecatedDbApi": r"""
<code>queryF()</code> and <code>quoteString()</code> are deprecated.
<p><code>queryF()</code> is the forced-write alias and delegates to <code>exec()</code>; <code>query()</code>
refuses a mutation on a GET request. The replacement therefore follows the SQL: <code>exec()</code>
for a write, <code>query()</code> for a read (resolved through a single local <code>$sql = &hellip;</code>
assignment when needed), and <strong>no automatic rewrite</strong> when the SQL cannot be resolved.
A call inside <code>if ($force)</code> is a write by intent.</p>
""",
    "XoopsMutatingQuery": r"""
<code>query()</code> with an INSERT / UPDATE / DELETE / DDL statement: the safe database class refuses it
on GET and strict mode warns on POST. Use <code>exec()</code>.
<p>Only a statement whose SQL resolves to a write is reported &mdash; an unreadable <code>$sql</code> is not
a finding.</p>
""",
    "XoopsFileHeader": r"""
Module PHP files open with a docblock carrying <code>@copyright</code> and <code>@license</code>.
<p>The leading comment is read in full. A file that already has a leading comment without the tag
(third-party MIT / GPL prose) is only noted, never rewritten. The fix is offered only for a file with no
leading comment at all, and only on the host file &mdash; a <code>"&lt;?php &hellip;"</code> string literal
is an injected fragment, never a file.</p>
""",
    "XoopsResultSetGuard": r"""
<code>fetchArray()</code> / <code>fetchRow()</code> / <code>fetchBoth()</code> on a handle that no
<code>isResultSet()</code> check dominates.
<p>Recognised guards: a positive check in the enclosing <code>if</code> / <code>while</code> condition
(including <code>while ($db-&gt;isResultSet($r) &amp;&amp; $row = &hellip;)</code>), an early-exit
<code>if (!$db-&gt;isResultSet($r))</code> or <code>if (!$r)</code> in a preceding statement, until the
handle is reassigned. A fetch inside <code>if (!$db-&gt;isResultSet($r)) { &hellip; }</code> is a reversed
condition and gets its own message.</p>
<p>The fix inserts an early return that keeps the enclosing function's failure contract
(<code>[]</code>, <code>0</code>, <code>false</code>, <code>null</code>) &mdash; never a <code>throw</code>.</p>
""",
    "XoopsFormLoader": r"""
<code>XoopsForm*</code> classes are not autoloaded. A file whose class extends one of them needs
<code>xoops_load('XoopsFormLoader')</code> (or a load of exactly the parent class) before the
<code>class</code> keyword.
<p>The fix inserts a real statement in front of the class and its docblock &mdash; never on an existing
line, never inside a comment.</p>
""",
    "XoopsSuperglobal": r"""
Raw <code>$_GET</code> / <code>$_POST</code> / <code>$_COOKIE</code> reads go through <code>Xmf\Request</code>;
<code>$_REQUEST</code> is banned outright.
<p>The rewrite is offered only where it preserves meaning: a keyed read becomes
<code>Request::getString('key', '', 'POST')</code>, a keyed <code>isset()</code> becomes
<code>Request::hasVar()</code>. A write, <code>unset()</code>, <code>empty()</code>, a <code>=== null</code>
comparison, a dynamic key or a bare <code>$_POST</code> are reported without a fix.</p>
""",
    "XoopsUnserialize": r"""
<code>unserialize()</code> without <code>['allowed_classes' =&gt; false]</code> can instantiate arbitrary classes.
<p>The fix adds the option; the argument count comes from the PSI, so an inner call with commas is handled.</p>
""",
    "XoopsHttpStatus": r"""
<code>header('HTTP/1.0 404 Not Found')</code> hard-codes the protocol version. Use
<code>http_response_code(404)</code>.
""",
    "XoopsSetcookie": r"""
Prefer <code>xoops_setcookie()</code> over <code>setcookie()</code>: same arguments, plus the site-wide
SameSite / domain policy.
""",
    "XoopsGuestUser": r"""
<code>isset($xoopsUser)</code> is true for a guest (the global is <code>''</code>). Test
<code>$xoopsUser instanceof \XoopsUser</code>. A condition that already includes
<code>is_object($xoopsUser)</code> or <code>instanceof</code> is not reported.
""",
    "XoopsUnlink": r"""
<code>unlink($path)</code> with no <code>is_file()</code> / <code>file_exists()</code> on the same expression
dominating it. A check in the enclosing <code>if</code> or a preceding early-exit counts.
""",
    "XoopsByRefArgument": r"""
A call passes a temporary (a call result, a literal) where the resolved declaration takes a reference.
Resolution goes through the PSI, so <code>parent::__construct()</code> is checked against the parent's
signature, not the current file's.
""",
    "XoopsLanguageLoad": r"""
A page script uses <code>_MD_</code> / <code>_CO_</code> / <code>_AM_</code> constants but loads no catalog:
neither <code>xoops_loadLanguage()</code>, nor the XMF helper's <code>loadLanguage()</code>, nor an include
of <code>header.php</code> / <code>admin_header.php</code> / <code>common.php</code>.
""",
    "XoopsCsrf": r"""
A <code>switch ($op)</code> branch that writes (handler insert / delete, <code>exec()</code>, file writes)
without a CSRF token check.
<p>A branch is covered by its own <code>$GLOBALS['xoopsSecurity']-&gt;check()</code> or
<code>xoops_confirm()</code>, or by a top-level gate before the switch &mdash; but a gate that only runs for
POST covers nothing when <code>$op</code> is read from GET. The branch content decides, never the case
label.</p>
""",
    "XoopsLanguageRole": r"""
Language catalogs: <code>_MI_</code> in modinfo.php, <code>_AM_</code> in admin.php, <code>_MD_</code> in
main.php, <code>_MB_</code> in blocks.php, <code>_CO_</code> in common.php.
<p>A canonical file with a foreign prefix gets one note per constant. A non-canonical file
(<code>modinfo1.php</code>, a backup copy) gets one file-level note.</p>
""",
    "XoopsMissingRegisteredTemplate": r"""
A template registered in <code>xoops_version.php</code> is not where the installer will read it:
<code>type =&gt; admin|blocks</code> adds that folder, every other type (including an invented
<code>partial</code>) is <code>templates/{file}</code> as written.
<p>When a file with that name exists elsewhere under <code>templates/</code>, the fix points the
registration at it. No empty stub is ever created.</p>
""",
    "XoopsUnregisteredTemplate": r"""
A module <code>.tpl</code> the manifest does not know, or a <code>db:</code> include nobody registers.
<p>Core <code>system_*</code> templates, templates whose prefix belongs to another module
(<code>db:tag_bar.tpl</code> inside Publisher) and includes inside a Smarty comment are not reported.
Registering a template changes what the installer stores, so the fix is an explicit Alt+Enter and
never part of "Fix all".</p>
""",
    "XoopsDynamicCode": r"""
Code that runs names as code: <code>$$name</code>, <code>${$name}</code>, <code>extract($_POST)</code>,
<code>eval()</code>, <code>create_function()</code>.
<p>A request array copied into the symbol table lets the client define any variable and is an
error. There is no local rewrite &mdash; the replacement is a different design &mdash; so nothing is
offered as a quick-fix.</p>
""",
    "XoopsOrderBy": r"""
<code>' ORDER BY ' . $criteria-&gt;getSort()</code> concatenates whatever <code>setSort()</code> received;
<code>setSort(Request::getString('sort'))</code> hands the column to the client.
<p>Quoting does not help: a column is an identifier, not a string. Allow-list the column or use
<code>XoopsObject::buildOrderBy()</code>. Reported without a fix.</p>
""",
    "XoopsLegacySuperglobal": r"""
<code>$HTTP_POST_VARS</code>, <code>$HTTP_GET_VARS</code> and the other long arrays were removed in PHP 5.4:
the page reads <code>null</code>.
<p>The fix renames the variable to its <code>$_POST</code> / <code>$_GET</code> counterpart; the raw
superglobal inspection then takes over.</p>
""",
    "XoopsDeprecatedCoreApi": r"""
Core APIs the 2.7.4 tree marks <code>@deprecated</code> (list generated from the real
<code>class/</code>, <code>kernel/</code> and <code>include/</code> docblocks) plus pre-2.5 idioms that still
surface in old modules: <code>XoopsTree</code>, <code>XoopsComments</code>, <code>Snoopy</code>,
<code>XOBJ_DTYPE_UNICODE_*</code>, <code>xoops_getModuleOption()</code>, the sanitizer's
<code>makeTboxData4*()</code> family, Smarty 2 method names on <code>XoopsTpl</code>, and PHP 4
constructor calls (<code>parent::XoopsObject()</code>).
<p>PhpStorm's own deprecation inspection needs the core in the project; this one works on a
standalone module and says what to use instead. A rename fix is offered only where the
replacement takes the same arguments (<code>destoryVars</code> &rarr; <code>destroyVars</code>,
<code>parent::XoopsObject()</code> &rarr; <code>parent::__construct()</code>, <code>assign_by_ref</code> &rarr;
<code>assignByRef</code> &hellip;). The UNICODE dtypes are never renamed: they decide how stored rows
are decoded.</p>
""",
    "XoopsSmartyDelimiter": r"""
XOOPS compiles Smarty with <code>&lt;{</code> <code>}&gt;</code> delimiters: a bare <code>{if $x}</code> or
<code>{$var}</code> is printed literally.
<p>Comments (<code>&lt;{* *}&gt;</code>) and <code>&lt;{literal}&gt;</code> blocks are masked; CSS and JavaScript
braces do not match because only Smarty keywords and <code>{$…}</code> are looked for. The fix wraps the
tag in <code>&lt;{ }&gt;</code>.</p>
""",
    "XoopsDoubleEncodedAmp": r"""
<code>redirect_header()</code>, <code>header()</code> and <code>ModuleAdmin::addItemButton()</code> take a raw URL:
<code>renderButton()</code> escapes it and <code>redirect_header()</code> sends it as is. A literal
<code>&amp;amp;</code> in that string reaches the server as a parameter named <code>amp;…</code>, so the
token check fails. The fix writes <code>&amp;</code>. A URL printed into HTML is left alone.</p>
""",
    "XoopsStrictCalendarCast": r"""
Under <code>declare(strict_types=1)</code>, <code>mktime(0, 0, 0, date('n'), 1, date('Y'))</code> throws a
TypeError on every call: <code>date()</code> returns a string and the calendar functions take <code>int</code>.
<p>The fix wraps the argument in <code>(int)</code>; on a numeric string the cast is exact.</p>
""",
    "XoopsLocaleConstant": r"""
<code>_AM_&lt;DIRNAME&gt;_X</code>, <code>_MD_…</code>, <code>_MI_…</code>, <code>_MB_…</code>, <code>_CO_…</code>
referenced in the module but <code>define()</code>d in none of its language files: PHP 8 throws
<code>Error: Undefined constant</code> where PHP 7 printed the constant's name.
<p>Only constants carrying the module's own dirname are checked, against PhpStorm's index of every
<code>define()</code> in the project. Reported without a fix.</p>
""",
    "XoopsDuplicateDefine": r"""
The same constant <code>define()</code>d twice in one language file: PHP warns "Constant already defined" on
every request and keeps the first value.
<p>An identical duplicate is deleted by the fix; two different values are reported for a human to choose.</p>
""",
    "XoopsThemeConstant": r"""
<code>define('THEME_…')</code> in a module language file. The <code>THEME_*</code> constants belong to the
theme's own language pack; whichever file loads second warns and the theme's text loses. Reported only.</p>
""",
    "XoopsMenuAutoload": r"""
<code>admin/menu.php</code> is read by the system module's menu builder before the module's own bootstrap:
a <code>XoopsModules\Module\Helper::getInstance()</code> there is "Class not found" unless the file requires
<code>preloads/autoloader.php</code> first.
<p>The fix inserts <code>require_once \dirname(__DIR__) . '/preloads/autoloader.php';</code> before the first
statement that uses the class, when that autoloader exists.</p>
""",
    "XoopsHandlerSignature": r"""
A handler extending <code>XoopsPersistableObjectHandler</code> / <code>XoopsObjectHandler</code> must declare
<code>&amp;getObjects(…)</code> and <code>&amp;getAll(…)</code>: the kernel returns by reference and PHP refuses an
incompatible override at class load. Reported only — adding <code>&amp;</code> also requires
<code>return $var;</code> instead of <code>return [];</code>.</p>
""",
    "XoopsSqlConcat": r"""
<code>$_GET['id']</code>, <code>$_POST[…]</code>, <code>$_REQUEST[…]</code> or <code>Request::getString()</code>
concatenated into the SQL that reaches <code>query()</code> / <code>exec()</code> / <code>queryF()</code>, with
no <code>quote()</code>, <code>(int)</code> or <code>intval()</code> around it. The <code>$sql</code> variable is
followed back to its assignment in the same block. Reported only: the right fix (quote, cast or allow-list)
depends on the column.</p>
""",
    "XoopsSqlComment": r"""
The module installer treats a line of <code>sql/*.sql</code> as a comment only when it starts with
<code>-- </code> (dash, dash, space) or <code>#</code>. A bare <code>--</code> or <code>--text</code> stays glued
to the next statement, the <code>CREATE TABLE</code> prefixer then fails, and that table is silently never
created. The SQL file is checked from the <code>$modversion['sqlfile']</code> entry in
<code>xoops_version.php</code>; the fix inserts the space in the SQL file.</p>
""",
    "XoopsSmartyRequestEscape": r"""
<code>&lt;{$smarty.get.x}&gt;</code>, <code>$smarty.request</code>, <code>$smarty.post</code>,
<code>$smarty.cookie</code> and <code>$smarty.server</code> print the request into the page.
<p>Unless the tag carries <code>|escape</code> that is reflected XSS. The fix appends
<code>|escape</code> after any other modifier.</p>
""",
}

OUT.mkdir(parents=True, exist_ok=True)
for name, body in DESCRIPTIONS.items():
    # lang + title keep SonarCloud's web rules (S5254, S1098) quiet; the IDE renders only the body.
    title = "XOOPS: " + re.sub(r"(?<!^)(?=[A-Z])", " ", name.removeprefix("Xoops"))
    html = ("<!doctype html>\n<html lang=\"en\">\n<head><title>" + title + "</title></head>\n<body>\n"
            + body.strip() + "\n</body>\n</html>\n")
    (OUT / f"{name}.html").write_text(html, encoding="utf-8")
print(f"wrote {len(DESCRIPTIONS)} descriptions to {OUT}")
