package org.xoops.support.psi

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.AssignmentExpression
import com.jetbrains.php.lang.psi.elements.BinaryExpression
import com.jetbrains.php.lang.psi.elements.ConcatenationExpression
import com.jetbrains.php.lang.psi.elements.ControlStatement
import com.jetbrains.php.lang.psi.elements.Declare
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.GroupStatement
import com.jetbrains.php.lang.psi.elements.If
import com.jetbrains.php.lang.psi.elements.Include
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParenthesizedExpression
import com.jetbrains.php.lang.psi.elements.PhpExit
import com.jetbrains.php.lang.psi.elements.PhpNamespace
import com.jetbrains.php.lang.psi.elements.PhpPsiElement
import com.jetbrains.php.lang.psi.elements.PhpReturn
import com.jetbrains.php.lang.psi.elements.PhpThrow
import com.jetbrains.php.lang.psi.elements.PhpUseList
import com.jetbrains.php.lang.psi.elements.Statement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.UnaryExpression
import com.jetbrains.php.lang.psi.elements.Variable

/**
 * PSI queries every inspection shares. Nothing here scans raw text for code
 * structure; comments, strings and injected fragments are what the parser says
 * they are.
 */
object Php {

    fun isInjected(file: PsiFile): Boolean =
        InjectedLanguageManager.getInstance(file.project).isInjectedFragment(file)

    /** True for the PHP tree of a .php file (PhpStorm also builds an HTML tree for the same file). */
    fun isPrimary(file: PsiFile): Boolean {
        val provider = file.viewProvider
        val base = provider.getPsi(provider.baseLanguage)
        return base == null || file == base
    }

    /**
     * Top-level statements in source order. An unbraced `namespace Foo;` wraps the rest of
     * the file in its own group; that group is flattened so callers see one list.
     */
    fun topLevelStatements(file: PhpFile): List<PsiElement> {
        val out = ArrayList<PsiElement>()
        fun collect(parent: PsiElement) {
            var child = parent.firstChild
            while (child != null) {
                when (child) {
                    is PhpNamespace -> {
                        out.add(child)
                        child.statements?.let { collect(it) }
                    }
                    is GroupStatement -> collect(child)
                    is PsiComment -> Unit
                    is PhpPsiElement -> out.add(child)
                }
                child = child.nextSibling
            }
        }
        collect(file)
        return out
    }

    /** Direct children of the file body: PhpStorm wraps the whole file in one group statement. */
    fun fileBody(file: PhpFile): PsiElement =
        file.firstChild?.takeIf { it is GroupStatement } ?: file

    /** Top-level statements that execute something: not declare, namespace, use. */
    fun executableTopLevel(file: PhpFile): List<PsiElement> =
        topLevelStatements(file).filter { it !is Declare && it !is PhpNamespace && it !is PhpUseList }

    /** Leading file comments between the open tag and the first statement. */
    fun leadingComments(file: PhpFile): List<PsiComment> {
        val out = ArrayList<PsiComment>()
        var child = fileBody(file).firstChild
        while (child != null) {
            when {
                child is PsiComment -> out.add(child)
                child is PsiWhiteSpace -> Unit
                child is PhpPsiElement && child !is Declare -> return out
                child is Declare -> Unit
            }
            child = child.nextSibling
        }
        return out
    }

    /** The statement that owns [element] inside its enclosing block (`{ ... }`, namespace or file). */
    fun statementInBlock(element: PsiElement): Statement? {
        var stmt: Statement? = PsiTreeUtil.getParentOfType(element, Statement::class.java, false)
        while (stmt != null) {
            val parent = stmt.parent
            if (parent is GroupStatement || parent is PhpFile) return stmt
            stmt = PsiTreeUtil.getParentOfType(stmt, Statement::class.java, true)
        }
        return null
    }

    /**
     * Statement anchors move in front of any doc/line comment glued to the statement.
     * A comment that shares its line with earlier code (`$r = query($sql); // TODO`) belongs
     * to that code and is never absorbed: an insertion before it would land mid-line.
     */
    fun withLeadingComments(statement: PsiElement): PsiElement {
        var anchor = statement
        var prev = anchor.prevSibling
        while (prev != null) {
            if (prev is PsiWhiteSpace) {
                if (prev.text.count { it == '\n' } > 1) break
                prev = prev.prevSibling
                continue
            }
            if ((prev is PsiComment || prev is PhpDocComment) && startsItsLine(prev)) {
                anchor = prev
                prev = anchor.prevSibling
                continue
            }
            break
        }
        return anchor
    }

