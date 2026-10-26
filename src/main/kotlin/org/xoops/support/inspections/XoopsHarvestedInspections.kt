package org.xoops.support.inspections

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.ArrayAccessExpression
import com.jetbrains.php.lang.psi.elements.AssignmentExpression
import com.jetbrains.php.lang.psi.elements.BinaryExpression
import com.jetbrains.php.lang.psi.elements.ClassReference
import com.jetbrains.php.lang.psi.elements.ControlStatement
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.ForeachStatement
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.Global
import com.jetbrains.php.lang.psi.elements.If
import com.jetbrains.php.lang.psi.elements.Include
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.NewExpression
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.ParenthesizedExpression
import com.jetbrains.php.lang.psi.elements.PhpUnset
import com.jetbrains.php.lang.psi.elements.SelfAssignmentExpression
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.TernaryExpression
import com.jetbrains.php.lang.psi.elements.UnaryExpression
import com.jetbrains.php.lang.psi.elements.Variable
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor
import org.xoops.support.fix.Edits
import org.xoops.support.fix.XoopsFix
import org.xoops.support.psi.Php
import org.xoops.support.psi.XoopsPaths

/*
 * Rules harvested from the Gemini / Grok / Codex plugins after review round 4 (2026-10-23). Each is
 * rebuilt on the PSI rather than on regexes over the file text, and each fix re-derives its edit
 * from the live tree at apply time.
 */

/**
 * `strpos($path, $base) === 0` / `str_starts_with($path, $base)` is a prefix test, not containment:
 * `/safe/../outside/secret` passes. Report only — the fix is `realpath()` on both sides, which the
 * author must place where the path is final. A `realpath()`'d haystack against a separator-terminated
 * base already is containment and is not reported.
 */
