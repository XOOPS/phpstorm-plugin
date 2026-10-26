package org.xoops.support

import com.intellij.psi.PsiPolyVariantReference
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import org.xoops.support.inspections.XoopsDoubleCpHeaderInspection
import org.xoops.support.inspections.XoopsFileHeaderInspection
import org.xoops.support.inspections.XoopsIncludeOnceHeaderInspection
import org.xoops.support.inspections.XoopsLanguageLoadInspection
import org.xoops.support.inspections.XoopsLanguageRoleInspection
import org.xoops.support.inspections.XoopsLocaleConstantInspection
import org.xoops.support.inspections.XoopsMenuAutoloadInspection
import org.xoops.support.inspections.XoopsMissingRegisteredTemplateInspection
import org.xoops.support.inspections.XoopsResultSetGuardInspection
import org.xoops.support.inspections.XoopsRootPathGuardInspection
import org.xoops.support.inspections.XoopsSmartyDelimiterInspection
import org.xoops.support.inspections.XoopsSuperglobalInspection
import org.xoops.support.inspections.XoopsUnregisteredTemplateInspection
import org.xoops.support.psi.XoopsCore
import org.xoops.support.scaffold.ModuleScaffold

/** The three Alpha 3 features rebuilt in Alpha 4: scaffold, settings override, constant navigation. */
class XoopsFeaturesTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/resources/fixtures"

    override fun runTestRunnable(testRunnable: com.intellij.util.ThrowableRunnable<Throwable>) {
        com.intellij.testFramework.LoggedErrorProcessor.executeWith<Throwable>(object : com.intellij.testFramework.LoggedErrorProcessor() {
            override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> =
                if (message.contains("LineMarkerProvider") || message.contains("recursion prevention") || message.contains("php.type.info")) Action.NONE else Action.ALL
        }) { super.runTestRunnable(testRunnable) }
    }

    private fun xoopsProblems(): List<String> =
        myFixture.doHighlighting().mapNotNull { it.description }.filter { it.startsWith("XOOPS:") }

    /** Both layouts: every generated PHP and template file is clean under the plugin's own rules. */
    fun testScaffoldPassesTheInspections() {
        for (hybrid in listOf(false, true)) {
            val dirname = if (hybrid) "hybridmod" else "plainmod"
            val files = ModuleScaffold.files(dirname, hybrid)
            assertTrue(files.containsKey("xoops_version.php") && files.containsKey("templates/${dirname}_index.tpl"))
            assertEquals(hybrid, files.containsKey("class/Helper.php"))
            for ((rel, content) in files) myFixture.addFileToProject("modules/$dirname/$rel", content)
            myFixture.enableInspections(
                XoopsRootPathGuardInspection(), XoopsFileHeaderInspection(), XoopsIncludeOnceHeaderInspection(),
                XoopsDoubleCpHeaderInspection(), XoopsMenuAutoloadInspection(), XoopsSuperglobalInspection(),
                XoopsLanguageRoleInspection(), XoopsLanguageLoadInspection(), XoopsLocaleConstantInspection(),
                XoopsMissingRegisteredTemplateInspection(), XoopsUnregisteredTemplateInspection(), XoopsSmartyDelimiterInspection(),
            )
            for (rel in files.keys.filter { it.endsWith(".php") || it.endsWith(".tpl") }) {
                myFixture.configureFromTempProjectFile("modules/$dirname/$rel")
                val found = xoopsProblems()
                assertTrue("$dirname/$rel:\n" + found.joinToString("\n"), found.isEmpty())
            }
        }
    }

    /** The settings override wins over a missing (or present) version.php: 2.5.9 means no isResultSet(). */
    fun testCoreVersionOverrideGatesTheResultSetFix() {
        myFixture.copyDirectoryToProject("ResultSetGuard/guard", "")
        val settings = XoopsSettings.getInstance(project)
        settings.coreVersion = "2.5.9"
        try {
            myFixture.enableInspections(XoopsResultSetGuardInspection())
            myFixture.configureFromTempProjectFile("modules/demo/class/H.php")
            assertEquals(XoopsCore.Version(2, 5, 9), XoopsCore.version(myFixture.file))
            val messages = xoopsProblems()
            assertTrue(messages.joinToString("\n"), messages.any { it.contains("without a dominating result check") })
            assertTrue(messages.none { it.contains("legacy result check") })
        } finally {
            settings.coreVersion = XoopsSettings.AUTO
        }
    }

    /** `'_MI_DEMO_NAME'` as a string resolves to its define(); English before another language, same module first. */
    fun testLanguageConstantStringResolvesToItsDefine() {
        myFixture.addFileToProject("modules/demo/language/english/modinfo.php", "<?php\ndefine('_MI_DEMO_NAME', 'Demo');\n")
        myFixture.addFileToProject("modules/demo/language/german/modinfo.php", "<?php\ndefine('_MI_DEMO_NAME', 'Demo (de)');\n")
        myFixture.addFileToProject("modules/other/language/english/modinfo.php", "<?php\ndefine('_MI_DEMO_NAME', 'Elsewhere');\n")
        val file = myFixture.addFileToProject("modules/demo/xoops_version.php", "<?php\n\$modversion['name'] = constant('_MI_DEMO_NAME');\n")
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val literal = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(file, StringLiteralExpression::class.java)
            .first { it.contents == "_MI_DEMO_NAME" }
        // Through the registry, as Ctrl+B does: proves plugin.xml registers the contributor for PHP.
        val reference = com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
            .getReferencesFromProviders(literal).filterIsInstance<org.xoops.support.completion.XoopsLanguageConstantReference>().firstOrNull()
        assertNotNull("a reference on the constant string", reference)
        assertNull(org.xoops.support.completion.XoopsLanguageConstantReferenceContributor.locate(
            myFixture.addFileToProject("modules/demo/x.php", "<?php echo 'plain text';").let {
                com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(it, StringLiteralExpression::class.java).first()
            },
        ))
        // PhpStorm contributes its own reference on a constant() argument; ours is the poly-variant one over language packs.
        val targets = reference!!.multiResolve(false).map { it.element!!.containingFile.virtualFile.path }
        assertEquals(targets.joinToString(), 3, targets.size)
        assertTrue(targets[0], targets[0].endsWith("modules/demo/language/english/modinfo.php"))
        assertTrue(targets[1], targets[1].endsWith("modules/demo/language/german/modinfo.php"))
    }

    /** The overview counts: preloads, classes (class/ + src/), language packs, blocks, flags, module version, Smarty major. */
    fun testScannerCountsTheOverviewColumns() {
        val root = java.nio.file.Files.createTempDirectory("xoops-scan")
        try {
            fun put(rel: String, content: String = "<?php\n") {
                val p = root.resolve(rel)
                java.nio.file.Files.createDirectories(p.parent)
                java.nio.file.Files.writeString(p, content)
            }
            put("htdocs/mainfile.php")
            put("htdocs/include/version.php", "<?php define('XOOPS_VERSION', 'XOOPS 2.7.4');")
            put("htdocs/class/libraries/vendor/smarty/smarty/src/Smarty.php", "<?php class Smarty { const SMARTY_VERSION = '5.4.1'; }")
            put("htdocs/modules/demo/xoops_version.php", "<?php \$modversion['version'] = '1.2.3';\n\$modversion['min_php'] = '7.4';\n\$modversion['min_xoops'] = '2.5.11';\n\$modversion['blocks'][] = [];\n\$modversion['blocks'][] = [];\n")
            put("htdocs/modules/demo/preloads/core.php")
            put("htdocs/modules/demo/preloads/autoloader.php")
            put("htdocs/modules/demo/class/Helper.php", "<?php\ndeclare(strict_types=1);\nclass Helper {}\n")
            put("htdocs/modules/demo/admin/admin_header.php", "<?php\n\$adminObject = \\Xmf\\Module\\Admin::getInstance();\n")
            put("htdocs/modules/demo/class/Form/Thing.php")
            put("htdocs/modules/demo/src/Service.php")
            put("htdocs/modules/demo/language/english/main.php")
            put("htdocs/modules/demo/language/german/main.php")
            // A placeholder inside a Smarty comment is not a delimiter finding (about_list.tpl field report); a bare tag is.
            put("htdocs/modules/demo/templates/demo_index.tpl", "<{* \$xoTheme->addStylesheet(\"modules/{\$xoops_dirname}/assets/css/style.css\") *}>\n<{\$x}>\n")
            put("htdocs/modules/demo/templates/demo_bad.tpl", "{if \$x}<{/if}>\n")
            put("htdocs/modules/demo/admin/index.php")
            put("htdocs/modules/demo/sql/mysql.sql", "-- x\n")
            put("htdocs/modules/demo/tests/ATest.php")
            val report = org.xoops.support.toolwindow.XoopsScanner().scan(root, org.xoops.support.toolwindow.ScanOptions(phpLines = true))
            assertEquals("5", report.smartyVersion)
            val m = report.modules.single()
            assertEquals("1.2.3", m.version)
            assertEquals("7.4", m.minPhp)
            assertEquals("2.5.11", m.minXoops)
            assertTrue(m.hasAutoloader && m.usesXmfAdmin && m.hasStrictTypes)
            assertTrue("php lines counted: ${m.phpLines}", m.phpLines >= 10)
            assertEquals(-1L, org.xoops.support.toolwindow.XoopsScanner().scan(root).modules.single().phpLines)
            // Column settings: everything shown by default, hiding one survives a round trip, unknown keys are harmless.
            val settings = XoopsSettings.getInstance(project)
            assertTrue(org.xoops.support.toolwindow.OverviewColumn.entries.all(settings::isColumnShown))
            settings.setColumnShown(org.xoops.support.toolwindow.OverviewColumn.LINES, false)
            val copy = XoopsSettings().apply { loadState(settings) }
            assertFalse(copy.isColumnShown(org.xoops.support.toolwindow.OverviewColumn.LINES))
            assertTrue(copy.isColumnShown(org.xoops.support.toolwindow.OverviewColumn.VERSION))
            settings.setColumnShown(org.xoops.support.toolwindow.OverviewColumn.LINES, true)
            assertEquals(2L, m.preloadCount)
            assertEquals(3L, m.classCount)
            assertEquals(2L, m.languageCount)
            assertEquals(2, m.blockCount)
            assertEquals(2L, m.templateCount)
            val delimiterFindings = report.findings.filter { it.message.contains("delimiter") }
            assertEquals(delimiterFindings.joinToString(), 1, delimiterFindings.size)
            assertTrue(delimiterFindings.single().path.toString().endsWith("demo_bad.tpl"))
            assertTrue(m.hasAdmin && m.hasSql && m.hasTests)
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
