package org.xoops.support.inspections

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.ArrayAccessExpression
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.AssignmentExpression
import com.jetbrains.php.lang.psi.elements.BinaryExpression
import com.jetbrains.php.lang.psi.elements.ClassReference
import com.jetbrains.php.lang.psi.elements.ConcatenationExpression
import com.jetbrains.php.lang.psi.elements.DoWhile
import com.jetbrains.php.lang.psi.elements.ElseIf
import com.jetbrains.php.lang.psi.elements.ForeachStatement
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.Global
import com.jetbrains.php.lang.psi.elements.If
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.MultiassignmentExpression
import com.jetbrains.php.lang.psi.elements.NewExpression
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.ParenthesizedExpression
import com.jetbrains.php.lang.psi.elements.PhpEchoStatement
import com.jetbrains.php.lang.psi.elements.PhpEmpty
import com.jetbrains.php.lang.psi.elements.PhpIsset
import com.jetbrains.php.lang.psi.elements.PhpNamespace
import com.jetbrains.php.lang.psi.elements.PhpPrintExpression
import com.jetbrains.php.lang.psi.elements.PhpSwitch
import com.jetbrains.php.lang.psi.elements.PhpUnset
import com.jetbrains.php.lang.psi.elements.PhpUseList
import com.jetbrains.php.lang.psi.elements.SelfAssignmentExpression
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.TernaryExpression
import com.jetbrains.php.lang.psi.elements.UnaryExpression
import com.jetbrains.php.lang.psi.elements.Variable
import com.jetbrains.php.lang.psi.elements.While
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor
import org.xoops.support.fix.Edits
import org.xoops.support.fix.XoopsFix
import org.xoops.support.psi.Php
import org.xoops.support.psi.XoopsPaths

/**
 * Raw `$_GET` / `$_POST` / `$_COOKIE` reads should go through `Xmf\Request`; `$_REQUEST`
 * is banned outright.
 *
 * The rewrite is offered only where the accessor provably keeps the value's meaning:
 *
 *  * `getArray()` where PHP already requires an array: `foreach`, `count()`, the array
 *    position of an array function, an `array`-typed parameter, an `XOBJ_DTYPE_ARRAY` field
 *    of the resolved receiver. `getString()` on an array is the string `"Array"`, which is how
 *    a permission form loses every group; a string offset `$_POST['v'][0]` is therefore never
 *    rewritten (it may be a real string offset);
 *  * `getInt()` only where an `(int)` cast or `intval()` already converts (the cast is replaced);
 *  * `getString()` where the value is compared to an identifier literal, used as an array key or
 *    a `switch` subject, or is an `in_array()` needle (tags cannot matter in an identifier);
 *  * `getText()` — keeps HTML — where the value is concatenated, printed, passed to a function
 *    whose parameter takes a string only, or stored in a text field. Digests, lengths and
 *    (de)serialisation are excluded: `getText()` trims;
 *  * `isset($_X['key'])` → `Request::hasVar('key', 'X')`;
 *  * `isset($_X['k']) ? $_X['k'] : 'd'` and `$_X['k'] ?? 'd'` become one call with `'d'` as the
 *    default; the default never decides the accessor, the use does.
 *
 * Everything else — a write, `unset()`, `empty()`, `!$_POST['x']`, a `=== null` comparison, an
 * interpolated or dynamic key, a bare `$_POST`, a by-reference argument, an untyped argument, a
 * variable captured by `compact()` / `global` or rebound — is reported without a fix.
 */
class XoopsSuperglobalInspection : XoopsInspection() {