    /** True when only whitespace precedes [element] on its line. */
    fun startsItsLine(element: PsiElement): Boolean {
        val doc = element.containingFile.viewProvider.document ?: return true
        val start = element.textRange.startOffset
        val lineStart = doc.getLineStartOffset(doc.getLineNumber(start))
        return doc.getText(com.intellij.openapi.util.TextRange(lineStart, start)).isBlank()
    }

    // --- database receivers ---------------------------------------------------------------

    private val databaseVariables = setOf("xoopsdb", "db", "xoopsdatabase", "database", "_db", "dbh")
    private val databaseClassNames = listOf("XoopsDatabase", "XoopsMySQLDatabase", "XoopsMySQLDatabaseSafe", "XoopsMySQLDatabaseProxy", "Database")

    /**
     * True when [reference] is a call on a XOOPS database object: `$GLOBALS['xoopsDB']`,
     * `$xoopsDB`, `$db`, `$this->db`, `$handler->db`, or any expression PhpStorm types as a
     * `*Database*` class. `$this->query()` inside an application class is not a database
     * call, however much the method name looks like one.
     */
    private val foreignDatabaseTypes = listOf("PDO", "PDOStatement", "mysqli", "mysqli_result", "wpdb", "Connection", "QueryBuilder", "Capsule", "DB")

    fun isDatabaseReceiver(reference: MethodReference): Boolean {
        val receiver = unwrap(reference.classReference) ?: return false
        // A `$db` PhpStorm knows to be PDO / mysqli / Doctrine is not the XOOPS database, whatever its name.
        if (receiver is com.jetbrains.php.lang.psi.elements.PhpTypedElement) {
            val short = receiver.type.types.map { it.trimStart('\\').substringAfterLast('\\') }
            if (short.any { t -> foreignDatabaseTypes.any { it.equals(t, true) } }) return false
        }
        if (receiver is Variable) {
            if (receiver.name.lowercase() in databaseVariables) return true
        }
        if (receiver is com.jetbrains.php.lang.psi.elements.FieldReference) {
            if (receiver.name?.lowercase() in databaseVariables) return true
        }
        if (receiver is com.jetbrains.php.lang.psi.elements.ArrayAccessExpression) {
            val base = unwrap(receiver.value)
            val key = (receiver.index?.value as? StringLiteralExpression)?.contents
            if (base is Variable && base.name == "GLOBALS" && key?.lowercase() in databaseVariables) return true
        }
        if (receiver is com.jetbrains.php.lang.psi.elements.PhpTypedElement) {
            val types = receiver.type.types
            if (types.any { t -> databaseClassNames.any { c -> t.trimStart('\\').substringAfterLast('\\').equals(c, true) } }) return true
        }
        return false
    }

    /** The enclosing class is itself a database implementation (the rules must not rewrite it). */
    fun inDatabaseClass(element: PsiElement): Boolean {
        val clazz = PsiTreeUtil.getParentOfType(element, com.jetbrains.php.lang.psi.elements.PhpClass::class.java) ?: return false
        val names = listOfNotNull(clazz.name, clazz.superName) + clazz.extendsList.referenceElements.mapNotNull { it.name }
        return names.any { n -> databaseClassNames.any { it.equals(n, true) } || n.endsWith("Database", true) }
    }

    /** Database call gate shared by the DB rules: a real database receiver outside the DB classes. */
    fun isDatabaseCall(reference: MethodReference): Boolean =
        isDatabaseReceiver(reference) && !inDatabaseClass(reference)

    fun previousStatements(statement: PsiElement): List<PsiElement> {
        val out = ArrayList<PsiElement>()
        var prev = statement.prevSibling
        while (prev != null) {
            if (prev is PhpPsiElement) out.add(prev)
            prev = prev.prevSibling
        }
        return out
    }

