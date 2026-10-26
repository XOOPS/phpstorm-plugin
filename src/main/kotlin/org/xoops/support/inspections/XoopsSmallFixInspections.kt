package org.xoops.support.inspections

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.BinaryExpression
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.GroupStatement
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParenthesizedExpression
import com.jetbrains.php.lang.psi.elements.PhpBreak
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpContinue
import com.jetbrains.php.lang.psi.elements.PhpIsset
import com.jetbrains.php.lang.psi.elements.Variable
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor
import org.xoops.support.fix.Edits
import org.xoops.support.fix.XoopsFix
import org.xoops.support.psi.ArrayUse
import org.xoops.support.psi.Php
import org.xoops.support.psi.XoopsPaths

/**
 * `unserialize($x)` without `allowed_classes` — argument count comes from the PSI, not from commas.
 *
 * The fix is offered only where [ArrayUse] proves every use of the result is array-shaped. xhelp
 * stores a serialized `CriteriaCompo` and oledrion an attribute object: with `allowed_classes`
 * forced to `false` they come back as `__PHP_Incomplete_Class` and the next `->` call fails
 * (review round 4). Those sites are reported with the class list left to the author.
 */
class XoopsUnserializeInspection : XoopsInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            override fun visitPhpFunctionCall(reference: FunctionReference) {
                if (!Php.isCall(reference, "unserialize") || reference is MethodReference || skip(reference.containingFile)) return
                if (reference.parameters.size != 1) return
                if (ArrayUse.provable(reference)) {
                    holder.registerProblem(
                        reference,
                        "XOOPS: unserialize() without ['allowed_classes' => false] can instantiate arbitrary classes",
                        AddAllowedClassesFix(),
                    )
                } else {
                    holder.registerProblem(
                        reference,
                        "XOOPS: unserialize() without allowed_classes — the result is not provably an array here " +
                            "(it may be a stored object), so pass ['allowed_classes' => [Expected::class]] by hand",
                    )
                }
            }
        }
    }
}

class AddAllowedClassesFix : XoopsFix() {
    override fun getFamilyName(): String = "Add ['allowed_classes' => false]"
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val call = descriptor.psiElement as? FunctionReference ?: return
        if (call.parameters.size != 1 || !ArrayUse.provable(call)) return
        val list = call.parameterList ?: return
        Edits.insertAt(project, call, list.textRange.endOffset, ", ['allowed_classes' => false]")
    }
}

/** `header('HTTP/1.0 404 Not Found')` → `http_response_code(404)`. */
class XoopsHttpStatusInspection : XoopsInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            override fun visitPhpFunctionCall(reference: FunctionReference) {
                if (!Php.isCall(reference, "header") || reference is MethodReference || skipConvention(reference.containingFile)) return
                if (reference.parameters.size != 1) return
                val code = statusOf(reference) ?: return
                holder.registerProblem(
                    reference,
                    "XOOPS: hard-coded HTTP status line — use http_response_code($code)",
                    ProblemHighlightType.WEAK_WARNING,
                    HttpResponseCodeFix(),
                )
            }
        }
    }

    companion object {
        private val status = Regex("""(?i)^HTTP/\d(?:\.\d)?\s+(\d{3})\b""")
        fun statusOf(call: FunctionReference): String? = Php.firstStringArg(call)?.let { status.find(it)?.groupValues?.get(1) }
    }
}

class HttpResponseCodeFix : XoopsFix() {
    override fun getFamilyName(): String = "Replace with http_response_code()"
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val call = descriptor.psiElement as? FunctionReference ?: return
        val code = XoopsHttpStatusInspection.statusOf(call) ?: return
        Edits.replace(project, call, "http_response_code($code)")
    }
}

/** `setcookie()` → `xoops_setcookie()` (same arguments, site-wide SameSite/domain policy). */
class XoopsSetcookieInspection : XoopsInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            override fun visitPhpFunctionCall(reference: FunctionReference) {
                if (!Php.isCall(reference, "setcookie") || reference is MethodReference || skipConvention(reference.containingFile)) return
                // The wrapper's own call is the one setcookie() that must stay.
                if (Php.enclosingFunction(reference)?.name.equals("xoops_setcookie", true)) return
                // Protector's precheck runs from mainfile.php before include/common.php defines
                // xoops_setcookie(): the rename would be a fatal on every banned request (purgeCookies()).
                if (XoopsPaths.path(reference.containingFile).contains("/modules/protector/")) return
                holder.registerProblem(
                    reference,
                    "XOOPS: prefer xoops_setcookie() — it applies the site cookie policy with the same arguments",
                    ProblemHighlightType.WEAK_WARNING,
                    RenameCallFix("setcookie", "xoops_setcookie"),
                )
            }
        }
    }
}

