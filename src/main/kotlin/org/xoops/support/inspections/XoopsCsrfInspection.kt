package org.xoops.support.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.AssignmentExpression
import com.jetbrains.php.lang.psi.elements.BinaryExpression
import com.jetbrains.php.lang.psi.elements.Else
import com.jetbrains.php.lang.psi.elements.ElseIf
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.If
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.PhpSwitch
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.UnaryExpression
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor
import org.xoops.support.psi.Php

/**
 * A `switch ($op)` branch that writes (handler insert/delete, exec, file writes) without a
 * CSRF token check. A branch is covered by its own `$GLOBALS['xoopsSecurity']->check()` /
 * `xoops_confirm()` (directly or through a helper of the same file that checks), by a top-level
 * gate before the switch, or by a dominating `if (!…->check()) { exit | $op = 'x'; }` that runs for
 * the branch's `$op` (`if (in_array($op, ['save', 'delete'])) { if (!check()) … }`). A gate that only
 * runs for POST (`'POST' === Request::getMethod() && …`) covers nothing when `$op` is read from GET
 * and the GET path still reaches the switch. The branch content decides, never the case label.
 */
class XoopsCsrfInspection : XoopsInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            override fun visitPhpSwitch(switch: PhpSwitch) {
                if (skip(switch.containingFile) || Php.enclosingFunction(switch) != null) return
                val file = switch.containingFile as? PhpFile ?: return
                val opName = Php.variableName(switch.argument) ?: return
                val gate = topLevelGate(file, switch)
                val covered = gate != null && (!postOnly(gate) || opFromPost(file, opName))
                if (covered) return
                val gated = gatedLabels(file, switch, opName) ?: return
                val helpers = tokenHelpers(file)
                for (case in switch.allCases) {
                    if (!mutates(case) || checksToken(case, helpers)) continue
                    val label = (Php.unwrap(case.condition) as? StringLiteralExpression)?.contents
                    if (label != null && label in gated) continue
                    holder.registerProblem(
                        case.condition ?: case.firstChild,
                        "XOOPS: this branch writes without a CSRF token check (\$GLOBALS['xoopsSecurity']->check())",
                    )
                }
            }
        }
    }

    private val mutatingMethods = arrayOf("insert", "delete", "update", "exec", "queryF", "deleteAll", "updateAll", "save", "store")
    private val mutatingFunctions = arrayOf("unlink", "mkdir", "rename", "file_put_contents", "copy", "rmdir")

    private fun mutates(scope: PsiElement): Boolean =
        Php.methodCalls(scope, *mutatingMethods).isNotEmpty() ||
            // `query()` with SQL that resolves to a write: the same write MutatingQuery renames to exec().
            Php.methodCalls(scope, "query").any { Php.sqlKind(Php.leadingSql(it.parameters.firstOrNull())) == Php.SqlKind.WRITE } ||
            Php.calls(scope, *mutatingFunctions).any { it !is MethodReference }

    private fun tokenCheck(call: MethodReference): Boolean =
        call.name == "check" && call.classReference?.text?.contains("xoopsSecurity") == true

    private fun checksToken(scope: PsiElement, helpers: Set<String> = emptySet()): Boolean =
        Php.methodCalls(scope, "check").any(::tokenCheck) ||
            Php.calls(scope, "xoops_confirm").isNotEmpty() ||
            (helpers.isNotEmpty() && Php.calls(scope, *helpers.toTypedArray()).any { it !is MethodReference })

    /**
     * Functions of this file that stop the request on a bad token: `function menus_require_token() {
     * if (!$GLOBALS['xoopsSecurity']->check()) { … redirect_header(…); } }` (system/admin/menus).
     */
    private fun tokenHelpers(file: PhpFile): Set<String> =
        PsiTreeUtil.findChildrenOfType(file, Function::class.java)
            .filter { fn -> PsiTreeUtil.findChildrenOfType(fn, If::class.java).any { failingCheckGate(it) && Php.terminates(it.statement) } }
            .mapNotNull { it.name }
            .toSet()

    /** `if (!$GLOBALS['xoopsSecurity']->check())` — the whole condition is the negated check. */
    private fun failingCheckGate(statement: If): Boolean {
        val condition = Php.unwrap(statement.condition) as? UnaryExpression ?: return false
        if (condition.operation?.text != "!") return false
        val call = Php.unwrap(condition.value) as? MethodReference ?: return false
        return tokenCheck(call)
    }

    /**
     * Labels covered by a dominating failing-check gate before the switch, or null when one gate covers
     * every label. A gate covers what its enclosing conditions allow `$op` to be: `in_array($op, ['a', 'b'])`
     * or `$op === 'a' || $op === 'b'` (modulesadmin, comment_post). It must leave the request or reset `$op`
     * to a literal, sit in the then-branch of each enclosing `if`, and no later assignment may give `$op` a
     * value that is not a literal. An enclosing POST-only condition counts only when the GET path cannot
     * reach the switch (its `else` exits) or `$op` comes from POST.
     */
    private fun gatedLabels(file: PhpFile, switch: PhpSwitch, opName: String): Set<String>? {
        val labels = HashSet<String>()
        val gates = PsiTreeUtil.findChildrenOfType(file, If::class.java).filter { gate ->
            Php.enclosingFunction(gate) == null &&
                gate.textRange.endOffset <= switch.textRange.startOffset &&
                failingCheckGate(gate) &&
                (Php.terminates(gate.statement) || resetsOp(gate.statement, opName))
        }
        for (gate in gates) {
            if (opReassignedFromInput(file, opName, gate, switch)) continue
            val constraint = constraintFor(gate, file, opName) ?: continue
            if (constraint.isEmpty()) return null
            labels += constraint
        }
        return labels
    }

    private fun resetsOp(body: PsiElement?, opName: String): Boolean =
        body != null && PsiTreeUtil.findChildrenOfType(body, AssignmentExpression::class.java)
            .any { Php.variableName(it.variable) == opName && Php.unwrap(it.value) is StringLiteralExpression }

    private fun opReassignedFromInput(file: PhpFile, opName: String, gate: If, switch: PhpSwitch): Boolean =
        PsiTreeUtil.findChildrenOfType(file, AssignmentExpression::class.java).any { a ->
            Php.enclosingFunction(a) == null && Php.variableName(a.variable) == opName &&
                a.textRange.startOffset > gate.textRange.endOffset && a.textRange.endOffset < switch.textRange.startOffset &&
                Php.unwrap(a.value) !is StringLiteralExpression
        }

    /**
     * The `$op` values the gate runs for: empty = every value, null = the gate cannot be relied on
     * (it sits in an `else`, or behind a POST-only condition the GET path bypasses).
     */
    private fun constraintFor(gate: If, file: PhpFile, opName: String): Set<String>? {
        var allowed: Set<String>? = null
        var node: PsiElement = gate
        while (true) {
            val parent = node.parent ?: break
            if (parent is PsiFile || parent is Function) break
            when (parent) {
                is com.jetbrains.php.lang.psi.elements.GroupStatement, is com.jetbrains.php.lang.psi.elements.PhpNamespace -> Unit
                is Else -> return null
                is ElseIf -> {
                    // Reached from the elseif's body: it runs exactly when its condition holds.
                    allowed = narrow(allowed, opValues(parent.condition, opName) ?: return null)
                }
                is If -> {
                    if (parent.statement == null || !PsiTreeUtil.isAncestor(parent.statement!!, node, false)) {
                        // Climbing out of an elseif / else of this `if`: already decided above.
                        if (node !is ElseIf) return null
                    } else {
                        val values = opValues(parent.condition, opName)
                        when {
                            values != null -> allowed = narrow(allowed, values)
                            // A POST-only block is fine when GET never reaches the switch (comment_post.php).
                            postOnlyCondition(parent.condition) -> {
                                val elseExits = parent.elseBranch?.statement?.let(Php::terminates) == true
                                if (!elseExits && !opFromPost(file, opName)) return null
                            }
                            // Any other condition makes the gate conditional on something else.
                            else -> return null
                        }
                    }
                }
                // Loops, switches, try blocks: the gate may not run.
                else -> return null
            }
            node = parent
        }
        return allowed ?: emptySet()
    }

    private fun narrow(current: Set<String>?, next: Set<String>): Set<String> = current?.intersect(next) ?: next

    /** `$op` values the condition requires: `in_array($op, [...])`, `$op === 'x'`, `||` unions, `&&` narrowing. */
    private fun opValues(condition: PsiElement?, opName: String): Set<String>? {
        val x = Php.unwrap(condition) ?: return null
        if (x is FunctionReference && x !is MethodReference && x.name.equals("in_array", true)) {
            if (Php.variableName(x.parameters.getOrNull(0)) != opName) return null
            val list = Php.unwrap(x.parameters.getOrNull(1)) as? ArrayCreationExpression ?: return null
            val values = list.children.mapNotNull { child ->
                (PsiTreeUtil.findChildOfType(child, StringLiteralExpression::class.java, false) ?: child as? StringLiteralExpression)?.contents
            }
            return values.toSet().takeIf { it.isNotEmpty() }
        }
        if (x is BinaryExpression) {
            val op = x.operation?.text?.lowercase()
            when (op) {
                "===", "==" -> {
                    val l = Php.unwrap(x.leftOperand)
                    val r = Php.unwrap(x.rightOperand)
                    if (Php.variableName(l) == opName && r is StringLiteralExpression) return setOf(r.contents)
                    if (Php.variableName(r) == opName && l is StringLiteralExpression) return setOf(l.contents)
                }
                "||", "or" -> {
                    val a = opValues(x.leftOperand, opName) ?: return null
                    val b = opValues(x.rightOperand, opName) ?: return null
                    return a + b
                }
                "&&", "and" -> {
                    val a = opValues(x.leftOperand, opName)
                    val b = opValues(x.rightOperand, opName)
                    return when {
                        a != null && b != null -> a.intersect(b)
                        else -> a ?: b
                    }
                }
            }
        }
        return null
    }

    private fun topLevelGate(file: PhpFile, switch: PhpSwitch): If? =
        Php.executableTopLevel(file)
            .filter { it.textRange.endOffset <= switch.textRange.startOffset }
            .filterIsInstance<If>()
            .firstOrNull { it.condition?.let { c -> checksToken(c) } == true }

    private fun postOnly(gate: If): Boolean = postOnlyCondition(gate.condition)

    private fun postOnlyCondition(condition: PsiElement?): Boolean {
        val text = condition?.text ?: return false
        return text.contains("'POST'") || text.contains("REQUEST_METHOD") || text.contains("getMethod")
    }

    private fun opFromPost(file: PhpFile, opName: String): Boolean {
        val assignments = PsiTreeUtil.findChildrenOfType(file, AssignmentExpression::class.java)
            .filter { Php.variableName(it.variable) == opName && Php.enclosingFunction(it) == null }
        if (assignments.isEmpty()) return false
        return assignments.all { a -> a.value?.text?.let { it.contains("'POST'") || it.contains("\$_POST") } == true }
    }
}
