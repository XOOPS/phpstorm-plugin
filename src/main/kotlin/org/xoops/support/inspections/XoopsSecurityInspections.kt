package org.xoops.support.inspections

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.PhpEval
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.Variable
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor
import org.xoops.support.fix.Edits
import org.xoops.support.fix.XoopsFix
import org.xoops.support.psi.Php

/**
 * Code that runs names as code: `$$name`, `${$name}`, `extract($_POST)`, `eval()`,
 * `create_function()`. There is no local rewrite — the replacement is a different design —
 * so every finding is reported without a fix. A request array copied into the symbol table
 * is the worst case and is an error; the rest are warnings.
 */
class XoopsDynamicCodeInspection : XoopsInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            override fun visitPhpVariable(variable: Variable) {
                if (skip(variable.containingFile)) return
                val text = variable.text
                if (!text.startsWith("$$") && !text.startsWith("\${")) return
                if (PsiTreeUtil.getParentOfType(variable, StringLiteralExpression::class.java) != null) return
                if (variable.parent is Variable) return
                holder.registerProblem(
                    variable as PsiElement,
                    "XOOPS: ${text.take(12)} runs a name as code — use an array or an explicit property; there is no quick-fix",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                )
            }

            override fun visitPhpEval(eval: PhpEval) {
                if (skip(eval.containingFile)) return
                holder.registerProblem(eval as PsiElement, "XOOPS: eval() runs data as code; there is no quick-fix", ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
            }

            override fun visitPhpFunctionCall(reference: FunctionReference) {
                if (reference is MethodReference || skip(reference.containingFile)) return
                when {
                    Php.isCall(reference, "create_function") -> holder.registerProblem(
                        reference as PsiElement,
                        "XOOPS: create_function() was removed in PHP 8 — use a closure",
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    )
                    Php.isCall(reference, "extract") -> {
                        val arg = reference.parameters.firstOrNull()?.text ?: ""
                        val fromRequest = requestArrays.any { arg.contains(it) }
                        holder.registerProblem(
                            reference as PsiElement,
                            if (fromRequest) "XOOPS: extract() of a request array lets the client define any variable — read named keys through Xmf\\Request"
                            else "XOOPS: extract() defines variables from data; name the keys you need",
                            if (fromRequest) ProblemHighlightType.GENERIC_ERROR_OR_WARNING else ProblemHighlightType.WEAK_WARNING,
                        )
                    }
                }
            }
        }
    }

    companion object {
        val requestArrays = listOf("\$_POST", "\$_GET", "\$_REQUEST", "\$_COOKIE", "\$HTTP_POST_VARS", "\$HTTP_GET_VARS")
    }
}

/**
 * `ORDER BY` built from the request. `' ORDER BY ' . $criteria->getSort()` concatenates
 * whatever `setSort()` received; `setSort(Request::getString('sort'))` hands the column name
 * to the client. Quoting does not help (a column is an identifier, not a string): allow-list
 * the column or use `XoopsObject::buildOrderBy()`. Reported without a fix.
 */
class XoopsOrderByInspection : XoopsInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            override fun visitPhpMethodReference(reference: MethodReference) {
                if (skip(reference.containingFile)) return
                val name = reference.name ?: return
                // getOrder()/setOrder() are normalised to ASC/DESC by Criteria; only the sort column is an identifier.
                when {
                    name.equals("getSort", true) -> {
                        val (operands, index) = Php.concatOperands(reference) ?: return
                        // `' ORDER BY ' . self::buildOrderBy($criteria->getSort(), …, [allow-list])` concatenates
                        // the allow-listed result, not the sort string (kernel/comment.php, image.php, notification.php).
                        if (operands[index] != reference) return
                        val afterOrderBy = (index - 1 downTo 0).asSequence().map { operands[it] }
                            .filterIsInstance<StringLiteralExpression>()
                            .any { orderBy.containsMatchIn(it.contents) }
                        if (!afterOrderBy) return
                        holder.registerProblem(
                            reference,
                            "XOOPS: ORDER BY concatenates $name() — allow-list the column (or use buildOrderBy()); quoting the sort string is not a fix",
                        )
                    }
                    name.equals("setSort", true) -> {
                        val arg = reference.parameters.firstOrNull() ?: return
                        val text = arg.text
                        val fromRequest = text.contains("Request::") || XoopsDynamicCodeInspection.requestArrays.any { text.contains(it) }
                        if (!fromRequest) return
                        holder.registerProblem(
                            reference,
                            "XOOPS: $name() receives a request value — the client picks the column; allow-list it first",
                        )
                    }
                }
            }
        }
    }

    companion object {
        private val orderBy = Regex("(?i)order\\s+by\\b")
    }
}

/** `$HTTP_POST_VARS` and friends were removed in PHP 5.4: the page reads nothing. */
class XoopsLegacySuperglobalInspection : XoopsInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            override fun visitPhpVariable(variable: Variable) {
                if (skip(variable.containingFile)) return
                val modern = legacy[variable.name] ?: return
                holder.registerProblem(
                    variable as PsiElement,
                    "XOOPS: \$${variable.name} no longer exists (PHP 5.4) — this reads null; use \$$modern",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    RenameVariableFix(variable.name, modern),
                )
            }
        }
    }

    companion object {
        val legacy = mapOf(
            "HTTP_POST_VARS" to "_POST", "HTTP_GET_VARS" to "_GET", "HTTP_COOKIE_VARS" to "_COOKIE",
            "HTTP_SERVER_VARS" to "_SERVER", "HTTP_ENV_VARS" to "_ENV", "HTTP_POST_FILES" to "_FILES",
            "HTTP_SESSION_VARS" to "_SESSION",
        )
    }
}

class RenameVariableFix(private val from: String, private val to: String) : XoopsFix() {
    override fun getFamilyName(): String = "Replace the PHP 4 superglobal with its modern name"
    override fun getName(): String = "Replace \$$from with \$$to"
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val variable = descriptor.psiElement as? Variable ?: return
        if (variable.name != from) return
        Edits.replace(project, variable, "\$$to")
    }
}
