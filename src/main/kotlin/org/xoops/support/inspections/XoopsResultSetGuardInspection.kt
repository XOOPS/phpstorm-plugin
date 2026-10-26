package org.xoops.support.inspections

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.AssignmentExpression
import com.jetbrains.php.lang.psi.elements.BinaryExpression
import com.jetbrains.php.lang.psi.elements.ControlStatement
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.GroupStatement
import com.jetbrains.php.lang.psi.elements.If
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.PhpBreak
import com.jetbrains.php.lang.psi.elements.PhpContinue
import com.jetbrains.php.lang.psi.elements.PhpReturn
import com.jetbrains.php.lang.psi.elements.TernaryExpression
import com.jetbrains.php.lang.psi.elements.UnaryExpression
import com.jetbrains.php.lang.psi.elements.Variable
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor
import org.xoops.support.fix.Edits
import org.xoops.support.fix.XoopsFix
import org.xoops.support.psi.Php
import org.xoops.support.psi.XoopsCore

/**
 * `fetch*()` on a handle that no `isResultSet()` check dominates. Recognised guards:
 * a positive check in the enclosing `if` / `while` condition (including
 * `while ($db->isResultSet($r) && $row = …)`), an early-exit `if (!$db->isResultSet($r))`,
 * `if (!$r)`, `if (!$r = $db->query(…))` or `if (false === $r)` in a preceding statement (ending in
 * an exit, `break` or `continue`), until the handle is reassigned; a check in the same expression
 * (`isResultSet($r) && … ? fetch($r) : false`, `isResultSet($r) && fetch($r)`); and a handle the
 * function receives as a parameter, which is the caller's to guard.
 *
 * The three legacy forms are guards (a failed `query()` is `false`), so no second check is
 * inserted in front of them; they get their own weak finding with an in-place rewrite to
 * `isResultSet()`, because on 2.7.4 `query()` can also return `true` for a statement that
 * yields no rows, and `true` is not a result set either.
 *
 * A fetch inside `if (!$db->isResultSet($r)) { … }` is a reversed condition and gets its
 * own message instead of a second guard. `fetch*($db->query(…))` with the query nested in
 * the fetch cannot be guarded without a variable and is reported without a fix. The guard
 * fix inserts an early return that keeps the enclosing function's failure contract
 * (`[]`, `0`, `false`, `$ret`) — never a `throw`.
 */
