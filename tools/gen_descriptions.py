"""Generates src/main/resources/inspectionDescriptions/<ShortName>.html for every inspection.

Run from the plugin root:  python -I tools/gen_descriptions.py
"""
import re
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/inspectionDescriptions"

DESCRIPTIONS = {
    "XoopsAllowlistAnchor": r"""
<p>In PCRE, <code>$</code> matches before a final newline. <code>preg_match('/^[a-z]+$/', $name)</code> accepts <code>"script\n"</code>, and the <code>in_array()</code> / <code>===</code> comparison that follows does not recognise it.</p><p>Reported when the pattern is a single-quoted literal ending in an unescaped <code>$</code> without the <code>D</code> or <code>m</code> modifier and the subject variable is compared exactly later in the same scope, with no reassignment in between (<code>$file = substr($file, 0, -4)</code> compares something else). The fix replaces that <code>$</code> with <code>\z</code>.</p>
""",
    "XoopsAttributeEscape": r"""
<code>getVar()</code> is not an escaper &mdash; <code>XOBJ_DTYPE_OTHER</code> / <code>SOURCE</code> fields
come back raw and attribute context needs <code>ENT_QUOTES</code> regardless.
<p>Context is decided from the string literal that precedes the accessor in the same
concatenation (<code>'&lt;a href="' . $obj-&gt;getVar('url')</code>). PHP code such as
<code>$title = &hellip;</code>, array keys and link text are never attribute context. Inside a URL
query (<code>'?src=' . &hellip;</code>) the fix uses <code>rawurlencode()</code> on the raw value. The fix wraps the complete
call expression, receiver included, and is idempotent.</p>
<p>The DTYPE comes from the class's <code>initVar()</code>; for an untyped receiver, from the one declaration of
the key in the module's <code>class/</code> / <code>src/</code>, else in core <code>kernel/</code>. Numeric DTYPEs are never reported; TXTBOX, TXTAREA,
URL and EMAIL are already <code>htmlspecialchars()</code>'d (<code>ENT_QUOTES</code>) by the default and
<code>'e'</code> formats. The attribute fix is offered only for a raw value (<code>OTHER</code>, <code>SOURCE</code>,
format <code>'n'</code>): when the DTYPE cannot be resolved the finding is a weak note without a fix, because
wrapping an already-escaped value prints <code>&amp;amp;#039;</code>.</p>
""",
    "XoopsByRefArgument": r"""
A call passes a temporary (a call result, a literal) where the resolved declaration takes a reference.
Resolution goes through the PSI, so <code>parent::__construct()</code> is checked against the parent's
signature, not the current file's.
""",
    "XoopsCsrf": r"""
A <code>switch ($op)</code> branch that writes (handler insert / delete, <code>exec()</code>, file writes)
without a CSRF token check.
<p>A branch is covered by its own <code>$GLOBALS['xoopsSecurity']-&gt;check()</code> or
<code>xoops_confirm()</code> (directly or through a function of the same file that checks and stops), by a
top-level gate before the switch, or by an <code>if (!$GLOBALS['xoopsSecurity']-&gt;check())</code> before it
that exits or resets <code>$op</code> for the branch's value: <code>if (in_array($op, ['save', 'delete'])) {
if (!…-&gt;check()) { … } }</code>. A gate behind any other condition, in an <code>else</code> or a loop covers
nothing; a gate that only runs for POST covers nothing when <code>$op</code> is read from GET and the GET
path still reaches the switch. The branch content decides, never the case label.</p>
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
    "XoopsDeprecatedDbApi": r"""
<code>queryF()</code> and <code>quoteString()</code> are deprecated.
<p><code>queryF()</code> is the forced-write alias and delegates to <code>exec()</code>; <code>query()</code>
refuses a mutation on a GET request. The replacement therefore follows the SQL: <code>exec()</code>
for a write, <code>query()</code> for a read (resolved through a single local <code>$sql = &hellip;</code>
assignment when needed), and <strong>no automatic rewrite</strong> when the SQL cannot be resolved.
A call inside <code>if ($force)</code> is a write by intent.</p>
""",
    "XoopsDoubleCpHeader": r"""
A second <code>xoops_cp_header()</code> on one request breaks the admin menu.
<p>The inspection reads the sibling <code>admin_header.php</code>: only when that file really calls
<code>xoops_cp_header()</code> is the page's own call a duplicate. Modules whose header file only
loads <code>cp_header.php</code> keep their call. Without an <code>admin_header.php</code>, a later call
duplicates the page's own first call only when that first call is a top-level statement; one call in
each branch of an <code>if</code> / <code>else</code> renders once.</p>
<p>The delete fix is offered only for the <code>admin_header.php</code> case, and not when
<code>$xoopsOption</code> (for example <code>template_main</code>) changes on the way to the second call. A
duplicate of the page's own call (the system admin pages: one on top, another in an error branch) is
reported without a fix: the page has used the first theme in between, so deleting the second call is not
output-neutral. The fix is never part of "Fix all".</p>
""",
    "XoopsDoubleEncodedAmp": r"""
<code>header('Location: …')</code> and <code>ModuleAdmin::addItemButton()</code> take a raw URL:
<code>renderButton()</code> escapes it and <code>header()</code> sends it as is. A literal
<code>&amp;amp;</code> in that string reaches the server as a parameter named <code>amp;…</code>, so the
token check fails. The fix writes <code>&amp;</code>. A URL printed into HTML is left alone.
<p><code>redirect_header()</code> (and the helper's <code>redirect()</code>) also take a raw URL, but turn
<code>&amp;amp;</code> back into <code>&amp;</code> for their meta refresh, so the browser requests the same URL
either way: there it is a weak note, and the same fix gives identical output.</p>
""",
    "XoopsDuplicateDefine": r"""
The same constant <code>define()</code>d twice in one language file: PHP warns "Constant already defined" on
every request and keeps the first value.
<p>An identical duplicate is deleted by the fix; two different values are reported for a human to choose.</p>
""",
    "XoopsDynamicCode": r"""
Code that runs names as code: <code>$$name</code>, <code>${$name}</code>, <code>extract($_POST)</code>,
<code>eval()</code>, <code>create_function()</code>.
<p>A request array copied into the symbol table lets the client define any variable and is an
error. There is no local rewrite &mdash; the replacement is a different design &mdash; so nothing is
offered as a quick-fix.</p>
""",
    "XoopsFileHeader": r"""
Module PHP files open with a docblock carrying <code>@copyright</code> and <code>@license</code>.
<p>The leading comment is read in full. A file that already has a leading comment without the tag
(third-party MIT / GPL prose) is only noted, never rewritten. The fix is offered only for a file with no
leading comment at all, and only on the host file &mdash; a <code>"&lt;?php &hellip;"</code> string literal
is an injected fragment, never a file.</p>
""",
    "XoopsFormLoader": r"""
<code>XoopsForm*</code> classes are not autoloaded. A file whose class extends one of them needs
<code>xoops_load('XoopsFormLoader')</code> (or a load of exactly the parent class) before the
<code>class</code> keyword.
<p>The fix inserts a real statement in front of the class and its docblock &mdash; never on an existing
line, never inside a comment. The core's own <code>class/xoopsform/</code> is what the loader includes and is
not reported.</p>
""",
    "XoopsGuestUser": r"""
<code>isset($xoopsUser)</code> is true for a guest (the global is <code>''</code>). Test
<code>$xoopsUser instanceof \XoopsUser</code>. A condition that already joins the isset with
<code>is_object($xoopsUser)</code> or <code>instanceof</code> is not reported: <code>isset($u) &amp;&amp; is_object($u)</code>,
or its De Morgan form <code>!isset($u) || !is_object($u)</code>.
""",
    "XoopsHandlerSignature": r"""
A handler extending <code>XoopsPersistableObjectHandler</code> / <code>XoopsObjectHandler</code> must declare
<code>&amp;getObjects(…)</code> and <code>&amp;getAll(…)</code>: the kernel returns by reference and PHP refuses an
incompatible override at class load. Reported only — adding <code>&amp;</code> also requires
<code>return $var;</code> instead of <code>return [];</code>.</p>
""",
    "XoopsHttpStatus": r"""
<code>header('HTTP/1.0 404 Not Found')</code> hard-codes the protocol version. Use
<code>http_response_code(404)</code>.
""",
    "XoopsIncludeOnceHeader": r"""
<p>Reports <code>include</code> or <code>require</code> of <code>header.php</code> / <code>footer.php</code>
without the <code>_once</code> form.</p>
<p>The core header defines functions and starts the theme. When the same request reaches it twice
through different include paths, PHP stops with a "cannot redeclare" fatal. The quick-fix renames the
keyword to <code>include_once</code> / <code>require_once</code> and changes nothing else.</p>
<!-- tooltip end -->
<p>Carried over from XOOPS Support 1.0.0 Alpha 3.</p>
""",
    "XoopsInstallSql": r"""
<p>Rules on the SQL file the installer runs: the file an active <code>$modversion['sqlfile']</code> entry names (<code>sql/mysql.sql</code> when the manifest names none) (resolved from the module root, so <code>sql/english/install.sql</code> counts), or the core's <code>install/sql/</code> schema. Adminer / phpMyAdmin dumps and old upgrade scripts in a module's <code>sql/</code> folder are never run and are not checked, nor is a file that only a comment mentions.</p><ul><li><b>FOREIGN KEY … REFERENCES</b>: <code>SqlUtility::prefixQuery()</code> prefixes only the table being created, never the REFERENCES target. The key dangles to a nonexistent table; a plain INSERT fails with 1452, an INSERT IGNORE silently writes nothing. Enforce the relation in the handler, or add the key in <code>oninstall.php</code> with <code>$db-&gt;prefix()</code>.</li><li><b>Hard-coded prefix</b>: <code>xoops_</code>, <code>{prefix}</code> and <code>%s</code> are not installer tokens; the installer adds the site prefix itself. Use the bare module table name.</li><li><b>ENGINE=MyISAM</b> (weak): no transactions, no row locks; create module tables with InnoDB, which supports FULLTEXT since MySQL 5.6. Not reported on the core installer schema (<code>install/sql/</code>), which keeps its legacy tables MyISAM on purpose.</li></ul><p>Report only: each change alters the schema and belongs to the author.</p>
""",
    "XoopsIsFileAsset": r"""
<p><code>file_exists($x)</code> and <code>is_readable($x)</code> are true for a directory. When the condition's body then includes <code>$x</code> or reads it with <code>readfile()</code>, <code>file_get_contents()</code>, <code>fopen()</code> or similar, a directory name reaches the reader.</p><p>The fix appends <code>&amp;&amp; is_file($x)</code>: it only narrows the condition. A negated check (<code>!file_exists($x)</code>) is reported without a fix, because <code>!a &amp;&amp; b</code> would invert it.</p>
""",
    "XoopsLanguageLoad": r"""
A page script (it boots <code>mainfile.php</code>) uses <code>_MD_</code> / <code>_CO_</code> / <code>_AM_</code>
constants but loads no catalog: neither <code>xoops_loadLanguage()</code>, nor the XMF helper's
<code>loadLanguage()</code>, nor an include of <code>header.php</code> / <code>admin_header.php</code> /
<code>common.php</code>.
<p>A file that does not boot <code>mainfile.php</code> is included by one that did and uses its catalogs
(system admin pages, blocks, <code>admin_footer.php</code>, Smarty plugins): not reported.</p>
""",
    "XoopsLanguageRole": r"""
Language catalogs: <code>_MI_</code> in modinfo.php, <code>_AM_</code> in admin.php, <code>_MD_</code> in
main.php, <code>_MB_</code> in blocks.php, <code>_CO_</code> in common.php.
<p>A canonical file with a foreign prefix gets one note per constant. A non-canonical file
(<code>modinfo1.php</code>, a backup copy) gets one file-level note, unless some code loads it by name
(<code>xoops_loadLanguage('cpanel', 'system')</code>).</p>
<p>Only a module's own catalogs directly under <code>language/&lt;lang&gt;/</code> are checked: the core's
<code>htdocs/language/</code>, the <code>Frameworks/</code> libraries (moduleadmin ships a xoops_version.php but loads its <code>main.php</code> by path) and sub-catalogs such as
<code>language/&lt;lang&gt;/admin/&lt;page&gt;.php</code> follow their own loaders. <code>_MD_AM_</code> (system)
and <code>_MD_A_</code> (Protector, D3) are admin constants by name and belong in admin.php.</p>
""",
    "XoopsLegacySuperglobal": r"""
<code>$HTTP_POST_VARS</code>, <code>$HTTP_GET_VARS</code> and the other long arrays were removed in PHP 5.4:
the page reads <code>null</code>.
<p>The fix renames the variable to its <code>$_POST</code> / <code>$_GET</code> counterpart; the raw
superglobal inspection then takes over.</p>
""",
    "XoopsLocalXoopsOption": r"""
<code>$xoopsOption['template_main'] = 'x.tpl'</code> inside a function or method writes a local array unless the
function declares <code>global $xoopsOption</code>. The page then renders without its template, and a read is an
undefined variable.
<p>The fix writes <code>$GLOBALS['xoopsOption']</code>. Script-level code already runs in the global scope and is
not reported, nor is a function that declares the global or takes <code>$xoopsOption</code> as a parameter.</p>
""",
    "XoopsLocaleConstant": r"""
<code>_AM_&lt;DIRNAME&gt;_X</code>, <code>_MD_…</code>, <code>_MI_…</code>, <code>_MB_…</code>, <code>_CO_…</code>
referenced in the module but <code>define()</code>d in none of its language files: PHP 8 throws
<code>Error: Undefined constant</code> where PHP 7 printed the constant's name.
<p>Only constants carrying the module's own dirname are checked, against PhpStorm's index of every
<code>define()</code> in the project. Reported without a fix.</p>
""",
    "XoopsMenuAutoload": r"""
<code>admin/menu.php</code> is read by the system module's menu builder before the module's own bootstrap:
a <code>XoopsModules\Module\Helper::getInstance()</code> there is "Class not found" unless the file requires
<code>preloads/autoloader.php</code> first.
<p>The fix inserts <code>require_once \dirname(__DIR__) . '/preloads/autoloader.php';</code> before the first
statement that uses the class, when that autoloader exists.</p>
""",
    "XoopsMissingRegisteredTemplate": r"""
A template registered in <code>xoops_version.php</code> is not where the installer will read it:
<code>type =&gt; admin|blocks</code> adds that folder, every other type (including an invented
<code>partial</code>) is <code>templates/{file}</code> as written.
<p>When a file with that name exists elsewhere under <code>templates/</code>, the fix points the
registration at it. No empty stub is ever created.</p>
""",
    "XoopsOrderBy": r"""
<code>' ORDER BY ' . $criteria-&gt;getSort()</code> concatenates whatever <code>setSort()</code> received;
<code>setSort(Request::getString('sort'))</code> hands the column to the client.
<p>Quoting does not help: a column is an identifier, not a string. Allow-list the column or use
<code>XoopsObject::buildOrderBy()</code>. Reported without a fix. <code>getSort()</code> passed to
<code>buildOrderBy()</code> is allow-listed and not reported.</p>
""",
    "XoopsPathContainment": r"""
<p><code>strpos($path, $base) === 0</code> or <code>str_starts_with($path, $base)</code> on a filesystem path is a prefix test, not containment: <code>/safe/../outside/secret</code> starts with <code>/safe</code>, and <code>/uploads2/x</code> starts with <code>/uploads</code>.</p><p>Not reported: a <code>realpath()</code>'d path (directly, through a local, or behind a failure ternary such as <code>$root !== false ? realpath(&hellip;) : false</code>, through a <code>str_replace()</code> that only swaps separators, or a private method's parameter whose every caller in the class passes one) against a base ending in <code>DIRECTORY_SEPARATOR</code> or <code>'/'</code> &mdash; that is containment; a literal needle (<code>'/'</code>, <code>'www.'</code>, <code>'ldaps://'</code>) &mdash; a shape test; a prefix strip for display (<code>str_starts_with($p, $root . '/') ? substr($p, strlen($root)) : $p</code>); a URL, a referer, or a prefix test inside a function that handles URLs (named for URLs or redirects, taking a URL, or calling <code>parse_url()</code>). A resolved path against a base without a separator gets its own message.</p><p>Report only. The fix is <code>realpath()</code> on both sides and a comparison against the directory plus a separator, placed where the path is final.</p>
""",
    "XoopsPreloadGlobal": r"""
<p>A file under <code>preloads/</code> runs inside every module's request. Assigning <code>$xoopsModule</code> or <code>$xoopsModuleConfig</code> there (or declaring them <code>global</code>, the prelude to it) repoints every later reader on the page to the wrong module: "Division by zero", null config keys, in a module that has nothing to do with the preload. Inside a function, <code>$xoopsModule = …</code> is a local unless the function declares it <code>global</code>; <code>$xoopsModule = $GLOBALS['xoopsModule'] ?? null;</code> is a read and is not reported.</p><p>Report only. Load the preload's own config into a local: <code>$cfg = xoops_getHandler('config')-&gt;getConfigsByCat(0, $mod-&gt;getVar('mid'))</code>.</p>
""",
    "XoopsQueryExec": r"""
<code>query()</code> with an INSERT / UPDATE / DELETE / DDL statement: the safe database class refuses it
on GET and strict mode warns on POST. Use <code>exec()</code>.
<p>Only a statement whose SQL resolves to a write is reported &mdash; an unreadable <code>$sql</code> is not
a finding. <code>$force ? $db-&gt;exec($sql) : $db-&gt;query($sql)</code> is the deliberate GET gate of a
non-forced write and is left alone.</p>
""",
    "XoopsResultSetGuard": r"""
<code>fetchArray()</code> / <code>fetchRow()</code> / <code>fetchBoth()</code> on a handle that no
<code>isResultSet()</code> check dominates.
<p>Recognised guards: a positive check in the enclosing <code>if</code> / <code>while</code> condition
(including <code>while ($db-&gt;isResultSet($r) &amp;&amp; $row = &hellip;)</code>), an early-exit
<code>if (!$db-&gt;isResultSet($r))</code> or <code>if (!$r)</code> in a preceding statement, until the
handle is reassigned. An early exit may also end in <code>break</code> / <code>continue</code>; a check in
the same expression guards too (<code>isResultSet($r) &amp;&amp; &hellip; ? fetch($r) : false</code>,
<code>isResultSet($r) &amp;&amp; &hellip; &amp;&amp; fetch($r)</code>), and a handle the function receives as a
parameter is the caller's to guard. A fetch inside <code>if (!$db-&gt;isResultSet($r)) { &hellip; }</code> is a reversed
condition and gets its own message.</p>
<p>The fix inserts an early return that keeps the enclosing function's failure contract
(<code>[]</code>, <code>0</code>, <code>false</code>, <code>null</code>) &mdash; never a <code>throw</code>.</p>
""",
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
    "XoopsSetcookie": r"""
Prefer <code>xoops_setcookie()</code> over <code>setcookie()</code>: same arguments, plus the site-wide
SameSite / domain policy.
<p>Not reported inside <code>xoops_setcookie()</code> itself, nor in Protector, which runs from
<code>mainfile.php</code> before <code>include/common.php</code> defines the wrapper.</p>
""",
    "XoopsSmarty5Api": r"""
<p>Smarty 5 removed <code>assignByRef()</code>, <code>appendByRef()</code>, the <code>register_*()</code> family and the public <code>template_dir</code> / <code>compile_dir</code> properties, and has no global <code>\Smarty</code> class (core 2.8 templates are <code>\XoopsTpl</code>, which extends <code>\Smarty\Smarty</code>).</p><p><code>assignByRef($k, $v)</code> → <code>assign($k, $v)</code> is offered only when <code>$v</code> is never written again in its scope — then by-reference and by-value assignment are the same thing. Everything else is reported for a hand rewrite.</p><p>Smarty 5 ships with core 2.8: on a 2.5 / 2.7 tree (Smarty 2 / 4, detected from <code>include/version.php</code> or the Core Version setting) these calls are the current API and nothing is reported. A standalone module without a core is checked.</p>
""",
    "XoopsSmarty5TemplatePrep": r"""
Constructs that work on Smarty 4 but need rework before a Smarty 5 core: bare <code>&lt;{parent}&gt;</code> /
<code>&lt;{child}&gt;</code>, <code>&lt;{insert}&gt;</code>, <code>&lt;{make_nocache}&gt;</code>,
<code>config_load scope=</code>, and PHP functions used as modifiers (<code>|substr</code>, <code>|trim</code>,
<code>|strtolower</code> …), which Smarty 5 only accepts when registered.
<p>Off by default: enable it for a tree that is being prepared for Smarty 5. There is no quick-fix.</p>
""",
    "XoopsSmartyBlockShorthand": r"""
<code>&lt;{block_parent}&gt;</code> and <code>&lt;{block_child}&gt;</code> are not Smarty tags. The fix writes the
canonical <code>&lt;{$smarty.block.parent}&gt;</code> / <code>&lt;{$smarty.block.child}&gt;</code>, which work on
Smarty 3, 4 and 5.
""",
    "XoopsSmartyDateFormatStrftime": r"""
<code>|date_format:'%d.%m.%Y'</code>: a format with <code>%</code> codes runs <code>strftime()</code>, deprecated
since PHP 8.1 and removed from Smarty 5. Without <code>%</code>, <code>date_format</code> uses <code>date()</code>.
<p>The fix rewrites <code>%Y %y %m %d %e %H %M %S %p %%</code> to <code>Y y m d j H i s A %</code>
(<code>%M</code> is minutes, so it becomes <code>i</code>). A format with a locale token
(<code>%A</code>, <code>%B</code>, <code>%j</code> …) or with literal letters is reported without a fix: letters
are format codes in <code>date()</code>, so <code>'Le %d'</code> cannot be rewritten mechanically.</p>
<p>Not reported on cores older than 2.5.11, whose Smarty always used <code>strftime()</code>.</p>
""",
    "XoopsSmartyForeachItemShadowsFrom": r"""
<code>&lt;{foreach item=story from=$story}&gt;</code>: Smarty 3 and later refuse an item variable with the same
name as the array it loops over.
<p>The fix renames the item to <code>story_item</code> in the tag and in every <code>$story</code> of the loop body,
up to <code>&lt;{foreachelse}&gt;</code> or <code>&lt;{/foreach}&gt;</code>. A nested loop that declares
<code>item=story</code> again keeps its own variable.</p>
""",
    "XoopsSmartyRemovedTag": r"""
<code>&lt;{includeq}&gt;</code> and <code>&lt;{foreachq}&gt;</code> were XOOPS Smarty 2 plugins and no longer exist;
the fix renames them to <code>&lt;{include}&gt;</code> and <code>&lt;{foreach}&gt;</code>.
<p><code>&lt;{php}&gt;</code> and <code>&lt;{include_php}&gt;</code> do not compile on Smarty 3 and later. They have
no fix: move the code into the PHP page and assign the result to the template.</p>
<p>Not reported on cores older than 2.5.11.</p>
""",
    "XoopsSmartyRequestEscape": r"""
<code>&lt;{$smarty.get.x}&gt;</code>, <code>$smarty.request</code>, <code>$smarty.post</code>,
<code>$smarty.cookie</code> and <code>$smarty.server</code> print the request into the page.
<p>Unless the tag carries <code>|escape</code> that is reflected XSS. The fix appends
<code>|escape</code> after any other modifier.</p>
""",
    "XoopsSmartyUnquotedPluginArg": r"""
<code>&lt;{xoAdminIcons home.png}&gt;</code>: since Smarty 3 (XOOPS 2.5.11) a bare plugin argument is parsed as
an expression and the template does not compile.
<p>The fix quotes it: <code>&lt;{xoAdminIcons 'home.png'}&gt;</code>. Single quotes are used everywhere, also
inside a single-quoted HTML attribute, because Smarty removes the whole tag before the browser sees it and most
<code>compiler.xo*.php</code> plugins strip only <code>'</code>.</p>
<p>Not reported on cores older than 2.5.11, where the argument had to be bare.</p>
""",
    "XoopsSqlComment": r"""
The module installer treats a line of <code>sql/*.sql</code> as a comment only when it starts with
<code>-- </code> (dash, dash, space) or <code>#</code>. A bare <code>--</code> or <code>--text</code> stays glued
to the next statement, the <code>CREATE TABLE</code> prefixer then fails, and that table is silently never
created. The SQL file is checked from the <code>$modversion['sqlfile']</code> entry in
<code>xoops_version.php</code>; the fix inserts the space in the SQL file.</p>
""",
    "XoopsSqlConcat": r"""
<code>$_GET['id']</code>, <code>$_POST[…]</code>, <code>$_REQUEST[…]</code> or <code>Request::getString()</code>
concatenated into the SQL that reaches <code>query()</code> / <code>exec()</code> / <code>queryF()</code>, with
no <code>quote()</code>, <code>(int)</code> or <code>intval()</code> around it. The <code>$sql</code> variable is
followed back to its assignment in the same block. Reported only: the right fix (quote, cast or allow-list)
depends on the column.</p>
""",
    "XoopsSqlFileComment": r"""
<p>A line that is exactly <code>--</code> is not a comment for <code>SqlUtility::splitMySqlFile()</code>; only <code>-- </code> (dash dash space) and <code>#</code> are. The bare line stays glued to the next statement, <code>prefixQuery()</code> fails its <code>^CREATE TABLE</code> match and the installer skips that table with no error.</p><p>Checked on the file the installer runs: the file an active <code>$modversion['sqlfile']</code> entry names (<code>sql/mysql.sql</code> when the manifest names none), or the core's <code>install/sql/</code> schema. Dumps and old upgrade scripts beside it are not checked.</p><p>One finding per line. The fix inserts the space on that line only, re-checking the live line text, so it is safe in any batch order. The same defect is also reported on <code>xoops_version.php</code> by "Bare -- comment in the SQL file".</p>
""",
    "XoopsStrictCalendarCast": r"""
Under <code>declare(strict_types=1)</code>, <code>mktime(0, 0, 0, date('n'), 1, date('Y'))</code> throws a
TypeError on every call: <code>date()</code> returns a string and the calendar functions take <code>int</code>.
<p>The fix wraps the argument in <code>(int)</code>; on a numeric string the cast is exact.</p>
""",
    "XoopsSuperglobal": r"""
Raw <code>$_GET</code> / <code>$_POST</code> / <code>$_COOKIE</code> reads go through <code>Xmf\Request</code>;
<code>$_REQUEST</code> is banned outright.
<p>The rewrite is offered only where it preserves meaning: a keyed read becomes
<code>Request::getString('key', '', 'POST')</code>, a keyed <code>isset()</code> becomes
<code>Request::hasVar()</code>. A write, <code>unset()</code>, <code>empty()</code>, a <code>=== null</code>
comparison, a dynamic key or a bare <code>$_POST</code> are reported without a fix. An assignment to <code>$_POST</code> itself
gets its own message: it changes what every later reader sees, <code>Xmf\Request</code> included, which may be the point.</p>
<p>Not inspected: Protector (the request firewall rewrites the raw superglobals before XOOPS boots), the core installer and upgrader (they run before the
xoops_lib path is known, so <code>Xmf\Request</code> cannot load) and, like
every security rule, test trees (<code>tests/</code>, <code>test/</code>, <code>testdata/</code>, <code>fixtures/</code>).</p>
""",
    "XoopsThemeConstant": r"""
<code>define('THEME_…')</code> in a module language file. The <code>THEME_*</code> constants belong to the
theme's own language pack; whichever file loads second warns and the theme's text loses. Reported only.</p>
""",
    "XoopsUnlink": r"""
<code>unlink($path)</code> with no <code>is_file()</code> / <code>file_exists()</code> on the same expression
dominating it. A check in the enclosing <code>if</code> or a preceding early-exit counts, at any block level up to
the function, and also inside a previous <code>try</code> whose every <code>catch</code> exits as well.
<code>break</code> / <code>continue</code> count as an exit. Also accepted: a <code>$this-&gt;exists()</code>-style helper of the
same class whose body checks the same <code>$this-&gt;…</code> path; a check on <code>$x</code> when the unlinked path is
<code>realpath($x)</code>; and <code>if (false === file_put_contents($p, …)) return;</code> (or <code>touch()</code>): the code
created the file itself. An <code>SplFileInfo</code> object's <code>isFile()</code> counts when its
<code>getRealPath()</code> or <code>getPathname()</code> is unlinked, directly or through a variable (a
<code>DirectoryIterator</code> loop), and its <code>isLink()</code> counts when <code>getPathname()</code> (the link
itself) is unlinked. A check behind an unrelated <code>&amp;&amp;</code> (<code>if ($a &amp;&amp; !is_file($p)) return;</code>)
does not count, because a false <code>$a</code> skips it; nor does <code>isFile()</code> before <code>getPath()</code>
(the containing directory) or <code>isLink()</code> before <code>getRealPath()</code> (the link's target).
""",
    "XoopsUnregisteredTemplate": r"""
A module <code>.tpl</code> the manifest does not know, or a <code>db:</code> include nobody registers.
<p>Core <code>system_*</code> templates, templates whose prefix belongs to another module
(<code>db:tag_bar.tpl</code> inside Publisher) and includes inside a Smarty comment are not reported.
Registering a template changes what the installer stores, so the fix is an explicit Alt+Enter and
never part of "Fix all".</p>
""",
    "XoopsUnserialize": r"""
<code>unserialize()</code> without <code>['allowed_classes' =&gt; false]</code> can instantiate arbitrary classes.
<p>The fix adds the option; the argument count comes from the PSI, so an inner call with commas is handled.</p>
""",
    "XoopsUpdateCreatesTable": r"""
<p>XOOPS runs <code>sql/mysql.sql</code> only on a fresh install. When a site updates a module it already has, that file is not run again, so a table added in a later version must also be created by the module's update code.</p><p>Example: version 1.0 ships tables <code>a</code> and <code>b</code>, and version 1.1 adds table <code>c</code> to <code>mysql.sql</code>. A new install of 1.1 gets all three. A site updating from 1.0 still has only <code>a</code> and <code>b</code>, and the first page that uses <code>c</code> fails with MySQL error 1146 (table doesn't exist). xnewsletter's <code>include/onupdate.php</code> shows the fix: one update step per version that added a table, each creating it if it is missing.</p><p>This note marks every <code>CREATE TABLE</code> that no update code mentions. The plugin cannot tell whether the table is new. If it has existed since the module's first release, every site already has it and the note can be ignored. If it was added later, add a create-if-missing step to the update code. That is why it is only a weak note.</p><p>Update paths searched: <code>onupdate.php</code>, <code>include/onupdate.php</code>, <code>include/update.php</code>, <code>include/install.php</code> and every YAML under <code>sql/</code>. An update that re-runs the SQL file or calls <code>createMissingTables()</code> / <code>synchronizeSchema()</code> covers every table. Modules without any update callback are not reported, nor is an SQL file the installer never runs: no active <code>$modversion['sqlfile']</code> (commented out or <code>false</code>) and no install callback that reads <code>mysql.sql</code> itself.</p><p>Report only: the idempotent create (table-exists check, migrate YAML) belongs to the author.</p>
""",
    "XoopsWeakToken": r"""
<ul><li>A variable named <code>*token*</code>, <code>*nonce*</code> or <code>*csrf*</code> assigned from <code>rand()</code>, <code>mt_rand()</code> or <code>uniqid()</code>: predictable. Use <code>bin2hex(random_bytes(n))</code> sized to the storage column, or the core token API.</li><li><code>md5()</code> / <code>sha1()</code> of a <code>*pass*</code> / <code>*secret*</code> variable: not a password hash. Use <code>password_hash()</code> / <code>password_verify()</code>. Compared with a value or passed as a <code>Criteria</code> value, it looks up a hash an old XOOPS stored (the 2.2 login fallback): a weak note to keep it only as a migration path and rehash on success once the password column holds modern hashes (the upgrader widens it to 255 in 2.5.8; before that a bcrypt hash would not fit).</li><li><code>==</code>, <code>!=</code>, <code>strcmp()</code> on a token, secret or password: <code>'0e123' == '0e456'</code> is true, and the comparison is not constant-time. Use <code>hash_equals()</code> on two strings. A comparison with a literal that is not a numeric string (<code>$pass == ''</code>, <code>$addtoken != false</code>) is an emptiness or flag test and is not reported.</li></ul><p>Fix: when both sides are request text (<code>Request::getString()</code> / <code>getText()</code> / <code>getPath()</code> / <code>getUrl()</code>, or <code>Request::getVar()</code> with a literal default and type <code>'string'</code>), assigned nowhere else in the scope, <code>!=</code> becomes <code>!==</code> and <code>==</code> becomes <code>===</code>. For such values the strict form only ever rejects more, and only where the loose one calls two different inputs equal &mdash; the password-confirmation case of <code>edituser.php</code>. Everything else is report only: the token's storage width and consumers are unknown, so no generator is swapped in.</p>
""",
    "XoopsWrongSmartyDelimiter": r"""
XOOPS compiles Smarty with <code>&lt;{</code> <code>}&gt;</code> delimiters: a bare <code>{if $x}</code> or
<code>{$var}</code> is printed literally.
<p>Comments (<code>&lt;{* *}&gt;</code>) and <code>&lt;{literal}&gt;</code> blocks are masked; CSS and JavaScript
braces do not match because only Smarty keywords and <code>{$…}</code> are looked for. The fix wraps the
tag in <code>&lt;{ }&gt;</code>.</p>
""",
}

OUT.mkdir(parents=True, exist_ok=True)
for name, body in DESCRIPTIONS.items():
    # lang + title keep SonarCloud's web rules (S5254, S1098) quiet; the IDE renders only the body.
    title = "XOOPS: " + re.sub(r"(?<!^)(?=[A-Z])", " ", name.removeprefix("Xoops"))
    html = ("<!doctype html>\n<html lang=\"en\">\n<head><title>" + title + "</title></head>\n<body>\n"
            + body.strip() + "\n</body>\n</html>\n")
    # newline="\n": the repository stores these pages with LF; Windows' default would write CRLF.
    (OUT / f"{name}.html").write_text(html, encoding="utf-8", newline="\n")
print(f"wrote {len(DESCRIPTIONS)} descriptions to {OUT}")