/**
 * `isset($xoopsUser)` is true for a guest (`''`). `is_object($xoopsUser)` or `instanceof`
 * anywhere in the same `&&` chain already makes the condition safe and is not reported.
 */
class XoopsGuestUserInspection : XoopsInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            override fun visitPhpIsset(isset: PhpIsset) {
                if (skip(isset.containingFile)) return
                val vars = isset.variables
                if (vars.size != 1 || Php.variableName(vars[0]) != "xoopsUser") return
                if (guardedByType(isset)) return
                holder.registerProblem(
                    isset,
                    "XOOPS: isset(\$xoopsUser) is true for a guest ('') — test \$xoopsUser instanceof \\XoopsUser",
                    ReplaceIssetXoopsUserFix(),
                )
            }
        }
    }

    /**
     * A type test on `$xoopsUser` in the same boolean condition, with the same polarity as the isset:
     * `isset($u) && is_object($u)`, and its De Morgan form `!isset($u) || !is_object($u)`, which is how
     * the core writes it (function.xoInboxCount.php, function.xoMemberInfo.php, protector/admin.php).
     */
    private fun guardedByType(isset: PhpIsset): Boolean {
        var root: PsiElement = isset
        while (true) {
            val p = root.parent
            val logical = p is BinaryExpression && p.operation?.text?.lowercase() in setOf("&&", "and", "||", "or")
            val not = p is com.jetbrains.php.lang.psi.elements.UnaryExpression && p.operation?.text == "!"
            if (p is ParenthesizedExpression || logical || not) root = p else break
        }
        val typeTests: List<PsiElement> =
            Php.calls(root, "is_object").filter { Php.variableName(it.parameters.firstOrNull()) == "xoopsUser" } +
                PsiTreeUtil.findChildrenOfType(root, BinaryExpression::class.java).plus(root as? BinaryExpression).filterNotNull()
                    .filter { it.operation?.text == "instanceof" && Php.variableName(it.leftOperand) == "xoopsUser" }
        return typeTests.any { safelyJoined(isset, it) }
    }

    /**
     * Where the two tests meet: `A && B` with both un-negated below the join, or `A || B` with both
     * negated (De Morgan). Negations above the join flip both alike and change nothing.
     */
    private fun safelyJoined(isset: PsiElement, test: PsiElement): Boolean {
        val join = PsiTreeUtil.findCommonParent(isset, test) as? BinaryExpression ?: return false
        val op = join.operation?.text?.lowercase()
        fun side(e: PsiElement): PsiElement? =
            listOfNotNull(join.leftOperand, join.rightOperand).firstOrNull { PsiTreeUtil.isAncestor(it, e, false) }
        val a = Php.negatedWithin(isset, side(isset) ?: return false)
        val b = Php.negatedWithin(test, side(test) ?: return false)
        return when (op) {
            "&&", "and" -> !a && !b
            "||", "or" -> a && b
            else -> false
        }
    }
}

class ReplaceIssetXoopsUserFix : XoopsFix() {
    override fun getFamilyName(): String = "Replace with \$xoopsUser instanceof \\XoopsUser"
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val isset = descriptor.psiElement as? PhpIsset ?: return
        if (isset.variables.size != 1) return
        Edits.replace(project, isset, "\$xoopsUser instanceof \\XoopsUser")
    }
}

