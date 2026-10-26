package org.xoops.support.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.ArrayAccessExpression
import com.jetbrains.php.lang.psi.elements.ArrayHashElement
import com.jetbrains.php.lang.psi.elements.AssignmentExpression
import com.jetbrains.php.lang.psi.elements.BinaryExpression
import com.jetbrains.php.lang.psi.elements.ConcatenationExpression
import com.jetbrains.php.lang.psi.elements.SelfAssignmentExpression
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.Variable
import org.xoops.support.fix.XoopsFix
import org.xoops.support.psi.XoopsPaths
import java.nio.charset.StandardCharsets

/*
 * Rules on the installer SQL file itself (every `.sql` file under a module's `sql` folder). They are not PHP inspections:
 * the file is visited as a whole and every finding carries a text range. Harvested from the
 * Gemini and Codex plugins after review round 4 and rebuilt without offset-based fixes.
 */

/** Shared gate and text helpers for the SQL rules. */
object InstallSql {
    /** The module schema the installer runs when no `sqlfile` entry names another file. */
    const val MODULE_SCHEMA = "mysql.sql"

    /** A finding on a text range of a non-PHP file; the plain range overload registered nothing in tests. */
    fun report(
        holder: ProblemsHolder, file: PsiFile, range: TextRange, message: String, onTheFly: Boolean,
        type: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING, vararg fixes: com.intellij.codeInspection.LocalQuickFix,
    ) {
        // Anchor on the leaf under the range: a descriptor on the file element itself is registered but
        // never reaches the highlighting pass for an SQL file (only one of four survived in tests).
        val leaf = file.findElementAt(range.startOffset) ?: file
        val inLeaf = if (leaf === file) range else TextRange(
            range.startOffset - leaf.textRange.startOffset,
            minOf(range.endOffset, leaf.textRange.endOffset) - leaf.textRange.startOffset,
        )
        holder.registerProblem(holder.manager.createProblemDescriptor(leaf, inLeaf, message, type, onTheFly, *fixes))
    }

    fun isInstallerSql(file: PsiFile): Boolean {
        val p = XoopsPaths.path(file)
        return p.endsWith(".sql") && p.contains("/sql/") && !XoopsPaths.isExcluded(file) && !XoopsPaths.isTestTree(file)
    }

    /**
     * The installer runs the file: a module's `sql/mysql.sql`, or the file an active `sqlfile` entry of its
     * `xoops_version.php` names, resolved from the module root (`sql/english/install.sql` is two levels down).
     * A `sql/` folder also holds Adminer / phpMyAdmin dumps and old upgrade scripts (newbb: `2510debug1.sql`,
     * `xoops_mysql.sql`, `mysql.400.sql`) that SqlUtility never sees; their engine, prefix and `--` lines are not
     * findings. Outside any module only the core installer's `install/sql/` schema stays inspected; any other
     * loose `sql/` folder holds dumps too.
     */
    fun installerFile(file: PsiFile): Boolean {
        val vf = file.virtualFile ?: return true
        val root = XoopsPaths.moduleRoot(vf) ?: return XoopsPaths.path(file).contains("/install/sql/")
        if (file.name.equals(MODULE_SCHEMA, true)) return true
        val manifestFile = root.findChild("xoops_version.php") ?: return false
        val manifest = PsiManager.getInstance(file.project).findFile(manifestFile) as? PhpFile ?: return false
        return sqlfilePaths(manifest).any { root.findFileByRelativePath(it.removePrefix("./")) == vf }
    }

