package org.xoops.support.inspections

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor
import org.xoops.support.fix.XoopsFix

/**
 * The module installer (`SqlUtility::splitMySqlFile()`) treats a line of the install SQL as a
 * comment only when it starts with `-- ` (dash, dash, space) or `#`. A bare `--` or `--text` stays
 * glued to the next statement, `prefixQuery()` then fails its `^CREATE TABLE` match, and that
 * table is silently never created.
 *
 * The SQL file is checked from the manifest that names it (`$modversion['sqlfile']['mysql']` in
 * `xoops_version.php`), so the finding sits in the file XOOPS loads and the fix edits the SQL file.
 */
object SqlCommentText {
    /** Start offsets of the `--` of every bare comment line. */
    fun bareComments(text: String): List<Int> =
        Regex("""(?m)^--(?=\S|$)""").findAll(text).map { it.range.first }.toList()

    /**
     * `--` alone is a phpMyAdmin separator with no comment in it: the line is blanked (not deleted,
     * so the line numbers the batch fixes were reported on stay valid). It must not become `-- `:
     * that ends in a space PhpStorm's "Remove trailing spaces on save" strips again, so the file
     * never changed on disk (0.7.2 review). `--text` gets its space. False when the line at [start]
     * is no longer a bare comment.
     */
    fun rewriteBareComment(doc: com.intellij.openapi.editor.Document, start: Int, content: String): Boolean {
        val body = content.trimEnd()
        if (!body.startsWith("--") || (body.length > 2 && body[2].isWhitespace())) return false
        if (body == "--") doc.deleteString(start, start + content.length) else doc.insertString(start + 2, " ")
        return true
    }

    fun lineNumbers(text: String, offsets: List<Int>): List<Int> =
        offsets.map { o -> text.substring(0, o).count { it == '\n' } + 1 }

    /** The SQL file a `'sql/mysql.sql'` literal in `xoops_version.php` points at, or null. */
    fun sqlFileOf(literal: StringLiteralExpression): VirtualFile? {
        val rel = literal.contents.replace('\\', '/').trimStart('/')
        if (!rel.endsWith(".sql")) return null
        val root = literal.containingFile.virtualFile?.parent ?: return null
        return root.findFileByRelativePath(rel)?.takeIf { !it.isDirectory }
    }
}

class XoopsSqlCommentInspection : XoopsInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            override fun visitPhpFile(file: PhpFile) {
                if (skipConvention(file) || !file.name.equals("xoops_version.php", true)) return
                for (literal in PsiTreeUtil.findChildrenOfType(file, StringLiteralExpression::class.java)) {
                    val sql = SqlCommentText.sqlFileOf(literal) ?: continue
                    // The open document first: a fix applied moments ago lives there, not yet on disk.
                    val text = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getCachedDocument(sql)?.text
                        ?: runCatching { VfsUtilCore.loadText(sql) }.getOrNull() ?: continue
                    val offsets = SqlCommentText.bareComments(text)
                    if (offsets.isEmpty()) continue
                    val lines = SqlCommentText.lineNumbers(text, offsets)
                    holder.registerProblem(
                        literal,
                        "XOOPS: ${literal.contents} has ${offsets.size} bare '--' comment line(s) (line ${lines.joinToString(", ")}) — " +
                            "the installer treats only '-- ' as a comment and skips the CREATE TABLE that follows",
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        SqlCommentSpaceFix(),
                    )
                }
            }
        }
    }
}

class SqlCommentSpaceFix : XoopsFix() {
    override fun getFamilyName(): String = "Make the bare -- comments in the SQL file acceptable to the installer"

    override fun getElementToMakeWritable(currentFile: PsiFile): PsiElement? {
        val literal = PsiTreeUtil.findChildrenOfType(currentFile, StringLiteralExpression::class.java)
            .firstOrNull { SqlCommentText.sqlFileOf(it) != null } ?: return currentFile
        val vf = SqlCommentText.sqlFileOf(literal) ?: return currentFile
        return PsiManager.getInstance(currentFile.project).findFile(vf) ?: currentFile
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val literal = descriptor.psiElement as? StringLiteralExpression ?: return
        val vf = SqlCommentText.sqlFileOf(literal) ?: return
        val psi = PsiManager.getInstance(project).findFile(vf) ?: return
        val doc = PsiDocumentManager.getInstance(project).getDocument(psi) ?: return
        for (offset in SqlCommentText.bareComments(doc.text).asReversed()) {
            val lineEnd = doc.getLineEndOffset(doc.getLineNumber(offset))
            SqlCommentText.rewriteBareComment(doc, offset, doc.getText(TextRange(offset, lineEnd)))
        }
        PsiDocumentManager.getInstance(project).commitDocument(doc)
    }
}