class XoopsPathContainmentInspection : XoopsInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            override fun visitPhpFunctionCall(reference: FunctionReference) {
                if (reference is MethodReference || skip(reference.containingFile)) return
                val name = reference.name?.lowercase() ?: return
                val prefixCheck = when (name) {
                    "str_starts_with" -> true
                    "strpos", "stripos", "mb_strpos", "mb_stripos" -> comparedWithZero(reference)
                    else -> false
                }
                if (!prefixCheck || !pathLike(reference)) return
                val haystack = reference.parameters.getOrNull(0) ?: return
                val needle = reference.parameters.getOrNull(1) ?: return
                val resolved = realpathDerived(haystack, 0)
                val separated = endsWithSeparator(needle, 0)
                if (resolved && separated) return
                if (prefixStrip(reference, haystack)) return
                holder.registerProblem(
                    reference,
                    if (resolved) {
                        "XOOPS: the base has no trailing separator — a sibling such as /uploads2/x passes a check for /uploads; " +
                            "append DIRECTORY_SEPARATOR to the base"
                    } else {
                        "XOOPS: a prefix check is not path containment — /safe/../outside/secret still passes; " +
                            "compare realpath() of the file with realpath() of the directory plus a separator"
                    },
                )
            }
        }
    }

    /** `realpath(…)`, possibly cast, trimmed, followed by a separator, or held in a variable assigned from it. */
    private fun realpathDerived(e: PsiElement?, depth: Int): Boolean {
        val x = Php.unwrap(e) ?: return false
        if (depth > 6) return false
        return when (x) {
            is MethodReference -> false
            is FunctionReference -> when (x.name?.lowercase()) {
                "realpath" -> true
                "rtrim", "trim" -> realpathDerived(x.parameters.firstOrNull(), depth + 1)
                // `str_replace('\\', '/', realpath($d))` only swaps one separator for the other.
                "str_replace" -> x.parameters.size == 3 && x.parameters.take(2).all { separatorOnly(it) } &&
                    realpathDerived(x.parameters[2], depth + 1)
                else -> false
            }
            is UnaryExpression -> realpathDerived(x.value, depth + 1)
            is com.jetbrains.php.lang.psi.elements.ConcatenationExpression ->
                isSeparator(x.rightOperand) && realpathDerived(x.leftOperand, depth + 1)
            is Variable -> Php.resolveAssignment(x)?.let { realpathDerived(it, depth + 1) } ?: privateParameterFromRealpath(x)
            is TernaryExpression -> everyRealBranch(x) { realpathDerived(it, depth + 1) }
            else -> false
        }
    }

    /**
     * `$root !== false ? realpath($root . '/x') : false`: every branch that is not a failure literal
     * (`false`, `null`, `''`) satisfies [test]. A failure value never passes str_starts_with() as a path.
     */
    private fun everyRealBranch(t: TernaryExpression, test: (PsiElement?) -> Boolean): Boolean {
        val branches = listOf(t.trueVariant, t.falseVariant).map { Php.unwrap(it) }
        if (branches.any { it == null }) return false
        val real = branches.filterNot { b -> b!!.text.lowercase() in setOf("false", "null", "''", "\"\"") }
        return real.isNotEmpty() && real.all(test)
    }

    /** `$base . DIRECTORY_SEPARATOR`, `$base . '/'`, or a variable assigned such a value. */
    private fun endsWithSeparator(e: PsiElement?, depth: Int): Boolean {
        val x = Php.unwrap(e) ?: return false
        if (depth > 3) return false
        return when (x) {
            is com.jetbrains.php.lang.psi.elements.ConcatenationExpression -> isSeparator(x.rightOperand)
            is Variable -> endsWithSeparator(Php.resolveAssignment(x), depth + 1)
            is TernaryExpression -> everyRealBranch(x) { endsWithSeparator(it, depth + 1) }
            else -> false
        }
    }

    private fun separatorOnly(e: PsiElement?): Boolean {
        val x = Php.unwrap(e)
        return (x is com.jetbrains.php.lang.psi.elements.ConstantReference && x.name == "DIRECTORY_SEPARATOR") ||
            (x is StringLiteralExpression && x.contents.isNotEmpty() && x.contents.replace("\\\\", "\\").all { it == '/' || it == '\\' })
    }

    /**
     * A parameter of a private method (`isAllowedDeleteRoot(string $path)`) whose every call in the class
     * passes a realpath()-derived argument: the callers are all visible, so the value is canonical.
     */
    private fun privateParameterFromRealpath(v: Variable): Boolean {
        val method = Php.enclosingFunction(v) as? com.jetbrains.php.lang.psi.elements.Method ?: return false
        if (method.access != com.jetbrains.php.lang.psi.elements.PhpModifier.Access.PRIVATE) return false
        val index = method.parameters.indexOfFirst { it.name == v.name }
        if (index < 0) return false
        val cls = method.containingClass ?: return false
        val calls = PsiTreeUtil.findChildrenOfType(cls, MethodReference::class.java).filter {
            it.name == method.name && it.classReference?.text?.lowercase() in setOf("self", "static", "\$this")
        }
        // A reassignment inside the method was already resolved above; only the callers remain.
        return calls.isNotEmpty() && calls.all { realpathDerived(it.parameters.getOrNull(index), 1) }
    }

    /**
     * `str_starts_with($p, $root . '/') ? substr($p, strlen($root)) : $p` strips a prefix for display;
     * nothing is allowed or refused on the result.
     */
    private fun prefixStrip(call: FunctionReference, haystack: PsiElement): Boolean {
        val t = PsiTreeUtil.getParentOfType(call, TernaryExpression::class.java) ?: return false
        if (t.condition?.let { PsiTreeUtil.isAncestor(it, call, false) } != true) return false
        val sub = Php.unwrap(t.trueVariant) as? FunctionReference ?: return false
        return sub !is MethodReference && sub.name.equals("substr", true) &&
            Php.unwrap(sub.parameters.firstOrNull())?.text == Php.unwrap(haystack)?.text
    }

    private fun isSeparator(e: PsiElement?): Boolean {
        val x = Php.unwrap(e)
        return (x is com.jetbrains.php.lang.psi.elements.ConstantReference && x.name == "DIRECTORY_SEPARATOR") ||
            (x is StringLiteralExpression && (x.contents.endsWith("/") || x.contents.endsWith("\\")))
    }

    private fun comparedWithZero(call: FunctionReference): Boolean {
        var child: PsiElement = call
        var p: PsiElement? = call.parent
        while (p is ParenthesizedExpression) { child = p; p = p.parent }
        val bin = p as? BinaryExpression ?: return false
        if (bin.operation?.text !in setOf("===", "!==", "==", "!=")) return false
        val other = Php.unwrap(if (bin.leftOperand == child) bin.rightOperand else bin.leftOperand)
        return other?.text == "0"
    }

    /**
     * A filesystem prefix test: the needle is a computed base directory and nothing in the call is a URL.
     * A literal needle (`'/'`, `'//'`, `'www.'`, `'ldaps://'`, `'multipart/'`, `'.'`) tests a shape, not a
     * containment root, and a URL / referer is not a path (XoopsCore27: 24 of 57 findings were these).
     */
    private fun pathLike(call: FunctionReference): Boolean {
        val needle = Php.unwrap(call.parameters.getOrNull(1)) ?: return false
        if (needle is StringLiteralExpression) return false
        if (call.parameters.any { urlWords.containsMatchIn(it.text) }) return false
        if (inUrlHelper(call)) return false
        return call.parameters.any { pathWords.containsMatchIn(it.text) }
    }

    /**
     * A URL path compared with the site's base path (`xoops_validateLocalRedirect()`,
     * `themeRedirectPathMatchesBase()`): the enclosing function is named for URLs / redirects, takes a
     * `$baseUrl`, or splits its input with parse_url(). Not a filesystem containment check.
     */
    private fun inUrlHelper(call: FunctionReference): Boolean {
        val fn = Php.enclosingFunction(call) ?: return false
        if (urlHelperWords.containsMatchIn(fn.name ?: "")) return true
        if (fn.parameters.any { urlHelperWords.containsMatchIn(it.name) }) return true
        return Php.calls(fn, "parse_url").any { it !is MethodReference }
    }

    private val pathWords = Regex("(?i)path|dir|root|file|folder|realpath|upload")
    private val urlHelperWords = Regex("(?i)url|uri|redirect|href|link")
    private val urlWords = Regex("(?i)url|uri\\b|referer|href|https?:")
}

