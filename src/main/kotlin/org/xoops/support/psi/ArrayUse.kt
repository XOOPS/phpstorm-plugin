package org.xoops.support.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.ArrayAccessExpression
import com.jetbrains.php.lang.psi.elements.AssignmentExpression
import com.jetbrains.php.lang.psi.elements.BinaryExpression
import com.jetbrains.php.lang.psi.elements.ControlStatement
import com.jetbrains.php.lang.psi.elements.ForeachStatement
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.ParenthesizedExpression
import com.jetbrains.php.lang.psi.elements.PhpEmpty
import com.jetbrains.php.lang.psi.elements.PhpIsset
import com.jetbrains.php.lang.psi.elements.PhpUnset
import com.jetbrains.php.lang.psi.elements.TernaryExpression
import com.jetbrains.php.lang.psi.elements.UnaryExpression
import com.jetbrains.php.lang.psi.elements.Variable

/**
 * Proves that a value is only ever used as an array. Used by the unserialize() fix: a serialized
 * `CriteriaCompo` handed back as `__PHP_Incomplete_Class` is a runtime failure, so
 * `['allowed_classes' => false]` is offered only where every use is array-shaped.
 *
 * ARRAY: `foreach`, `[...]`, `(array)`, an `array_*` / `count()` / `in_array()` / `implode()` argument.
 * NEUTRAL: a truthiness test, `isset`/`empty`, a comparison with `false` / `null` / `[]`.
 * UNKNOWN: everything else — `->`, `instanceof`, a return, a field, an array element, another call.
 */
object ArrayUse {
    private val arrayFunctions = setOf(
        "is_array", "count", "sizeof", "in_array", "implode", "join", "array_key_exists", "key_exists",
        "extract", "ksort", "sort", "asort", "arsort", "krsort", "usort", "uasort", "uksort", "shuffle",
        "json_encode", "var_export", "print_r", "serialize", "iterator_to_array", "max", "min", "reset", "end",
        "current", "key", "next", "prev", "each", "compact", "range",
    )
    private val neutralLiterals = setOf("false", "null", "[]", "array()", "true")

    enum class Kind { ARRAY, NEUTRAL, UNKNOWN }

    /** Every use of [value] is array-shaped and at least one such use exists. */
    fun provable(value: PsiElement): Boolean {
        val kinds = ArrayList<Kind>()
        classify(value, kinds, 0)
        return kinds.any { it == Kind.ARRAY } && kinds.none { it == Kind.UNKNOWN }
    }

    private fun classify(node: PsiElement, out: MutableList<Kind>, depth: Int) {
        var child: PsiElement = node
        var p: PsiElement? = node.parent
        while (p is ParenthesizedExpression) { child = p; p = p.parent }
        when {
            p is ForeachStatement && p.array == child -> out += Kind.ARRAY
            p is ArrayAccessExpression && p.value == child -> out += Kind.ARRAY
            p is UnaryExpression && p.operation?.text?.replace(" ", "").equals("(array)", true) -> out += Kind.ARRAY
            p is UnaryExpression && p.operation?.text == "!" -> out += Kind.NEUTRAL
            p is ParameterList -> {
                val call = p.parent
                val name = (call as? FunctionReference)?.name?.lowercase()
                out += if (call is FunctionReference && call !is MethodReference &&
                    (name in arrayFunctions || name?.startsWith("array_") == true)
                ) Kind.ARRAY else Kind.UNKNOWN
            }
            p is PhpIsset || p is PhpEmpty || p is PhpUnset -> out += Kind.NEUTRAL
            p is BinaryExpression && p.operation?.text in setOf("===", "!==", "==", "!=", "<>") -> {
                val other = Php.unwrap(if (p.leftOperand == child) p.rightOperand else p.leftOperand)
                out += if (other?.text?.lowercase() in neutralLiterals) Kind.NEUTRAL else Kind.UNKNOWN
            }
            p is BinaryExpression && p.operation?.text in setOf("&&", "||", "and", "or", "xor") -> out += Kind.NEUTRAL
            // `$v ?? []` / `$v ?: []`: the value flows on through the whole expression.
            p is BinaryExpression && p.operation?.text == "??" && p.leftOperand == child -> classify(p, out, depth)
            p is TernaryExpression && p.condition == child -> {
                if (p.trueVariant == null) classify(p, out, depth) else out += Kind.NEUTRAL
            }
            p is ControlStatement && p.condition == child -> out += Kind.NEUTRAL
            p is AssignmentExpression && p.value == child -> {
                val target = Php.unwrap(p.variable) as? Variable
                if (target == null || depth > 0) { out += Kind.UNKNOWN; return }
                val uses = laterUses(target, p)
                if (uses.isEmpty()) out += Kind.UNKNOWN
                for (use in uses) classify(use, out, depth + 1)
            }
            else -> out += Kind.UNKNOWN
        }
    }

    /** Reads of [target]'s name after [assignment] in the same function (a later re-assignment target is skipped). */
    private fun laterUses(target: Variable, assignment: AssignmentExpression): List<Variable> {
        val fn = Php.enclosingFunction(assignment)
        val scope: PsiElement = fn ?: assignment.containingFile
        val after = assignment.textRange.endOffset
        return PsiTreeUtil.findChildrenOfType(scope, Variable::class.java).filter { v ->
            v.name == target.name && v.textRange.startOffset >= after && Php.enclosingFunction(v) == fn &&
                !((v.parent as? AssignmentExpression)?.variable == v)
        }
    }
}