/** `unlink($p)` with no `is_file($p)` / `file_exists($p)` on the same expression dominating it. */
class XoopsUnlinkInspection : XoopsInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            override fun visitPhpFunctionCall(reference: FunctionReference) {
                if (!Php.isCall(reference, "unlink") || reference is MethodReference || skip(reference.containingFile)) return
                val arg = reference.parameters.firstOrNull() ?: return
                // `$resolved = realpath($x)` names the file `$x` names: a check on either path counts.
                val alias = ((Php.unwrap(arg) as? Variable)?.let(Php::resolveAssignment) as? FunctionReference)
                    ?.takeIf { Php.isCall(it, "realpath") && it !is MethodReference }?.parameters?.firstOrNull()
                // `unlink($info->getRealPath())`, or `$name = $info->getPathname(); unlink($name)`: the SplFileInfo
                // object names the file, so `$info->isFile()` is the check (newbb FilesManagement, DirectoryIterator loops).
                val target = Php.unwrap(arg).let { (it as? Variable)?.let(Php::resolveAssignment) ?: it } as? MethodReference
                val receiver = target?.classReference?.text?.let(::normalize)
                val spl = when {
                    receiver == null -> emptyList()
                    target?.name == "getRealPath" -> listOf(SPL_FILE + receiver)
                    target?.name == "getPathname" -> listOf(SPL_FILE + receiver, SPL_LINK + receiver)
                    else -> emptyList()
                }
                val keys = setOfNotNull(normalize(arg.text), alias?.let { normalize(it.text) }) + spl
                if (dominated(reference, keys)) return
                holder.registerProblem(
                    reference as PsiElement,
                    "XOOPS: unlink() without an is_file() check on the same path",
                    ProblemHighlightType.WEAK_WARNING,
                )
            }
        }
    }

    private fun normalize(text: String) = text.replace(Regex("\\s+"), "")

    // An SplFileInfo check covers the accessor that returns the entry it checked. isFile() covers getRealPath() and
    // getPathname(); isLink() covers only getPathname() (the link itself), because getRealPath() resolves to the
    // target. getPath() is the containing directory and is covered by neither.
    private val SPL_FILE = "spl-file:"
    private val SPL_LINK = "spl-link:"

    private fun checks(scope: PsiElement, keys: Set<String>): List<FunctionReference> =
        Php.calls(scope, "is_file", "file_exists", "is_link").filter { c -> c.parameters.firstOrNull()?.let { normalize(it.text) } in keys } +
            Php.methodCalls(scope, "isFile").filter { c -> c.classReference?.text?.let { SPL_FILE + normalize(it) } in keys } +
            Php.methodCalls(scope, "isLink").filter { c -> c.classReference?.text?.let { SPL_LINK + normalize(it) } in keys } +
            PsiTreeUtil.collectElementsOfType(scope, MethodReference::class.java).filter { helperChecks(it, keys) }

    /**
     * `$this->exists()` whose body checks `is_file($this->pwd())` when the path is `$this->pwd()`: the
     * same text means the same file only when the path reads nothing but `$this`.
     */
    private fun helperChecks(call: MethodReference, keys: Set<String>): Boolean {
        if (call.classReference?.text != "\$this" || call.parameters.isNotEmpty()) return false
        val own = keys.filter { it.startsWith("\$this->") && it.count { c -> c == '$' } == 1 }.toSet()
        if (own.isEmpty()) return false
        val method = call.resolve() as? Method ?: return false
        if (method.containingClass != PsiTreeUtil.getParentOfType(call, PhpClass::class.java)) return false
        return Php.calls(method, "is_file", "file_exists").any { c ->
            c.parameters.firstOrNull()?.let { normalize(it.text) } in own && !Php.negatedWithin(c, method)
        }
    }

    private fun dominated(call: PsiElement, key: Set<String>): Boolean {
        var p: PsiElement? = call.parent
        while (p != null && p !is com.jetbrains.php.lang.psi.elements.Function) {
            val cond = Php.conditionOf(p)
            if (cond != null && checks(cond, key).any { !Php.negatedWithin(it, cond) }) return true
            if (p is com.jetbrains.php.lang.psi.elements.TernaryExpression && p.condition?.let { checks(it, key).isNotEmpty() } == true) return true
            p = p.parent
        }
        // An early exit on a sibling statement, at any block level up to the function. A check inside a
        // previous `try { if (!file_exists($p)) return; } catch (…) { return; }` counts when every catch exits too.
        val function = Php.enclosingFunction(call)
        var anchor: PsiElement? = Php.statementInBlock(call)
        while (anchor != null) {
            if (Php.previousStatements(anchor).any { exits(it, key) || guardingTry(it, key) }) return true
            val block = anchor.parent
            if (block !is GroupStatement || function != null && !PsiTreeUtil.isAncestor(function, block, true)) return false
            anchor = Php.statementInBlock(block)
            if (anchor != null && anchor.parent is com.jetbrains.php.lang.psi.elements.Function) return false
        }
        return false
    }

    /**
     * `if (!is_file($p)) return;` before the unlink, or `if (false === file_put_contents($p, …)) return;`:
     * the code created the file itself. `break` / `continue` leave the loop that holds every later sibling.
     */
    private fun exits(prev: PsiElement, key: Set<String>): Boolean {
        if (prev !is com.jetbrains.php.lang.psi.elements.If) return false
        val c = prev.condition ?: return false
        val body = prev.statement
        val last = (body as? GroupStatement)?.statements?.lastOrNull() ?: body
        if (!Php.terminates(body) && last !is PhpBreak && last !is PhpContinue) return false
        return checks(c, key).any { Php.negatedWithin(it, c) && unconditional(it, c, key) } ||
            Php.calls(c, "file_put_contents", "touch").any { f ->
                f.parameters.firstOrNull()?.let { normalize(it.text) } in key && failed(f, c) && unconditional(f, c, key)
            }
    }

    /**
     * Not behind an unrelated `&&`: in `if ($a && !is_file($p)) return;` a false `$a` skips the test and
     * falls through. `!file_exists($p) && !is_link($p)` is fine: every side tests the same path.
     */
    private fun unconditional(e: PsiElement, c: PsiElement, key: Set<String>): Boolean {
        var child: PsiElement = e
        var p: PsiElement? = e.parent
        while (p != null && p != c.parent) {
            if (p is BinaryExpression && p.operation?.text?.lowercase() in setOf("&&", "and")) {
                val other = if (p.leftOperand == child) p.rightOperand else p.leftOperand
                if (other == null || checks(other, key).none { Php.negatedWithin(it, other) }) return false
            }
            child = p
            p = p.parent
        }
        return true
    }

    /** `!f(…)`, `false === f(…)` or `f(…) === false`: the condition holds exactly when the write failed. */
    private fun failed(f: FunctionReference, c: PsiElement): Boolean {
        if (Php.negatedWithin(f, c)) return true
        var p: PsiElement = f
        while (p.parent is ParenthesizedExpression) p = p.parent
        val cmp = p.parent as? BinaryExpression ?: return false
        if (cmp.operation?.text != "===") return false
        val other = if (cmp.leftOperand == p) cmp.rightOperand else cmp.leftOperand
        return other?.text.equals("false", true) && !Php.negatedWithin(cmp, c)
    }

    private fun guardingTry(prev: PsiElement, key: Set<String>): Boolean {
        if (prev !is com.jetbrains.php.lang.psi.elements.Try) return false
        val body = PsiTreeUtil.getChildOfType(prev, GroupStatement::class.java) ?: return false
        return body.statements.any { exits(it, key) } && prev.catchClauses.all { c ->
            Php.terminates(PsiTreeUtil.getChildOfType(c, GroupStatement::class.java))
        }
    }
}