/**
 * `file_exists($x)` / `is_readable($x)` is true for a directory. Where the condition's body then
 * includes or reads `$x`, the fix appends `&& is_file($x)`: it only ever narrows the condition.
 * A negated check (`!file_exists($x)`) is reported without a fix — `!a && b` would invert it.
 */
class XoopsIsFileAssetInspection : XoopsInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            override fun visitPhpFunctionCall(reference: FunctionReference) {
                if (reference is MethodReference || skip(reference.containingFile)) return
                if (reference.name?.lowercase() !in setOf("file_exists", "is_readable")) return
                val variable = Php.unwrap(reference.parameters.singleOrNull()) as? Variable ?: return
                val control = enclosingCondition(reference) ?: return
                if (!readsAsFile(control, variable.name)) return
                if (PsiTreeUtil.findChildrenOfType(control.condition, FunctionReference::class.java)
                        .any { it.name.equals("is_file", true) && Php.variableName(it.parameters.firstOrNull()) == variable.name }
                ) return
                val negated = Php.negatedWithin(reference, control.condition!!)
                holder.registerProblem(
                    reference,
                    "XOOPS: ${reference.name}() is true for a directory — the body reads \$${variable.name} as a file; add is_file()",
                    ProblemHighlightType.WEAK_WARNING,
                    *(if (negated) emptyArray() else arrayOf(RequireIsFileFix())),
                )
            }
        }
    }

    companion object {
        /** The if / elseif / while whose condition holds [element], without crossing a function boundary. */
        fun enclosingCondition(element: PsiElement): ControlStatement? {
            var node: PsiElement? = element
            while (node != null && node !is com.jetbrains.php.lang.psi.elements.Function) {
                val p = node.parent
                if (p is ControlStatement && p.condition == node) return p
                node = p
            }
            return null
        }

        /** The controlled statement includes `$name` or hands it to a file reader. */
        fun readsAsFile(control: ControlStatement, name: String?): Boolean {
            val body = control.statement ?: return false
            if (PsiTreeUtil.findChildrenOfType(body, Include::class.java).any { inc ->
                    PsiTreeUtil.findChildrenOfType(inc, Variable::class.java).any { it.name == name } ||
                        (inc.argument as? Variable)?.name == name
                }
            ) return true
            val readers = setOf("readfile", "file_get_contents", "fopen", "fpassthru", "file", "parse_ini_file", "simplexml_load_file", "getimagesize")
            return PsiTreeUtil.findChildrenOfType(body, FunctionReference::class.java).any { call ->
                call !is MethodReference && call.name?.lowercase() in readers && Php.variableName(call.parameters.firstOrNull()) == name
            }
        }
    }
}

