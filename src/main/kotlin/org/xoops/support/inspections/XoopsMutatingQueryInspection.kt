package org.xoops.support.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor
import org.xoops.support.psi.Php
import org.xoops.support.psi.XoopsCore

/**
 * `query()` with a mutating statement: the safe database class refuses it on GET and
 * strict mode warns on POST. Only a statement whose SQL resolves to a write is reported —
 * a `$sql` the plugin cannot read is not a finding — and only on a receiver that is the
 * XOOPS database: an application wrapper that happens to be named `query()` (a version
 * control class, a cache) has no `exec()` to rename to. A core older than 2.5.12 has no `exec()`
 * either: there the forced write is `queryF()`, and that is what the fix emits.
 */
class XoopsMutatingQueryInspection : XoopsInspection() {
    /** The published name (XOOPS Support 1.0.0 Alpha 3), so profile settings carry over. */
    override fun getShortName(): String = "XoopsQueryExec"


    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            override fun visitPhpMethodReference(reference: MethodReference) {
                if (reference.name != "query" || skip(reference.containingFile)) return
                if (!Php.isDatabaseCall(reference)) return
                val params = reference.parameters
                if (params.size != 1) return
                if (Php.sqlKind(Php.leadingSql(params[0])) != Php.SqlKind.WRITE) return
                if (forceIdiom(reference)) return
                val target = if (XoopsCore.atLeast(reference.containingFile, XoopsCore.EXEC_API)) "exec" else "queryF"
                holder.registerProblem(
                    reference,
                    "XOOPS: query() called with a mutating statement — the GET proxy refuses it; use $target()",
                    RenameCallFix("query", target),
                )
            }
        }
    }

    /**
     * `$force ? $db->exec($sql) : $db->query($sql)`: the non-forced write goes through query() on
     * purpose, so the proxy refuses it on GET (profile/class/field.php). Renaming it would make both
     * branches force the write.
     */
    private fun forceIdiom(reference: MethodReference): Boolean {
        var e: com.intellij.psi.PsiElement = reference
        while (e.parent is com.jetbrains.php.lang.psi.elements.ParenthesizedExpression) e = e.parent
        val ternary = e.parent as? com.jetbrains.php.lang.psi.elements.TernaryExpression ?: return false
        val other = Php.unwrap(if (ternary.falseVariant == e) ternary.trueVariant else ternary.falseVariant) as? MethodReference ?: return false
        return other.name?.lowercase() in setOf("exec", "queryf")
    }
}