    private fun bareRead(holder: ProblemsHolder, variable: Variable, mixed: String, name: String, source: String) {
        val fixable = Rewrite.bareReadable(variable)
        holder.registerProblem(
            variable as PsiElement,
            "XOOPS: ${mixed}bare \$$name — read it through Xmf\\Request" +
                if (fixable) " (Request::get('$source', …) keeps the array as is)" else "",
            ProblemHighlightType.WEAK_WARNING,
            *(if (fixable) arrayOf(ReplaceSuperglobalFix("", source, Accessor.WHOLE, null)) else emptyArray()),
        )
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            override fun visitPhpVariable(variable: Variable) {
                val name = variable.name
                if (name !in sources || skip(variable.containingFile)) return
                // Protector is the request firewall: it reads and rewrites the raw superglobals before
                // the rest of XOOPS boots, which Xmf\Request (a reader of them) cannot do.
                if (XoopsPaths.path(variable.containingFile).contains("/modules/protector/")) return
                // The installer and upgrader run before the xoops_lib path is known (page_pathsettings.php
                // asks for it): Xmf\Request is not loadable there.
                if (XoopsPaths.isInstallerOrUpgrader(variable.containingFile.virtualFile)) return
                // A script that never boots XOOPS (a payment callback, a bundled thumbnailer, a PEAR
                // example) has no autoloader for Xmf\Request: the rewrite would fatal on the first hit.
                if (!XoopsPaths.isXoopsCode(variable.containingFile)) return
                val source = hashOf(name)
                val mixed = if (name == "_REQUEST") "\$_REQUEST mixes GET, POST and COOKIE — " else ""
                val access = variable.parent as? ArrayAccessExpression
                if (access == null || access.value != variable) {
                    var bare: PsiElement = variable
                    while (bare.parent is ParenthesizedExpression) bare = bare.parent
                    when (bare.parent) {
                        is Global -> holder.registerProblem(
                            variable as PsiElement,
                            "XOOPS: ${mixed}global \$$name is a no-op — a superglobal is in scope everywhere",
                            ProblemHighlightType.WEAK_WARNING,
                            RemoveSuperglobalFromGlobalFix(name),
                        )
                        is PhpIsset -> holder.registerProblem(
                            variable as PsiElement,
                            "XOOPS: ${mixed}isset(\$$name) is always true — a superglobal always exists; drop the check",
                            ProblemHighlightType.WEAK_WARNING,
                        )
                        is AssignmentExpression -> if ((bare.parent as AssignmentExpression).variable == bare) holder.registerProblem(
                            variable as PsiElement,
                            "XOOPS: ${mixed}assigning \$$name rewrites the request for every later reader, Xmf\\Request included — " +
                                "keep derived values in a local unless later code depends on it",
                            ProblemHighlightType.WEAK_WARNING,
                        ) else bareRead(holder, variable, mixed, name, source)
                        else -> bareRead(holder, variable, mixed, name, source)
                    }
                    return
                }
                val key = Rewrite.literalKey(access)
                val anyKey = access.index?.value?.text
                val raw = if (anyKey != null && Rewrite.rawReadable(access)) arrayOf(ReplaceSuperglobalFix(anyKey, source, Accessor.RAW, null)) else emptyArray()
                // hasVar() takes any key expression: the index text goes in verbatim, quotes included.
                val hasVar = if (anyKey != null) arrayOf(ReplaceSuperglobalFix(anyKey, source, Accessor.HAS_VAR, null)) else emptyArray()
                when (usageOf(access)) {
                    Usage.WRITE -> holder.registerProblem(
                        variable as PsiElement,
                        "XOOPS: ${mixed}writes to \$$name change request state for every later reader; keep a local variable instead",
                    )
                    Usage.STRICT_NULL -> holder.registerProblem(
                        variable as PsiElement,
                        "XOOPS: ${mixed}\$$name[…] compared with null — that is isset(): use Request::hasVar(${anyKey ?: "'key'"}, '$source')",
                        ProblemHighlightType.WEAK_WARNING,
                        *hasVar,
                    )
                    Usage.LOOSE_NULL -> holder.registerProblem(
                        variable as PsiElement,
                        "XOOPS: ${mixed}\$$name[…] loosely compared with null — Request::getVar() keeps the raw value and the comparison",
                        ProblemHighlightType.WEAK_WARNING,
                        *raw,
                    )
                    Usage.EMPTY -> holder.registerProblem(
                        variable as PsiElement,
                        "XOOPS: ${mixed}empty(\$$name[…]) — hasVar() has isset semantics ('0' stays set); empty(Request::getVar(…)) keeps the test",
                        ProblemHighlightType.WEAK_WARNING,
                        *raw,
                    )
                    Usage.ISSET -> holder.registerProblem(
                        variable as PsiElement,
                        "XOOPS: ${mixed}prefer Request::hasVar(${anyKey ?: "'key'"}, '$source') over isset(\$$name[…])",
                        ProblemHighlightType.WEAK_WARNING,
                        *hasVar,
                    )
                    Usage.READ -> {
                        val plan = if (key != null) Rewrite.plan(access) else null
                        if (plan != null) {
                            holder.registerProblem(
                                variable as PsiElement,
                                "XOOPS: ${mixed}prefer Request::${plan.accessor.method}('$key', …, '$source') over raw \$$name",
                                ProblemHighlightType.WEAK_WARNING,
                                ReplaceSuperglobalFix(key!!, source, plan.accessor, plan.default),
                            )
                        } else {
                            holder.registerProblem(
                                variable as PsiElement,
                                "XOOPS: ${mixed}raw \$$name[…] read — how the value is used cannot be told here; " +
                                    "Request::getVar(…, 'none', MASK_ALLOW_RAW | MASK_NO_TRIM) keeps it byte for byte, then pick a typed accessor by hand",
                                ProblemHighlightType.WEAK_WARNING,
                                *raw,
                            )
                        }
                    }
                }
            }
        }
    }

    enum class Usage { READ, WRITE, ISSET, EMPTY, STRICT_NULL, LOOSE_NULL }

    private fun usageOf(access: ArrayAccessExpression): Usage {
        var child: PsiElement = access
        var p: PsiElement? = access.parent
        while (p is ParenthesizedExpression) { child = p; p = p.parent }
        val op = (p as? BinaryExpression)?.operation?.text
        val withNull = p is BinaryExpression && listOf(p.leftOperand, p.rightOperand).any { it != child && Php.unwrap(it)?.text.equals("null", true) }
        return when {
            p is AssignmentExpression && p.variable == child -> Usage.WRITE
            p is SelfAssignmentExpression && p.variable == child -> Usage.WRITE
            p is PhpUnset -> Usage.WRITE
            p is PhpIsset -> Usage.ISSET
            p is PhpEmpty -> Usage.EMPTY
            withNull && (op == "===" || op == "!==") -> Usage.STRICT_NULL
            withNull && (op == "==" || op == "!=" || op == "<>") -> Usage.LOOSE_NULL
            else -> Usage.READ
        }
    }

    /** Which `Xmf\Request` accessor keeps the value's meaning; [method] is the call, [default] its neutral default. */
    enum class Accessor(val method: String, val default: String) {
        ARRAY("getArray", "[]"), INT("getInt", "0"), STRING("getString", "''"), TEXT("getText", "''"), HAS_VAR("hasVar", ""),
        /** `getVar(key, null, hash, 'none', MASK_ALLOW_RAW | MASK_NO_TRIM)`: byte-identical to the raw read, null when absent. */
        RAW("getVar", "null"),
        /** `get(hash, MASK_ALLOW_RAW | MASK_NO_TRIM)`: the whole array, as is. */
        WHOLE("get", "");
    }

    class Rewrite(val accessor: Accessor, val default: String?, val target: PsiElement) {
        companion object {
            /** Array functions and the parameter positions that take the array (null = every position). */
            private val arrayPositions: Map<String, Set<Int>?> = mapOf(
                "count" to setOf(0), "sizeof" to setOf(0), "array_keys" to setOf(0), "array_values" to setOf(0),
                "array_unique" to setOf(0), "array_filter" to setOf(0), "array_merge" to null, "array_flip" to setOf(0),
                "array_sum" to setOf(0), "array_slice" to setOf(0), "array_diff" to null, "array_intersect" to null,
                "array_combine" to setOf(0, 1), "array_reverse" to setOf(0), "array_key_exists" to setOf(1),
                "array_search" to setOf(1), "in_array" to setOf(1), "implode" to setOf(1), "join" to setOf(1),
                "array_map" to setOf(1, 2, 3), "array_column" to setOf(0), "array_chunk" to setOf(0),
                "array_fill_keys" to setOf(0), "array_intersect_key" to null, "array_diff_key" to null,
                "array_key_first" to setOf(0), "array_key_last" to setOf(0),
            )
            /** Array functions that take the array by reference: a call result cannot be passed; evidence only, no direct rewrite. */
            private val byRefArrayFunctions = setOf(
                "array_walk", "array_pop", "array_shift", "array_push", "array_unshift", "sort", "rsort", "asort", "arsort",
                "ksort", "krsort", "usort", "uasort", "uksort", "shuffle", "current", "reset", "end", "key", "next", "prev", "array_splice",
            )
            /** Scalar positions of array functions: `in_array($needle, …)`, `implode($glue, …)`. */
            private val needlePosition = mapOf("in_array" to 0, "array_search" to 0, "array_key_exists" to 0, "implode" to 0, "join" to 0)
            private val identifierFunctions = setOf("is_numeric", "ctype_digit", "ctype_alpha", "ctype_alnum", "strcmp", "strcasecmp", "strncmp", "strncasecmp")
            /** Functions whose named parameter takes a string only and for which a trimmed value is the same text. */
            private val stringPositions: Map<String, Set<Int>> = mapOf(
                "trim" to setOf(0), "ltrim" to setOf(0), "rtrim" to setOf(0), "strtolower" to setOf(0), "strtoupper" to setOf(0),
                "mb_strtolower" to setOf(0), "mb_strtoupper" to setOf(0), "ucfirst" to setOf(0), "ucwords" to setOf(0), "lcfirst" to setOf(0),
                "htmlspecialchars" to setOf(0), "htmlentities" to setOf(0), "html_entity_decode" to setOf(0), "htmlspecialchars_decode" to setOf(0),
                "urlencode" to setOf(0), "rawurlencode" to setOf(0), "urldecode" to setOf(0), "basename" to setOf(0), "dirname" to setOf(0),
                "addslashes" to setOf(0), "stripslashes" to setOf(0), "strip_tags" to setOf(0), "nl2br" to setOf(0), "substr" to setOf(0),
                "mb_substr" to setOf(0), "explode" to setOf(1), "preg_match" to setOf(1), "preg_match_all" to setOf(1), "preg_split" to setOf(1),
                "strpos" to setOf(0), "stripos" to setOf(0), "strrpos" to setOf(0), "str_contains" to setOf(0), "str_starts_with" to setOf(0),
                "str_ends_with" to setOf(0), "strtotime" to setOf(0), "wordwrap" to setOf(0), "strrev" to setOf(0), "str_pad" to setOf(0),
                "strstr" to setOf(0), "stristr" to setOf(0), "strtr" to setOf(0), "parse_url" to setOf(0), "xoops_trim" to setOf(0),
                "xoops_substr" to setOf(0), "redirect_header" to setOf(0, 2), "sprintf" to setOf(1, 2, 3, 4, 5, 6), "number_format" to setOf(0),
                "floatval" to setOf(0), "is_string" to setOf(0),
            )
            private val textDtypes = setOf("TXTBOX", "TXTAREA", "SOURCE", "OTHER", "URL", "EMAIL", "MTIME", "STIME", "LTIME")
            private val arrayDtypes = setOf("ARRAY", "SIMPLE_ARRAY")
            private val intDtypes = setOf("INT")
            private val identifierLiteral = Regex("^[A-Za-z0-9_.:/-]*$")

            /** `$_POST['k']` with a plain single-quoted key: no interpolation, no escapes. */
            fun literalKey(access: ArrayAccessExpression): String? {
                val literal = access.index?.value as? StringLiteralExpression ?: return null
                if (literal.isSingleQuote.not() && literal.contents.contains('$')) return null
                val raw = literal.contents
                if (raw.contains('\\') || raw.contains('$') || raw.contains('{')) return null
                return raw
            }

            private fun isNeedle(name: String, index: Int, arity: Int): Boolean =
                needlePosition[name] == index && !((name == "implode" || name == "join") && arity == 1)

            private fun isArrayPosition(name: String, index: Int, arity: Int): Boolean {
                if (!arrayPositions.containsKey(name)) return false
                if ((name == "implode" || name == "join") && arity == 1) return index == 0
                val positions = arrayPositions[name] ?: return true
                return index in positions
            }

            /** The read sits under `isset()` / `empty()` / `unset()`, is written through, iterated by reference or destructured. */
            private fun inWriteOrIssetContext(access: ArrayAccessExpression): Boolean {
                var node: PsiElement = access
                var p: PsiElement? = access.parent
                while (p is ParenthesizedExpression || (p is ArrayAccessExpression && p.value == node)) { node = p; p = p.parent }
                return when {
                    isCallee(node, p) -> true
                    p is PhpIsset || p is PhpEmpty || p is PhpUnset -> true
                    p is AssignmentExpression && p.variable == node -> true
                    p is SelfAssignmentExpression && p.variable == node -> true
                    p is UnaryExpression && p.operation?.text in setOf("++", "--") -> true
                    p is ForeachStatement && (p.array != node || byRefIteration(p)) -> true
                    p is MultiassignmentExpression -> true
                    p is UnaryExpression && p.operation?.text == "&" -> true
                    else -> false
                }
            }

            /** `foreach ($a as &$v)` / `as $k => &$v`. */
            private fun byRefIteration(foreach: ForeachStatement): Boolean {
                val head = foreach.text.substringBefore(')')
                return head.substringAfter(" as ", "").contains('&')
            }

            /** No string interpolation between [element] and its statement: a static call cannot be interpolated. */
            private fun interpolated(element: PsiElement): Boolean {
                var p: PsiElement? = element.parent
                while (p != null && p !is com.jetbrains.php.lang.psi.elements.Statement && p !is com.intellij.psi.PsiFile) {
                    if (p is StringLiteralExpression) return true
                    p = p.parent
                }
                return false
            }

            /** [element] is handed to a by-reference parameter (a call result cannot be passed by reference). */
            private fun passedByReference(element: PsiElement): Boolean {
                var node: PsiElement = element
                while (node.parent is ParenthesizedExpression) node = node.parent
                val list = node.parent as? ParameterList ?: return false
                val call = list.parent as? FunctionReference ?: return false
                val index = list.parameters.indexOf(node)
                if (call !is MethodReference && call.name?.lowercase() in byRefArrayFunctions) return true
                val fn = call.resolve() as? Function ?: return false
                return fn.parameters.getOrNull(index)?.isPassByRef == true
            }

            /**
             * The raw accessor is byte-identical to the read everywhere a value is read: not written through,
             * not under isset()/unset(), not interpolated, not iterated or passed by reference. `empty()` is a read.
             */
            fun rawReadable(access: ArrayAccessExpression): Boolean {
                var node: PsiElement = access
                var p: PsiElement? = access.parent
                while (p is ParenthesizedExpression || (p is ArrayAccessExpression && p.value == node)) { node = p; p = p.parent }
                if (isCallee(node, p)) return false
                if (p is PhpIsset || p is PhpUnset) return false
                if (p is AssignmentExpression && p.variable == node) return false
                if (p is SelfAssignmentExpression && p.variable == node) return false
                if (p is UnaryExpression && p.operation?.text in setOf("++", "--", "&")) return false
                if (p is ForeachStatement && (p.array != node || byRefIteration(p))) return false
                if (p is MultiassignmentExpression) return false
                if (interpolated(access) || passedByReference(node)) return false
                return true
            }

            /**
             * `new $_REQUEST['mod']()`, `$_GET['f']()`, `$obj->$_POST['m']()`: the value names the class,
             * function or method and must stay a variable — `new \Xmf\Request::getVar(…)()` does not parse
             * (pgv/module.php in the 224-module review).
             */
            private fun isCallee(node: PsiElement, p: PsiElement?): Boolean {
                if (p is FunctionReference) return p.parameterList?.let { PsiTreeUtil.isAncestor(it, node, false) } != true
                // `new $_REQUEST['mod']()`: the access sits in a class-reference wrapper under the NewExpression.
                val new = PsiTreeUtil.getParentOfType(node, NewExpression::class.java, true, ParameterList::class.java) ?: return false
                return new.parameterList?.let { PsiTreeUtil.isAncestor(it, node, false) } != true
            }

            /** A bare `$_POST` that is only read: `foreach`, an argument, `count()`, `empty()`, an assignment's right side. */
            fun bareReadable(variable: Variable): Boolean {
                var node: PsiElement = variable
                while (node.parent is ParenthesizedExpression) node = node.parent
                val p = node.parent
                if (p is Global || p is PhpIsset || p is PhpUnset) return false
                if (p is AssignmentExpression && p.variable == node) return false
                if (p is SelfAssignmentExpression && p.variable == node) return false
                if (p is UnaryExpression && p.operation?.text == "&") return false
                if (p is ForeachStatement && (p.array != node || byRefIteration(p))) return false
                if (p is MultiassignmentExpression) return false
                if (p is StringLiteralExpression) return false
                if (interpolated(variable) || passedByReference(node)) return false
                return true
            }

            /** The rewrite for a keyed read, or null when its use cannot be classified. */
            fun plan(access: ArrayAccessExpression): Rewrite? {
                if (literalKey(access) == null || inWriteOrIssetContext(access)) return null
                var node: PsiElement = access
                var p: PsiElement? = access.parent
                while (p is ParenthesizedExpression) { node = p; p = p.parent }

                // `isset($_POST['k']) ? $_POST['k'] : 'd'` — the whole ternary is one read with a default.
                if (p is TernaryExpression && p.trueVariant == node && p.falseVariant != null) {
                    if (!guardsSameKey(Php.unwrap(p.condition), access)) return null
                    val default = literalDefault(p.falseVariant) ?: return null
                    return classify(p, default)
                }
                // `$_POST['k'] ?? 'd'`
                if (p is BinaryExpression && p.operation?.text == "??" && p.leftOperand == node) {
                    val default = literalDefault(p.rightOperand) ?: return null
                    return classify(p, default)
                }
                return classify(node, null)
            }

            /** `isset($_POST['k'])` or `Xmf\Request::hasVar('k', 'POST')` on the same key and source. */
            private fun guardsSameKey(cond: PsiElement?, access: ArrayAccessExpression): Boolean {
                if (cond is PhpIsset) return cond.variables.singleOrNull()?.let { Php.unwrap(it)?.text == access.text } == true
                if (cond is MethodReference && cond.name == "hasVar") {
                    val cls = Php.unwrap(cond.classReference) as? ClassReference ?: return false
                    val fqn = (cls.fqn ?: cls.text).trimStart('\\')
                    if (!fqn.equals("Xmf\\Request", true) && cls.text != "Request") return false
                    val key = literalKey(access) ?: return false
                    val source = (access.value as? Variable)?.name?.removePrefix("_")
                    return Php.firstStringArg(cond) == key && (cond.parameters.getOrNull(1) as? StringLiteralExpression)?.contents == source
                }
                return false
            }

            /** `'d'`, `0`, `[]`, `array()` as a default; null for anything else (no fix). */
            private fun literalDefault(e: PsiElement?): Pair<Accessor?, String>? {
                val u = Php.unwrap(e) ?: return null
                return when {
                    u is StringLiteralExpression && !u.text.contains('$') -> null to u.text
                    u is ArrayCreationExpression && u.text.replace(" ", "") in setOf("[]", "array()") -> Accessor.ARRAY to "[]"
                    u.text.matches(Regex("-?\\d+")) -> Accessor.INT to u.text
                    else -> null
                }
            }

            /**
             * [node] is the expression that will be replaced (the access, or the ternary / coalesce that owns it);
             * [default] is a fixed default and, for `[]` / an integer, the accessor it must agree with — the use
             * decides, the default only has to be compatible.
             */
            private fun classify(node: PsiElement, default: Pair<Accessor?, String>?): Rewrite? {
                val forced = default?.first
                val use = usageKind(node) ?: return null
                val accessor = when (use) {
                    Kind.ARRAY -> Accessor.ARRAY
                    Kind.INT -> Accessor.INT
                    Kind.IDENTIFIER -> Accessor.STRING
                    Kind.TEXT -> Accessor.TEXT
                }
                if (forced != null && forced != accessor) return null
                if (forced == null && default != null && accessor != Accessor.STRING && accessor != Accessor.TEXT) return null
                val wrapper = castOf(node) ?: intvalOf(node)
                val target = if (use == Kind.INT && wrapper != null) wrapper else node
                return Rewrite(accessor, default?.second, target)
            }

            enum class Kind { ARRAY, INT, IDENTIFIER, TEXT }

            private fun castOf(node: PsiElement): UnaryExpression? {
                val p = node.parent as? UnaryExpression ?: return null
                return if (p.operation?.text?.replace(" ", "")?.matches(Regex("\\((int|integer)\\)")) == true) p else null
            }

            private fun intvalOf(node: PsiElement): FunctionReference? {
                val list = node.parent as? ParameterList ?: return null
                val call = list.parent as? FunctionReference ?: return null
                return if (call !is MethodReference && Php.isCall(call, "intval") && call.parameters.size == 1) call else null
            }

            private fun isIdentifierLiteral(e: PsiElement?): Boolean {
                val u = Php.unwrap(e) ?: return false
                if (u is StringLiteralExpression) return !u.text.contains('$') && identifierLiteral.matches(u.contents)
                return u.text.matches(Regex("-?\\d+"))
            }

            /** How [node] is consumed; null when that cannot be told. */
            private fun usageKind(node: PsiElement): Kind? {
                val p = node.parent
                when {
                    p is ForeachStatement && p.array == node -> return Kind.ARRAY
                    castOf(node) != null || intvalOf(node) != null -> return Kind.INT
                    p is UnaryExpression && p.operation?.text?.replace(" ", "") == "(array)" -> return Kind.ARRAY
                    p is UnaryExpression && p.operation?.text?.replace(" ", "") == "(string)" -> return Kind.TEXT
                    p is ArrayAccessExpression && p.index?.value == node -> return Kind.IDENTIFIER
                    p is PhpSwitch && p.argument == node -> return Kind.IDENTIFIER
                    p is StringLiteralExpression -> return null // "{$_POST['x']}": a static call cannot be interpolated
                    p is ConcatenationExpression || p is PhpEchoStatement || p is PhpPrintExpression -> return Kind.TEXT
                    p is BinaryExpression -> {
                        val op = p.operation?.text ?: return null
                        val other = if (p.leftOperand == node) p.rightOperand else p.leftOperand
                        return when {
                            op in setOf("==", "===", "!=", "!==", "<>") && isIdentifierLiteral(other) -> Kind.IDENTIFIER
                            op == "." -> Kind.TEXT
                            else -> null
                        }
                    }
                    p is ParameterList -> {
                        val call = p.parent
                        val index = p.parameters.indexOf(node)
                        val arity = p.parameters.size
                        if (call is FunctionReference && call !is MethodReference) {
                            val name = call.name?.lowercase() ?: return null
                            if (name in byRefArrayFunctions) return null
                            if (arrayPositions.containsKey(name)) {
                                return when {
                                    isNeedle(name, index, arity) -> Kind.IDENTIFIER
                                    isArrayPosition(name, index, arity) -> Kind.ARRAY
                                    else -> null
                                }
                            }
                            if (name in identifierFunctions) return Kind.IDENTIFIER
                            stringPositions[name]?.let { return if (index in it) Kind.TEXT else null }
                        }
                        if (call is MethodReference && call.name == "setVar" && index == 1) return dtypeKind(call)
                        return parameterKind(call as? FunctionReference, index)
                    }
                    p is AssignmentExpression && p.value == node -> {
                        val variable = Php.unwrap(p.variable) as? Variable ?: return null
                        return traceVariable(variable, p)
                    }
                }
                return null
            }

            /** `$obj->setVar('key', $_POST['key'])`: the `initVar()` DTYPE of the resolved receiver's class decides. */
            private fun dtypeKind(call: MethodReference): Kind? {
                val dtype = XoopsAttributeEscapeInspection.fieldDtype(call) ?: return null
                val bare = dtype.removePrefix("XOBJ_DTYPE_").removePrefix("UNICODE_")
                return when (bare) {
                    in arrayDtypes -> Kind.ARRAY
                    in intDtypes -> Kind.INT
                    in textDtypes -> Kind.TEXT
                    else -> null
                }
            }

            /** The declared type of parameter [index] of the resolved callee, when it decides the kind. */
            private fun parameterKind(call: FunctionReference?, index: Int): Kind? {
                val fn = call?.resolve() as? Function ?: return null
                val param = fn.parameters.getOrNull(index) ?: return null
                if (param.isPassByRef) return null
                val types = param.declaredType.types.map { it.trimStart('\\').lowercase() }.filter { it != "null" }
                if (types.isEmpty()) return null
                return when {
                    types.all { it == "array" || it == "iterable" } -> Kind.ARRAY
                    types.all { it == "int" } -> Kind.INT
                    types.all { it == "string" } -> Kind.TEXT
                    else -> null
                }
            }

            /**
             * `$v = $_POST['k'];` — every later use of `$v` in the same scope is read first; one use that cannot
             * be classified (an untyped or by-reference argument, `compact()`, `global`, a rebinding, a hand-over
             * to another variable, a value in an array literal) leaves the read without a fix. Only then does one
             * array use decide ARRAY, identifier-only uses STRING, and any text use TEXT.
             */
            private fun traceVariable(variable: Variable, assignment: AssignmentExpression): Kind? {
                val fn = Php.enclosingFunction(assignment)
                val scope: PsiElement = fn ?: assignment.containingFile
                val name = variable.name
                // compact('v') captures the variable by name; a `${'v'}` / `$$` read does too.
                val captured = PsiTreeUtil.findChildrenOfType(scope, StringLiteralExpression::class.java).any { lit ->
                    lit.contents == name && Php.enclosingFunction(lit) == fn &&
                        ((lit.parent as? ParameterList)?.parent?.let { it is FunctionReference && Php.isCall(it, "compact", "extract") } == true || lit.parent is Variable)
                }
                if (captured) return null
                if (PsiTreeUtil.findChildrenOfType(scope, Global::class.java).any { g -> g.text.contains("\$$name") }) return null
                val uses = PsiTreeUtil.findChildrenOfType(scope, Variable::class.java)
                    .filter { it.name == name && it != variable && Php.enclosingFunction(it) == fn }
                if (uses.any { it.textRange.startOffset < assignment.textRange.startOffset }) return null // used before (loop-carried or earlier binding)
                if (uses.isEmpty()) return null
                var array = false
                var text = false
                var identifier = false
                for (use in uses) {
                    var n: PsiElement = use
                    while (n.parent is ParenthesizedExpression) n = n.parent
                    val parent = n.parent
                    when {
                        parent is AssignmentExpression && parent.variable == n -> return null // rebound
                        parent is SelfAssignmentExpression && parent.variable == n -> return null
                        parent is ForeachStatement && parent.array != n -> return null // loop variable rebinding
                        parent is MultiassignmentExpression -> return null
                        parent is PhpIsset || parent is PhpEmpty || parent is PhpUnset -> Unit
                        parent is ForeachStatement -> if (byRefIteration(parent)) return null else array = true
                        parent is ArrayAccessExpression && parent.value == n -> {
                            // `$v[0]` may be a string offset; `$v[] = …` or `$v['k'] = …` is a write through: give up.
                            var outer: PsiElement = parent
                            while (outer.parent is ArrayAccessExpression) outer = outer.parent
                            if (outer.parent is AssignmentExpression && (outer.parent as AssignmentExpression).variable == outer) return null
                            return null
                        }
                        parent is ArrayAccessExpression && parent.index?.value == n -> identifier = true
                        parent is PhpSwitch && parent.argument == n -> identifier = true
                        parent is UnaryExpression && parent.operation?.text?.replace(" ", "") == "(array)" -> array = true
                        parent is UnaryExpression && parent.operation?.text?.replace(" ", "")?.matches(Regex("\\((int|integer|float|double|bool|boolean)\\)")) == true -> identifier = true
                        parent is UnaryExpression && parent.operation?.text == "&" -> return null
                        parent is UnaryExpression -> return null // `!$v`: an empty array is falsy, "Array" is not
                        parent is ConcatenationExpression || (parent is BinaryExpression && parent.operation?.text == ".") -> text = true
                        parent is StringLiteralExpression || parent is PhpEchoStatement || parent is PhpPrintExpression -> text = true
                        parent is BinaryExpression -> {
                            val op = parent.operation?.text
                            val other = if (parent.leftOperand == n) parent.rightOperand else parent.leftOperand
                            if (op in setOf("==", "===", "!=", "!==", "<>") && isIdentifierLiteral(other)) identifier = true else return null
                        }
                        parent is ParameterList -> {
                            val call = parent.parent
                            val index = parent.parameters.indexOf(n)
                            val arity = parent.parameters.size
                            val fname = (call as? FunctionReference)?.takeIf { it !is MethodReference }?.name?.lowercase()
                            when {
                                fname != null && fname in byRefArrayFunctions -> array = true
                                fname != null && arrayPositions.containsKey(fname) -> when {
                                    isNeedle(fname, index, arity) -> identifier = true
                                    isArrayPosition(fname, index, arity) -> array = true
                                    else -> return null
                                }
                                fname != null && fname in identifierFunctions -> identifier = true
                                fname != null && stringPositions[fname] != null -> if (index in stringPositions[fname]!!) text = true else return null
                                call is MethodReference && call.name == "setVar" && index == 1 -> when (dtypeKind(call)) {
                                    Kind.ARRAY -> array = true
                                    Kind.TEXT -> text = true
                                    Kind.INT, Kind.IDENTIFIER -> identifier = true
                                    null -> return null
                                }
                                else -> when (parameterKind(call as? FunctionReference, index)) {
                                    Kind.ARRAY -> array = true
                                    Kind.TEXT -> text = true
                                    Kind.INT, Kind.IDENTIFIER -> identifier = true
                                    null -> return null
                                }
                            }
                        }
                        else -> return null
                    }
                }
                return when {
                    array && !text && !identifier -> Kind.ARRAY
                    array -> null // mixed array and scalar use: no single accessor
                    text -> Kind.TEXT
                    identifier -> Kind.IDENTIFIER
                    else -> null
                }
            }
        }
    }

    companion object {
        val sources = setOf("_GET", "_POST", "_COOKIE", "_REQUEST")

        /** The `Xmf\Request` hash name: `$_REQUEST` is the library's `'default'` hash. */
        fun hashOf(variableName: String): String = if (variableName == "_REQUEST") "default" else variableName.removePrefix("_")

        fun literalKey(access: ArrayAccessExpression): String? = Rewrite.literalKey(access)

        /** `Request` when `use Xmf\Request;` is in scope for this element's namespace, else the FQN. */
        fun requestClass(context: PsiElement): String {
            val namespace = PsiTreeUtil.getParentOfType(context, PhpNamespace::class.java)
            val scope: PsiElement = namespace ?: context.containingFile
            val imported = PsiTreeUtil.findChildrenOfType(scope, PhpUseList::class.java)
                .filter { PsiTreeUtil.getParentOfType(it, PhpNamespace::class.java) == namespace }
                .flatMap { it.declarations.toList() }
                .any { it.aliasName == null && it.targetReference?.fqn?.trimStart('\\').equals("Xmf\\Request", true) }
            return if (imported) "Request" else "\\Xmf\\Request"
        }
    }
}

