package org.xoops.support.inspections

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor
import org.xoops.support.fix.Edits
import org.xoops.support.fix.XoopsFix
import org.xoops.support.psi.Php
import org.xoops.support.psi.XoopsPaths

/**
 * Module PHP files open with a docblock carrying `@license`. Every comment in the first
 * 4000 characters is read from the PSI — XOOPS files often carry the licence in the second
 * docblock, after the "You may not change or alter" banner — and any mention of a licence
 * or a copyright ends the check: a tag is never written over somebody else's provenance.
 * The fix is offered only for a file whose head has no comment at all, only outside
 * third-party and test trees, and only on the host file: a `"<?php …"` string literal inside
 * the code is an injected fragment, never a file.
 */
class XoopsFileHeaderInspection : XoopsInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            override fun visitFile(file: PsiFile) {
                val php = file as? PhpFile ?: return
                if (skipConvention(php) || XoopsPaths.isLanguageFile(php)) return
                val path = XoopsPaths.path(php)
                if (path.endsWith("/xoops_version.php")) return
                if (listOf("/modules/", "/class/", "/kernel/", "/include/").none { path.contains(it) }) return
                if (!php.text.removePrefix("﻿").startsWith("<?php")) return
                val statements = Php.executableTopLevel(php)
                if (statements.isNotEmpty() && RootPathGuardPolicy.is404Stub(statements)) return

                val head = headComments(php)
                if (head.any { provenance.containsMatchIn(it) }) return
                val anchor = php.firstChild ?: php
                if (head.isNotEmpty()) {
                    holder.registerProblem(
                        anchor,
                        "XOOPS: the file head has comments but no @license tag (kept as is: provenance must be checked by hand)",
                        ProblemHighlightType.WEAK_WARNING,
                    )
                } else {
                    holder.registerProblem(
                        anchor,
                        "XOOPS: file has no header docblock with @copyright / @license",
                        ProblemHighlightType.WEAK_WARNING,
                        AddFileHeaderFix(),
                    )
                }
            }
        }
    }

    companion object {
        const val HEAD = 4000
        private val provenance = Regex("(?i)@license|license:|licensed under|@copyright|copyright|\\(c\\)\\s*\\d")

        /** Text of every comment that starts within the first [HEAD] characters. */
        fun headComments(file: PhpFile): List<String> =
            (PsiTreeUtil.collectElementsOfType(file, PsiComment::class.java) + PsiTreeUtil.collectElementsOfType(file, PhpDocComment::class.java))
                .filter { it.textRange.startOffset < HEAD }
                .map { it.text }
    }
}

class AddFileHeaderFix : XoopsFix() {
    override fun getFamilyName(): String = "Add XOOPS file header docblock"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val file = descriptor.psiElement?.containingFile as? PhpFile ?: return
        if (Php.isInjected(file) || XoopsFileHeaderInspection.headComments(file).isNotEmpty()) return
        val text = file.text
        if (!text.startsWith("<?php")) return
        val firstLineEnd = text.indexOf('\n').let { if (it < 0) text.length else it }
        // `<?php … ?>` on the first line: the header must stay inside PHP, right after the open tag.
        val insertAt = if (text.substring(0, firstLineEnd).contains("?>")) "<?php".length else firstLineEnd
        Edits.insertAt(project, file, insertAt, "\n$HEADER")
    }

    companion object {
        val HEADER = """
            |/**
            | * @copyright   XOOPS Project (https://xoops.org)
            | * @license     GPL-2.0-or-later (https://www.gnu.org/licenses/gpl-2.0.html)
            | */
        """.trimMargin()
    }
}