class XoopsResultSetGuardInspection : XoopsInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            private val reportedLegacy = HashSet<PsiElement>()

            override fun visitPhpMethodReference(reference: MethodReference) {
                val name = reference.name ?: return
                if (fetchMethods.none { it.equals(name, true) } || skip(reference.containingFile)) return
                if (!Php.isDatabaseCall(reference)) return
                val arg = Php.unwrap(reference.parameters.firstOrNull()) ?: return
                val modern = XoopsCore.atLeast(reference.containingFile, XoopsCore.RESULT_SET_API)
                if (arg is MethodReference && arg.name?.let { "query".equals(it, true) || "queryF".equals(it, true) } == true) {
                    holder.registerProblem(
                        reference,
                        "XOOPS: $name() is fed straight from query() — a failed query is false; assign the result and " +
                            (if (modern) "guard it with isResultSet()" else "test it before fetching"),
                    )
                    return
                }
                val handle = arg as? Variable ?: return
                when (val state = GuardFlow(reference, handle.name).state()) {
                    is GuardState.Guarded -> {
                        val legacy = state.legacyCondition ?: return
                        // Before 2.5.11 there is no isResultSet(): the truthiness test IS the guard.
                        if (!modern || !reportedLegacy.add(legacy)) return
                        holder.registerProblem(
                            legacy,
                            "XOOPS: legacy result check — query() can also return true on 2.7.4; test isResultSet()",
                            ProblemHighlightType.WEAK_WARNING,
                            ModernizeResultGuardFix(reference.classReference?.text ?: return),
                        )
                    }
                    GuardState.Reversed -> holder.registerProblem(
                        reference,
                        "XOOPS: reversed isResultSet() condition — this fetch runs only when the result is NOT a result set",
                    )
                    GuardState.Unguarded -> {
                        val fix = if (Php.statementInBlock(reference) != null && neutralReturn(reference) != null) arrayOf(InsertResultSetGuardFix()) else emptyArray()
                        holder.registerProblem(
                            reference,
                            if (modern) "XOOPS: $name() without a dominating isResultSet() guard on \$${handle.name}"
                            else "XOOPS: $name() without a dominating result check on \$${handle.name}",
                            ProblemHighlightType.WEAK_WARNING,
                            *fix,
                        )
                    }
                }
            }
        }
    }

    sealed class GuardState {
        /** [legacyCondition] is the `!$r` / `!$r = …` / `false === $r` expression when that is what guards the fetch. */
        class Guarded(val legacyCondition: PsiElement?) : GuardState()
        object Reversed : GuardState()
        object Unguarded : GuardState()
    }

    class GuardFlow(private val fetch: PsiElement, private val handle: String) {

        fun state(): GuardState {
            val function = Php.enclosingFunction(fetch)
            var p: PsiElement? = fetch.parent
            while (p != null && p !is Function && p !is PhpFile) {
                // `isResultSet($r) && … ? fetch($r) : false` and `isResultSet($r) && … && fetch($r)`.
                if (p is TernaryExpression && p.trueVariant?.let { PsiTreeUtil.isAncestor(it, fetch, false) } == true &&
                    positiveCheck(p.condition)
                ) return GuardState.Guarded(null)
                if (p is BinaryExpression && p.operation?.text?.lowercase().let { it == "&&" || it == "and" } &&
                    p.rightOperand?.let { PsiTreeUtil.isAncestor(it, fetch, false) } == true && positiveCheck(p.leftOperand)
                ) return GuardState.Guarded(null)
                if (p is ControlStatement) {
                    val cond = p.condition
                    if (cond != null) {
                        val inCondition = PsiTreeUtil.isAncestor(cond, fetch, false)
                        val inThen = p.statement?.let { PsiTreeUtil.isAncestor(it, fetch, false) } == true
                        for (check in Php.methodCalls(cond, "isResultSet").filter { argumentIs(it, handle) }) {
                            val negated = Php.negatedWithin(check, cond)
                            if (!negated && onlyAndAbove(check, cond)) return GuardState.Guarded(null)
                            // `if (!isResultSet($r)) { … } else { fetch }`: the else branch is the guarded one.
                            if (negated && !inCondition) return if (inThen) GuardState.Reversed else GuardState.Guarded(null)
                        }
                        // `if ($r = $db->query($sql)) { fetch }` / `while ($r && …)`: a truthiness test on the handle.
                        if (!inCondition) {
                            for (operand in Php.andOperands(cond)) {
                                val u = Php.unwrap(operand) ?: continue
                                val tests = (u is Variable && u.name == handle) ||
                                    (u is AssignmentExpression && Php.variableName(u.variable) == handle)
                                if (tests && onlyAndAbove(u, cond)) return GuardState.Guarded(u)
                            }
                        }
                    }
                }
                p = p.parent
            }
            // A legacy `if (!$r)` exit still guards, but an isResultSet() exit above it makes it redundant
            // rather than legacy (`if (!isResultSet($r)) throw …; if (!$r) …`): keep looking up to the assignment.
            var legacy: PsiElement? = null
            var anchor: PsiElement? = Php.statementInBlock(fetch)
            while (anchor != null) {
                for (prev in Php.previousStatements(anchor)) {
                    if (assigns(prev)) return if (legacy != null) GuardState.Guarded(legacy) else GuardState.Unguarded
                    if (prev is If) {
                        val guard = earlyExitGuard(prev) ?: continue
                        if (guard.legacy == null) return GuardState.Guarded(null)
                        if (legacy == null) legacy = guard.legacy
                    }
                }
                val block = anchor.parent
                if (block !is GroupStatement || function != null && !PsiTreeUtil.isAncestor(function, block, true)) break
                anchor = Php.statementInBlock(block)
                if (anchor != null && anchor.parent is Function) break
            }
            if (legacy != null) return GuardState.Guarded(legacy)
            // A handle the function receives (`convertResultSet($result)`, a fetchArray() wrapper) is the
            // caller's to guard; a reassignment inside the function was caught by assigns() above.
            if (function != null && function.parameters.any { it.name == handle }) return GuardState.Guarded(null)
            return GuardState.Unguarded
        }

        /** [cond] holds a non-negated `isResultSet()` of the handle joined only by `&&`. */
        private fun positiveCheck(cond: PsiElement?): Boolean {
            cond ?: return false
            return Php.methodCalls(cond, "isResultSet").any {
                argumentIs(it, handle) && !Php.negatedWithin(it, cond) && onlyAndAbove(it, cond)
            }
        }

        /** `isResultSet($r)` or `isResultSet($r = $db->query(…))` both test the handle. */
        private fun argumentIs(call: MethodReference, name: String): Boolean {
            val arg = Php.unwrap(call.parameters.firstOrNull()) ?: return false
            if (Php.variableName(arg) == name) return true
            return arg is AssignmentExpression && Php.variableName(arg.variable) == name
        }

        /** Every binary operator between [call] and [root] is `&&`: an `||` alternative is no guard. */
        private fun onlyAndAbove(call: PsiElement, root: PsiElement): Boolean {
            var p = call.parent
            while (p != null && p != root.parent) {
                if (p is BinaryExpression) {
                    val op = p.operation?.text?.lowercase()
                    if (op != "&&" && op != "and") return false
                }
                p = p.parent
            }
            return true
        }

        private fun assigns(statement: PsiElement): Boolean {
            val a = statement.firstChild as? AssignmentExpression ?: return false
            return Php.variableName(a.variable) == handle
        }

        /** An early-exit guard; [legacy] is the `!$r`-style operand when the guard is not `isResultSet()`. */
        private class Exit(val legacy: PsiElement?)

        private fun earlyExitGuard(ifStatement: If): Exit? {
            val cond = ifStatement.condition ?: return null
            // `break` / `continue` leave the enclosing loop or switch, which holds every later sibling.
            val body = ifStatement.statement
            val last = (body as? GroupStatement)?.statements?.lastOrNull() ?: body
            if (!Php.terminates(body) && last !is PhpBreak && last !is PhpContinue) return null
            for (operand in Php.orOperands(cond)) {
                val u = Php.unwrap(operand) ?: continue
                if (u is UnaryExpression && u.operation?.text == "!") {
                    val inner = Php.unwrap(u.value)
                    when {
                        inner is Variable && inner.name == handle -> return Exit(u)
                        inner is AssignmentExpression && Php.variableName(inner.variable) == handle -> return Exit(u)
                        inner is MethodReference && "isResultSet".equals(inner.name, true) && argumentIs(inner, handle) -> return Exit(null)
                        inner is BinaryExpression && inner.operation?.text == "instanceof" && Php.variableName(inner.leftOperand) == handle -> return Exit(null)
                    }
                }
                if (u is BinaryExpression && (u.operation?.text == "===" || u.operation?.text == "==")) {
                    val sides = setOf(Php.unwrap(u.leftOperand)?.text, Php.unwrap(u.rightOperand)?.text)
                    if (sides.contains("false") && sides.contains("\$$handle")) return Exit(u)
                }
            }
            return null
        }
    }

    companion object {
        val fetchMethods = listOf("fetchArray", "fetchRow", "fetchBoth", "fetchObject", "fetchField")

        /**
         * The early return that keeps the enclosing function's failure contract, or null when none
         * can be established (a non-nullable object / `never` / `static` return type): then no fix
         * is offered rather than a `return null;` that will not compile.
         */
        fun neutralReturn(element: PsiElement): String? {
            val fn = Php.enclosingFunction(element) ?: return "return;"
            val plain = plainReturn(fn, element) ?: return null
            // `function &f(): array { return []; }` is "Only variable references should be returned by reference".
            if (!fn.isReturningByReference || plain == "return;") return plain
            val literal = plain.removePrefix("return ").removeSuffix(";")
            return if (literal.startsWith("\$")) plain else "\$ret = $literal;\n    return \$ret;"
        }

        private fun plainReturn(fn: Function, element: PsiElement): String? {
            val declaredRaw = fn.returnType?.text?.replace(":", "")?.trim()?.lowercase()
            if (!declaredRaw.isNullOrBlank()) {
                val nullable = declaredRaw.startsWith("?") || declaredRaw.split('|').any { it.trim() == "null" }
                val declared = declaredRaw.removePrefix("?")
                return when {
                    declared.contains("void") -> "return;"
                    declared.contains("never") -> null
                    declared.contains("array") || declared.contains("iterable") -> "return [];"
                    declared.contains("int") -> "return 0;"
                    declared.contains("bool") || declared.contains("false") -> "return false;"
                    declared.contains("string") -> "return '';"
                    declared.contains("float") -> "return 0.0;"
                    nullable || declared == "mixed" -> "return null;"
                    else -> null
                }
            }
            val literal = setOf("false", "0", "[]", "null", "''", "\"\"", "array()")
            val returns = PsiTreeUtil.findChildrenOfType(fn, PhpReturn::class.java).filter { Php.enclosingFunction(it) == fn }
            val before = returns.filter { it.textRange.endOffset <= element.textRange.startOffset }
            val candidate = (before + returns).mapNotNull { it.argument }
                .firstOrNull { it.text in literal || (it is Variable && assignedBefore(it, element, fn)) }
            if (candidate != null) return "return ${candidate.text};"
            return if (returns.isEmpty() || returns.all { it.argument == null }) "return;" else "return null;"
        }

        /** `$ret` may only be returned early when some assignment to it precedes the insertion point. */
        private fun assignedBefore(variable: Variable, point: PsiElement, fn: Function): Boolean =
            PsiTreeUtil.findChildrenOfType(fn, AssignmentExpression::class.java).any { a ->
                Php.variableName(a.variable) == variable.name && a.textRange.endOffset <= point.textRange.startOffset &&
                    Php.enclosingFunction(a) == fn
            }
    }
}