/**
 * Three batch families: the keyed read (whatever typed accessor the use requires), `isset()` / strict
 * null comparison → `hasVar()`, and the raw read (`getVar(…, 'none', MASK_ALLOW_RAW | MASK_NO_TRIM)` /
 * `get(hash, …)`) that keeps the value byte for byte. The exact replacement is the fix's own name.
 */
class ReplaceSuperglobalFix(
    private val key: String,
    private val source: String,
    private val accessor: XoopsSuperglobalInspection.Accessor,
    private val default: String?,
) : XoopsFix() {
    private val hasVar = accessor == XoopsSuperglobalInspection.Accessor.HAS_VAR
    private val raw = accessor == XoopsSuperglobalInspection.Accessor.RAW || accessor == XoopsSuperglobalInspection.Accessor.WHOLE

    override fun getFamilyName(): String = when {
        hasVar -> "Replace isset(\$_X['key']) / null comparison with Request::hasVar()"
        raw -> "Read through Xmf\\Request keeping the raw value (getVar … MASK_ALLOW_RAW | MASK_NO_TRIM)"
        else -> "Replace the raw \$_X['key'] read with a typed Xmf\\Request accessor"
    }

    override fun getName(): String = when (accessor) {
        XoopsSuperglobalInspection.Accessor.HAS_VAR -> "Replace with Request::hasVar($key, '$source')"
        XoopsSuperglobalInspection.Accessor.RAW -> "Replace with Request::getVar($key, null, '$source', 'none', MASK_ALLOW_RAW | MASK_NO_TRIM)"
        XoopsSuperglobalInspection.Accessor.WHOLE -> "Replace with Request::get('$source', MASK_ALLOW_RAW | MASK_NO_TRIM)"
        else -> "Replace with Request::${accessor.method}('$key', ${default ?: accessor.default}, '$source')"
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val variable = descriptor.psiElement as? Variable ?: return
        val liveSource = variable.name?.let(XoopsSuperglobalInspection::hashOf) ?: return
        if (liveSource != source) return
        val cls = XoopsSuperglobalInspection.requestClass(variable)
        val mask = "$cls::MASK_ALLOW_RAW | $cls::MASK_NO_TRIM"
        if (accessor == XoopsSuperglobalInspection.Accessor.WHOLE) {
            if (variable.parent is ArrayAccessExpression && (variable.parent as ArrayAccessExpression).value == variable) return
            if (!XoopsSuperglobalInspection.Rewrite.bareReadable(variable)) return
            Edits.replace(project, variable, "$cls::get('$liveSource', $mask)")
            return
        }
        val access = variable.parent as? ArrayAccessExpression ?: return
        if (accessor == XoopsSuperglobalInspection.Accessor.RAW) {
            val liveKey = access.index?.value?.text ?: return
            if (liveKey != key || !XoopsSuperglobalInspection.Rewrite.rawReadable(access)) return
            Edits.replace(project, access, "$cls::getVar($liveKey, null, '$liveSource', 'none', $mask)")
            return
        }
        // Everything else is re-derived from the live tree; a descriptor that no longer matches it does nothing.
        if (hasVar) {
            val liveKey = access.index?.value?.text ?: return
            if (liveKey != key) return
            var child: PsiElement = access
            var p: PsiElement? = access.parent
            while (p is ParenthesizedExpression) { child = p; p = p.parent }
            if (p is PhpIsset) {
                // `isset($_POST['a'], $_GET['b'], $x)` is a conjunction: every superglobal term becomes
                // hasVar(), every other term keeps its own isset(); the joined form is parenthesised unless
                // it is a whole if/elseif/while condition.
                val terms = p.variables.map { term ->
                    val e = Php.unwrap(term)
                    val v = (e as? ArrayAccessExpression)?.value as? Variable
                    val idx = (e as? ArrayAccessExpression)?.index?.value?.text
                    if (v != null && v.name in XoopsSuperglobalInspection.sources && idx != null) {
                        "$cls::hasVar($idx, '${XoopsSuperglobalInspection.hashOf(v.name!!)}')"
                    } else {
                        "isset(${term.text})"
                    }
                }
                val joined = terms.joinToString(" && ")
                val condition = p.parent is If || p.parent is ElseIf || p.parent is While || p.parent is DoWhile
                Edits.replace(project, p, if (terms.size > 1 && !condition) "($joined)" else joined)
                return
            }
            if (p is BinaryExpression) {
                val op = p.operation?.text
                val other = if (p.leftOperand == child) p.rightOperand else p.leftOperand
                if (Php.unwrap(other)?.text?.equals("null", true) != true) return
                when (op) {
                    "!==" -> Edits.replace(project, p, "$cls::hasVar($liveKey, '$liveSource')")
                    "===" -> Edits.replace(project, p, "!$cls::hasVar($liveKey, '$liveSource')")
                }
            }
            return
        }
        val liveKey = XoopsSuperglobalInspection.literalKey(access) ?: return
        if (liveKey != key) return
        val escapedKey = liveKey.replace("\\", "\\\\").replace("'", "\\'")
        val plan = XoopsSuperglobalInspection.Rewrite.plan(access) ?: return
        if (plan.accessor != accessor) return
        Edits.replace(project, plan.target, "$cls::${plan.accessor.method}('$escapedKey', ${plan.default ?: plan.accessor.default}, '$liveSource')")
    }
}