class RequireIsFileFix : XoopsFix() {
    override fun getFamilyName(): String = "Require is_file() for the asset"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val call = descriptor.psiElement as? FunctionReference ?: return
        val variable = Php.unwrap(call.parameters.singleOrNull()) as? Variable ?: return
        val control = XoopsIsFileAssetInspection.enclosingCondition(call) ?: return
        if (Php.negatedWithin(call, control.condition!!)) return
        if (call.text.contains("is_file")) return
        Edits.replace(project, call, "${call.text} && is_file(\$${variable.name})")
    }
}

/**
 * `preg_match('/^[a-z]+$/', $name)` followed by an exact comparison of `$name`: `$` also matches
 * before a trailing newline, so `"script\n"` passes the allowlist and misses the blacklist.
 * The fix turns the final `$` into `\z` (single-quoted pattern, no `D` modifier).
 */
class XoopsAllowlistAnchorInspection : XoopsInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            override fun visitPhpFunctionCall(reference: FunctionReference) {
                if (reference is MethodReference || skip(reference.containingFile)) return
                if (!reference.name.equals("preg_match", true)) return
                val literal = reference.parameters.getOrNull(0) as? StringLiteralExpression ?: return
                val subject = Php.unwrap(reference.parameters.getOrNull(1)) as? Variable ?: return
                if (dollarOffset(literal) == null) return
                if (!comparedExactlyLater(reference, subject.name)) return
                holder.registerProblem(
                    reference,
                    "XOOPS: this allowlist pattern ends with $ — it matches \"${subject.name}\\n\" too, and the later exact comparison does not; anchor with \\z",
                    AnchorWithZFix(),
                )
            }
        }
    }

    companion object {
        /** Offset in the file of the unescaped `$` that closes the pattern, or null when there is none to fix. */
        fun dollarOffset(literal: StringLiteralExpression): Int? {
            if (!literal.isSingleQuote) return null
            val body = literal.contents
            if (body.length < 3) return null
            val delimiter = body[0]
            val closing = if (delimiter == '(') ')' else if (delimiter == '{') '}' else if (delimiter == '[') ']' else if (delimiter == '<') '>' else delimiter
            val end = body.lastIndexOf(closing)
            if (end <= 0) return null
            val modifiers = body.substring(end + 1)
            if (modifiers.contains('D') || modifiers.contains('m')) return null
            val pattern = body.substring(1, end)
            if (!pattern.endsWith("$") || pattern.endsWith("\\$")) return null
            // contents start one char after the opening quote
            return literal.textRange.startOffset + 1 + (end - 1)
        }

        fun comparedExactlyLater(call: FunctionReference, name: String?): Boolean {
            if (name == null) return false
            val scope: PsiElement = Php.enclosingFunction(call) ?: call.containingFile
            val after = call.textRange.endOffset
            val exact = setOf("===", "!==", "==", "!=")
            // A reassignment in between compares something else: class/preload.php matches `/(\.php)$/i`,
            // then `$file = substr($file, 0, -4)` and compares that with 'index'.
            val reassigned = PsiTreeUtil.findChildrenOfType(scope, AssignmentExpression::class.java)
                .filter { Php.variableName(it.variable) == name && it.textRange.startOffset >= after }
                .map { it.textRange.startOffset }
            return PsiTreeUtil.findChildrenOfType(scope, Variable::class.java).any { v ->
                v.name == name && v.textRange.startOffset >= after && reassigned.none { it < v.textRange.startOffset } && run {
                    var child: PsiElement = v
                    var p: PsiElement? = v.parent
                    while (p is ParenthesizedExpression) { child = p; p = p.parent }
                    (p is BinaryExpression && p.operation?.text in exact) ||
                        (p is ParameterList && (p.parent as? FunctionReference)?.name?.lowercase() in setOf("in_array", "strcmp", "strcasecmp", "strncasecmp", "strncmp", "array_key_exists"))
                }
            }
        }
    }
}

class AnchorWithZFix : XoopsFix() {
    override fun getFamilyName(): String = "Anchor the allowlist pattern with \\z"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val call = descriptor.psiElement as? FunctionReference ?: return
        val literal = call.parameters.getOrNull(0) as? StringLiteralExpression ?: return
        val offset = XoopsAllowlistAnchorInspection.dollarOffset(literal) ?: return
        val doc = com.intellij.psi.PsiDocumentManager.getInstance(project).getDocument(literal.containingFile) ?: return
        if (doc.getText(com.intellij.openapi.util.TextRange(offset, offset + 1)) != "$") return
        doc.replaceString(offset, offset + 1, "\\z")
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitDocument(doc)
    }
}