class InsertResultSetGuardFix : XoopsFix() {
    override fun getFamilyName(): String = "Insert isResultSet() guard with the function's failure return"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val reference = descriptor.psiElement as? MethodReference ?: return
        val handle = Php.unwrap(reference.parameters.firstOrNull()) as? Variable ?: return
        if (XoopsResultSetGuardInspection.GuardFlow(reference, handle.name).state() !is XoopsResultSetGuardInspection.GuardState.Unguarded) return
        val receiver = reference.classReference?.text ?: return
        val statement = Php.statementInBlock(reference) ?: return
        val ret = XoopsResultSetGuardInspection.neutralReturn(reference) ?: return
        // A core older than 2.5.11 has no isResultSet(); a failed query() is false there, so test the handle.
        val check = if (XoopsCore.atLeast(reference.containingFile, XoopsCore.RESULT_SET_API)) "$receiver->isResultSet(\$${handle.name})" else "\$${handle.name}"
        Edits.insertLinesBefore(
            project,
            Php.withLeadingComments(statement),
            "if (!$check) {\n    $ret\n}\n",
        )
    }
}

/** `!$r` / `!$r = …` / `false === $r` → `!$db->isResultSet(…)`, keeping the assignment. */
class ModernizeResultGuardFix(private val receiver: String) : XoopsFix() {
    override fun getFamilyName(): String = "Replace the legacy result check with isResultSet()"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val e = descriptor.psiElement ?: return
        val (inner, negated) = when {
            e is UnaryExpression && e.operation?.text == "!" -> (Php.unwrap(e.value)?.text ?: return) to true
            e is BinaryExpression -> (listOf(e.leftOperand, e.rightOperand).map { Php.unwrap(it) }.firstOrNull { it is Variable }?.text ?: return) to true
            e is Variable || e is AssignmentExpression -> e.text to false
            else -> return
        }
        if (inner.contains("isResultSet")) return
        Edits.replace(project, e, (if (negated) "!" else "") + "$receiver->isResultSet($inner)")
    }
}