/**
 * A call passes a temporary where the resolved declaration takes a reference. Resolution
 * goes through the PSI (`parent::__construct` lands on the parent), so a by-reference
 * constructor in the current file does not taint an unrelated call.
 */
class XoopsByRefArgumentInspection : XoopsInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            override fun visitPhpFunctionCall(reference: FunctionReference) = check(reference)
            override fun visitPhpMethodReference(reference: MethodReference) = check(reference)

            private fun check(reference: FunctionReference) {
                if (skip(reference.containingFile)) return
                val results = reference.multiResolve(false)
                if (results.size != 1) return
                val fn = results[0].element as? com.jetbrains.php.lang.psi.elements.Function ?: return
                val params = fn.parameters
                val args = reference.parameters
                for (i in args.indices) {
                    val p = params.getOrNull(i) ?: break
                    if (!p.isPassByRef || p.isVariadic) continue
                    val a = Php.unwrap(args[i]) ?: continue
                    if (a is Variable || a is com.jetbrains.php.lang.psi.elements.ArrayAccessExpression ||
                        a is com.jetbrains.php.lang.psi.elements.FieldReference
                    ) continue
                    holder.registerProblem(
                        args[i],
                        "XOOPS: ${fn.name}() takes \$${p.name} by reference — pass a variable, not an expression",
                    )
                }
            }
        }
    }
}
