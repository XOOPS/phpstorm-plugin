package org.xoops.support

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.xoops.support.inspections.XoopsAttributeEscapeInspection
import org.xoops.support.inspections.XoopsDeprecatedDbApiInspection
import org.xoops.support.inspections.XoopsMissingRegisteredTemplateInspection
import org.xoops.support.inspections.XoopsMutatingQueryInspection
import org.xoops.support.inspections.XoopsResultSetGuardInspection
import org.xoops.support.inspections.XoopsRootPathGuardInspection
import org.xoops.support.inspections.XoopsSuperglobalInspection

/**
 * Real Publisher files (GPL, copied verbatim from the 2026-10 corpus) pinned to the facts the
 * four-plugin review established about them. A golden fixture proves a rule on the shape its
 * author imagined; these prove it on the shape that embarrassed 0.4.1.
 */
class CorpusSmokeTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/resources/corpus"

    override fun runTestRunnable(testRunnable: com.intellij.util.ThrowableRunnable<Throwable>) {
        com.intellij.testFramework.LoggedErrorProcessor.executeWith<Throwable>(object : com.intellij.testFramework.LoggedErrorProcessor() {
            override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> =
                if (message.contains("LineMarkerProvider") || message.contains("recursion prevention") || message.contains("php.type.info")) Action.NONE else Action.ALL
        }) { super.runTestRunnable(testRunnable) }
    }

    private val copied = HashSet<String>()

    private fun problems(inspection: LocalInspectionTool, target: String, module: String = "publisher"): List<String> {
        if (copied.add(module)) myFixture.copyDirectoryToProject(module, "modules/$module")
        myFixture.enableInspections(inspection)
        myFixture.configureFromTempProjectFile("modules/$module/$target")
        return myFixture.doHighlighting().mapNotNull { it.description }.filter { it.startsWith("XOOPS:") }
    }

    private fun applyAll(prefix: String) {
        var rounds = 0
        while (rounds++ < 400) {
            val fix = myFixture.getAllQuickFixes().firstOrNull { it.familyName.startsWith(prefix) || it.text.startsWith(prefix) } ?: break
            myFixture.launchAction(fix)
        }
    }

    private fun text(): String = myFixture.editor.document.text.replace("\r\n", "\n")

    /** Codex F1: the application wrapper `VersionControlMySQL::query()` must never be renamed to `exec()`. */
    fun testVersionControlWrapperIsNotADatabaseReceiver() {
        val found = problems(XoopsMutatingQueryInspection(), "class/Versioncontrol/VersionControlMySQL.php")
        assertEquals(found.joinToString("\n"), 0, found.size)
        val deprecated = problems(XoopsDeprecatedDbApiInspection(), "class/Versioncontrol/VersionControlMySQL.php")
        assertEquals(deprecated.joinToString("\n"), 0, deprecated.size)
    }

    /** Codex K2 / grok: the fetch inside `if (!isResultSet)` is a reversed condition, reported once, with no guard fix. */
    fun testVersionControlReversedConditionIsTheOnlyResultFinding() {
        val found = problems(XoopsResultSetGuardInspection(), "class/Versioncontrol/VersionControlMySQL.php")
        assertEquals(found.joinToString("\n"), 1, found.size)
        assertTrue(found[0], found[0].contains("reversed"))
        assertTrue("no guard fix for a reversed condition", myFixture.getAllQuickFixes().none { it.familyName.startsWith("Insert isResultSet()") })
    }

    /** Spec §3.4: the guard must land on its own line after `$result = query($sql); // TODO: error check`. */
    fun testNotificationGuardLandsOnItsOwnLine() {
        val found = problems(XoopsResultSetGuardInspection(), "include/notification.inc.php")
        assertEquals(found.joinToString("\n"), 2, found.size)
        var rounds = 0
        while (rounds++ < 10) {
            val fix = myFixture.getAllQuickFixes().firstOrNull { it.familyName.startsWith("Insert isResultSet()") } ?: break
            myFixture.launchAction(fix)
        }
        val text = myFixture.editor.document.text.replace("\r\n", "\n")
        val d = "\\$"
        assertTrue(text, Regex("""query\(${d}sql\); // TODO: error check\n\s+if \(!${d}GLOBALS\['xoopsDB'\]->isResultSet\(${d}result\)\) \{\n""").containsMatchIn(text))
        assertFalse("guard glued to the assignment line", text.contains("query(\$sql); if ("))
        assertEquals(0, myFixture.doHighlighting().mapNotNull { it.description }.count { it.startsWith("XOOPS:") && it.contains("without a dominating") })
    }

    /**
     * Spec §2.6 / review F8: `mime_id`, `mime_admin`, `mime_user` are XOBJ_DTYPE_INT (resolved from
     * `class/Mimetype.php` when PhpStorm cannot type `$mime`), and a getVar() used as a ternary condition
     * prints nothing — 0.4.1 reported 15 here, the right number is 0.
     */
    fun testIntegerMimeIdIsNeverAnEscapeFinding() {
        val found = problems(XoopsAttributeEscapeInspection(), "class/MimetypesUtility.php")
        assertEquals(found.joinToString("\n"), 0, found.size)
    }

    // --- 16-module run (03inspections), round 3 ---------------------------------------------

    /**
     * Codex round 3: News rebuilds its permissions from POST arrays inside foreach. A getString()
     * rewrite would hand the loop the string "Array" and save no group; the accessor must be getArray().
     */
    fun testNewsPermissionArraysBecomeGetArray() {
        problems(XoopsSuperglobalInspection(), "admin/index.php", module = "news")
        applyAll("Replace ")
        val after = text()
        assertTrue(after, after.contains("foreach (Request::getArray('groups_news_can_approve', [], 'POST') as"))
        assertTrue(after, after.contains("foreach (Request::getArray('blacklist', [], 'POST') as"))
        assertFalse("array read rewritten to a string accessor", Regex("""getString\('(groups_news_can_\w+|blacklist|delupload)'""").containsMatchIn(after))
    }

    /** newbb/footer.php: the first statement is `global $xoTheme;`; 0.5.0 put the guard inside it (parse error). */
    fun testFooterGuardGoesAboveTheGlobalStatement() {
        val found = problems(XoopsRootPathGuardInspection(), "footer.php", module = "newbb")
        assertEquals(found.joinToString("\n"), 1, found.size)
        applyAll("Insert XOOPS_ROOT_PATH")
        val after = text()
        assertFalse(after, after.contains("global defined("))
        assertTrue(after, after.indexOf("defined('XOOPS_ROOT_PATH') || exit('Restricted access');") < after.indexOf("global \$xoTheme;"))
    }

    /** pm/index.php is a redirect, profile/index.php boots through user.php, xasset/verify.php through servicemain.php. */
    fun testPublicEntryPagesAreNotGuarded() {
        for ((module, file) in listOf("pm" to "index.php", "profile" to "index.php", "xasset" to "verify.php")) {
            val found = problems(XoopsRootPathGuardInspection(), file, module = module)
            assertEquals("$module/$file: " + found.joinToString("\n"), 0, found.size)
        }
    }

    /** adslight: `if (!isResultSet) { trigger_error(..., E_USER_ERROR); }` is a guard; no second guard behind it. */
    fun testNoSecondGuardBehindAUserErrorGuard() {
        problems(XoopsResultSetGuardInspection(), "admin/view_ads.php", module = "adslight")
        applyAll("Insert isResultSet()")
        val after = text()
        assertFalse(after, Regex("""E_USER_ERROR\);\s*\}\s*if \(!\$\w+->isResultSet\(""").containsMatchIn(after))
        assertEquals(0, myFixture.doHighlighting().mapNotNull { it.description }.count { it.startsWith("XOOPS:") && it.contains("without a dominating") })
    }

    /**
     * xhelp: `$ret = $this->addMembership(...); if (!$ret) break;` tests a boolean, not a result set.
     * Grok rewrote four of these to isResultSet() and the loop broke on the first success. The legacy-guard
     * rewrite must only touch a variable assigned from a database query.
     */
    fun testBooleanChecksAreNotResultSetGuards() {
        problems(XoopsResultSetGuardInspection(), "class/MembershipHandler.php", module = "xhelp")
        val before = text()
        applyAll("Insert isResultSet()")
        applyAll("Replace the legacy")
        val after = text()
        val boolCheck = Regex("""if \(!${'$'}ret\) \{""")
        assertEquals("boolean checks changed", boolCheck.findAll(before).count(), boolCheck.findAll(after).count())
        assertFalse(after, after.contains("isResultSet(\$ret)) {\n                break;"))
    }

    /**
     * wgevents registers its block templates with `'type' => 'block'` (singular): the installer reads
     * `templates/<file>` for that type, so the five entries are broken and the fix must point them at
     * `blocks/<file>` - and that result must not be reported again (round 4 reported it as "read from blocks/").
     */
    fun testWgeventsBlockTypeIsRepointedOnce() {
        val found = problems(XoopsMissingRegisteredTemplateInspection(), "xoops_version.php", module = "wgevents")
        assertEquals(found.joinToString("\n"), 6, found.size)
        applyAll("Change to")
        val after = text()
        assertTrue(after, after.contains("['file' => 'blocks/wgevents_block_events_table.tpl', 'description' => '', 'type' => 'block']"))
        val left = myFixture.doHighlighting().mapNotNull { it.description }.filter { it.startsWith("XOOPS:") }
        assertEquals(left.joinToString("\n"), 1, left.size)
        assertTrue(left[0], left[0].contains("wgevents_search.tpl"))
    }

    /** suico registers `'file' => 'blocks/suico_block_friends.tpl'` with no type: read from templates/blocks/, which exists. */
    fun testSuicoBlocksFolderRegistrationIsNotAFinding() {
        val found = problems(XoopsMissingRegisteredTemplateInspection(), "xoops_version.php", module = "suico")
        assertFalse(found.joinToString("\n"), found.any { it.contains("suico_block_friends") || it.contains("suico_block_lastpictures") })
    }
}
