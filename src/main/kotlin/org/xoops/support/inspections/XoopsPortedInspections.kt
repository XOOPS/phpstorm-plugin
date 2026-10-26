package org.xoops.support.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.ArrayAccessExpression
import com.jetbrains.php.lang.psi.elements.AssignmentExpression
import com.jetbrains.php.lang.psi.elements.ClassReference
import com.jetbrains.php.lang.psi.elements.ConcatenationExpression
import com.jetbrains.php.lang.psi.elements.ConstantReference
import com.jetbrains.php.lang.psi.elements.Declare
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.GroupStatement
import com.jetbrains.php.lang.psi.elements.Include
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.ParenthesizedExpression
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.Variable
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor
import org.xoops.support.fix.Edits
import org.xoops.support.fix.XoopsFix
import org.xoops.support.psi.Php
import org.xoops.support.psi.XoopsPaths

/*
 * Rules ported from the Grok / Gemini catalogue after the 16-module run, each limited to the
 * form where the finding is a real defect and the fix is a pure text edit whose result is
 * fixed by the source.
 */

/**
 * `redirect_header('x.php?a=1&amp;b=2')`, `header('Location: …&amp;…')`, `addItemButton(…, 'x.php?op=a&amp;b=c')`:
 * these APIs take a raw URL (`renderButton()` escapes it, `header()` sends it), so the literal
 * `&amp;` reaches the server as a parameter named `amp;b` and every token check fails.
 * `redirect_header()` is the exception that undoes it: there the finding is a weak convention note.
 */
class XoopsDoubleEncodedAmpInspection : XoopsInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            override fun visitPhpStringLiteralExpression(literal: StringLiteralExpression) {
                if (!literal.text.contains("&amp;") || skip(literal.containingFile)) return
                val call = rawUrlCall(literal) ?: return
                if (call == "redirect_header" || call == "redirect") {
                    // redirect_header() escapes the URL and turns &amp;amp; back into &amp; for its meta refresh
                    // (functions.php, 2.5 and 2.7 alike), so the browser requests the same URL either way —
                    // verified on 2.7.4. The raw & is still the API's contract; the rewrite is identical output.
                    holder.registerProblem(
                        literal,
                        "XOOPS: $call() takes a raw URL — write & (the &amp; only works because redirect_header() decodes it again)",
                        ProblemHighlightType.WEAK_WARNING,
                        ReplaceAmpFix(),
                    )
                    return
                }
                holder.registerProblem(
                    literal,
                    "XOOPS: $call() takes a raw URL — a literal &amp; becomes the parameter 'amp;…' (the token check fails)",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    ReplaceAmpFix(),
                )
            }
        }
    }

    companion object {
        /** The raw-URL API this literal is a direct (possibly concatenated) argument of, or null. */
        fun rawUrlCall(literal: StringLiteralExpression): String? {
            var e: PsiElement = literal
            while (e.parent is ConcatenationExpression || e.parent is ParenthesizedExpression) e = e.parent
            val list = e.parent as? ParameterList ?: return null
            val call = list.parent
            val index = list.parameters.indexOf(e)
            val firstLiteral = (e as? StringLiteralExpression) ?: PsiTreeUtil.findChildOfType(e, StringLiteralExpression::class.java)
            val isLocation = firstLiteral?.contents?.trimStart()?.startsWith("location:", ignoreCase = true) == true
            return when {
                call is MethodReference && call.name == "addItemButton" && index == 1 -> call.name
                call is MethodReference && call.name == "redirect" && index == 0 -> call.name
                call is FunctionReference && call !is MethodReference && call.name == "redirect_header" && index == 0 -> call.name
                call is FunctionReference && call !is MethodReference && call.name == "header" && index == 0 && isLocation -> call.name
                else -> null
            }
        }
    }
}