    fun enclosingFunction(element: PsiElement): Function? =
        PsiTreeUtil.getParentOfType(element, Function::class.java)

    fun isCall(element: PsiElement?, vararg names: String): Boolean =
        element is FunctionReference && element.name?.let { n -> names.any { it.equals(n, true) } } == true

    /** Calls named [names] inside [scope], the scope itself included when it is the call. */
    fun calls(scope: PsiElement, vararg names: String): List<FunctionReference> =
        PsiTreeUtil.collectElementsOfType(scope, FunctionReference::class.java).filter { isCall(it, *names) }

    fun methodCalls(scope: PsiElement, vararg names: String): List<MethodReference> =
        PsiTreeUtil.collectElementsOfType(scope, MethodReference::class.java).filter { isCall(it, *names) }

    fun firstStringArg(call: FunctionReference): String? =
        (call.parameters.firstOrNull() as? StringLiteralExpression)?.contents

    fun unwrap(e: PsiElement?): PsiElement? {
        var x = e
        while (x is ParenthesizedExpression) x = x.argument
        return x
    }

    /** `!expr` once (an odd number of negations). */
    fun isNegated(e: PsiElement): Boolean =
        e is UnaryExpression && e.operation?.text == "!"

    /** True when [call] sits under an odd number of `!` between itself and [root]. */
    fun negatedWithin(call: PsiElement, root: PsiElement): Boolean {
        var count = 0
        var p: PsiElement? = call.parent
        while (p != null && p != root.parent) {
            if (p is UnaryExpression && p.operation?.text == "!") count++
            p = p.parent
        }
        return count % 2 == 1
    }

    /** Operands of an `&&` / `and` chain, flattened. */
    fun andOperands(e: PsiElement?): List<PsiElement> {
        val x = unwrap(e) ?: return emptyList()
        if (x is BinaryExpression) {
            val op = x.operation?.text?.lowercase()
            if (op == "&&" || op == "and") {
                return andOperands(x.leftOperand) + andOperands(x.rightOperand)
            }
        }
        return listOf(x)
    }

    /** Operands of an `||` / `or` chain, flattened. */
    fun orOperands(e: PsiElement?): List<PsiElement> {
        val x = unwrap(e) ?: return emptyList()
        if (x is BinaryExpression) {
            val op = x.operation?.text?.lowercase()
            if (op == "||" || op == "or") {
                return orOperands(x.leftOperand) + orOperands(x.rightOperand)
            }
        }
        return listOf(x)
    }

    /** The branch body always leaves the enclosing function / request. */
    fun terminates(body: PsiElement?): Boolean {
        val last: PsiElement? = when (body) {
            null -> null
            is GroupStatement -> body.statements.lastOrNull()
            else -> body
        }
        if (last == null) return false
        if (last is PhpReturn || last is PhpThrow) return true
        if (PsiTreeUtil.findChildOfType(last, PhpExit::class.java, false) != null) return true
        val firstCall = PsiTreeUtil.findChildOfType(last, FunctionReference::class.java, false) ?: return false
        if (isCall(firstCall, "redirect_header")) return true
        // An unconditional `trigger_error(…, E_USER_ERROR);` statement is fatal: PHP halts, and
        // XoopsLogger::handleError() — registered on every XOOPS request — calls exit().
        val own = (last.firstChild as? FunctionReference) ?: (last as? FunctionReference) ?: return false
        return isCall(own, "trigger_error") && own.parameters.getOrNull(1)?.text?.endsWith("E_USER_ERROR") == true
    }

    /** Operands of the concatenation chain that contains [element], outermost chain, left to right. */
    fun concatOperands(element: PsiElement): Pair<List<PsiElement>, Int>? {
        var top: ConcatenationExpression = PsiTreeUtil.getParentOfType(element, ConcatenationExpression::class.java)
            ?: return null
        while (true) {
            val up = top.parent
            if (up is ConcatenationExpression) top = up
            else if (up is ParenthesizedExpression && up.parent is ConcatenationExpression) top = up.parent as ConcatenationExpression
            else break
        }
        val flat = ArrayList<PsiElement>()
        fun walk(e: PsiElement?) {
            val x = unwrap(e) ?: return
            if (x is ConcatenationExpression) { walk(x.leftOperand); walk(x.rightOperand) } else flat.add(x)
        }
        walk(top)
        val index = flat.indexOfFirst { PsiTreeUtil.isAncestor(it, element, false) }
        return if (index < 0) null else flat to index
    }

