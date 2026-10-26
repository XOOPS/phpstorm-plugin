package org.xoops.support.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.ArrayHashElement
import com.jetbrains.php.lang.psi.elements.AssignmentExpression
import com.jetbrains.php.lang.psi.elements.ArrayAccessExpression
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import org.xoops.support.fix.Edits
import org.xoops.support.fix.XoopsFix
import org.xoops.support.psi.Php
import org.xoops.support.psi.XoopsPaths

/**
 * Template registrations read from the manifest PSI. Disk resolution follows the
 * installer: `type => admin|blocks` adds that folder, every other type (including a
 * missing one or an invented `partial`) is `templates/{file}` as written; block-section
 * entries always live in `templates/blocks/`.
 */
object ManifestTemplates {

    data class Registration(val literal: StringLiteralExpression, val name: String, val block: Boolean, val type: String) {
        val diskPath: String get() = diskPath(name, if (block) "blocks" else type)

        /** The `tpl_file` the installer stores: bare name for folder types, as written otherwise. */
        val storedName: String
            get() = if (block || type == "admin" || type == "blocks") name.substringAfterLast('/') else name
    }

    private val section = Regex("""^\${'$'}modversion\s*\[\s*['"](templates|blocks)['"]\s*]""")

    fun registrations(manifest: PhpFile): List<Registration> {
        val out = ArrayList<Registration>()
        for (h in PsiTreeUtil.findChildrenOfType(manifest, ArrayHashElement::class.java)) {
            val key = (h.key as? StringLiteralExpression)?.contents?.lowercase() ?: continue
            if (key != "file" && key != "template") continue
            val value = h.value as? StringLiteralExpression ?: continue
            val sec = sectionOf(h) ?: continue
            val type = (h.parent as? ArrayCreationExpression)?.hashElements
                ?.firstOrNull { (it.key as? StringLiteralExpression)?.contents.equals("type", true) }
                ?.let { (it.value as? StringLiteralExpression)?.contents?.lowercase() } ?: ""
            out.add(Registration(value, normalize(value.contents), sec == "blocks", type))
        }
        for (a in PsiTreeUtil.findChildrenOfType(manifest, AssignmentExpression::class.java)) {
            val target = a.variable as? ArrayAccessExpression ?: continue
            val key = (target.index?.value as? StringLiteralExpression)?.contents?.lowercase() ?: continue
            if (key != "file" && key != "template") continue
            val value = a.value as? StringLiteralExpression ?: continue
            val sec = sectionOf(a) ?: continue
            out.add(Registration(value, normalize(value.contents), sec == "blocks", ""))
        }
        return out.filter { it.name.lowercase().endsWith(".tpl") }
    }

    private fun sectionOf(element: PsiElement): String? {
        val statement = Php.statementInBlock(element) ?: return null
        return section.find(statement.text.trimStart())?.groupValues?.get(1)?.lowercase()
    }