    /**
     * Every `.sql` path a `sqlfile` declaration of the manifest assigns, read from the PHP PSI, so comments,
     * strings, heredocs, `?>` and comparisons are the parser's business. `$modversion['sqlfile']['mysql'] = 'x'`,
     * `$modversion['sqlfile'][XOOPS_DB_TYPE] = 'x'`, `$modversion['sqlfile'] = ['pgsql' => 'y', 'mysql' => 'x']` and
     * the array-manifest `$modversion = ['sqlfile' => ['mysql' => 'x']]` (also `array(…)`) all count; every database
     * entry is read, and `'sql/' . 'install.sql'` is joined. Only `$modversion` declares: `$other['sqlfile']` does not.
     */
    fun sqlfilePaths(manifest: PhpFile): List<String> {
        val values = ArrayList<PsiElement>()
        for (a in PsiTreeUtil.findChildrenOfType(manifest, AssignmentExpression::class.java)) {
            // `.=` appends to a path set elsewhere; it is not the declaration.
            if (a is SelfAssignmentExpression || !isModversion(a.variable)) continue
            // Only the `[…]` chain being written counts: a `sqlfile` read inside an index, or a destructuring
            // target, does not make the right-hand side an installer file.
            var access = a.variable as? ArrayAccessExpression
            while (access != null && !isSqlfileKey(access.index?.value)) access = access.value as? ArrayAccessExpression
            if (access != null) a.value?.let(values::add)
        }
        for (h in PsiTreeUtil.findChildrenOfType(manifest, ArrayHashElement::class.java)) {
            // A top-level entry of the array assigned to `$modversion` itself.
            val owner = h.parent?.parent as? AssignmentExpression
            if (isSqlfileKey(h.key) && owner?.value === h.parent && isModversion(owner.variable)) h.value?.let(values::add)
        }
        return values.flatMap(::strings).filter { it.endsWith(".sql", ignoreCase = true) }
    }

    private fun isSqlfileKey(e: PsiElement?): Boolean = (e as? StringLiteralExpression)?.contents.equals("sqlfile", ignoreCase = true)

    private fun isModversion(e: PsiElement?): Boolean {
        var root = e
        while (root is ArrayAccessExpression) root = root.value
        return (root as? Variable)?.name == "modversion"
    }

    /** The constant string of a literal or a literal-only concatenation, else null. */
    private fun constant(e: PsiElement?): String? = when (e) {
        is StringLiteralExpression -> e.contents
        is ConcatenationExpression -> constant(e.leftOperand)?.let { l -> constant(e.rightOperand)?.let { l + it } }
        else -> null
    }

    /** Every constant string in an `sqlfile` value; a concatenation with a variable part names no file. */
    private fun strings(e: PsiElement): List<String> =
        constant(e)?.let(::listOf) ?: if (e is BinaryExpression) emptyList() else e.children.flatMap(::strings)

    /** `-- ` and `#` line comments and block comments blanked, offsets kept. */
    fun stripComments(text: String): String {
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            if (text.startsWith("-- ", i) || text[i] == '#') {
                val end = text.indexOf('\n', i).let { if (it < 0) text.length else it }
                repeat(end - i) { out.append(' ') }
                i = end
                continue
            }
            if (text.startsWith("/*", i)) {
                val end = text.indexOf("*/", i + 2).let { if (it < 0) text.length else it + 2 }
                for (k in i until end) out.append(if (text[k] == '\n' || text[k] == '\r') text[k] else ' ')
                i = end
                continue
            }
            out.append(text[i])
            i++
        }
        return out.toString()
    }

    val createTable = Regex("""(?i)\bCREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?[`'"]?([A-Za-z0-9_{}%]+)""")

    fun read(file: VirtualFile?): String? {
        if (file == null || file.isDirectory || file.length > 1_500_000) return null
        return runCatching { String(file.contentsToByteArray(), StandardCharsets.UTF_8) }.getOrNull()
    }
}

/**
 * Installer DDL the XOOPS installer cannot run as written: a `FOREIGN KEY … REFERENCES` (the
 * prefixer rewrites only the created table, so the reference dangles and every later write
 * fails with 1452 or is silently dropped under INSERT IGNORE), a hard-coded prefix on the table
 * name (`xoops_`, `{prefix}`, `%s` are not installer tokens), and `ENGINE=MyISAM` (no
 * transactions, no row locks; InnoDB has had FULLTEXT since MySQL 5.6). Report only.
 */
class XoopsInstallSqlInspection : LocalInspectionTool() {
    override fun getShortName(): String = "XoopsInstallSql"