class ReplaceAmpFix : XoopsFix() {
    override fun getFamilyName(): String = "Replace &amp; with & in the URL"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val literal = descriptor.psiElement as? StringLiteralExpression ?: return
        if (XoopsDoubleEncodedAmpInspection.rawUrlCall(literal) == null) return
        Edits.replace(project, literal, literal.text.replace("&amp;", "&"))
    }
}

/**
 * Under `declare(strict_types=1)`, `mktime(0, 0, 0, date('n'), 1, date('Y'))` is a TypeError on
 * every call: `date()` returns a string and the calendar functions take `int`. The cast is the
 * fix PHP itself suggests; `(int)` on a numeric string is exact.
 */
class XoopsStrictCalendarCastInspection : XoopsInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            override fun visitPhpFunctionCall(reference: FunctionReference) {
                if (reference is MethodReference || skip(reference.containingFile)) return
                if (!Php.isCall(reference, *calendar)) return
                if (!isStrict(reference.containingFile)) return
                for (param in reference.parameters) {
                    val u = Php.unwrap(param) ?: continue
                    if (!returnsNumericString(u)) continue
                    holder.registerProblem(
                        param,
                        "XOOPS: strict_types — ${reference.name}() takes int and this argument is a string; cast it with (int)",
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        CastIntFix(),
                    )
                }
            }
        }
    }

    companion object {
        val calendar = arrayOf("mktime", "gmmktime", "checkdate", "cal_days_in_month", "jdtogregorian", "gregoriantojd")
        private val stringProducers = setOf("date", "gmdate", "substr", "mb_substr", "strftime", "gmstrftime", "str_pad", "trim", "sprintf", "number_format", "strval")

        fun isStrict(file: PsiFile): Boolean =
            PsiTreeUtil.findChildrenOfType(file, Declare::class.java).any { it.text.replace(" ", "").contains("strict_types=1") }

        fun returnsNumericString(e: PsiElement): Boolean {
            if (e is FunctionReference && e !is MethodReference && e.name?.lowercase() in stringProducers) return true
            if (e is MethodReference && e.name in setOf("getString", "getCmd") && (Php.unwrap(e.classReference) as? ClassReference)?.name == "Request") return true
            if (e is ArrayAccessExpression && (e.value as? Variable)?.name in XoopsSuperglobalInspection.sources) return true
            return false
        }
    }
}

class CastIntFix : XoopsFix() {
    override fun getFamilyName(): String = "Cast the calendar argument with (int)"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val param = descriptor.psiElement ?: return
        val u = Php.unwrap(param) ?: return
        if (!XoopsStrictCalendarCastInspection.returnsNumericString(u)) return
        Edits.replace(project, param, "(int) ${param.text}")
    }
}

/**
 * `_AM_<DIRNAME>_X` / `_MD_<DIRNAME>_X` / `_MI_…` / `_MB_…` / `_CO_…` referenced in the module but
 * `define()`d in none of its language files: PHP 8 throws `Error: Undefined constant`, so the
 * page dies where PHP 7 printed the constant's name. Reported without a fix — the translation
 * has to be written.
 */
class XoopsLocaleConstantInspection : XoopsInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            override fun visitPhpConstantReference(constantReference: ConstantReference) {
                val file = constantReference.containingFile
                if (skipConvention(file) || XoopsPaths.isLanguageFile(file)) return
                val name = constantReference.name ?: return
                val m = prefix.matchEntire(name) ?: return
                val root = XoopsPaths.moduleRoot(file.virtualFile) ?: return
                if (!m.groupValues[2].equals(root.name.replace("-", "_"), true)) return
                if (PhpIndex.getInstance(constantReference.project).getConstantsByName(name).isNotEmpty()) return
                if (guardedByDefined(constantReference, name)) return
                holder.registerProblem(
                    constantReference as PsiElement,
                    "XOOPS: $name is defined in no language file of this module — a bare read is fatal on PHP 8",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                )
            }
        }
    }

    companion object {
        val prefix = Regex("^_(AM|MD|MI|MB|CO|AD)_([A-Z0-9]+)_[A-Z0-9_]+$")

        /** `defined('X') ? X : …`, `if (defined('X')) …`, `defined('X') || define('X', …)`: a deliberate fallback. */
        fun guardedByDefined(element: PsiElement, name: String): Boolean {
            val statement = Php.statementInBlock(element) ?: return false
            var scope: PsiElement? = statement
            while (scope != null && scope !is PsiFile) {
                val text = scope.text
                if (text.contains("defined('$name')") || text.contains("defined(\"$name\")")) return true
                scope = scope.parent
            }
            return false
        }
    }
}