    fun normalize(name: String): String =
        name.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }.joinToString("/")

    /**
     * Exactly `xoops_module_gettemplate()`: `templates/<type>/<file>` for type `admin` / `blocks` (and every
     * `$modversion['blocks']` entry), `templates/<file>` as written for every other type — `block`, `partial`,
     * none — so `'file' => 'blocks/x.tpl'` with no type is read from `templates/blocks/x.tpl`.
     */
    fun diskPath(name: String, folderType: String): String =
        if (folderType == "admin" || folderType == "blocks") "templates/$folderType/$name" else "templates/$name"

    /** The folder the installer prepends for this registration, or null when it reads `templates/<file>` as written. */
    fun folderOf(reg: Registration): String? = when {
        reg.block || reg.type == "blocks" -> "blocks"
        reg.type == "admin" -> "admin"
        else -> null
    }

    /**
     * The prefix this module's templates carry (`publisher_item.tpl` → `publisher`), read from
     * `$modversion['dirname']` when it is a literal, else the most common prefix among the
     * registered templates, else the directory name. A copy named `publisher-copy` still
     * owns `publisher_*`.
     */
    fun templatePrefix(root: VirtualFile, registrations: List<Registration>): String? {
        val manifest = root.findChild("xoops_version.php")
        val text = manifest?.let { runCatching { String(it.contentsToByteArray(), Charsets.UTF_8) }.getOrNull() } ?: ""
        Regex("""\${'$'}modversion\s*\[\s*['"]dirname['"]\s*]\s*=\s*['"]([A-Za-z0-9_-]+)['"]""").find(text)
            ?.groupValues?.get(1)?.lowercase()?.let { return it }
        val counts = registrations.map { it.name.substringAfterLast('/').substringBefore('_').lowercase() }
            .filter { it.isNotEmpty() }.groupingBy { it }.eachCount()
        val top = counts.maxByOrNull { it.value }
        if (top != null && top.value >= 2) return top.key
        return root.name.lowercase().substringBefore('-').takeIf { it.isNotEmpty() }
    }

    fun manifestOf(file: PsiFile): PhpFile? {
        val root = XoopsPaths.moduleRoot(file.virtualFile) ?: return null
        val vf = root.findChild("xoops_version.php") ?: return null
        return PsiManager.getInstance(file.project).findFile(vf) as? PhpFile
    }

    /** Files under `templates/` whose name matches, module-relative. */
    fun findByBasename(moduleRoot: VirtualFile, basename: String): List<String> {
        val templates = moduleRoot.findChild("templates") ?: return emptyList()
        val found = ArrayList<String>()
        VfsUtilCore.iterateChildrenRecursively(templates, null) { vf ->
            if (!vf.isDirectory && vf.name.equals(basename, true)) {
                VfsUtilCore.getRelativePath(vf, moduleRoot, '/')?.let(found::add)
            }
            true
        }
        return found
    }
}

/** A registration whose file is not where the installer will look. */
class XoopsMissingRegisteredTemplateInspection : LocalInspectionTool() {
    override fun getShortName(): String = "XoopsMissingRegisteredTemplate"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                val php = file as? PhpFile ?: return
                if (!php.name.equals("xoops_version.php", true) || !Php.isPrimary(php) || XoopsPaths.isExcluded(php)) return
                val root = php.virtualFile?.parent ?: return
                for (reg in ManifestTemplates.registrations(php)) {
                    if (root.findFileByRelativePath(reg.diskPath) != null) continue
                    val candidates = ManifestTemplates.findByBasename(root, reg.name.substringAfterLast('/'))
                    if (candidates.size == 1) {
                        val folder = ManifestTemplates.folderOf(reg)
                        val prefix = if (folder == null) "templates/" else "templates/$folder/"
                        if (candidates[0].startsWith(prefix)) {
                            holder.registerProblem(
                                reg.literal,
                                "XOOPS: registered template is read from ${reg.diskPath} but the file is at ${candidates[0]}",
                                RepointTemplateFix(candidates[0].removePrefix(prefix)),
                            )
                        } else {
                            holder.registerProblem(
                                reg.literal,
                                "XOOPS: registered template is read from ${reg.diskPath} but the file is at ${candidates[0]} — " +
                                    "the installer only reads $prefix for this type; move the file or change the type",
                            )
                        }
                    } else {
                        holder.registerProblem(
                            reg.literal,
                            "XOOPS: registered template does not exist on disk: ${reg.diskPath}",
                        )
                    }
                }
            }
        }
    }
}

class RepointTemplateFix(private val relativeUnderTemplates: String) : XoopsFix() {
    override fun getFamilyName(): String = "Point the registration at the existing file"
    override fun getName(): String = "Change to '$relativeUnderTemplates'"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val literal = descriptor.psiElement as? StringLiteralExpression ?: return
        val quote = if (literal.isSingleQuote) "'" else "\""
        Edits.replace(project, literal, "$quote$relativeUnderTemplates$quote")
    }
}

/**
 * A module `.tpl` the manifest does not know, and a `db:` include that nobody registers.
 * Core `system_*` templates and templates owned by another installed module are not this
 * module's problem; an include inside a Smarty comment is not an include.
 */
class XoopsUnregisteredTemplateInspection : LocalInspectionTool() {
    override fun getShortName(): String = "XoopsUnregisteredTemplate"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                if (!file.name.lowercase().endsWith(".tpl") || !Php.isPrimary(file) || XoopsPaths.isExcluded(file)) return
                val vf = file.virtualFile ?: return
                val root = XoopsPaths.moduleRoot(vf) ?: return
                val relative = XoopsPaths.relativeToModule(vf) ?: return
                val manifest = ManifestTemplates.manifestOf(file) ?: return
                val registrations = ManifestTemplates.registrations(manifest)

