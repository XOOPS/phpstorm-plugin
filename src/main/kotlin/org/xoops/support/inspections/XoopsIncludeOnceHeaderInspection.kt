package org.xoops.support.inspections

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.Include
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor
import org.xoops.support.fix.Edits
import org.xoops.support.fix.XoopsFix

/**
 * `include` / `require` of `header.php` or `footer.php` instead of the `_once` form. The core
 * header defines functions and starts the theme; a second inclusion through another include path
 * is a redeclaration fatal. Carried over from XOOPS Support 1.0.0 Alpha 3 (`XoopsIncludeOnceHeader`),
 * rebuilt on the PSI: the keyword leaf is the problem element and the fix renames only that leaf.
 */
class XoopsIncludeOnceHeaderInspection : XoopsInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            override fun visitPhpInclude(include: Include) {
                if (skipConvention(include.containingFile)) return
                val keyword = include.firstChild ?: return
                val word = keyword.text
                if (word != "include" && word != "require") return
                val literal = PsiTreeUtil.findChildrenOfType(include, StringLiteralExpression::class.java).lastOrNull() ?: return
                // The core's own header.php / footer.php only: cp_header.php and a module's admin_header.php
                // are bootstraps that are included exactly once by design.
                val name = literal.contents.replace('\\', '/').lowercase().substringAfterLast('/')
                if (name != "header.php" && name != "footer.php") return
                holder.registerProblem(
                    keyword,
                    "XOOPS: use ${word}_once for header.php / footer.php — a second inclusion redeclares the core",
                    ProblemHighlightType.WEAK_WARNING,
                    IncludeOnceFix(word),
                )
            }
        }
    }
}

class IncludeOnceFix(private val word: String) : XoopsFix() {
    override fun getFamilyName(): String = "Replace include with include_once"
    override fun getName(): String = "Replace $word with ${word}_once"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val keyword = descriptor.psiElement ?: return
        if (keyword.text != word) return
        Edits.replace(project, keyword, "${word}_once")
    }
}