    private val foreignKey = Regex("""(?i)\b(?:FOREIGN\s+KEY|REFERENCES)\b""")
    private val engine = Regex("""(?i)\b(?:ENGINE|TYPE)\s*=\s*MyISAM\b""")

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                if (!InstallSql.isInstallerSql(file) || !InstallSql.installerFile(file)) return
                val code = InstallSql.stripComments(file.text)
                for (m in foreignKey.findAll(code)) {
                    InstallSql.report(
                        holder, file, TextRange(m.range.first, m.range.last + 1),
                        "XOOPS: the installer prefixes the created table but not the REFERENCES target — the key dangles; " +
                            "enforce the relation in the handler, or add it in oninstall.php with \$db->prefix()", isOnTheFly,
                    )
                }
                for (m in InstallSql.createTable.findAll(code)) {
                    val name = m.groupValues[1]
                    if (name.startsWith("xoops_", true) || name.startsWith("{") || name.startsWith("%")) {
                        InstallSql.report(
                            holder, file, TextRange(m.groups[1]!!.range.first, m.groups[1]!!.range.last + 1),
                            "XOOPS: the installer adds the site prefix itself — use the bare module table name, '$name' is not a substitution token", isOnTheFly,
                        )
                    }
                }
                // The core schema (install/sql/mysql.structure.sql) keeps MyISAM for its legacy tables on
                // purpose and creates the new ones InnoDB; converting it is a core decision, not a lint.
                if (XoopsPaths.moduleRoot(file.virtualFile) == null) return
                for (m in engine.findAll(code)) {
                    InstallSql.report(
                        holder, file, TextRange(m.range.first, m.range.last + 1),
                        "XOOPS: MyISAM has no transactions or row locks — create module tables with ENGINE=InnoDB (FULLTEXT is supported since MySQL 5.6)", isOnTheFly,
                        ProblemHighlightType.WEAK_WARNING,
                    )
                }
            }
        }
    }
}

/**
 * A `CREATE TABLE` in `sql/mysql.sql` that no update path mentions is never created for a module
 * that is already installed: the next upgrade fails with 1146. Update paths: `onupdate.php`,
 * `include/onupdate.php`, `include/update.php`, every YAML under `sql`, or an update that re-runs the
 * SQL file / `createMissingTables()`. Report only — the idempotent create belongs to the author.
 */
class XoopsUpdateCreatesTableInspection : LocalInspectionTool() {
    override fun getShortName(): String = "XoopsUpdateCreatesTable"

    private val wholesale = Regex("""(?i)sqlfile|mysql\.sql|createMissingTables|installTables|synchronizeSchema|SqlUtility""")

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                if (!InstallSql.isInstallerSql(file) || !file.name.equals(InstallSql.MODULE_SCHEMA, true)) return
                val module = file.virtualFile?.parent?.parent ?: return
                if (!installerRuns(module)) return
                val updates = updateText(module) ?: return
                if (wholesale.containsMatchIn(updates)) return
                val code = InstallSql.stripComments(file.text)
                for (m in InstallSql.createTable.findAll(code)) {
                    val table = m.groupValues[1]
                    if (updates.contains(table, ignoreCase = true)) continue
                    InstallSql.report(
                        holder, file, TextRange(m.range.first, m.range.last + 1),
                        // Whether a released version shipped without the table cannot be told from the tree: a
                        // table as old as the module needs no update path, so this is a note, not a defect.
                        "XOOPS: no update path mentions '$table' — if a released version shipped without it, sites that update " +
                            "from that version never get it (error 1146); then create it idempotently in onupdate.php or list it in the migrate YAML",
                        isOnTheFly, ProblemHighlightType.WEAK_WARNING,
                    )
                }
            }
        }
    }

    /**
     * The file is installed at all: the manifest declares an active `sqlfile` (not commented out, not
     * `false`), or an install callback reads `mysql.sql` itself (Protector). pm 2.7 comments its
     * sqlfile out and uses the core priv_msgs table, so its `pm_messages` DDL is dead.
     */
    private fun installerRuns(module: VirtualFile): Boolean {
        val manifest = InstallSql.read(module.findChild("xoops_version.php")) ?: return true
        val declared = manifest.lines().any { line ->
            val t = line.trim()
            !t.startsWith("//") && !t.startsWith("#") && !t.startsWith("*") && sqlfile.containsMatchIn(t) && !sqlfileFalse.containsMatchIn(t)
        }
        if (declared) return true
        return listOf("oninstall.php", "include/oninstall.php", "include/install.php").any { rel ->
            InstallSql.read(module.findFileByRelativePath(rel))?.contains(InstallSql.MODULE_SCHEMA) == true
        }
    }

    private val sqlfile = Regex("""\${'$'}modversion\s*\[\s*['"]sqlfile['"]\s*]""")
    private val sqlfileFalse = Regex("""\]\s*=\s*(?i:false|null)\s*;""")

    /** Combined update sources, or null when the module has no update callback at all. */
    private fun updateText(module: VirtualFile): String? {
        val parts = ArrayList<String>()
        for (rel in listOf("onupdate.php", "include/onupdate.php", "include/update.php", "include/install.php")) {
            InstallSql.read(module.findFileByRelativePath(rel))?.let(parts::add)
        }
        module.findChild("sql")?.children?.forEach { child ->
            val n = child.name.lowercase()
            if (n.endsWith(".yml") || n.endsWith(".yaml")) InstallSql.read(child)?.let(parts::add)
        }
        return if (parts.isEmpty()) null else parts.joinToString("\n")
    }
}

