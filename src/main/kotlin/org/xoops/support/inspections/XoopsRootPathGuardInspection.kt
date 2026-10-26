package org.xoops.support.inspections

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.Declare
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.GroupStatement
import com.jetbrains.php.lang.psi.elements.Include
import com.jetbrains.php.lang.psi.elements.PhpExit
import com.jetbrains.php.lang.psi.elements.PhpNamespace
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor
import org.xoops.support.fix.Edits
import org.xoops.support.fix.XoopsFix
import org.xoops.support.psi.Php
import org.xoops.support.psi.XoopsPaths

/**
 * Include-only files (classes, preloads, includes, blocks) carry
 * `defined('XOOPS_ROOT_PATH') || exit('Restricted access');`.
 *
 * A file that bootstraps the core itself — any top-level `require` of mainfile.php,
 * header.php, cp_header.php, admin_header.php, print.php … — is a public entry point
 * and must never get the guard, no matter how many `error_reporting()` or flag
 * assignments precede that require. `preloads/autoloader.php` is required by
 * admin_header.php before the core is loaded and is excluded by name.
 */
class XoopsRootPathGuardInspection : XoopsInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            override fun visitFile(file: PsiFile) {
                val php = file as? PhpFile ?: return
                if (skipConvention(php) || !RootPathGuardPolicy.requiresGuard(php)) return
                holder.registerProblem(
                    php.firstChild ?: php,
                    "XOOPS: include-only file has no defined('XOOPS_ROOT_PATH') || exit('Restricted access'); guard",
                    ProblemHighlightType.WEAK_WARNING,
                    InsertRootPathGuardFix(),
                )
            }
        }
    }
}

object RootPathGuardPolicy {

    const val GUARD = "defined('XOOPS_ROOT_PATH') || exit('Restricted access');"

    // `|| exit`, `|| die`, `|| throw`; and `if (!defined(…) [|| more conditions]) { exit | die | throw }`.
    // The compound form (`|| !($GLOBALS['xoopsUser'] instanceof \XoopsUser)`) and the `throw new
    // \RuntimeException('Restricted access')` body are guards too: a second guard on top of them
    // was the largest false-positive class of the 224-module review (about 400 files).
    private val guardOr = Regex(
        """(?is)^\\?defined\s*\(\s*['"]XOOPS_ROOT_PATH['"]\s*\)\s*\|\|\s*\\?(?:exit|die|throw)\b""",
    )
    // A status line before the exit (`http_response_code(404); exit;`, `header('HTTP/1.0 404 …'); die;`) is the same guard.
    private val guardIf = Regex(
        """(?is)^if\s*\(\s*!\s*\\?defined\s*\(\s*['"]XOOPS_ROOT_PATH['"]\s*\)[^;{]*\{?\s*(?:\\?(?:http_response_code|header)\s*\([^;]*\)\s*;\s*)*\\?(?:exit|die|throw|return)\b""",
    )
    private val bootstrapFiles = arrayOf(
        "mainfile.php", "header.php", "cp_header.php", "admin_header.php", "admin_footer.php",
        "common.php", "footer.php", "print.php",
    )
    private val watched = listOf("/modules/", "/class/", "/preloads/", "/kernel/", "/include/", "/blocks/", "/src/")
    private val neverGuarded = listOf("/language/", "/tests/", "/test/", "/testdata/", "/preloads/autoloader.php")

    fun requiresGuard(file: PhpFile): Boolean {
        val path = XoopsPaths.path(file)
        if (!XoopsPaths.isPhp(file) || path.endsWith("/xoops_version.php")) return false
        if (neverGuarded.any { path.contains(it) }) return false
        if (path.contains("/admin/") && !path.contains("/class/")) return false
        if (watched.none { path.contains(it) }) return false
        if (!startsWithPhp(file.text)) return false

        val statements = Php.executableTopLevel(file)
        if (statements.isEmpty()) return false
        if (statements.any(::isGuard)) return false
        if (is404Stub(statements)) return false
        if (statements.any { Php.includes(it, *bootstrapFiles) }) return false
        if (answersRequest(statements)) return false
        if (isModuleRootPage(path)) {
            if (statements.any(::bootsSomething)) return false
            // A root page is guarded only on positive evidence that it is include-only.
            val name = path.substringAfterLast('/')
            return name in includeOnlyNames || declarationsOnly(statements)
        }
        if (includeDirs.none { path.contains(it) }) return declarationsOnly(statements)
        return true
    }

    /** `modules/<dir>/<file>.php`: a page the browser can open, unless it only declares things. */
    private fun isModuleRootPage(path: String): Boolean = Regex("/modules/[^/]+/[^/]+\\.php$").containsMatchIn(path)

    /**
     * A root page that includes any sibling file (`user.php`, `servicemain.php`) or sends a header
     * (`header('Location: …')`) boots the core through that file or answers the request itself;
     * a guard in front of it turns the page into "Restricted access".
     */
    private fun bootsSomething(statement: PsiElement): Boolean {
        if (Php.calls(statement, "header").isNotEmpty()) return true
        val include = (statement as? Include) ?: PsiTreeUtil.findChildOfType(statement, Include::class.java, false) ?: return false
        // `require __DIR__ . '/user.php'` boots through a sibling page; `'modules/x/include/functions.php'`
        // or `'/include/common.php'` is a library include that an include-only footer.php also has.
        val literal = PsiTreeUtil.findChildrenOfType(include, com.jetbrains.php.lang.psi.elements.StringLiteralExpression::class.java)
            .lastOrNull()?.contents?.replace('\\', '/')?.lowercase() ?: return false
        return literal.trimStart('/').let { it.endsWith(".php") && !it.contains('/') }
    }

