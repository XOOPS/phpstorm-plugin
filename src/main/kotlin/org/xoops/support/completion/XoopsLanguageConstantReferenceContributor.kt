package org.xoops.support.completion

import com.intellij.lang.Language
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.ResolveResult
import com.intellij.util.ProcessingContext
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import org.xoops.support.XoopsSettings
import org.xoops.support.psi.XoopsPaths

/**
 * Ctrl+B / Find Usages for XOOPS language constants where PhpStorm's own resolver does not reach:
 * the name as a string (`'_MI_DEMO_NAME'` in `xoops_version.php` config options, `constant()`,
 * `defined()`) and `$smarty.const._MD_DEMO_TITLE` in a Smarty template. A plain `_MI_DEMO_NAME`
 * in PHP already resolves through the PHP plugin and is not touched.
 *
 * The reference is poly-variant: every `define()` in a `language/` tree is a target, same module
 * first and English first, so Ctrl+B on a constant that five language packs define offers the
 * list instead of guessing. Find Usages on the `define()` then lists the string and template uses.
 */
class XoopsLanguageConstantReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        val provider = object : PsiReferenceProvider() {
            override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                if (!XoopsSettings.getInstance(element.project).enabled) return PsiReference.EMPTY_ARRAY
                val (name, range) = locate(element) ?: return PsiReference.EMPTY_ARRAY
                return arrayOf(XoopsLanguageConstantReference(element, range, name))
            }
        }
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(StringLiteralExpression::class.java), provider)
        // The Smarty plugin is bundled with PhpStorm but is its own plugin: bind by language id when present.
        Language.findLanguageByID("Smarty")?.let { smarty ->
            registrar.registerReferenceProvider(PlatformPatterns.psiElement().withLanguage(smarty), provider)
        }
    }

    companion object {
        val NAME = Regex("""_(?:MI|AM|MD|CO|MB)_[A-Z0-9_]+""")
        private val SMARTY_CONST = Regex("""\${'$'}smarty\.const\.(_(?:MI|AM|MD|CO|MB)_[A-Z0-9_]+)""")

        /** The constant name and its range inside [element], or null when the element is not a constant use. */
        fun locate(element: PsiElement): Pair<String, TextRange>? {
            if (element is StringLiteralExpression) {
                val name = element.contents
                if (!NAME.matches(name)) return null
                return name to ElementManipulators.getValueTextRange(element)
            }
            if (element.children.isNotEmpty()) return null
            val text = element.text
            SMARTY_CONST.find(text)?.let { m -> return m.groupValues[1] to TextRange(m.groups[1]!!.range.first, m.groups[1]!!.range.last + 1) }
            if (NAME.matches(text)) return text to TextRange(0, text.length)
            return null
        }
    }
}

class XoopsLanguageConstantReference(element: PsiElement, range: TextRange, private val name: String) :
    PsiPolyVariantReferenceBase<PsiElement>(element, range, true) {

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val project = element.project
        val from = element.containingFile?.virtualFile?.path?.replace('\\', '/')?.lowercase()
        val module = from?.let { Regex("^(.*/modules/[^/]+/)").find(it)?.groupValues?.get(1) }
        val indexed: List<PsiElement> = PhpIndex.getInstance(project).getConstantsByName(name)
            .filter { it.containingFile?.let(XoopsPaths::isLanguageFile) == true }
        // The constant index lags behind freshly written language files; a direct scan of the language
        // trees is the fallback, so navigation works right after a language pack was added.
        val defines = indexed.ifEmpty { scanLanguageFiles(project, element.containingFile?.virtualFile, name) }
        if (defines.isEmpty()) return ResolveResult.EMPTY_ARRAY
        val ranked = defines.sortedWith(
            compareByDescending<PsiElement> { module != null && XoopsPaths.path(it.containingFile).startsWith(module) }
                .thenByDescending { XoopsPaths.path(it.containingFile).contains("/english/") }
                .thenBy { XoopsPaths.path(it.containingFile) },
        )
        return ranked.map { PsiElementResolveResult(it) }.toTypedArray()
    }

    override fun getCanonicalText(): String = name

    override fun getVariants(): Array<Any> = emptyArray()

    /** `define('NAME', …)` calls in every PHP file under a `language/` tree of the project; the name literal is the target. */
    private fun scanLanguageFiles(project: com.intellij.openapi.project.Project, from: com.intellij.openapi.vfs.VirtualFile?, name: String): List<PsiElement> {
        val psiManager = com.intellij.psi.PsiManager.getInstance(project)
        // No index: the `language/` trees that can define the constant, walked through the VFS — the
        // module the use sits in, every module beside it, and the core's own language folder.
        val moduleRoot = XoopsPaths.moduleRoot(from)
        val modulesDir = moduleRoot?.parent?.takeIf { it.name == "modules" }
        val roots = buildList {
            moduleRoot?.findChild("language")?.let(::add)
            modulesDir?.children?.forEach { m -> m.findChild("language")?.let(::add) }
            modulesDir?.parent?.findChild("language")?.let(::add)
        }.distinct()
        val files = ArrayList<com.intellij.openapi.vfs.VirtualFile>()
        for (root in roots) {
            com.intellij.openapi.vfs.VfsUtilCore.iterateChildrenRecursively(root, null) { vf ->
                if (!vf.isDirectory && vf.name.endsWith(".php", ignoreCase = true)) files.add(vf)
                true
            }
        }
        val out = ArrayList<PsiElement>()
        for (vf in files) {
            val file = psiManager.findFile(vf) ?: continue
            for (call in com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(file, com.jetbrains.php.lang.psi.elements.FunctionReference::class.java)) {
                if (!"define".equals(call.name, ignoreCase = true)) continue
                val literal = call.parameters.firstOrNull() as? StringLiteralExpression ?: continue
                if (literal.contents == name) out.add(literal)
            }
        }
        return out
    }
}
