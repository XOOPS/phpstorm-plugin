package org.xoops.support.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.ArrayAccessExpression
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.Global
import com.jetbrains.php.lang.psi.elements.Variable
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor
import org.xoops.support.psi.Php

/**
 * `$xoopsOption['template_main'] = …` inside a function or method that has no `global $xoopsOption`
 * writes a local array: the page renders without its template, and a read is an undefined variable.
 * Script-level code is the global scope and is left alone. Ported from rector_xoops'
 * GlobalXoopsOptionRector, narrowed to the scope where the local really is a bug.
 */
class XoopsLocalXoopsOptionInspection : XoopsInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            override fun visitPhpArrayAccessExpression(expression: ArrayAccessExpression) {
                val variable = expression.value as? Variable ?: return
                if (variable.name != "xoopsOption" || skipConvention(expression.containingFile)) return
                val fn = Php.enclosingFunction(expression) ?: return
                if (fn.isClosure || declaresGlobal(fn) || fn.parameters.any { it.name == "xoopsOption" }) return
                holder.registerProblem(
                    variable,
                    "XOOPS: \$xoopsOption inside ${fn.name.ifEmpty { "a function" }}() is a local array without global \$xoopsOption; use \$GLOBALS['xoopsOption']",
                    ReplaceTextFix(variable.text, "\$GLOBALS['xoopsOption']"),
                )
            }
        }
    }

    private fun declaresGlobal(fn: Function): Boolean =
        PsiTreeUtil.findChildrenOfType(fn, Global::class.java).any { g ->
            Php.enclosingFunction(g) == fn && g.variables.any { it.name == "xoopsOption" }
        }
}
