package org.xoops.support

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ex.QuickFixWrapper
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.PhpReturn
import org.w3c.dom.Element
import org.xoops.support.fix.XoopsFix
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Review round 4 (2026-10-23): the shapes on which the Gemini and Grok plugins left files that no
 * longer parse or run, taken verbatim from the original modules. Every registered rule runs on each
 * file and every XOOPS fix it offers is applied, in whatever order the IDE lists them; afterwards the
 * file must parse, and the specific trap must not have been sprung.
 */
class NeverBreakTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/resources/corpus"

    /** PhpStorm's type inference trips its own test-only recursion assertion on big legacy pages; it is not under test. */
    override fun setUp() {
        super.setUp()
        com.intellij.openapi.util.RecursionManager.disableMissedCacheAssertions(testRootDisposable)
    }

    override fun runTestRunnable(testRunnable: com.intellij.util.ThrowableRunnable<Throwable>) {
        com.intellij.testFramework.LoggedErrorProcessor.executeWith<Throwable>(object : com.intellij.testFramework.LoggedErrorProcessor() {
            override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> =
                if (message.contains("LineMarkerProvider") || message.contains("recursion prevention") || message.contains("php.type.info")) Action.NONE else Action.ALL
        }) { super.runTestRunnable(testRunnable) }
    }

    /** Every `localInspection` of plugin.xml, instantiated. */
    private fun allInspections(): List<LocalInspectionTool> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(javaClass.getResourceAsStream("/META-INF/plugin.xml"))
        val nodes = document.getElementsByTagName("localInspection")
        return (0 until nodes.length).map { i ->
            Class.forName((nodes.item(i) as Element).getAttribute("implementationClass")).getDeclaredConstructor().newInstance() as LocalInspectionTool
        }
    }

    private val copied = HashSet<String>()

    /** Runs every rule and applies every XOOPS fix on `modules/<module>/<target>`; returns the final text. */
    private fun fixEverything(module: String, target: String): String {
        if (copied.add(module)) myFixture.copyDirectoryToProject(module, "modules/$module")
        myFixture.enableInspections(*allInspections().toTypedArray())
        myFixture.configureFromTempProjectFile("modules/$module/$target")
        var rounds = 0
        while (rounds++ < 600) {
            val fix = myFixture.getAllQuickFixes().firstOrNull { QuickFixWrapper.unwrap(it) is XoopsFix } ?: break
            myFixture.launchAction(fix)
        }
        assertTrue("more than 600 fix rounds on $module/$target", rounds < 600)
        val errors = PsiTreeUtil.findChildrenOfType(myFixture.file, PsiErrorElement::class.java)
        assertTrue(
            "$module/$target no longer parses: " + errors.joinToString(" | ") { it.errorDescription + " @" + it.textRange.startOffset },
            errors.isEmpty(),
        )
        return myFixture.editor.document.text.replace("\r\n", "\n")
    }

    /** Gemini/Grok: `isset($_POST['submit'], $_POST['passwd'])` became a comma expression. */
    fun testMultiVariableIssetStaysOneCondition() {
        val text = fixEverything("suico", "changemail.php")
        assertTrue(text, text.contains("if (Request::hasVar('submit', 'POST') && Request::hasVar('passwd', 'POST')) {"))
        assertFalse(text, text.contains("'POST'), "))
    }

    /** Gemini/Grok: a guard landed inside `if (!isset($GLOBALS['xoTheme'])`, producing `isset(require_once …`. */
    fun testHeaderGuardNeverLandsInsideACondition() {
        val text = fixEverything("xoopstube", "header.php")
        assertTrue(text, text.contains("if (!isset(\$GLOBALS['xoTheme']) || !is_object(\$GLOBALS['xoTheme'])) {"))
        assertFalse(text, text.contains("isset(require"))
    }

    /** Gemini: `\_AM_CONST` became `\(defined(…) ? … : '')`; Grok nested the ternary 2,128 times. */
    fun testNamespacedConstantIsNeverWrappedInATernary() {
        val text = fixEverything("oledrion", "class/Action/OrdersAction.php")
        assertFalse(text, text.contains("\\("))
        assertFalse(text, text.contains("defined('_AM_"))
    }

    /** Grok: `return false;` inserted into `function index(): void`. */
    fun testVoidFunctionGetsABareReturn() {
        val text = fixEverything("adslight", "index.php")
        val voidFunctions = PsiTreeUtil.findChildrenOfType(myFixture.file, Function::class.java)
            .filter { it.returnType?.text?.contains("void") == true }
        assertTrue("fixture lost its void functions", voidFunctions.isNotEmpty())
        for (fn in voidFunctions) {
            val valued = PsiTreeUtil.findChildrenOfType(fn, PhpReturn::class.java).filter { it.argument != null }
            assertTrue("value returned from ${fn.name}(): void — " + valued.joinToString { it.text }, valued.isEmpty())
        }
        assertFalse(text, text.contains("return false;\n    }\n    \$adminObject"))
    }

    /** 0.6.2 (own defect): `function &f(): array` got `return [];`. */
    fun testByReferenceHandlerNeverReturnsALiteral() {
        fixEverything("xhelp", "class/MembershipHandler.php")
        val byRef = PsiTreeUtil.findChildrenOfType(myFixture.file, Function::class.java).filter { it.isReturningByReference }
        assertTrue("fixture lost its by-reference methods", byRef.size >= 4)
        for (fn in byRef) {
            val literal = PsiTreeUtil.findChildrenOfType(fn, PhpReturn::class.java)
                .filter { PsiTreeUtil.getParentOfType(it, Function::class.java) == fn }
                .filter { r -> r.argument.let { it is ArrayCreationExpression || it?.text in setOf("[]", "null", "false", "0", "''", "true") } }
            assertTrue("${fn.name}() returns a literal by reference: " + literal.joinToString { it.text }, literal.isEmpty())
        }
    }
}