/**
 * The same constant `define()`d twice in one language file: PHP warns "Constant already defined"
 * on every request and keeps the first value. An identical duplicate is deleted; a conflicting
 * one is reported for a human to choose.
 */
class XoopsDuplicateDefineInspection : XoopsInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            override fun visitPhpFile(file: PhpFile) {
                if (skipConvention(file) || !XoopsPaths.isLanguageFile(file)) return
                val seen = HashMap<String, FunctionReference>()
                val topLevel = Php.executableTopLevel(file).toSet()
                // PhpStorm wraps `define(...)` in a PhpDefine node inside the Statement; unconditional means the
                // statement's block is the file body, not an if / else / loop body.
                fun standalone(c: FunctionReference): Boolean {
                    val st = Php.statementInBlock(c) ?: return false
                    val own = st.firstChild == c || st.firstChild == c.parent
                    val block = st.parent
                    return own && block is GroupStatement && block.parent is PhpFile
                }
                fun literalValue(c: FunctionReference): Boolean {
                    val v = Php.unwrap(c.parameters.getOrNull(1)) ?: return false
                    return v is StringLiteralExpression || v is ConstantReference || v.text.matches(Regex("-?\\d+(\\.\\d+)?")) ||
                        v.text.equals("true", true) || v.text.equals("false", true) || v.text.equals("null", true)
                }
                for (call in Php.calls(file, "define")) {
                    if (call is MethodReference || Php.enclosingFunction(call) != null) continue
                    val name = Php.firstStringArg(call) ?: continue
                    val first = seen.putIfAbsent(name, call) ?: continue
                    val sameValue = call.parameters.getOrNull(1)?.text == first.parameters.getOrNull(1)?.text
                    val deletable = sameValue && standalone(call) && standalone(first) && literalValue(call)
                    if (deletable) {
                        holder.registerProblem(
                            call,
                            "XOOPS: $name is defined twice with the same value — PHP warns 'Constant already defined' on every request",
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                            DeleteStatementFix("Remove the duplicate define()", batchSafe = true),
                        )
                    } else {
                        holder.registerProblem(
                            call as PsiElement,
                            if (sameValue) "XOOPS: $name is defined twice — PHP warns 'Constant already defined' on every request (conditional or non-literal: not deleted automatically)"
                            else "XOOPS: $name is defined twice with different values — the first definition wins and PHP warns on every request",
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        )
                    }
                }
            }
        }
    }
}

/**
 * `define('THEME_…')` in a module language file: the `THEME_*` constants belong to the theme's
 * own language pack, so whichever loads second warns "Constant already defined" and the theme's
 * text loses. Reported only.
 */
class XoopsThemeConstantInspection : XoopsInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            override fun visitPhpFunctionCall(reference: FunctionReference) {
                if (reference is MethodReference || !Php.isCall(reference, "define")) return
                val file = reference.containingFile
                if (skipConvention(file) || !XoopsPaths.isLanguageFile(file) || !XoopsPaths.path(file).contains("/modules/")) return
                val name = Php.firstStringArg(reference) ?: return
                if (!name.startsWith("THEME_")) return
                if (XoopsLocaleConstantInspection.guardedByDefined(reference, name)) return
                holder.registerProblem(
                    reference as PsiElement,
                    "XOOPS: $name belongs to the theme's language pack — a module define() makes the theme warn and lose its text",
                    ProblemHighlightType.WEAK_WARNING,
                )
            }
        }
    }
}