/**
 * A file under `preloads/` runs inside every module's request. Assigning `$xoopsModule` or
 * `$xoopsModuleConfig` there (or declaring them `global`, which is the prelude to it) repoints every
 * later reader on the page to the wrong module. Report only: the replacement is a local
 * `$cfg = xoops_getHandler('config')->getConfigsByCat(0, $mod->getVar('mid'))`.
 */
class XoopsPreloadGlobalInspection : XoopsInspection() {
    private val names = setOf("xoopsModule", "xoopsModuleConfig")

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            override fun visitPhpGlobal(globalStatement: Global) {
                if (skip(globalStatement.containingFile) || !XoopsPaths.path(globalStatement.containingFile).contains("/preloads/")) return
                for (v in globalStatement.variables) {
                    if (v.name in names) {
                        holder.registerProblem(v, "XOOPS: a preload must not touch \$${v.name} — load the module's config into a local variable instead")
                    }
                }
            }

            override fun visitPhpAssignmentExpression(assignmentExpression: AssignmentExpression) {
                if (skip(assignmentExpression.containingFile) || !XoopsPaths.path(assignmentExpression.containingFile).contains("/preloads/")) return
                val target = Php.unwrap(assignmentExpression.variable)
                val name = when {
                    // Inside a function `$xoopsModule` is a local unless the function declares it global
                    // (system/preloads/core.php: `$xoopsModule = $GLOBALS['xoopsModule'] ?? null;` is a read).
                    target is Variable -> target.name?.takeIf { isGlobalIn(target, it) }
                    target is ArrayAccessExpression && Php.variableName(target.value) == "GLOBALS" ->
                        (target.index?.value as? StringLiteralExpression)?.contents
                    else -> null
                }
                if (name in names) {
                    holder.registerProblem(
                        assignmentExpression,
                        "XOOPS: a preload must not assign \$$name — every later reader on the page sees the wrong module " +
                            "(\"Division by zero\", null config in the module actually being displayed)",
                    )
                }
            }
        }
    }

    /** File-scope code, or a function body that brought [name] in with `global`. */
    private fun isGlobalIn(variable: Variable, name: String): Boolean {
        val fn = Php.enclosingFunction(variable) ?: return true
        return PsiTreeUtil.findChildrenOfType(fn, Global::class.java)
            .any { g -> Php.enclosingFunction(g) == fn && g.variables.any { it.name == name } }
    }
}

/**
 * Tokens from `rand()` / `mt_rand()` / `uniqid()`, `md5()` / `sha1()` of a password or secret, and
 * `==` / `strcmp()` on a token or secret. A token's storage width and consumers are unknown, so no
 * generator is swapped in; the general comparison fix is `hash_equals()` on two strings the author has
 * to cast. One fix is provable: two request strings compared loosely become `!==` / `===`
 * ([StrictComparisonFix], the password confirmation of edituser.php / profile changepass.php).
 */
class XoopsWeakTokenInspection : XoopsInspection() {
    private val tokenName = Regex("(?i)token|nonce|csrf")
    private val secretName = Regex("(?i)pass|secret|token")

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            override fun visitPhpAssignmentExpression(assignmentExpression: AssignmentExpression) {
                if (skip(assignmentExpression.containingFile)) return
                val target = Php.unwrap(assignmentExpression.variable) as? Variable ?: return
                if (!tokenName.containsMatchIn(target.name ?: return)) return
                val generator = PsiTreeUtil.findChildrenOfType(assignmentExpression.value, FunctionReference::class.java)
                    .plus(listOfNotNull(assignmentExpression.value as? FunctionReference))
                    .firstOrNull { it !is MethodReference && it.name?.lowercase() in setOf("rand", "mt_rand", "uniqid", "lcg_value", "srand", "mt_srand") }
                    ?: return
                holder.registerProblem(
                    generator,
                    "XOOPS: ${generator.name}() is not a token generator — use bin2hex(random_bytes(n)) sized to the column, or the core token API",
                )
            }

