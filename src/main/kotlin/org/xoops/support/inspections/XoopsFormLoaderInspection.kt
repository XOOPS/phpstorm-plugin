package org.xoops.support.inspections

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor
import org.xoops.support.fix.Edits
import org.xoops.support.fix.XoopsFix
import org.xoops.support.psi.Php

/**
 * `XoopsForm*` classes are not autoloaded: a file whose class extends one of them needs
 * `xoops_load('XoopsFormLoader')` (or a load of exactly the parent class) before the
 * `class` keyword. The fix inserts a real statement in front of the class and its
 * docblock — never on an existing line, never inside a comment.
 */
class XoopsFormLoaderInspection : XoopsInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            override fun visitPhpClass(clazz: PhpClass) {
                val parent = clazz.superName ?: return
                if (!isFormParent(parent) || skipConvention(clazz.containingFile)) return
                val file = clazz.containingFile as? PhpFile ?: return
                if (isCoreFormLibrary(file)) return
                if (loaderPresent(file, parent)) return
                holder.registerProblem(
                    clazz.nameIdentifier ?: clazz,
                    "XOOPS: class extends $parent but the file never calls xoops_load('XoopsFormLoader')",
                    InsertFormLoaderFix(),
                )
            }
        }
    }

    companion object {
        private val formParents = listOf("XoopsForm", "XoopsThemeForm", "XoopsSimpleForm")

        fun isFormParent(name: String): Boolean = formParents.any { name.startsWith(it, true) }

        /**
         * The core's own `class/xoopsform/` (outside any module): those files are what
         * xoopsformloader.php includes, in dependency order. `xoops_load('XoopsFormLoader')` inside
         * them is a circular load, not a fix (XoopsCore27: 14 findings, all in that directory).
         */
        fun isCoreFormLibrary(file: PhpFile): Boolean =
            org.xoops.support.psi.XoopsPaths.path(file).contains("/class/xoopsform/") &&
                org.xoops.support.psi.XoopsPaths.moduleRoot(file.virtualFile) == null

        fun loaderPresent(file: PhpFile, parent: String): Boolean {
            val loads = Php.calls(file, "xoops_load").mapNotNull { Php.firstStringArg(it)?.lowercase() }
            if (loads.any { it == "xoopsformloader" || it == parent.lowercase() }) return true
            return Php.topLevelStatements(file).any { Php.includes(it, "xoopsformloader.php") }
        }
    }
}

class InsertFormLoaderFix : XoopsFix() {
    override fun getFamilyName(): String = "Insert xoops_load('XoopsFormLoader') before the class"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val clazz = PsiTreeUtil.getParentOfType(descriptor.psiElement, PhpClass::class.java, false) ?: return
        val file = clazz.containingFile as? PhpFile ?: return
        val parent = clazz.superName ?: return
        if (XoopsFormLoaderInspection.loaderPresent(file, parent)) return
        Edits.insertLinesBefore(project, Php.withLeadingComments(clazz), "\\xoops_load('XoopsFormLoader');\n\n")
    }
}