/**
 * `global $xoopsDB, $_POST;` → `global $xoopsDB;` — a superglobal is in scope everywhere, so the declaration
 * is a no-op. A statement that names only the superglobal is removed with its line.
 */
class RemoveSuperglobalFromGlobalFix(private val name: String) : XoopsFix() {
    override fun getFamilyName(): String = "Remove the superglobal from the global statement (always in scope)"
    override fun getName(): String = "Remove \$$name from the global statement"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val variable = descriptor.psiElement as? Variable ?: return
        if (variable.name != name) return
        val global = variable.parent as? Global ?: return
        val vars = global.variables
        val index = vars.indexOf(variable)
        if (index < 0) return
        if (vars.size == 1) {
            // PhpStorm's Global element starts at the first variable; the `global` keyword is a previous sibling.
            var keyword: PsiElement? = global.prevSibling
            while (keyword != null && keyword.text != "global") keyword = keyword.prevSibling
            Edits.deleteLineRange(project, global, keyword?.textRange?.startOffset ?: global.textRange.startOffset, global.textRange.endOffset)
            return
        }
        // Take the following separator with the variable, or the preceding one for the last variable.
        val start = if (index < vars.lastIndex) variable.textRange.startOffset else vars[index - 1].textRange.endOffset
        val end = if (index < vars.lastIndex) vars[index + 1].textRange.startOffset else variable.textRange.endOffset
        Edits.deleteRange(project, global, start, end)
    }
}