    fun isGuard(statement: PsiElement): Boolean {
        val text = statement.text
        return guardOr.containsMatchIn(text) || guardIf.containsMatchIn(text)
    }

    /** Directory `index.php` stubs: only header('HTTP/1.0 404 …') / http_response_code() / exit. */
    fun is404Stub(statements: List<PsiElement>): Boolean = statements.all { s ->
        val call = PsiTreeUtil.findChildOfType(s, FunctionReference::class.java, false)
        when {
            PsiTreeUtil.findChildOfType(s, PhpExit::class.java, false) != null -> true
            call != null && Php.isCall(call, "header") ->
                Php.firstStringArg(call)?.matches(Regex("(?i)HTTP/\\d(?:\\.\\d)?\\s+40[34]\\b.*")) == true
            call != null && Php.isCall(call, "http_response_code") ->
                call.parameters.firstOrNull()?.text?.matches(Regex("40[34]")) == true
            else -> false
        }
    }

    /** First statement after the prologue (open tag, declare, namespace), including any comment glued to it. */
    fun insertionAnchor(file: PhpFile): PsiElement? {
        val first = Php.topLevelStatements(file).firstOrNull { it !is Declare && it !is PhpNamespace } ?: return null
        return Php.withLeadingComments(withLeadingKeyword(first))
    }

    /**
     * `global $x;` / `static $x;` are parsed as a keyword leaf followed by the statement, so the statement's
     * range starts after the keyword. The anchor absorbs such leaves on the same line, and stops at a
     * previous statement, the open tag, `declare` or `namespace` — so `<?php declare(strict_types=1); global $x;`
     * gets the guard between the declare and the `global` keyword, never inside the statement.
     */
    private fun withLeadingKeyword(statement: PsiElement): PsiElement {
        var anchor = statement
        var prev = anchor.prevSibling
        while (prev != null) {
            if (prev is com.intellij.psi.PsiWhiteSpace) {
                if (prev.text.contains('\n')) break
                prev = prev.prevSibling
                continue
            }
            if (prev is com.intellij.psi.PsiComment || prev is Declare || prev is PhpNamespace || prev.firstChild != null) break
            val t = prev.text
            if (t == ";" || t == "}" || t == "{" || t.startsWith("<?") || t.endsWith("?>")) break
            anchor = prev
            prev = anchor.prevSibling
        }
        return anchor
    }

    private val includeOnlyNames = setOf("header.php", "footer.php", "functions.php", "config.php", "constants.php", "menu.php", "bootstrap.php", "common.php")
    private val includeDirs = listOf("/class/", "/include/", "/blocks/", "/preloads/", "/src/", "/kernel/", "/plugins/")

    /** Only declarations: functions, classes, defines, constants, `return`, `use`, `global`. */
    private fun declarationsOnly(statements: List<PsiElement>): Boolean = statements.all { s ->
        s is com.jetbrains.php.lang.psi.elements.Function || s is com.jetbrains.php.lang.psi.elements.PhpClass ||
            s is com.jetbrains.php.lang.psi.elements.PhpReturn || s is com.jetbrains.php.lang.psi.elements.Global ||
            s is com.jetbrains.php.lang.psi.elements.Constant || s is com.jetbrains.php.lang.psi.elements.PhpUseList ||
            (s.firstChild as? FunctionReference)?.let { Php.isCall(it, "define") } == true
    }

    /** `echo` / `print` / `header()` at top level, or an include whose target is not a literal: the file answers a request. */
    private fun answersRequest(statements: List<PsiElement>): Boolean = statements.any { s ->
        s is com.jetbrains.php.lang.psi.elements.PhpEchoStatement ||
            PsiTreeUtil.findChildOfType(s, com.jetbrains.php.lang.psi.elements.PhpPrintExpression::class.java, false) != null ||
            Php.calls(s, "header").isNotEmpty() ||
            ((s as? Include) ?: PsiTreeUtil.findChildOfType(s, Include::class.java, false))?.let { inc ->
                PsiTreeUtil.findChildOfType(inc, com.jetbrains.php.lang.psi.elements.StringLiteralExpression::class.java) == null
            } == true
    }

    private fun startsWithPhp(text: String): Boolean {
        val t = text.removePrefix("﻿").trimStart()
        return t.startsWith("<?php", true) || (t.startsWith("<?") && !t.startsWith("<?xml", true))
    }
}

class InsertRootPathGuardFix : XoopsFix() {
    override fun getFamilyName(): String = "Insert XOOPS_ROOT_PATH guard"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val file = descriptor.psiElement?.containingFile as? PhpFile ?: return
        if (!RootPathGuardPolicy.requiresGuard(file)) return
        val anchor = RootPathGuardPolicy.insertionAnchor(file)
        if (anchor != null) {
            Edits.insertLinesBefore(project, anchor, RootPathGuardPolicy.GUARD + "\n\n")
        } else {
            Edits.insertLinesAfter(project, file.firstChild, RootPathGuardPolicy.GUARD)
        }
    }
}