            override fun visitPhpFunctionCall(reference: FunctionReference) {
                if (reference is MethodReference || skip(reference.containingFile)) return
                val name = reference.name?.lowercase() ?: return
                if (name in setOf("md5", "sha1")) {
                    val arg = Php.unwrap(reference.parameters.firstOrNull()) as? Variable ?: return
                    if (Regex("(?i)pass|secret").containsMatchIn(arg.name ?: return)) {
                        // Compared, or a Criteria value: it looks up a hash an old XOOPS stored, it does not store one.
                        val p = reference.parent
                        val lookup = (p as? BinaryExpression)?.operation?.text in setOf("==", "===", "!=", "!==") ||
                            ((p as? ParameterList)?.parent as? NewExpression)?.classReference?.name?.endsWith("Criteria", true) == true
                        holder.registerProblem(
                            reference as PsiElement,
                            // Before upd-2.5.7-to-2.5.8 the pass column is too short for a bcrypt hash: rehash only once it is widened.
                            if (lookup) "XOOPS: $name() matches a legacy password hash — keep it only as a migration path; once the schema holds " +
                                "modern hashes, rehash with password_hash() after a successful login"
                            else "XOOPS: $name() is not a password hash — use password_hash() / password_verify()",
                            if (lookup) ProblemHighlightType.WEAK_WARNING else ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        )
                    }
                }
                if (name == "strcmp" || name == "strcasecmp") {
                    if (reference.parameters.any { (Php.unwrap(it) as? Variable)?.name?.let(secretName::containsMatchIn) == true }) {
                        holder.registerProblem(reference, "XOOPS: $name() is not a constant-time comparison — use hash_equals((string) \$expected, (string) \$given)")
                    }
                }
            }

            override fun visitPhpBinaryExpression(expression: BinaryExpression) {
                if (skip(expression.containingFile)) return
                if (expression.operation?.text !in setOf("==", "!=", "<>")) return
                val named = listOf(expression.leftOperand, expression.rightOperand)
                    .mapNotNull { Php.unwrap(it) as? Variable }
                    .firstOrNull { secretName.containsMatchIn(it.name ?: "") } ?: return
                // `$pass == ''`, `$addtoken != false`: a literal that is not a numeric string has no
                // '0e…' collision — that is an emptiness / flag test (8 of 12 XoopsCore27 findings).
                if (listOf(expression.leftOperand, expression.rightOperand).any { nonNumericLiteral(it) }) return
                val strict = if (bothFromRequest(expression)) arrayOf(StrictComparisonFix()) else emptyArray()
                holder.registerProblem(
                    expression,
                    "XOOPS: loose comparison of \$${named.name} — '0e123' == '0e456' is true; " +
                        if (strict.isEmpty()) "use hash_equals() on two strings" else "compare strictly",
                    *strict,
                )
            }
        }
    }

    private fun nonNumericLiteral(e: PsiElement?): Boolean {
        val x = Php.unwrap(e)
        if (x is com.jetbrains.php.lang.psi.elements.ConstantReference) return x.name?.lowercase() in setOf("true", "false", "null")
        if (x is StringLiteralExpression) return !numericString.matches(x.contents)
        return false
    }

    private val numericString = Regex("""\s*[+-]?(\d|\.\d).*""")

    companion object {
        /**
         * PHP functions that take every argument by value. isWrite() counts an argument of an unresolved
         * plain function as a possible by-reference write (sort(), preg_match()'s third argument); these
         * never are, whether or not the PHP stubs resolve (the edituser.php checks: strlen(),
         * mb_strtolower(), password_hash()).
         */
        private val byValueFunctions = setOf(
            "strlen", "mb_strlen", "strtolower", "strtoupper", "mb_strtolower", "mb_strtoupper", "trim", "ltrim", "rtrim",
            "substr", "mb_substr", "strpos", "stripos", "mb_strpos", "str_contains", "str_starts_with", "str_ends_with",
            "htmlspecialchars", "sprintf", "password_hash", "password_verify", "hash", "md5", "sha1", "hash_equals",
            "strcmp", "strcasecmp", "is_string", "is_array", "is_numeric", "ctype_digit", "preg_quote", "urlencode", "rawurlencode",
        )

        private fun readOnlyInternalArgument(v: Variable): Boolean {
            val list = v.parent as? ParameterList ?: return false
            val call = list.parent as? FunctionReference ?: return false
            return call !is MethodReference && call.name?.lowercase() in byValueFunctions
        }

        /** Xmf\Request accessors that cast their result to string. */
        private val stringAccessors = setOf("getstring", "gettext", "getpath", "geturl")

        /**
         * Both sides are request input read as text, directly or through a local assigned from it:
         * `Request::getString()` / `getText()` / `getPath()` / `getUrl()` (always a string), or
         * `Request::getVar($k, '<literal>', $hash, 'string' …)` (a string, or with MASK_ALLOW_RAW the raw
         * input: a string or an array of strings). For such values `!==` only ever rejects more than `!=`,
         * and only where the loose form calls two different inputs equal ('0e1' / '0e2', '1' / '01') —
         * exactly the defect. A DB value, a hash, `$_POST[…]` (null when absent) or a typed property may be
         * compared loosely on purpose: no fix.
         */
        fun bothFromRequest(expression: BinaryExpression): Boolean =
            listOf(expression.leftOperand, expression.rightOperand).all { fromRequest(it, 0) }

        private fun fromRequest(e: PsiElement?, depth: Int): Boolean {
            val x = Php.unwrap(e) ?: return false
            if (depth > 2) return false
            return when (x) {
                is MethodReference -> {
                    if (!(x.classReference as? ClassReference)?.name.equals("Request", true)) return false
                    val name = x.name?.lowercase()
                    name in stringAccessors || (
                        name == "getvar" &&
                            x.parameters.getOrNull(1) is StringLiteralExpression &&
                            (x.parameters.getOrNull(3) as? StringLiteralExpression)?.contents.equals("string", true)
                        )
                }
                is Variable -> onlyAssignedFromRequest(x, depth)
                else -> false
            }
        }

        /**
         * Every write of the variable in its scope is a plain assignment from a request string, and it is
         * not a parameter or a `global` / `static` import. One other write (`$v = 5;` in some branch, a
         * by-reference call, a foreach target) and the value is not proven.
         */
        private fun onlyAssignedFromRequest(variable: Variable, depth: Int): Boolean {
            val name = variable.name ?: return false
            val fn = Php.enclosingFunction(variable)
            if (fn != null && fn.parameters.any { it.name == name }) return false
            val scope: PsiElement = fn ?: variable.containingFile
            val imported = PsiTreeUtil.findChildrenOfAnyType(scope, Global::class.java, com.jetbrains.php.lang.psi.elements.PhpStaticStatement::class.java)
                .any { Php.enclosingFunction(it) == fn && Regex("\\$" + Regex.escape(name) + "\\b").containsMatchIn(it.text) }
            if (imported) return false
            val writes = PsiTreeUtil.findChildrenOfType(scope, Variable::class.java)
                .filter { it.name == name && Php.enclosingFunction(it) == fn && XoopsSmarty5ApiInspection.isWrite(it) && !readOnlyInternalArgument(it) }
            if (writes.isEmpty()) return false
            return writes.all { w ->
                val assignment = w.parent as? AssignmentExpression
                assignment != null && assignment.variable == w && fromRequest(assignment.value, depth + 1)
            }
        }
    }
}

