package org.xoops.support.inspections

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.jetbrains.php.lang.inspections.PhpInspection
import com.jetbrains.php.lang.psi.elements.FunctionReference
import org.xoops.support.fix.Edits
import org.xoops.support.fix.XoopsFix
import org.xoops.support.psi.Php
import org.xoops.support.psi.XoopsPaths

/**
 * Common gate: only the PHP tree of a real, non-vendored file is inspected.
 *
 * [skip] is the security gate: bundled code under `assets/` is still read, because an
 * upload endpoint shipped with a module is live code. [skipConvention] is for rules
 * about headers, guards, status lines and deprecations, which have nothing to say
 * about code the module author did not write.
 */
abstract class XoopsInspection : PhpInspection() {
    override fun getShortName(): String = "Xoops" + javaClass.simpleName.removeSuffix("Inspection").removePrefix("Xoops")

    protected fun skip(file: PsiFile): Boolean =
        !org.xoops.support.XoopsSettings.getInstance(file.project).enabled ||
            !Php.isPrimary(file) || Php.isInjected(file) || XoopsPaths.isExcluded(file) ||
            // PHPUnit code saves and restores $_POST, unlinks temp files and builds stubs with eval():
            // none of it is reachable from a request (02inspections: ~230 findings under tests/).
            XoopsPaths.isTestTree(file)

    // A file that never names XOOPS is not module code: a convention fix there (xoops_setcookie(),
    // xoops_load(), a root-path guard) introduces an API the script cannot load — and the word it
    // introduces then qualifies the file for every other rewrite on the next pass (0.7.2 review).
    protected fun skipConvention(file: PsiFile): Boolean =
        skip(file) || XoopsPaths.isThirdParty(file) || XoopsPaths.isTestTree(file) || !XoopsPaths.isXoopsCode(file)
}

/** Replaces the element when its text is still [expected]; a second application finds nothing to do. */
class ReplaceTextFix(private val expected: String, private val replacement: String) : XoopsFix() {
    override fun getFamilyName(): String = "Replace the deprecated form"
    override fun getName(): String = "Replace with $replacement"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement ?: return
        if (element.text != expected) return
        Edits.replace(project, element, replacement)
    }
}

/** `oldName(...)` → `newName(...)`; declines when the call no longer has the expected name. */
class RenameCallFix(private val oldName: String, private val newName: String) : XoopsFix() {
    override fun getFamilyName(): String = "Replace the deprecated call with its replacement"
    override fun getName(): String = "Replace $oldName() with $newName()"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val call = descriptor.psiElement as? FunctionReference ?: return
        if (!oldName.equals(call.name, ignoreCase = true)) return
        Edits.renameCall(project, call, newName)
    }
}
