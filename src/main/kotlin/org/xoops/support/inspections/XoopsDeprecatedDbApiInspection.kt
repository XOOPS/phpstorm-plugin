package org.xoops.support.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.If
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor
import org.xoops.support.psi.Php
import org.xoops.support.psi.XoopsCore
import org.xoops.support.psi.Php.SqlKind

/**
 * `queryF()` and `quoteString()` are deprecated (XOOPS 2.5.12 / 2.7).
 *
 * `queryF()` is the forced-write alias: on this core it delegates to `exec()`, while
 * `query()` refuses a mutation on a GET request. The replacement therefore depends on the
 * SQL: `exec()` for a write, `query()` for a read, and **no automatic rewrite** when the SQL
 * cannot be resolved. A call inside `if ($force)` is a write by intent. Only calls on the
 * XOOPS database object count; the database classes themselves are never rewritten.
 * On a core older than 2.5.12 `queryF()` is the only forced write and `exec()` does not exist,
 * so it is not reported there; `quote()` exists on every supported core.
 */
class XoopsDeprecatedDbApiInspection : XoopsInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            override fun visitPhpMethodReference(reference: MethodReference) {
                if (skipConvention(reference.containingFile)) return
                val name = reference.name ?: return
                if (!"quoteString".equals(name, true) && !"queryF".equals(name, true)) return
                if (!Php.isDatabaseCall(reference)) return
                when {
                    "quoteString".equals(name, true) -> holder.registerProblem(
                        reference,
                        "XOOPS: quoteString() is deprecated — use quote()",
                        ProblemHighlightType.LIKE_DEPRECATED,
                        RenameCallFix("quoteString", "quote"),
                    )
                    XoopsCore.atLeast(reference.containingFile, XoopsCore.EXEC_API) -> reportQueryF(holder, reference)
                }
            }
        }
    }

    private fun reportQueryF(holder: ProblemsHolder, reference: MethodReference) {
        val params = reference.parameters
        val kind = if (inForceBranch(reference)) SqlKind.WRITE else Php.sqlKind(Php.leadingSql(params.firstOrNull()))
        val (message, fixes) = when {
            kind == SqlKind.WRITE && params.size == 1 ->
                "XOOPS: queryF() is deprecated — this statement writes, use exec()" to arrayOf<LocalQuickFix>(RenameCallFix("queryF", "exec"))
            kind == SqlKind.WRITE ->
                "XOOPS: queryF() is deprecated — this statement writes; exec() takes one argument, drop limit/start" to emptyArray()
            kind == SqlKind.READ ->
                "XOOPS: queryF() is deprecated — this statement reads, use query()" to arrayOf<LocalQuickFix>(RenameCallFix("queryF", "query"))
            else ->
                "XOOPS: queryF() is deprecated — the SQL could not be resolved; use exec() for a write or query() for a read" to emptyArray()
        }
        holder.registerProblem(reference, message, ProblemHighlightType.LIKE_DEPRECATED, *fixes)
    }

    private fun inForceBranch(element: PsiElement): Boolean {
        var p: If? = PsiTreeUtil.getParentOfType(element, If::class.java)
        while (p != null) {
            val cond = Php.unwrap(p.condition)
            if (Php.variableName(cond) == "force" && p.statement?.let { PsiTreeUtil.isAncestor(it, element, false) } == true) {
                return true
            }
            p = PsiTreeUtil.getParentOfType(p, If::class.java)
        }
        return false
    }
}