/** `$a != $b` → `$a !== $b` (and `==` → `===`) for two request strings; see [XoopsWeakTokenInspection.bothFromRequest]. */
class StrictComparisonFix : XoopsFix() {
    override fun getFamilyName(): String = "Compare the two request values strictly"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val expression = descriptor.psiElement as? BinaryExpression ?: return
        val op = expression.operation ?: return
        val strict = when (op.text) {
            "==" -> "==="
            "!=", "<>" -> "!=="
            else -> return
        }
        if (!XoopsWeakTokenInspection.bothFromRequest(expression)) return
        val doc = com.intellij.psi.PsiDocumentManager.getInstance(project).getDocument(expression.containingFile) ?: return
        doc.replaceString(op.textRange.startOffset, op.textRange.endOffset, strict)
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitDocument(doc)
    }
}

/**
 * Smarty 5 (core 2.8) removed `assignByRef()`, `appendByRef()`, `register_function()` and the
 * public `template_dir` property, and has no global `\Smarty` class. `assignByRef($k, $v)` →
 * `assign($k, $v)` is offered only when `$v` is never written again in its scope — then by-reference
 * and by-value assignment are the same thing. Everything else is reported.
 */
class XoopsSmarty5ApiInspection : XoopsInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            override fun visitPhpMethodReference(reference: MethodReference) {
                if (notSmarty5(reference.containingFile)) return
                val name = reference.name ?: return
                when (name.lowercase()) {
                    "assignbyref", "appendbyref" -> {
                        val replacement = if (name.lowercase() == "assignbyref") "assign" else "append"
                        val variable = Php.unwrap(reference.parameters.getOrNull(1)) as? Variable
                        val safe = variable != null && !writtenAfter(variable, reference)
                        holder.registerProblem(
                            reference,
                            "XOOPS: Smarty 5 removed $name() — $replacement() is the replacement" +
                                if (safe) "" else " (the variable is modified after this call, so the by-reference semantics matter: rewrite by hand)",
                            *(if (safe) arrayOf(RenameSmartyMethodFix(name, replacement)) else emptyArray()),
                        )
                    }
                    "register_function", "register_block", "register_modifier", "register_object", "unregister_function" ->
                        holder.registerProblem(reference, "XOOPS: Smarty 5 removed $name() — use registerPlugin()")
                }
            }

            override fun visitPhpAssignmentExpression(assignmentExpression: AssignmentExpression) {
                if (notSmarty5(assignmentExpression.containingFile)) return
                val field = Php.unwrap(assignmentExpression.variable) as? FieldReference ?: return
                if (field.name in setOf("template_dir", "compile_dir", "config_dir", "cache_dir", "plugins_dir")) {
                    holder.registerProblem(assignmentExpression, "XOOPS: Smarty 5 has no public ${field.name} — use the set*() method")
                }
            }

            override fun visitPhpClassReference(classReference: ClassReference) {
                if (notSmarty5(classReference.containingFile)) return
                val fqn = classReference.fqn ?: return
                if (fqn == "\\Smarty" && classReference.parent !is com.jetbrains.php.lang.psi.elements.PhpUseList) {
                    holder.registerProblem(classReference, "XOOPS: Smarty 5 has no global \\Smarty class — on core 2.8 the template object is \\XoopsTpl (extends \\Smarty\\Smarty)")
                }
            }
        }
    }

    /**
     * Smarty 5 ships with core 2.8. On a 2.5 / 2.7 tree (Smarty 2 / 4) `assignByRef()`, `\Smarty` and
     * the public dirs are the current API, and renaming a by-reference assign changes what the template
     * sees (XoopsCore27: 38 findings, every one valid code). A standalone module (no core) is checked.
     */
    private fun notSmarty5(file: com.intellij.psi.PsiFile): Boolean =
        skipConvention(file) || !org.xoops.support.psi.XoopsCore.atLeast(file, org.xoops.support.psi.XoopsCore.SMARTY5_API)

    companion object {
        /** `$v` is assigned, incremented, unset, iterated by reference or passed by reference after [point]. */
        fun writtenAfter(variable: Variable, point: PsiElement): Boolean {
            val fn = Php.enclosingFunction(point)
            val scope: PsiElement = fn ?: point.containingFile
            val after = point.textRange.endOffset
            return PsiTreeUtil.findChildrenOfType(scope, Variable::class.java).any { v ->
                v.name == variable.name && v.textRange.startOffset >= after && Php.enclosingFunction(v) == fn && isWrite(v)
            }
        }

        fun isWrite(v: Variable): Boolean {
            var node: PsiElement = v
            var p: PsiElement? = v.parent
            while (p is ParenthesizedExpression || (p is ArrayAccessExpression && p.value == node)) { node = p; p = p.parent }
            return when {
                p is AssignmentExpression && p.variable == node -> true
                p is SelfAssignmentExpression && p.variable == node -> true
                p is UnaryExpression && p.operation?.text in setOf("++", "--", "&") -> true
                p is PhpUnset -> true
                p is ForeachStatement && p.array != node -> true
                p is ParameterList -> {
                    val call = p.parent as? FunctionReference
                    val index = p.parameters.indexOf(node)
                    val fnDecl = call?.resolve() as? com.jetbrains.php.lang.psi.elements.Function
                    // An unresolved plain function may take a reference (sort, preg_match); an unresolved
                    // method on the template object (assign, append, display) is a read.
                    if (fnDecl == null) call !is MethodReference else fnDecl.parameters.getOrNull(index)?.isPassByRef == true
                }
                else -> false
            }
        }
    }
}

class RenameSmartyMethodFix(private val from: String, private val to: String) : XoopsFix() {
    override fun getFamilyName(): String = "Replace the removed Smarty call with its Smarty 5 equivalent"
    override fun getName(): String = "Replace $from() with $to()"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val call = descriptor.psiElement as? MethodReference ?: return
        if (!from.equals(call.name, true)) return
        val variable = Php.unwrap(call.parameters.getOrNull(1)) as? Variable ?: return
        if (XoopsSmarty5ApiInspection.writtenAfter(variable, call)) return
        Edits.renameCall(project, call, to)
    }
}