/**
 * `admin/menu.php` is read by the system module's menu builder before the module's own
 * bootstrap ran: a `XoopsModules\<Module>\Helper::getInstance()` there is "Class not found"
 * unless the file requires `preloads/autoloader.php` first.
 */
class XoopsMenuAutoloadInspection : XoopsInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            override fun visitPhpFile(file: PhpFile) {
                if (skipConvention(file) || !XoopsPaths.path(file).endsWith("/admin/menu.php")) return
                val statements = Php.executableTopLevel(file)
                val firstInclude = statements.firstOrNull { it is Include || PsiTreeUtil.findChildOfType(it, Include::class.java, false) != null }
                val reference = PsiTreeUtil.findChildrenOfType(file, ClassReference::class.java)
                    .firstOrNull { (it.fqn ?: "").trimStart('\\').startsWith("XoopsModules\\", true) && loadsTheClass(it) }
                    ?: return
                if (firstInclude != null && firstInclude.textRange.startOffset < reference.textRange.startOffset) return
                val statement = Php.statementInBlock(reference)
                val fixable = statement != null && Php.enclosingFunction(reference) == null &&
                    XoopsPaths.moduleRoot(file.virtualFile)?.findFileByRelativePath("preloads/autoloader.php") != null
                holder.registerProblem(
                    reference,
                    "XOOPS: admin/menu.php uses a XoopsModules class before the module autoloader is loaded — 'Class not found' in the system menu",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    *(if (fixable) arrayOf<LocalQuickFix>(InsertAutoloaderFix()) else emptyArray()),
                )
            }
        }
    }
}

/** `new X`, `X::method()`, `X::CONST` load the class; `X::class`, `use X`, a type hint or `instanceof` do not. */
private fun loadsTheClass(reference: ClassReference): Boolean {
    val p = reference.parent
    return p is MethodReference || p is com.jetbrains.php.lang.psi.elements.NewExpression ||
        (p is com.jetbrains.php.lang.psi.elements.ClassConstantReference && p.name != "class") ||
        p is com.jetbrains.php.lang.psi.elements.FieldReference
}

class InsertAutoloaderFix : XoopsFix() {
    override fun getFamilyName(): String = "Require preloads/autoloader.php before the first class use"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val reference = descriptor.psiElement ?: return
        val statement = Php.statementInBlock(reference) ?: return
        if (Php.enclosingFunction(reference) != null) return
        if (statement.containingFile.text.contains("autoloader.php")) return
        Edits.insertLinesBefore(project, Php.withLeadingComments(statement), "require_once \\dirname(__DIR__) . '/preloads/autoloader.php';\n")
    }
}

/**
 * A handler that extends `XoopsPersistableObjectHandler` / `XoopsObjectHandler` must declare
 * `&getObjects(…)` (the kernel returns by reference) or PHP refuses to load the class:
 * "Declaration … must be compatible with & XoopsPersistableObjectHandler::getObjects(…)".
 * Reported only — adding `&` also requires `return $var;`, not `return [];`.
 */
class XoopsHandlerSignatureInspection : XoopsInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            override fun visitPhpClass(clazz: PhpClass) {
                if (skipConvention(clazz.containingFile)) return
                val parent = clazz.extendsList.referenceElements.firstOrNull()?.name ?: return
                if (parent != "XoopsPersistableObjectHandler") return
                for (method in clazz.ownMethods) {
                    if (!method.name.equals("getObjects", true) && !method.name.equals("getAll", true)) continue
                    val header = method.text.substringBefore("(")
                    if (header.contains("&")) continue
                    holder.registerProblem(
                        method.nameIdentifier ?: method,
                        "XOOPS: ${method.name}() must be declared &${method.name}() — the kernel returns by reference and PHP refuses an incompatible override at class load",
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    )
                }
            }
        }
    }
}

