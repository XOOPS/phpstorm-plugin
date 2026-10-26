package org.xoops.support.inspections

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor
import org.xoops.support.fix.Edits
import org.xoops.support.fix.XoopsFix
import org.xoops.support.psi.Php
import org.xoops.support.psi.XoopsPaths

/**
 * A second `xoops_cp_header()` on one request breaks the admin menu. The first one is
 * only "already there" when the included `admin_header.php` really calls it — that
 * file is read, never assumed (about a third of real modules call it there, the rest
 * leave it to the page). Without it, the page's own first call counts only when it is a
 * top-level statement; only the admin_header.php duplicate gets the delete fix.
 */
class XoopsDoubleCpHeaderInspection : XoopsInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            override fun visitFile(file: PsiFile) {
                val php = file as? PhpFile ?: return
                if (skip(php) || !XoopsPaths.path(php).contains("/admin/")) return
                val statements = Php.executableTopLevel(php)
                val include = statements.indexOfFirst { Php.includes(it, "admin_header.php") }

                val ownCalls = statements.flatMap { s -> topLevelCalls(s) }.sortedBy { it.textRange.startOffset }
                // What rendered first: the included admin_header.php, or a call that is itself a top-level
                // statement. A call inside a branch dominates nothing — `if ($a) { header } else { header }`
                // renders once (the system admin pages call it once on top, then again in error branches).
                var first: PsiElement? = if (include >= 0 && siblingRenders(php)) statements[include] else null
                for (call in ownCalls) {
                    val renderer = first
                    if (renderer == null) {
                        val own = Php.statementInBlock(call)
                        if (statements.any { it == own }) first = call
                        continue
                    }
                    if (call.textRange.startOffset <= renderer.textRange.startOffset) continue
                    val own = renderer is FunctionReference
                    val where = if (own) "was already called on line ${lineOf(php, renderer)}" else "is already called by admin_header.php"
                    // The page's own earlier call (system admin: once on top, again in a branch): the page has
                    // used the first theme in between (stylesheets, breadcrumbs, template variables), and the
                    // second header() builds a new one, so deleting it is not output-neutral. The repair is
                    // structural — one call per path — and stays with the author.
                    val fixable = !own && !optionsChangedBetween(php, renderer, call)
                    holder.registerProblem(
                        call,
                        "XOOPS: xoops_cp_header() $where; a second call re-initialises the theme and breaks the admin menu",
                        *(if (fixable) arrayOf(DeleteStatementFix("Remove the duplicate xoops_cp_header() call")) else emptyArray()),
                    )
                }
            }
        }
    }

    private fun lineOf(file: PhpFile, element: PsiElement): Int =
        (file.viewProvider.document?.getLineNumber(element.textRange.startOffset) ?: 0) + 1

    /**
     * `$xoopsOption['template_main'] = 'system_header.tpl';` on the way to the second call: that call
     * renders a different page template, so deleting it changes the page. Report it, offer no fix.
     * An assignment counts when its block also holds the second call (a sibling `case` does not).
     */
    private fun optionsChangedBetween(file: PhpFile, from: PsiElement, to: PsiElement): Boolean =
        com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(file, com.jetbrains.php.lang.psi.elements.AssignmentExpression::class.java)
            .any { a ->
                a.textRange.startOffset > from.textRange.endOffset && a.textRange.endOffset < to.textRange.startOffset &&
                    a.variable?.text?.contains("xoopsOption") == true &&
                    Php.statementInBlock(a)?.parent?.let { block -> com.intellij.psi.util.PsiTreeUtil.isAncestor(block, to, true) } == true
            }

    private fun topLevelCalls(statement: PsiElement): List<FunctionReference> =
        Php.calls(statement, "xoops_cp_header").filter { Php.enclosingFunction(it) == null }

    private fun siblingRenders(file: PhpFile): Boolean {
        val vf = file.virtualFile?.parent?.findChild("admin_header.php") ?: return false
        val sibling = PsiManager.getInstance(file.project).findFile(vf) ?: return false
        return Php.calls(sibling, "xoops_cp_header").any { Php.enclosingFunction(it) == null }
    }
}

/** Deletes the statement that owns the flagged element. Not offered in "Fix all". */
class DeleteStatementFix(private val name: String, batchSafe: Boolean = false) : XoopsFix(batchSafe) {
    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement ?: return
        val statement = Php.statementInBlock(element) ?: return
        Edits.deleteStatementLine(project, statement)
    }
}