    enum class SqlKind { READ, WRITE, UNKNOWN }

    private val readVerbs = setOf("select", "show", "describe", "desc", "explain", "with")
    private val writeVerbs = setOf(
        "insert", "update", "delete", "replace", "alter", "create", "drop", "truncate", "rename",
        "set", "lock", "unlock", "optimize", "repair", "call", "load",
    )

    fun sqlKind(sql: String?): SqlKind {
        if (sql == null) return SqlKind.UNKNOWN
        val verb = sql.trimStart(' ', '\t', '\r', '\n', '(').takeWhile { it.isLetter() }.lowercase()
        return when (verb) {
            in readVerbs -> SqlKind.READ
            in writeVerbs -> SqlKind.WRITE
            else -> SqlKind.UNKNOWN
        }
    }

    /**
     * Leading SQL text of an argument: a literal, the first literal of a concatenation,
     * or the single dominating assignment of a local variable. Anything else is unknown.
     */
    fun leadingSql(arg: PsiElement?, depth: Int = 0): String? {
        val x = unwrap(arg) ?: return null
        if (depth > 3) return null
        return when (x) {
            is StringLiteralExpression -> x.contents
            is ConcatenationExpression -> leadingSql(leftmost(x), depth + 1)
            is FunctionReference -> if (isCall(x, "sprintf")) leadingSql(x.parameters.firstOrNull(), depth + 1) else null
            is Variable -> resolveAssignment(x)?.let { leadingSql(it, depth + 1) }
            else -> null
        }
    }

    private fun leftmost(c: ConcatenationExpression): PsiElement? {
        var e: PsiElement? = c
        while (true) {
            val u = unwrap(e)
            e = if (u is ConcatenationExpression) u.leftOperand else return u
        }
    }

    /**
     * Value of the last assignment to [variable] that precedes it in the same function
     * (or file for top-level code). Null when there is none or when an intervening
     * assignment is conditional (inside a nested block the use is not in).
     */
    fun resolveAssignment(variable: Variable): PsiElement? {
        val name = variable.name
        val scope: PsiElement = enclosingFunction(variable) ?: variable.containingFile
        val use = variable.textRange.startOffset
        var best: AssignmentExpression? = null
        for (a in PsiTreeUtil.findChildrenOfType(scope, AssignmentExpression::class.java)) {
            if (a.textRange.endOffset > use) continue
            val target = a.variable as? Variable ?: continue
            if (target.name != name) continue
            if (enclosingFunction(a) != enclosingFunction(variable)) continue
            if (best == null || a.textRange.startOffset > best.textRange.startOffset) best = a
        }
        return best?.value
    }

    /** True when the include statement references one of the given file names. */
    fun includes(statement: PsiElement, vararg names: String): Boolean {
        val include = PsiTreeUtil.findChildOfType(statement, Include::class.java, false)
            ?: (statement as? Include) ?: return false
        val text = include.text.replace('\\', '/').lowercase()
        return names.any { Regex("(?<![\\w.-])" + Regex.escape(it.lowercase()) + "(?![\\w.-])").containsMatchIn(text) }
    }

    fun variableName(e: PsiElement?): String? = (unwrap(e) as? Variable)?.name

    fun conditionOf(control: PsiElement?): PsiElement? = (control as? ControlStatement)?.condition

    fun isIfWithCondition(e: PsiElement, predicate: (PsiElement) -> Boolean): Boolean =
        e is If && e.condition?.let(predicate) == true

    fun lineIndent(element: PsiElement): String {
        val doc = element.containingFile.viewProvider.document ?: return ""
        val line = doc.getLineNumber(element.textRange.startOffset)
        val lineStart = doc.getLineStartOffset(line)
        val prefix = doc.getText(com.intellij.openapi.util.TextRange(lineStart, element.textRange.startOffset))
        return prefix.takeWhile { it == ' ' || it == '\t' }
    }
}