/**
 * A request value concatenated into SQL that reaches `query()` / `exec()` / `queryF()`:
 * `$_GET['id']`, `$_POST[...]`, `$_REQUEST[...]` or `Request::getString()` with no `quote()`,
 * `(int)` or `intval()` around it. Reported only — the right fix (quote, cast or allow-list)
 * depends on the column.
 */
class XoopsSqlConcatInspection : XoopsInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            override fun visitPhpMethodReference(reference: MethodReference) {
                if (skip(reference.containingFile)) return
                if (!Php.isCall(reference, "query", "exec", "queryF") || !Php.isDatabaseCall(reference)) return
                val sql = Php.unwrap(reference.parameters.firstOrNull()) ?: return
                val expression: PsiElement = when (sql) {
                    is Variable -> assignmentOf(sql, reference) ?: return
                    else -> sql
                }
                for (operand in operands(expression)) {
                    val raw = rawRequestValue(operand) ?: continue
                    holder.registerProblem(
                        raw,
                        "XOOPS: request value concatenated into SQL — quote() a string, cast an id with (int), allow-list an identifier",
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    )
                }
            }
        }
    }

    companion object {
        private val accessors = setOf("getString", "getText", "getVar", "getCmd", "getWord", "getUrl", "getEmail")

        /** The value assigned to `$sql` in the statements before the call, within the same block. */
        fun assignmentOf(variable: Variable, call: MethodReference): PsiElement? {
            val statement = Php.statementInBlock(call) ?: return null
            for (prev in Php.previousStatements(statement).take(25)) {
                val a = PsiTreeUtil.findChildOfType(prev, AssignmentExpression::class.java, false) ?: (prev.firstChild as? AssignmentExpression) ?: continue
                if (Php.variableName(a.variable) == variable.name) return a.value
            }
            return null
        }

        fun operands(e: PsiElement): List<PsiElement> {
            val u = Php.unwrap(e) ?: return emptyList()
            if (u is ConcatenationExpression) return operands(u.leftOperand ?: return emptyList()) + operands(u.rightOperand ?: return emptyList())
            return listOf(u)
        }

        /** The raw request read inside [operand], unless something between them quotes or casts it. */
        fun rawRequestValue(operand: PsiElement): PsiElement? {
            val candidates = ArrayList<PsiElement>()
            if (operand is StringLiteralExpression) {
                candidates += PsiTreeUtil.findChildrenOfType(operand, Variable::class.java).filter { it.name in XoopsSuperglobalInspection.sources || it.name == "_REQUEST" }
                    .map { it.parent as? ArrayAccessExpression ?: it }
            } else {
                PsiTreeUtil.findChildrenOfType(operand, ArrayAccessExpression::class.java).plus(listOfNotNull(operand as? ArrayAccessExpression))
                    .filter { (it.value as? Variable)?.name.let { n -> n in XoopsSuperglobalInspection.sources || n == "_REQUEST" } }
                    .forEach(candidates::add)
                PsiTreeUtil.findChildrenOfType(operand, MethodReference::class.java).plus(listOfNotNull(operand as? MethodReference))
                    .filter { it.name in accessors && (Php.unwrap(it.classReference) as? ClassReference)?.name == "Request" }
                    .forEach(candidates::add)
            }
            for (c in candidates) {
                var p: PsiElement? = c.parent
                var safe = false
                while (p != null && p != operand.parent) {
                    val text = p.text
                    if (p is FunctionReference && Php.isCall(p, "intval", "floatval", "escape", "addslashes", "count", "sizeof", "strlen", "mb_strlen", "abs", "round", "floor", "ceil", "max", "min")) safe = true
                    if (p is MethodReference && Php.isCall(p, "quote", "quoteString", "escape", "prefix")) safe = true
                    if (p is com.jetbrains.php.lang.psi.elements.UnaryExpression && text.startsWith("(int)") || text.startsWith("(float)")) safe = true
                    p = p.parent
                }
                if (!safe) return c
            }
            return null
        }
    }
}