/**
 * A line that is exactly `--` is not a comment for `SqlUtility::splitMySqlFile()` (only `-- ` and
 * `#` are): it stays glued to the next statement, `prefixQuery()` then fails its `^CREATE TABLE`
 * match and the installer skips that table without an error. One finding per line; the fix inserts
 * the space on that line and nothing else, so it is safe in any batch order.
 */
class XoopsSqlFileCommentInspection : LocalInspectionTool() {
    override fun getShortName(): String = "XoopsSqlFileComment"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                if (!InstallSql.isInstallerSql(file) || !InstallSql.installerFile(file)) return
                val text = file.text
                for (offset in SqlCommentText.bareComments(text)) {
                    val line = text.substring(0, offset).count { it == '\n' }
                    InstallSql.report(
                        holder, file, TextRange(offset, offset + 2),
                        "XOOPS: a bare '--' is not a comment for the installer — the CREATE TABLE that follows is skipped", isOnTheFly,
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING, SqlFileCommentSpaceFix(line, file.virtualFile),
                    )
                }
            }
        }
    }
}

/**
 * A line that is exactly `--` is a phpMyAdmin separator with nothing in it and is blanked (kept as
 * an empty line so later findings keep their line numbers). It must NOT become `-- `: that line ends in a space, and
 * PhpStorm's "Remove trailing spaces on save" strips it again on every save — the edit was visible
 * in the document and gone 130 ms later (`TrailingSpacesStripper.beforeDocumentSaving`, 0.7.2.6
 * run). `--text` keeps its text and gets the space.
 *
 * The document is taken from the descriptor's file when that is still valid (editor, intention
 * preview) and from the file captured at report time otherwise (batch "Fix all", where the SQL leaf
 * is already stale). A physical file is saved afterwards: in a batch run no editor holds it.
 */
class SqlFileCommentSpaceFix(private val line: Int, private val target: VirtualFile?) : XoopsFix() {
    override fun getFamilyName(): String = "Make the bare -- a comment the installer accepts"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val live = descriptor.psiElement?.takeIf { it.isValid }?.containingFile
        val doc = when {
            live != null -> PsiDocumentManager.getInstance(project).getDocument(live)
            target != null -> com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(target)
            else -> null
        } ?: return
        if (line >= doc.lineCount) return
        val start = doc.getLineStartOffset(line)
        val content = doc.getText(TextRange(start, doc.getLineEndOffset(line)))
        // Re-derived from the live line: only an unchanged bare comment is edited.
        if (!SqlCommentText.rewriteBareComment(doc, start, content)) return
        PsiDocumentManager.getInstance(project).commitDocument(doc)
        if (live?.isPhysical != false) {
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(
                { com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().saveDocument(doc) },
                com.intellij.openapi.application.ModalityState.defaultModalityState(),
            )
        }
    }
}