                reportDbIncludes(file, root, registrations)

                if (!relative.startsWith("templates/")) return
                if (registrations.any { it.diskPath.equals(relative, true) }) return
                val anchor = file.firstChild ?: file
                if (relative.startsWith("templates/blocks/")) {
                    holder.registerProblem(
                        anchor,
                        "XOOPS: block template is not registered — set 'template' => '${relative.substringAfterLast('/')}' on the block entry",
                        ProblemHighlightType.WEAK_WARNING,
                    )
                    return
                }
                holder.registerProblem(
                    anchor,
                    "XOOPS: template is not registered in xoops_version.php: $relative",
                    ProblemHighlightType.WEAK_WARNING,
                    RegisterTemplateFix(relative),
                )
            }

            private fun reportDbIncludes(file: PsiFile, root: VirtualFile, registrations: List<ManifestTemplates.Registration>) {
                val text = maskSmartyComments(file.text)
                val stored = registrations.map { it.storedName.lowercase() }.toSet()
                val ownPrefix = ManifestTemplates.templatePrefix(root, registrations)
                for (m in dbInclude.findAll(text)) {
                    val name = ManifestTemplates.normalize(m.groupValues[1])
                    val lower = name.lowercase()
                    if (lower.startsWith("system_")) continue
                    val owner = lower.substringAfterLast('/').substringBefore('_')
                    // `db:tag_bar.tpl` inside publisher is the tag module's template: not this module's problem.
                    if (ownPrefix != null && owner != ownPrefix) continue
                    if (!owner.equals(root.name, true) && root.parent?.findChild(owner) != null) continue
                    if (lower in stored) continue
                    val range = TextRange(m.groups[1]!!.range.first, m.groups[1]!!.range.last + 1)
                    val onDisk = root.findFileByRelativePath("templates/$name") != null ||
                        root.findFileByRelativePath("templates/admin/${name.substringAfterLast('/')}") != null
                    holder.registerProblem(
                        file,
                        range,
                        if (onDisk) "XOOPS: db:$name is on disk but not registered in xoops_version.php"
                        else "XOOPS: db:$name is neither registered nor present under templates/",
                    )
                }
            }
        }
    }

    companion object {
        private val dbInclude = Regex("""<\{\s*include(?:_once)?\s+file\s*=\s*['"]db:([^'"]+)['"]""")

        fun maskSmartyComments(text: String): String {
            val sb = StringBuilder(text)
            var i = 0
            while (true) {
                val open = sb.indexOf("<{*", i)
                if (open < 0) break
                val close = sb.indexOf("*}>", open + 3).let { if (it < 0) sb.length else it + 3 }
                for (k in open until close) if (sb[k] != '\n' && sb[k] != '\r') sb.setCharAt(k, ' ')
                i = close
            }
            return sb.toString()
        }
    }
}

/** Registering a template changes what the installer stores: an explicit Alt+Enter, never part of "Fix all". */
class RegisterTemplateFix(private val relative: String) : XoopsFix(batchSafe = false) {
    override fun getFamilyName(): String = "Register template in xoops_version.php"

    override fun getElementToMakeWritable(currentFile: PsiFile): PsiElement? =
        ManifestTemplates.manifestOf(currentFile) ?: currentFile

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val tpl = descriptor.psiElement?.containingFile ?: return
        val manifest = ManifestTemplates.manifestOf(tpl) ?: return
        if (ManifestTemplates.registrations(manifest).any { it.diskPath.equals(relative, true) }) return
        val underTemplates = relative.removePrefix("templates/")
        val entry = if (underTemplates.startsWith("admin/")) {
            "['file' => '${underTemplates.removePrefix("admin/")}', 'type' => 'admin', 'description' => '']"
        } else {
            "['file' => '$underTemplates', 'description' => '']"
        }
        val last = Php.topLevelStatements(manifest).lastOrNull() ?: return
        Edits.insertLinesAfter(project, last, "\$modversion['templates'][] = $entry;")
    }
}
