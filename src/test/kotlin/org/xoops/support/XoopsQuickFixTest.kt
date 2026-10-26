package org.xoops.support

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.PhpFile
import org.xoops.support.inspections.XoopsAttributeEscapeInspection
import org.xoops.support.inspections.XoopsAllowlistAnchorInspection
import org.xoops.support.inspections.XoopsInstallSqlInspection
import org.xoops.support.inspections.XoopsIsFileAssetInspection
import org.xoops.support.inspections.XoopsPathContainmentInspection
import org.xoops.support.inspections.XoopsPreloadGlobalInspection
import org.xoops.support.inspections.XoopsSmarty5ApiInspection
import org.xoops.support.inspections.XoopsSqlFileCommentInspection
import org.xoops.support.inspections.XoopsUpdateCreatesTableInspection
import org.xoops.support.inspections.XoopsWeakTokenInspection
import org.xoops.support.inspections.XoopsByRefArgumentInspection
import org.xoops.support.inspections.XoopsCsrfInspection
import org.xoops.support.inspections.XoopsDeprecatedCoreApiInspection
import org.xoops.support.inspections.XoopsDeprecatedDbApiInspection
import org.xoops.support.inspections.XoopsDoubleEncodedAmpInspection
import org.xoops.support.inspections.XoopsDuplicateDefineInspection
import org.xoops.support.inspections.XoopsDynamicCodeInspection
import org.xoops.support.inspections.XoopsHandlerSignatureInspection
import org.xoops.support.inspections.XoopsLocaleConstantInspection
import org.xoops.support.inspections.XoopsMenuAutoloadInspection
import org.xoops.support.inspections.XoopsSqlCommentInspection
import org.xoops.support.inspections.XoopsSqlConcatInspection
import org.xoops.support.inspections.XoopsStrictCalendarCastInspection
import org.xoops.support.inspections.XoopsThemeConstantInspection
import org.xoops.support.inspections.XoopsLegacySuperglobalInspection
import org.xoops.support.inspections.XoopsOrderByInspection
import org.xoops.support.inspections.XoopsSmartyDelimiterInspection
import org.xoops.support.inspections.XoopsSmartyRequestEscapeInspection
import org.xoops.support.inspections.XoopsSmarty5TemplatePrepInspection
import org.xoops.support.inspections.XoopsLocalXoopsOptionInspection
import org.xoops.support.inspections.XoopsSmartyBlockShorthandInspection
import org.xoops.support.inspections.XoopsSmartyDateFormatStrftimeInspection
import org.xoops.support.inspections.XoopsSmartyForeachItemShadowsFromInspection
import org.xoops.support.inspections.XoopsSmartyRemovedTagInspection
import org.xoops.support.inspections.XoopsSmartyUnquotedPluginArgInspection
import org.xoops.support.inspections.XoopsDoubleCpHeaderInspection
import org.xoops.support.inspections.XoopsFileHeaderInspection
import org.xoops.support.inspections.XoopsFormLoaderInspection
import org.xoops.support.inspections.XoopsGuestUserInspection
import org.xoops.support.inspections.XoopsHttpStatusInspection
import org.xoops.support.inspections.XoopsIncludeOnceHeaderInspection
import org.xoops.support.inspections.XoopsLanguageLoadInspection
import org.xoops.support.inspections.XoopsLanguageRoleInspection
import org.xoops.support.inspections.XoopsMissingRegisteredTemplateInspection
import org.xoops.support.inspections.XoopsMutatingQueryInspection
import org.xoops.support.inspections.XoopsResultSetGuardInspection
import org.xoops.support.inspections.XoopsRootPathGuardInspection
import org.xoops.support.inspections.XoopsSetcookieInspection
import org.xoops.support.inspections.XoopsSuperglobalInspection
import org.xoops.support.inspections.XoopsUnlinkInspection
import org.xoops.support.inspections.XoopsUnregisteredTemplateInspection
import org.xoops.support.inspections.XoopsUnserializeInspection

/**
 * Golden-file contract for every inspection: a fixture tree is copied into a light project,
 * the target file is highlighted, the problem count is asserted, every quick-fix of the
 * family is applied through the IDE (re-computed after each application, exactly as a
 * batch run does), the resulting file is compared whole, and a second highlighting pass
 * must offer no further fix of that family (idempotence). [fixFamily] is matched as a prefix of the fix's
 * displayed text (its `getName()`), not of `getFamilyName()`.
 */
class XoopsQuickFixTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/resources/fixtures"

    /** PhpStorm's bundled line-marker providers occasionally log errors during highlighting; they are not under test. */
    override fun runTestRunnable(testRunnable: com.intellij.util.ThrowableRunnable<Throwable>) {
        com.intellij.testFramework.LoggedErrorProcessor.executeWith<Throwable>(object : com.intellij.testFramework.LoggedErrorProcessor() {
            override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> =
                if (message.contains("LineMarkerProvider") || message.contains("recursion prevention") || message.contains("php.type.info")) Action.NONE else Action.ALL
        }) { super.runTestRunnable(testRunnable) }
    }

    private fun xoopsProblems(): List<String> =
        myFixture.doHighlighting().mapNotNull { it.description }.filter { it.startsWith("XOOPS:") }

    private fun check(
        inspection: LocalInspectionTool,
        dir: String,
        target: String,
        problems: Int,
        fixFamily: String? = null,
        after: String? = null,
        remaining: Int = 0,
        alsoCheck: Pair<String, String>? = null,
    ) {
        myFixture.copyDirectoryToProject(dir, "")
        myFixture.enableInspections(inspection)
        myFixture.configureFromTempProjectFile(target)
        val found = xoopsProblems()
        assertEquals("problems in $dir/$target:\n" + found.joinToString("\n"), problems, found.size)
        if (fixFamily == null) return

        var rounds = 0
        while (rounds++ < 30) {
            val fix = myFixture.getAllQuickFixes().firstOrNull { it.familyName.startsWith(fixFamily) || it.text.startsWith(fixFamily) } ?: break
            myFixture.launchAction(fix)
        }
        if (after != null) {
            val expected = java.io.File(testDataPath, "$dir/$after").readText(Charsets.UTF_8)
            val seen = myFixture.getAllQuickFixes().joinToString("\n") { "  fix: " + it.familyName + " | " + it.text }
            assertEquals("$dir/$after (applied prefix '$fixFamily'; fixes still offered:\n$seen)", expected.replace("\r\n", "\n"), myFixture.editor.document.text.replace("\r\n", "\n"))
        }
        val left = xoopsProblems()
        assertEquals("problems after fixes in $dir/$target:\n" + left.joinToString("\n"), remaining, left.size)
        assertNoParseError(myFixture.file)
        assertTrue(
            "fix '$fixFamily' is still offered after applying it (not idempotent)",
            myFixture.getAllQuickFixes().none { it.familyName.startsWith(fixFamily) || it.text.startsWith(fixFamily) },
        )
        if (alsoCheck != null) {
            val vf = myFixture.findFileInTempDir(alsoCheck.first)!!
            val actual = myFixture.psiManager.findFile(vf)!!.text
            val expected = java.io.File(testDataPath, "$dir/${alsoCheck.second}").readText(Charsets.UTF_8)
            assertEquals(expected.replace("\r\n", "\n").trimEnd(), actual.replace("\r\n", "\n").trimEnd())
            assertNoParseError(myFixture.psiManager.findFile(vf)!!)
        }
    }

    /**
     * An SQL file target. The editor highlighting pass hides non-weak inspection results in an SQL file
     * that has no data source, which is how the test IDE runs; the batch "Run inspection" does not, so
     * the SQL rules are checked by descriptor exactly as the batch path collects them.
     */
    private fun checkSql(
        inspection: LocalInspectionTool,
        dir: String,
        target: String,
        problems: Int,
        fixFamily: String? = null,
        after: String? = null,
    ) {
        myFixture.copyDirectoryToProject(dir, "")
        myFixture.configureFromTempProjectFile(target)
        fun descriptors(): List<com.intellij.codeInspection.ProblemDescriptor> =
            com.intellij.codeInspection.InspectionEngine.runInspectionOnFile(
                myFixture.file, com.intellij.codeInspection.ex.LocalInspectionToolWrapper(inspection),
                (com.intellij.codeInspection.InspectionManager.getInstance(myFixture.project) as com.intellij.codeInspection.ex.InspectionManagerEx).createNewGlobalContext(),
            ).filter { it.descriptionTemplate.startsWith("XOOPS:") }
        val found = descriptors()
        assertEquals("problems in $dir/$target:\n" + found.joinToString("\n") { it.descriptionTemplate }, problems, found.size)
        if (fixFamily == null) return
        var rounds = 0
        while (rounds++ < 30) {
            val d = descriptors().firstOrNull { d -> d.fixes?.any { it.familyName.startsWith(fixFamily) } == true } ?: break
            val fix = d.fixes!!.first { it.familyName.startsWith(fixFamily) }
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(myFixture.project) { fix.applyFix(myFixture.project, d) }
        }
        if (after != null) {
            // A fixed bare `--` line necessarily ends in a space; editors strip it from the fixture, so compare per line.
            fun norm(t: String) = t.replace("\r\n", "\n").lines().joinToString("\n") { it.trimEnd() }
            val expected = java.io.File(testDataPath, "$dir/$after").readText(Charsets.UTF_8)
            assertEquals(norm(expected), norm(myFixture.editor.document.text))
        }
        assertTrue("fix '$fixFamily' still offered", descriptors().none { d -> d.fixes?.any { it.familyName.startsWith(fixFamily) } == true })
    }

    /** Round 4 gate: no fix may leave a PHP file that does not parse (Gemini 11, Grok 8 such files). */
    private fun assertNoParseError(file: com.intellij.psi.PsiFile) {
        if (file !is com.jetbrains.php.lang.psi.PhpFile) return
        val errors = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(file, com.intellij.psi.PsiErrorElement::class.java)
        assertTrue(
            "parse error after fixes in ${file.name}: " + errors.joinToString(" | ") { it.errorDescription + " at " + it.textRange.startOffset },
            errors.isEmpty(),
        )
    }

    // --- RootPathGuard -------------------------------------------------------------------

    fun testGuardGoesBetweenNamespaceAndUse() =
        check(XoopsRootPathGuardInspection(), "RootPathGuard/namespaced", "modules/demo/class/Foo.php", 1,
            "Insert XOOPS_ROOT_PATH", "modules/demo/class/Foo.after.php")

    fun testGuardGoesBeforeClassDocblock() =
        check(XoopsRootPathGuardInspection(), "RootPathGuard/docblock", "modules/demo/class/Baz.php", 1,
            "Insert XOOPS_ROOT_PATH", "modules/demo/class/Baz.after.php")

    fun testEntryPointWithSetupBeforeBootstrapIsNotGuarded() =
        check(XoopsRootPathGuardInspection(), "RootPathGuard/entry", "modules/demo/pop.php", 0)

    fun testGuardGoesAboveAGlobalStatement() =
        check(XoopsRootPathGuardInspection(), "RootPathGuard/global", "modules/demo/include/functions.php", 1,
            "Insert XOOPS_ROOT_PATH", "modules/demo/include/functions.after.php")

    fun testRootPageWithRedirectIsNotGuarded() =
        check(XoopsRootPathGuardInspection(), "RootPathGuard/rootpage", "modules/demo/index.php", 0)

    fun testRootPageBootingThroughSiblingIsNotGuarded() =
        check(XoopsRootPathGuardInspection(), "RootPathGuard/rootpage", "modules/demo/verify.php", 0)

    fun testRootFunctionsFileIsStillGuarded() =
        check(XoopsRootPathGuardInspection(), "RootPathGuard/rootpage", "modules/demo/functions.php", 1)

    fun testVariableIncludeIsAnEntryPoint() =
        check(XoopsRootPathGuardInspection(), "RootPathGuard/rootpage", "modules/demo/run.php", 0)

    fun testEchoingScriptInAnyDirectoryIsNotGuarded() =
        check(XoopsRootPathGuardInspection(), "RootPathGuard/rootpage", "modules/demo/api/ping.php", 0)

    fun testRootFooterWithLibraryIncludeIsGuarded() =
        check(XoopsRootPathGuardInspection(), "RootPathGuard/rootpage", "modules/demo/footer.php", 1)

    fun testGuardAfterSameLineDeclareStaysOutOfTheGlobalStatement() =
        check(XoopsRootPathGuardInspection(), "RootPathGuard/sameline", "modules/demo/include/strict.php", 1,
            "Insert XOOPS_ROOT_PATH", "modules/demo/include/strict.after.php")

    fun testAutoloaderIsNeverGuarded() =
        check(XoopsRootPathGuardInspection(), "RootPathGuard/autoloader", "modules/demo/preloads/autoloader.php", 0)

    fun testQualifiedGuardIsRecognised() =
        check(XoopsRootPathGuardInspection(), "RootPathGuard/qualified", "modules/demo/class/Bar.php", 0)

    fun testNotFoundStubIsNotGuarded() =
        check(XoopsRootPathGuardInspection(), "RootPathGuard/stub", "modules/demo/images/index.php", 0)

    // --- DoubleCpHeader ------------------------------------------------------------------

    fun testHeaderRenderedByAdminHeaderIsDuplicate() =
        check(XoopsDoubleCpHeaderInspection(), "DoubleCpHeader/renders", "modules/demo/admin/index.php", 1,
            "Remove the duplicate", "modules/demo/admin/index.after.php")

    fun testHeaderNotRenderedByAdminHeaderIsKept() =
        check(XoopsDoubleCpHeaderInspection(), "DoubleCpHeader/norender", "modules/demo/admin/index.php", 0)

    // --- AttributeEscape -----------------------------------------------------------------

    /**
     * Unknown DTYPE in an attribute: reported, never wrapped (a TXTBOX is already htmlspecialchars'd and
     * would print &amp;#039;). The URL case is rewritten from the raw value and fixed.
     */
    fun testAccessorInAttributeWrapsWholeCall() =
        check(XoopsAttributeEscapeInspection(), "AttributeEscape/attr", "modules/demo/class/Util.php", 3,
            "Wrap in", "modules/demo/class/Util.after.php", remaining = 2)

    // --- DeprecatedDbApi -----------------------------------------------------------------

    fun testQueryFFollowsTheSql() =
        check(XoopsDeprecatedDbApiInspection(), "DeprecatedDbApi/queryf", "modules/demo/class/Handler.php", 5,
            "Replace ", "modules/demo/class/Handler.after.php", remaining = 1)

    fun testMutatingQueryOnlyWhenSqlResolves() =
        check(XoopsMutatingQueryInspection(), "MutatingQuery/write", "modules/demo/class/H.php", 1,
            "Replace query()", "modules/demo/class/H.after.php")

    // --- FileHeader ----------------------------------------------------------------------

    fun testHeaderAddedAfterDeclareLine() =
        check(XoopsFileHeaderInspection(), "FileHeader/noheader", "modules/demo/class/A.php", 1,
            "Add XOOPS file header", "modules/demo/class/A.after.php")

    fun testPhpOpenTagInsideStringIsNotAFile() =
        check(XoopsFileHeaderInspection(), "FileHeader/thumb", "modules/demo/class/T.php", 0)

    /** A third-party copyright notice is provenance: nothing is reported and nothing is written (grok.md, gemini). */
    fun testThirdPartyHeaderIsLeftAlone() =
        check(XoopsFileHeaderInspection(), "FileHeader/thirdparty", "modules/demo/class/V.php", 0)

    // --- ResultSetGuard ------------------------------------------------------------------

    /** 2 unguarded + 1 reversed + 1 legacy `if (!$result)`; after the guard fixes the reversed and legacy notes stay. */
    fun testResultSetGuardKeepsFailureContract() =
        check(XoopsResultSetGuardInspection(), "ResultSetGuard/guard", "modules/demo/class/H.php", 4,
            "Insert isResultSet()", "modules/demo/class/H.after.php", remaining = 2)

    /** adslight shape: `if (!isResultSet) { trigger_error(..., E_USER_ERROR); }` already guards - no second guard. */
    fun testUserErrorGuardIsAGuard() =
        check(XoopsResultSetGuardInspection(), "ResultSetGuard/euser", "modules/demo/include/count.php", 0)

    // --- FormLoader ----------------------------------------------------------------------

    fun testFormLoaderInsertedBeforeClassDocblock() =
        check(XoopsFormLoaderInspection(), "FormLoader/tray", "modules/demo/class/Form/Tray.php", 1,
            "Insert xoops_load", "modules/demo/class/Form/Tray.after.php")

    fun testParentClassLoadSatisfiesLoader() =
        check(XoopsFormLoaderInspection(), "FormLoader/parentload", "modules/demo/class/BlockForm.php", 0)

    // --- Superglobal ---------------------------------------------------------------------

    /**
     * Codex round-3 finding: a POST array handed to getString() is the string "Array" and the permission
     * form saves no group. The accessor follows the use: getArray / getInt / getString / getText, and a use
     * that cannot be read (an untyped argument, an unknown setVar() field) gets no fix.
     */
    fun testSuperglobalAccessorFollowsTheUse() =
        check(XoopsSuperglobalInspection(), "Superglobal/typed", "modules/demo/admin/perms.php", 26,
            "Re", "modules/demo/admin/perms.after.php", remaining = 4)

    /** Every read the typed tiers decline gets the byte-identical raw accessor; `$_REQUEST` is the 'default' hash. */
    fun testRawAccessorKeepsEveryOtherRead() =
        check(XoopsSuperglobalInspection(), "Superglobal/raw", "modules/demo/include/raw.php", 14,
            "Re", "modules/demo/include/raw.after.php", remaining = 3)

    fun testSuperglobalRewritesOnlyMeaningPreservingReads() =
        check(XoopsSuperglobalInspection(), "Superglobal/keyed", "modules/demo/include/ajax.php", 14,
            "Re", "modules/demo/include/ajax.after.php", remaining = 2)

    /** `global $_POST` is a no-op: the superglobal leaves the list, a list of one leaves with its line. */
    fun testSuperglobalLeavesTheGlobalStatement() =
        check(XoopsSuperglobalInspection(), "Superglobal/global", "modules/demo/include/functions.php", 3,
            "Remove \$_", "modules/demo/include/functions.after.php")

    /** Round 4: `assets/js` is a vendored tree and is excluded from every rule (it was inspected for security until 0.6.2). */
    fun testAssetsJsIsExcludedFromEveryRule() =
        check(XoopsSuperglobalInspection(), "Superglobal/assets", "modules/demo/assets/js/handler.php", 0)

    // --- Small fixes ---------------------------------------------------------------------

    /**
     * Round 4: a serialized CriteriaCompo (xhelp) came back as __PHP_Incomplete_Class under
     * `allowed_classes => false`. The fix now needs an array-only use; `$search->add()` and the
     * array element `$ret['search']` stay reported without a fix.
     */
    fun testUnserializeFixOnlyWhereTheResultIsAnArray() =
        check(XoopsUnserializeInspection(), "Unserialize/unser", "modules/demo/class/U.php", 5,
            "Add ['allowed_classes'", "modules/demo/class/U.after.php", remaining = 2)

    /** Round 4: `function &f(): array` must not get `return [];` (PHP notice); `: void` gets a bare `return;`. */
    fun testByReferenceFunctionReturnsAVariable() =
        check(XoopsResultSetGuardInspection(), "ResultSetGuard/byref", "modules/demo/class/R.php", 3,
            "Insert isResultSet()", "modules/demo/class/R.after.php")

    fun testHttpStatusLine() =
        check(XoopsHttpStatusInspection(), "HttpStatus/status", "modules/demo/images/index.php", 1,
            "Replace with http_response_code", "modules/demo/images/index.after.php")

    fun testSetcookie() =
        check(XoopsSetcookieInspection(), "Setcookie/cookie", "modules/demo/class/U.php", 1,
            "Replace setcookie()", "modules/demo/class/U.after.php")

    fun testGuestUserReplacesBareIssetOnly() =
        check(XoopsGuestUserInspection(), "GuestUser/guest", "modules/demo/vote.php", 1,
            "Replace with \$xoopsUser", "modules/demo/vote.after.php")

    fun testUnlinkGuardedByWholeExpression() =
        check(XoopsUnlinkInspection(), "Unlink/unlink", "modules/demo/include/onupdate.php", 1)

    /**
     * `$info->isFile()` guards `unlink($info->getRealPath())` and `$name = $info->getPathname(); unlink($name)`.
     * Reported: no check, `getPath()` (the parent directory), and `isLink()` before `getRealPath()` (the link's target).
     */
    fun testUnlinkGuardedBySplFileInfoIsFile() =
        check(XoopsUnlinkInspection(), "Unlink/spl", "modules/demo/class/Cleaner.php", 3)

    fun testInstallSqlIgnoresDumpsTheInstallerNeverRuns() =
        checkSql(XoopsInstallSqlInspection(), "InstallSql/fk", "modules/demo/sql/dump.sql", 0)

    fun testSqlFileCommentIgnoresDumpsTheInstallerNeverRuns() =
        checkSql(XoopsSqlFileCommentInspection(), "InstallSql/fk", "modules/demo/sql/dump.sql", 0)

    /** The core schema has no module manifest, and the core installer still runs it. */
    fun testSqlFileCommentStillChecksTheCoreSchema() =
        checkSql(XoopsSqlFileCommentInspection(), "InstallSql/corecomment", "install/sql/mysql.structure.sql", 1)

    /** `sqlfile` naming `sql/english/install.sql` resolves from the module root, two levels above the file. */
    fun testInstallSqlChecksANestedSqlfileEntry() =
        checkSql(XoopsInstallSqlInspection(), "InstallSql/nested", "modules/demo/sql/english/install.sql", 1)

    /** A comment that mentions `sql/upgrade.sql` does not make the installer run it. */
    fun testInstallSqlIgnoresAFileOnlyACommentNames() =
        checkSql(XoopsInstallSqlInspection(), "InstallSql/nested", "modules/demo/sql/upgrade.sql", 0)

    /** The array manifest form: `'sqlfile' => ['mysql' => 'sql/english/install.sql']`. */
    fun testInstallSqlChecksTheArrayManifestSqlfile() =
        checkSql(XoopsInstallSqlInspection(), "InstallSql/arraymanifest", "modules/demo/sql/english/install.sql", 1)

    /**
     * The manifest is read from the PHP PSI: inline and block comments, a nowdoc holding a comment opener, a string that
     * looks like a declaration, a comparison, `?>` after a line comment, a multi-line index and several DB entries.
     */
    fun testSqlfilePathsReadsOnlyLiveDeclarations() {
        fun paths(php: String): List<String> =
            org.xoops.support.inspections.InstallSql.sqlfilePaths(myFixture.configureByText("xoops_version.php", php) as PhpFile)
        val live = listOf("sql/live.sql")
        assertEquals(live, paths("<?php /* schema */ \$modversion['sqlfile']['mysql'] = /* x */ 'sql/live.sql';"))
        assertEquals(emptyList<String>(), paths("<?php /*\n\$modversion['sqlfile']['mysql'] = 'sql/old.sql';\n*/"))
        assertEquals(emptyList<String>(), paths("<?php \$x = 1; // \$modversion['sqlfile']['mysql'] = 'sql/old.sql';"))
        assertEquals(live, paths("<?php \$help = <<<'DOC'\n/*\nDOC;\n\$modversion['sqlfile']['mysql'] = 'sql/live.sql';"))
        assertEquals(emptyList<String>(), paths("<?php \$help = \"'sqlfile' => 'sql/backup.sql'\";"))
        assertEquals(emptyList<String>(), paths("<?php if (\$modversion['sqlfile']['mysql'] === 'sql/backup.sql') {}"))
        assertEquals(live, paths("<?php // note ?><?php \$modversion['sqlfile']['mysql'] = 'sql/live.sql';"))
        assertEquals(live, paths("<?php \$modversion['sqlfile'][\nXOOPS_DB_TYPE\n] = 'sql/live.sql';"))
        assertEquals(
            listOf("sql/pg.sql", "sql/live.sql"),
            paths("<?php \$modversion['sqlfile'] = ['pgsql' => 'sql/pg.sql', 'mysql' => 'sql/live.sql'];"),
        )
        assertEquals(live, paths("<?php \$modversion = ['name' => 'Demo', 'sqlfile' => array('mysql' => 'sql/live.sql')];"))
        assertEquals(emptyList<String>(), paths("<?php \$files[\$modversion['sqlfile']['mysql']] = 'sql/backup.sql';"))
        assertEquals(emptyList<String>(), paths("<?php [\$modversion['sqlfile']['mysql'], \$b] = ['sql/live.sql', 'sql/backup.sql'];"))
        assertEquals(
            listOf("sql/live.sql"),
            paths("<?php \$modversion['sqlfile']['mysql'] = 'sql/live.sql'; \$modversion['sqlfile']['mysql'] .= '.sql';"),
        )
    }

    fun testUnregisteredTemplateSkipsTestTrees() =
        check(XoopsUnregisteredTemplateInspection(), "Templates/testtree", "modules/demo/tests/example.tpl", 0)

    fun testByRefResolvesParentConstructor() =
        check(XoopsByRefArgumentInspection(), "ByRefArgument/byref", "modules/demo/class/Form/CategoryForm.php", 1)

    // --- Language ------------------------------------------------------------------------

    fun testHelperLoadLanguageCounts() =
        check(XoopsLanguageLoadInspection(), "LanguageLoad/helper", "modules/demo/include/ajax_rating.php", 0)

    fun testMissingLanguageLoadIsReported() =
        check(XoopsLanguageLoadInspection(), "LanguageLoad/missing", "modules/demo/include/ajax2.php", 1)

    fun testNonCanonicalCatalogIsOneFinding() =
        check(XoopsLanguageRoleInspection(), "LanguageRole/copy", "modules/demo/language/english/modinfo1.php", 1)

    fun testWrongPrefixInCanonicalCatalog() =
        check(XoopsLanguageRoleInspection(), "LanguageRole/prefix", "modules/demo/language/english/admin.php", 1)

    // --- CSRF ----------------------------------------------------------------------------

    fun testPostOnlyGateDoesNotCoverGetOp() =
        check(XoopsCsrfInspection(), "Csrf/getop", "modules/demo/admin/mimetypes.php", 1)

    // --- 0.5.0: receiver proof (Codex F1) -------------------------------------------------

    fun testApplicationQueryWrapperIsNeverRenamedToExec() =
        check(XoopsMutatingQueryInspection(), "MutatingQuery/wrapper", "modules/demo/class/VersionControlMySQL.php", 0)

    fun testQueryFOnDatabaseFieldIsRenamedButOwnWrapperIsNot() =
        check(XoopsDeprecatedDbApiInspection(), "DeprecatedDbApi/field", "modules/demo/class/VersionControlMySQL.php", 1,
            "Replace queryF()", "modules/demo/class/VersionControlMySQL.after.php")

    // --- 0.5.0: legacy guards, nested query, trailing comment -----------------------------

    /** 3 legacy guards (`!$r`, `!$r = ...`, unbraced `if ($r = ...)`) + 1 nested query; the else branch of `!isResultSet` is guarded. */
    fun testLegacyGuardIsModernisedInPlaceAndNestedQueryIsReported() =
        check(XoopsResultSetGuardInspection(), "ResultSetGuard/legacy", "modules/demo/class/L.php", 4,
            "Replace the legacy result check", "modules/demo/class/L.after.php", remaining = 1)

    /** A non-nullable object return type gets no `return null;` fix; a `$row` assigned after the fetch is not reused. */
    fun testNoFixWhenNoFailureReturnExists() =
        check(XoopsResultSetGuardInspection(), "ResultSetGuard/nofix", "modules/demo/class/N.php", 2,
            "Insert isResultSet()", "modules/demo/class/N.after.php", remaining = 1)

    fun testGuardLandsOnItsOwnLineAfterATrailingComment() =
        check(XoopsResultSetGuardInspection(), "ResultSetGuard/comment", "modules/demo/include/notification.inc.php", 1,
            "Insert isResultSet()", "modules/demo/include/notification.inc.after.php")

    // --- 0.5.0: file header reads the whole head ------------------------------------------

    fun testLicenceInSecondDocblockIsAHeader() =
        check(XoopsFileHeaderInspection(), "FileHeader/secondblock", "modules/demo/class/Helper.php", 0)

    fun testHeaderStaysInsidePhpWhenFirstLineClosesTheTag() =
        check(XoopsFileHeaderInspection(), "FileHeader/inlinehtml", "modules/demo/class/Snippet.php", 1,
            "Add XOOPS file header", "modules/demo/class/Snippet.after.php")

    fun testLicensedUnderProseIsAHeader() =
        check(XoopsFileHeaderInspection(), "FileHeader/ccby", "modules/demo/include/ajax_rating.php", 0)

    fun testThirdPartyTreeGetsNoHeader() =
        check(XoopsFileHeaderInspection(), "FileHeader/assets", "modules/demo/assets/js/uploader.php", 0)

    // --- 0.5.0: DTYPE-aware escaping ------------------------------------------------------

    /** raw OTHER + format 'n' in attributes, one URL case (re-read with format 'n'); SQL literals are not HTML. */
    fun testNumericAndSanitisedDtypesAreNotReportedRawOnesAre() =
        check(XoopsAttributeEscapeInspection(), "AttributeEscape/dtype", "modules/demo/class/MimeUtil.php", 3,
            "Wrap in", "modules/demo/class/MimeUtil.after.php")

    // --- 0.5.0: foreign db: templates -----------------------------------------------------

    fun testForeignPrefixDbIncludeIsNotThisModulesProblem() =
        check(XoopsUnregisteredTemplateInspection(), "Templates/foreign", "modules/publisher-copy/templates/publisher_item.tpl", 1)

    // --- 0.5.0: new rules -----------------------------------------------------------------

    fun testDynamicCode() =
        check(XoopsDynamicCodeInspection(), "DynamicCode/dyn", "modules/demo/admin/preferences.php", 3)

    /** getSort() in ORDER BY and setSort(Request::...); getOrder()/setOrder() are normalised by Criteria and stay silent. */
    fun testOrderByFromCriteriaAndRequest() =
        check(XoopsOrderByInspection(), "OrderBy/sort", "modules/demo/class/H.php", 2)

    fun testLegacySuperglobalIsRenamed() =
        check(XoopsLegacySuperglobalInspection(), "LegacySuperglobal/old", "modules/demo/index.php", 1,
            "Replace \$HTTP_GET_VARS", "modules/demo/index.after.php")

    fun testDeprecatedCoreApiRenamesOnlySameSignatureCalls() =
        check(XoopsDeprecatedCoreApiInspection(), "DeprecatedCoreApi/old", "modules/demo/class/Old.php", 5,
            "Replace ", "modules/demo/class/Old.after.php", remaining = 2)

    // Ported from rector_xoops: moved kernel includes, xoops_load() aliases, new ModuleAdmin().
    fun testRectorPortsRewriteOnlyCoreRootedIncludesAndCoreLoadAliases() =
        check(XoopsDeprecatedCoreApiInspection(), "DeprecatedCoreApi/rector", "modules/demo/admin/legacy.php", 6,
            "Replace with ", "modules/demo/admin/legacy.after.php")

    fun testXoopsOptionInsideFunctionBecomesGlobal() =
        check(XoopsLocalXoopsOptionInspection(), "LocalXoopsOption/fn", "modules/demo/include/functions.php", 2,
            "Replace with ", "modules/demo/include/functions.after.php")

    fun testSmartyTagsWithoutXoopsDelimitersAreWrapped() =
        check(XoopsSmartyDelimiterInspection(), "Smarty/delims", "modules/demo/templates/demo_x.tpl", 3,
            "Wrap in <{", "modules/demo/templates/demo_x.after.tpl")

    fun testSmartyRequestVariablesGetEscape() =
        check(XoopsSmartyRequestEscapeInspection(), "Smarty/escape", "modules/demo/templates/demo_y.tpl", 2,
            "Add |escape", "modules/demo/templates/demo_y.after.tpl")

    // Rules ported from the 2.7.4 upgrade preflight (Smarty4/5TemplateChecks + Repair).
    fun testXoPluginArgumentIsQuotedWithSingleQuotes() =
        check(XoopsSmartyUnquotedPluginArgInspection(), "Smarty/quote", "modules/demo/templates/demo_q.tpl", 2,
            "Quote the plugin argument", "modules/demo/templates/demo_q.after.tpl")

    fun testRemovedQTagsAreRenamedAndPhpTagIsReported() =
        check(XoopsSmartyRemovedTagInspection(), "Smarty/removed", "modules/demo/templates/demo_r.tpl", 4,
            "Use include / foreach", "modules/demo/templates/demo_r.after.tpl", remaining = 2)

    fun testForeachItemRenameRespectsNestedScopeAndForeachelse() =
        check(XoopsSmartyForeachItemShadowsFromInspection(), "Smarty/foreach", "modules/demo/templates/demo_f.tpl", 2,
            "Rename the foreach item", "modules/demo/templates/demo_f.after.tpl")

    fun testBlockShorthandBecomesSmartyBlock() =
        check(XoopsSmartyBlockShorthandInspection(), "Smarty/block", "modules/demo/templates/demo_b.tpl", 2,
            "Use \$smarty.block", "modules/demo/templates/demo_b.after.tpl")

    fun testDateFormatRewrittenOnlyWithoutLocaleTokensOrLetters() =
        check(XoopsSmartyDateFormatStrftimeInspection(), "Smarty/date", "modules/demo/templates/demo_d.tpl", 3,
            "Use date() format codes", "modules/demo/templates/demo_d.after.tpl", remaining = 2)

    fun testSmarty5PrepIsReportOnly() =
        check(XoopsSmarty5TemplatePrepInspection(), "Smarty/prep", "modules/demo/templates/demo_p.tpl", 3)

    fun testSmarty2CoreIsNotReported() =
        check(XoopsSmartyUnquotedPluginArgInspection(), "Smarty/gate", "modules/demo/templates/demo_g.tpl", 0)

    fun testPostOpIsCoveredByPostGate() =
        check(XoopsCsrfInspection(), "Csrf/postop", "modules/demo/admin/item.php", 0)

    // --- Templates -----------------------------------------------------------------------

    fun testMisplacedRegistrationIsRepointedNotStubbed() =
        check(XoopsMissingRegisteredTemplateInspection(), "Templates/missing", "modules/demo/xoops_version.php", 5,
            "Change to", "modules/demo/xoops_version.after.php", remaining = 2)

    fun testDbIncludesSkipSystemOtherModulesAndComments() =
        check(XoopsUnregisteredTemplateInspection(), "Templates/unregistered", "modules/demo/templates/demo_item.tpl", 2,
            "Register template", null, remaining = 1,
            alsoCheck = "modules/demo/xoops_version.php" to "modules/demo/xoops_version.after.php")

    // --- Ported after the 16-module run ---------------------------------------------------

    fun testAmpInRedirectUrlIsDecoded() =
        check(XoopsDoubleEncodedAmpInspection(), "DoubleEncodedAmp/redirect", "modules/demo/admin/main.php", 3,
            "Replace &amp;", "modules/demo/admin/main.after.php")

    fun testStrictCalendarArgumentsAreCast() =
        check(XoopsStrictCalendarCastInspection(), "StrictCalendarCast/strict", "modules/demo/class/Utility.php", 2,
            "Cast the calendar", "modules/demo/class/Utility.after.php")

    fun testCalendarWithoutStrictTypesIsLeftAlone() =
        check(XoopsStrictCalendarCastInspection(), "StrictCalendarCast/loose", "modules/demo/class/Loose.php", 0)

    fun testUndefinedModuleConstantIsReported() =
        check(XoopsLocaleConstantInspection(), "LocaleConstant/missing", "modules/demo/admin/index.php", 2)

    fun testIdenticalDuplicateDefineIsRemoved() =
        check(XoopsDuplicateDefineInspection(), "DuplicateDefine/dup", "modules/demo/language/english/admin.php", 3,
            "Remove the duplicate", "modules/demo/language/english/admin.after.php", remaining = 2)

    fun testThemeConstantInModuleIsReported() =
        check(XoopsThemeConstantInspection(), "ThemeConstant/theme", "modules/demo/language/english/main.php", 1)

    fun testMenuGetsAutoloaderBeforeHelper() =
        check(XoopsMenuAutoloadInspection(), "MenuAutoload/menu", "modules/demo/admin/menu.php", 1,
            "Require preloads", "modules/demo/admin/menu.after.php")

    fun testMenuWithStrictTypesGetsAutoloaderBeforeHelper() =
        check(XoopsMenuAutoloadInspection(), "MenuAutoload/strict", "modules/demo/admin/menu.php", 1,
            "Require preloads", "modules/demo/admin/menu.after.php")

    fun testMenuWithStrictTypesAndBootstrapRequireIsFine() =
        check(XoopsMenuAutoloadInspection(), "MenuAutoload/bootstrap", "modules/demo/admin/menu.php", 0)

    fun testMenuWithStrictTypesHeaderCommentsGetsAutoloaderBeforeHelper() =
        check(XoopsMenuAutoloadInspection(), "MenuAutoload/tagstyle", "modules/demo/admin/menu.php", 1,
            "Require preloads", "modules/demo/admin/menu.after.php")

    /** newbb's real menu.php: two spaces after the open tag, CRLF line endings, the Helper call several statements down. */
    fun testMenuWithCrlfAndDoubleSpacedDeclareGetsAutoloaderBeforeHelper() =
        check(XoopsMenuAutoloadInspection(), "MenuAutoload/crlf", "modules/demo/admin/menu.php", 1,
            "Require preloads", "modules/demo/admin/menu.after.php")

    fun testClassConstantDoesNotLoadTheClass() =
        check(XoopsMenuAutoloadInspection(), "MenuAutoload/classconst", "modules/demo/admin/menu.php", 0)

    fun testMenuWithAutoloaderIsFine() =
        check(XoopsMenuAutoloadInspection(), "MenuAutoload/ok", "modules/demo/admin/menu.php", 0)

    fun testGetObjectsWithoutReferenceIsReported() =
        check(XoopsHandlerSignatureInspection(), "HandlerSignature/handler", "modules/demo/class/ItemHandler.php", 1)

    fun testRequestValueInSqlIsReported() =
        check(XoopsSqlConcatInspection(), "SqlConcat/concat", "modules/demo/include/search.php", 2)

    fun testBareSqlCommentGetsItsSpace() =
        check(XoopsSqlCommentInspection(), "SqlComment/sql", "modules/demo/xoops_version.php", 1,
            "Make the bare", null, alsoCheck = "modules/demo/sql/mysql.sql" to "modules/demo/sql/mysql.after.sql")

    // --- 0.7.0: rules harvested after review round 4 ------------------------------------------

    fun testInstallSqlForeignKeyPrefixAndEngine() =
        checkSql(XoopsInstallSqlInspection(), "InstallSql/fk", "modules/demo/sql/mysql.sql", 4)

    fun testUpdateNeverCreatesTheNewTable() =
        checkSql(XoopsUpdateCreatesTableInspection(), "UpdateCreatesTable/missing", "modules/demo/sql/mysql.sql", 1)

    fun testUpdateThatReRunsTheSqlFileCoversEveryTable() =
        checkSql(XoopsUpdateCreatesTableInspection(), "UpdateCreatesTable/wholesale", "modules/demo/sql/mysql.sql", 0)

    fun testBareSqlCommentLinesAreFixedOneByOne() =
        checkSql(XoopsSqlFileCommentInspection(), "SqlFileComment/sql", "modules/demo/sql/mysql.sql", 3,
            "Make the bare", "modules/demo/sql/mysql.after.sql")

    fun testPrefixCheckIsNotContainment() =
        check(XoopsPathContainmentInspection(), "PathContainment/prefix", "modules/demo/class/Files.php", 2)

    /** Negated check: reported, no fix; already guarded with is_file(): silent; mkdir body: silent. */
    fun testIsFileAppendedOnlyToAPositiveCheck() =
        check(XoopsIsFileAssetInspection(), "IsFileAsset/asset", "modules/demo/include/assets.php", 3,
            "Require is_file()", "modules/demo/include/assets.after.php", remaining = 1)

    /** `$` → `\\z` only for an unescaped `$`, no D modifier, and a later exact comparison. */
    fun testAllowlistAnchorBecomesZ() =
        check(XoopsAllowlistAnchorInspection(), "AllowlistAnchor/anchor", "modules/demo/class/Tags.php", 1,
            "Anchor the allowlist", "modules/demo/class/Tags.after.php")

    fun testPreloadMustNotAssignTheModuleGlobals() =
        check(XoopsPreloadGlobalInspection(), "PreloadGlobal/core", "modules/demo/preloads/core.php", 4)

    fun testSameAssignmentOutsidePreloadsIsNotReported() =
        check(XoopsPreloadGlobalInspection(), "PreloadGlobal/core", "modules/demo/include/notpreload.php", 0)

    fun testWeakTokenShapes() =
        check(XoopsWeakTokenInspection(), "WeakToken/weak", "modules/demo/include/token.php", 4)

    /** assignByRef on a variable never written again is renamed; appendByRef on one written later is not. */
    fun testSmarty5RemovedApi() =
        check(XoopsSmarty5ApiInspection(), "Smarty5Api/ref", "modules/demo/include/render.php", 5,
            "Replace assignByRef", "modules/demo/include/render.after.php", remaining = 4)

    fun testVendoredFoldersAreNeverInspected() {
        check(XoopsSuperglobalInspection(), "Paths/excluded", "modules/demo/assets/js/jquery/upload.php", 0)
        myFixture.configureFromTempProjectFile("modules/demo/include/00/old.php")
        assertEquals(0, xoopsProblems().size)
        myFixture.configureFromTempProjectFile("modules/demo/class/jpgraph/jpgraph.php")
        assertEquals(0, xoopsProblems().size)
    }

    // --- Core version gate (2.5.9 web root: no isResultSet(), no exec()) ------------------

    fun testCoreVersionParses() {
        assertEquals(org.xoops.support.psi.XoopsCore.Version(2, 5, 9), org.xoops.support.psi.XoopsCore.parse("define('XOOPS_VERSION', 'XOOPS 2.5.9');"))
        assertEquals(org.xoops.support.psi.XoopsCore.Version(2, 8, 0), org.xoops.support.psi.XoopsCore.parse("define('XOOPS_VERSION', 'XOOPS 2.8.0-Alpha1');"))
        assertEquals(org.xoops.support.psi.XoopsCore.Version(2, 7, 0), org.xoops.support.psi.XoopsCore.parse("define('XOOPS_VERSION', 'XOOPS 2.7');"))
        assertNull(org.xoops.support.psi.XoopsCore.parse("<?php echo 1;"))
        assertTrue(org.xoops.support.psi.XoopsCore.Version(2, 5, 11) >= org.xoops.support.psi.XoopsCore.RESULT_SET_API)
        assertTrue(org.xoops.support.psi.XoopsCore.Version(2, 5, 10) < org.xoops.support.psi.XoopsCore.RESULT_SET_API)
    }

    /** On 2.5.9 the guard fix tests the handle (`if (!$result)`); the legacy `if (!$result)` is not a finding. */
    fun testResultSetGuardOnOldCoreTestsTheHandle() =
        check(XoopsResultSetGuardInspection(), "ResultSetGuard/guard259", "modules/demo/class/H.php", 3,
            "Insert isResultSet()", "modules/demo/class/H.after.php", remaining = 1)

    /** On 2.5.9 only the nested `fetch*($db->query())` remains; no legacy guard is rewritten to isResultSet(). */
    fun testLegacyGuardIsNotModernisedOnOldCore() =
        check(XoopsResultSetGuardInspection(), "ResultSetGuard/legacy259", "modules/demo/class/L.php", 1)

    fun testMutatingQueryOnOldCoreRenamesToQueryF() =
        check(XoopsMutatingQueryInspection(), "MutatingQuery/write259", "modules/demo/class/H.php", 1,
            "Replace query()", "modules/demo/class/H.after.php")

    /** On 2.5.9 `queryF()` is not deprecated and `exec()` does not exist; only `quoteString()` is reported. */
    fun testQueryFIsNotReportedOnOldCore() =
        check(XoopsDeprecatedDbApiInspection(), "DeprecatedDbApi/queryf259", "modules/demo/class/Handler.php", 1,
            "Replace ", "modules/demo/class/Handler.after.php")

    // --- 224-module review (0.7.2) -------------------------------------------------------

    /** `new $_REQUEST['x']()`, `$_GET['f']()`, `$o->$_POST['m']()` name a callee: reported, never rewritten; the plain echo still is. */
    fun testSuperglobalLeavesDynamicCallees() =
        check(XoopsSuperglobalInspection(), "Superglobal/callee", "modules/demo/include/dispatch.php", 4,
            "Replace", "modules/demo/include/dispatch.after.php", remaining = 3)

    /** A script that never mentions XOOPS has no autoloader for Xmf\Request: nothing is reported. */
    fun testSuperglobalSkipsScriptsThatDoNotBootXoops() =
        check(XoopsSuperglobalInspection(), "Superglobal/standalone", "modules/demo/ipn/ipn.php", 0)

    fun testSuperglobalSkipsDocsExamples() =
        check(XoopsSuperglobalInspection(), "Superglobal/standalone", "modules/demo/docs/examples/sample.php", 0)

    /** `if (!defined(…)) { throw … }` after `use`, and the compound `|| !($GLOBALS['xoopsUser'] instanceof …)` form, are guards. */
    fun testThrowGuardIsAGuard() =
        check(XoopsRootPathGuardInspection(), "RootPathGuard/throwguard", "modules/demo/include/action.module.php", 0)

    fun testCompoundGuardIsAGuard() =
        check(XoopsRootPathGuardInspection(), "RootPathGuard/throwguard", "modules/demo/blocks/menu.php", 0)

    /** `query()` whose SQL resolves to a write is a write for the CSRF rule; the SELECT branch is not. */
    fun testCsrfSeesWritesThroughQuery() =
        check(XoopsCsrfInspection(), "Csrf/querywrite", "modules/demo/admin/main.php", 1)

    /** The direct-access guard alone does not make a bundled SDK XOOPS code. */
    fun testSuperglobalIgnoresTheGuardLineAsEvidence() =
        check(XoopsSuperglobalInspection(), "Superglobal/standalone", "modules/demo/lib/sdk.php", 0)

    /** `if (!defined(…)) { http_response_code(404); exit; }` is a guard. */
    fun testStatusLineBeforeExitIsAGuard() =
        check(XoopsRootPathGuardInspection(), "RootPathGuard/throwguard", "modules/demo/include/icons.php", 0)

    /** A bundled page that never names XOOPS gets no convention fix: xoops_setcookie() would not exist there. */
    fun testSetcookieSkipsScriptsThatDoNotBootXoops() =
        check(XoopsSetcookieInspection(), "Setcookie/standalone", "modules/demo/pgv/login.php", 0)

    /** `if (!defined(…) || …) { return; }` ends the include and is a guard. */
    fun testReturnGuardIsAGuard() =
        check(XoopsRootPathGuardInspection(), "RootPathGuard/throwguard", "modules/demo/include/welcome.php", 0)

    /** Alpha 3 carry-over: `include header.php` → `include_once`; `_once` and non-header includes untouched. */
    fun testIncludeOnceHeader() =
        check(XoopsIncludeOnceHeaderInspection(), "IncludeOnceHeader/once", "modules/demo/view.php", 1,
            "Replace include", "modules/demo/view.after.php")

    // --- alpha.5: XoopsCore27 false positives (01inspections, 2026-10-25) ------------------

    /** Upgrade-test fixtures and the tests/tmp scratch copies are not templates of the site. */
    fun testUpgradeRulesSkipTestTrees() =
        check(XoopsSmartyRemovedTagInspection(), "Smarty/testtree", "upgrade/tests/fixtures/smarty/php_tag.tpl", 0)

    fun testDelimiterRuleSkipsTestTrees() =
        check(XoopsSmartyDelimiterInspection(), "Smarty/testtree", "upgrade/tests/fixtures/smarty/php_tag.tpl", 0)

    fun testUpgradeRulesStillReadSiteTemplates() =
        check(XoopsSmartyRemovedTagInspection(), "Smarty/testtree", "themes/demo/page.tpl", 2)

    fun testDelimiterRuleStillReadsSiteTemplates() =
        check(XoopsSmartyDelimiterInspection(), "Smarty/testtree", "themes/demo/page.tpl", 1)

    /** Core 2.7 ships Smarty 4: assignByRef() and \Smarty are its API, nothing to report. */
    fun testSmarty5ApiSilentOnCore27() =
        check(XoopsSmarty5ApiInspection(), "Smarty5Api/core27", "class/template.php", 0)

    fun testSmarty5ApiReportsOnCore28() =
        check(XoopsSmarty5ApiInspection(), "Smarty5Api/core28", "class/template.php", 2)

    /** modulesadmin: $op-scoped gates cover their labels, in both switches; 'remove' is not gated. */
    fun testCsrfGatesScopedByOp() =
        check(XoopsCsrfInspection(), "Csrf/gates", "modules/demo/admin/modules.php", 1)

    /** menus: a same-file helper that checks the token and stops covers its branch. */
    fun testCsrfHelperThatChecksTheToken() =
        check(XoopsCsrfInspection(), "Csrf/helper", "modules/demo/admin/menus.php", 1)

    /** comment_post: the gate is in a POST-only block whose else exits; 'delete' is outside its labels. */
    fun testCsrfGateInsidePostBlockThatExitsOnGet() =
        check(XoopsCsrfInspection(), "Csrf/postblock", "modules/demo/include/comment_post.php", 1)

    /** Behind `$debugMode`, or behind a POST-only block that GET walks past: no cover. */
    fun testCsrfConditionalGatesDoNotCover() =
        check(XoopsCsrfInspection(), "Csrf/conditional", "modules/demo/admin/items.php", 1)

    fun testGuestUserDeMorganFormIsSafe() =
        check(XoopsGuestUserInspection(), "GuestUser/demorgan", "modules/demo/include/inbox.php", 1)

    fun testPreloadLocalCopyIsNotAGlobalWrite() =
        check(XoopsPreloadGlobalInspection(), "PreloadGlobal/local", "modules/demo/preloads/core.php", 0)

    fun testOrderByThroughBuildOrderByIsAllowListed() =
        check(XoopsOrderByInspection(), "OrderBy/builder", "modules/demo/class/H.php", 1)

    fun testSetcookieWrapperItselfIsNotReported() =
        check(XoopsSetcookieInspection(), "Setcookie/wrapper", "include/xoopssetcookie.php", 0)

    /** Protector runs before common.php: xoops_setcookie() would be an undefined function there. */
    fun testSetcookieInProtectorIsNotRenamed() =
        check(XoopsSetcookieInspection(), "Setcookie/wrapper", "xoops_lib/modules/protector/class/protector.php", 0)

    /** Two request strings compared loosely get `!==`; `== ''`, `!= false` and parameters do not. */
    fun testWeakTokenStrictFixForTwoRequestStrings() =
        check(XoopsWeakTokenInspection(), "WeakToken/confirm", "modules/demo/edituser.php", 2,
            "Compare the two request values strictly", "modules/demo/edituser.after.php", remaining = 1)

    fun testWeakTokenNoFixWhenOneSideIsNotRequestText() {
        check(XoopsWeakTokenInspection(), "WeakToken/confirm", "modules/demo/changepass.php", 1)
        assertTrue(myFixture.getAllQuickFixes().none { it.text.startsWith("Compare the two request values strictly") })
    }

    fun testFormLoaderSkipsTheCoreFormLibrary() =
        check(XoopsFormLoaderInspection(), "FormLoader/core", "class/xoopsform/formtext.php", 0)

    /** System admin: header on top, again in two cases. Reported with the real first line; deleting is not output-neutral, so no fix. */
    fun testDoubleCpHeaderWithoutAdminHeader() {
        check(XoopsDoubleCpHeaderInspection(), "DoubleCpHeader/system", "modules/system/admin/avatars/main.php", 2)
        assertTrue(xoopsProblems().all { it.contains("already called on line 7") })
        assertTrue(myFixture.getAllQuickFixes().none { it.text.startsWith("Remove the duplicate") })
    }

    fun testDoubleCpHeaderInExclusiveBranchesIsOneCall() =
        check(XoopsDoubleCpHeaderInspection(), "DoubleCpHeader/branches", "modules/demo/admin/index.php", 0)

    fun testForceTernaryKeepsQuery() =
        check(XoopsMutatingQueryInspection(), "MutatingQuery/force", "modules/demo/class/FieldHandler.php", 1)

    fun testPathContainmentRealpathAndSeparatorIsContainment() =
        check(XoopsPathContainmentInspection(), "PathContainment/core", "modules/demo/class/Paths.php", 2)

    fun testLanguageLoadSkipsIncludedFragments() =
        check(XoopsLanguageLoadInspection(), "LanguageLoad/fragment", "modules/demo/admin/admin_footer.php", 0)

    fun testLanguageRoleSkipsCoreCatalogs() =
        check(XoopsLanguageRoleInspection(), "LanguageRole/core", "language/english/global.php", 0)

    fun testLanguageRoleSkipsSubCatalogs() =
        check(XoopsLanguageRoleInspection(), "LanguageRole/core", "modules/system/language/english/admin/avatars.php", 0)

    /** `_MD_AM_` / `_MD_A_` are admin names; `_MI_` in admin.php is still reported. */
    fun testLanguageRoleAcceptsLegacyAdminPrefixes() =
        check(XoopsLanguageRoleInspection(), "LanguageRole/core", "modules/system/language/english/admin.php", 1)

    fun testLanguageRoleExplicitlyLoadedCatalog() =
        check(XoopsLanguageRoleInspection(), "LanguageRole/core", "modules/system/language/english/cpanel.php", 0)

    fun testAllowlistAnchorIgnoresAReassignedSubject() =
        check(XoopsAllowlistAnchorInspection(), "AllowlistAnchor/reassigned", "modules/demo/include/preloads.php", 0)

    // --- alpha.5: XoopsCore27 second run (02inspections, 2026-10-25) ----------------------

    /** Ternary and `&&` guards, `break` after the guard, a handle passed in: guarded. `!` ternary and `||`: not. */
    fun testResultSetGuardShortCircuitBreakAndParameter() =
        check(XoopsResultSetGuardInspection(), "ResultSetGuard/flow", "modules/demo/class/Flow.php", 2)

    /** realpath() behind a `false !== $root ? … : false` ternary is resolved; a plain concatenation is not. */
    fun testPathContainmentRealpathThroughFailureTernary() =
        check(XoopsPathContainmentInspection(), "PathContainment/ternary", "upgrade/login.php", 1)

    fun testLanguageRoleSkipsFrameworksLibraries() =
        check(XoopsLanguageRoleInspection(), "LanguageRole/frameworks", "Frameworks/moduleclasses/moduleadmin/language/english/main.php", 0)

    /** pm 2.7: the sqlfile line is commented out, the installer never runs this DDL. */
    fun testUpdateCreatesTableIgnoresAnSqlFileTheInstallerNeverRuns() =
        checkSql(XoopsUpdateCreatesTableInspection(), "UpdateCreatesTable/dead", "modules/demo/sql/mysql.sql", 0)

    fun testInstallSqlLeavesTheCoreSchemaEngineAlone() =
        checkSql(XoopsInstallSqlInspection(), "InstallSql/core", "install/sql/mysql.structure.sql", 0)

    fun testSecurityRulesSkipTestTrees() =
        check(XoopsSuperglobalInspection(), "Superglobal/scope", "tests/unit/RequestTest.php", 0)

    fun testSuperglobalLeavesTheProtectorFirewallAlone() =
        check(XoopsSuperglobalInspection(), "Superglobal/scope", "xoops_lib/modules/protector/class/protector.php", 0)

    fun testSuperglobalStillReportsAPage() =
        check(XoopsSuperglobalInspection(), "Superglobal/scope", "modules/demo/page.php", 1)

    // --- alpha.5: XoopsCore27 third run (03inspections, 2026-10-25) -----------------------

    /** filelogger / ScannerProcess / upgrade delete helper are containment or display; public param and `..` strip are not. */
    fun testPathContainmentCanonicalisedAndDisplayPaths() =
        check(XoopsPathContainmentInspection(), "PathContainment/canonical", "class/logger/Paths.php", 2)

    /** page_tablesfill.php: an isResultSet() exit above a `!$result` exit is no legacy guard. */
    fun testResultSetGuardRedundantLegacyExitIsNotReported() =
        check(XoopsResultSetGuardInspection(), "ResultSetGuard/redundant", "modules/demo/class/Redundant.php", 1)

    /** file_safety.php: the existence check in a previous try (every catch returns) dominates unlink(). */
    fun testUnlinkCheckedInsidePreviousTry() =
        check(XoopsUnlinkInspection(), "Unlink/try", "modules/demo/include/cleanup.php", 1)

    fun testSuperglobalLeavesTheInstallerAlone() =
        check(XoopsSuperglobalInspection(), "Superglobal/installer", "install/page_pathsettings.php", 0)

    fun testSuperglobalLeavesTheUpgraderAlone() =
        check(XoopsSuperglobalInspection(), "Superglobal/installer", "upgrade/index.php", 0)

    /** findusers.php: `uname` resolves through kernel/ (TXTBOX, escaped); OTHER and undeclared keys stay. */
    fun testAttributeEscapeResolvesCoreKernelFields() =
        check(XoopsAttributeEscapeInspection(), "AttributeEscape/core", "include/findusers.php", 2)

    // --- alpha.5: XoopsCore27 fourth run (04inspections, 2026-10-25) ----------------------

    /** File::delete() via exists(), a realpath() alias, a probe the code just wrote; other path and `&&` write stay. */
    fun testUnlinkHelperAliasAndCreatedFile() {
        check(XoopsUnlinkInspection(), "Unlink/helpers", "modules/demo/class/DemoFile.php", 2)
        val lines = myFixture.doHighlighting().filter { it.description?.startsWith("XOOPS:") == true }
            .map { myFixture.editor.document.getLineNumber(it.startOffset) + 1 }.sorted()
        assertEquals(listOf(30, 58), lines)
    }

    /** register.php: the merged write to `$_POST` is told apart from the read beside it. */
    fun testSuperglobalWriteHasItsOwnMessage() {
        check(XoopsSuperglobalInspection(), "Superglobal/write", "modules/demo/register.php", 2)
        val found = xoopsProblems()
        assertTrue(found.joinToString("\n"), found.any { it.contains("assigning \$_POST rewrites the request") })
        assertTrue(found.joinToString("\n"), found.any { it.contains("read it through Xmf\\Request") })
    }

    /** upgrade/login.php: md5() as a Criteria value looks up an old hash; a stored md5() stays an error. */
    fun testWeakTokenLegacyHashLookup() {
        check(XoopsWeakTokenInspection(), "WeakToken/legacy", "modules/demo/login.php", 2)
        val found = xoopsProblems()
        assertEquals(found.joinToString("\n"), 1, found.count { it.contains("matches a legacy password hash") })
        assertEquals(found.joinToString("\n"), 1, found.count { it.contains("is not a password hash") })
    }
}
