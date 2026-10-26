package org.xoops.support.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.xoops.support.fix.XoopsFix
import org.xoops.support.psi.Php
import org.xoops.support.psi.XoopsCore
import org.xoops.support.psi.XoopsPaths

/**
 * Template rules ported from the 2.7.4 upgrade preflight
 * (`htdocs/upgrade/class/Xoops/Upgrade/Smarty{4,5}Template{Checks,Repair}.php`).
 *
 * They describe Smarty 3+ (2.5.11 and later). On a Smarty 2 core the old forms are the
 * correct ones: `xoAdminIcons` received its argument raw, `includeq` existed and `date_format`
 * was always strftime. So nothing is reported when the core is older than 2.5.11.
 *
 * Two deliberate departures from the preflight repair, both of which break templates there:
 * a plugin argument is always quoted with `'` (most `compiler.xo*.php` plugins trim only `'`,
 * so `"home.png"` reaches the URL with its quotes), and a date format is rewritten only when it
 * has no literal letters (`'Le %d'` would become `'Le d'`, and `L` and `e` are date() codes).
 */
object SmartyUpgrade {
    val SMARTY3 = XoopsCore.Version(2, 5, 11)

    /** `<{xoAdminIcons home.png}>`: one bare argument to an `xo*` plugin. Named arguments (`a=b`) are fine. */
    val unquotedArg = Regex("""<\{(xo[A-Za-z\d]*)\s+([^\s'"$=}<>]+)\s*\}>""")
    val removedTag = Regex("""<\{\s*(includeq|foreachq)(?=\s)""")
    val phpTag = Regex("""<\{\s*/?\s*php\s*\}>|<\{\s*include_php\b[^}]*\}>""")
    val foreachTag = Regex("""<\{\s*foreach\b([^}]*)\}>""")
    val itemAttr = Regex("""\bitem=([A-Za-z_]\w*)""")

    /** `from=$x` or `from=$x|modifier`; `from=$x.list` is another variable and compiles. */
    val fromAttr = Regex("""\bfrom=\$([A-Za-z_]\w*)(?=[\s|]|$)""")
    val blockShorthand = Regex("""<\{\s*block_(parent|child)\s*\}>""")
    val dateFormat = Regex("""(\|\s*date_format\s*:\s*)(['"])([^'"]*%[^'"]*)\2""")

    /** strftime → date(). `%M` is minutes (`i`), not the month name. Locale tokens (`%A %B %j`…) have no safe mapping. */
    val dateMap = mapOf(
        "%Y" to "Y", "%y" to "y", "%m" to "m", "%d" to "d", "%e" to "j",
        "%H" to "H", "%M" to "i", "%S" to "s", "%p" to "A", "%%" to "%",
    )

    /** Smarty 5 rework the preflight reports but cannot repair: rule → message. */
    val smarty5Prep = listOf(
        Regex("""<\{\s*(?:parent|child)\s*\}>""") to "bare <{parent}> / <{child}>: use <{\$smarty.block.parent}> / <{\$smarty.block.child}>",
        Regex("""<\{\s*insert\b[^}]*\}>""") to "<{insert}> is removed in Smarty 5: render the value in PHP or a registered function",
        Regex("""<\{\s*make_nocache\b[^}]*\}>""") to "<{make_nocache}> is removed in Smarty 5",
        Regex("""<\{\s*config_load\b[^}]*\bscope\s*=""") to "config_load scope= is removed in Smarty 5",
        Regex("""\|\s*(?:substr|str_replace|strlen|trim|strtolower|strtoupper|ucfirst|reset)\b""") to
            "PHP function used as a modifier: Smarty 5 needs it registered as a modifier",
    )

    fun isTemplate(file: PsiFile): Boolean {
        val name = file.name.lowercase()
        return (name.endsWith(".tpl") || name.endsWith(".html")) && Php.isPrimary(file) &&
            !XoopsPaths.isExcluded(file) && !XoopsPaths.isTestTree(file) && XoopsCore.atLeast(file, SMARTY3)
    }

    /** The date() form of [format], or null when a token or a literal letter has no safe rewrite. */
    fun dateFormatFix(format: String): String? {
        val out = StringBuilder()
        var i = 0
        while (i < format.length) {
            val c = format[i]
            if (c == '%') {
                out.append(dateMap[format.substring(i, minOf(i + 2, format.length))] ?: return null)
                i += 2
            } else {
                if (c.isLetter() || c == '\\') return null
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    /**
     * Where `_item` goes to rename the item of the foreach opening at [tagStart]: after `item=X`
     * and after every `$X` in its body, up to `<{foreachelse}>` or the matching `<{/foreach}>`.
     * A nested foreach that declares `item=X` again has its own scope and is skipped. Null when
     * the tag no longer shadows its array or the loop is not closed. [text] must be masked.
     */
    fun foreachItemRenameOffsets(text: String, tagStart: Int): List<Int>? {
        val tag = foreachTag.find(text, tagStart)?.takeIf { it.range.first == tagStart } ?: return null
        val attrs = tag.groups[1]!!
        val item = itemAttr.find(attrs.value) ?: return null
        val name = item.groupValues[1]
        if (fromAttr.find(attrs.value)?.groupValues?.get(1) != name) return null
        val bodyStart = tag.range.last + 1
        val bodyEnd = loopBodyEnd(text, bodyStart) ?: return null
        val ref = Regex("""\$$name(?!\w)""")
        val shadow = Regex("""<\{\s*foreach\b(?=[^}]*\bitem=$name\b)[^}]*\}>""")
        val offsets = mutableListOf(attrs.range.first + item.range.last + 1)
        var pos = bodyStart
        while (pos < bodyEnd) {
            val nested = shadow.find(text, pos)?.takeIf { it.range.first < bodyEnd }
            // The nested tag itself is still outer scope: its `from=$X.list` reads the renamed item.
            val segmentEnd = nested?.range?.last?.plus(1) ?: bodyEnd
            ref.findAll(text.substring(pos, segmentEnd)).forEach { offsets += pos + it.range.last + 1 }
            pos = if (nested == null) bodyEnd else matchingClose(text, nested.range.last + 1) ?: bodyEnd
        }
        return offsets
    }

    /** Smarty 2's `<{foreachq}>` is closed by `<{/foreach}>` too. */
    private val loopTag = Regex("""<\{\s*(/?)(foreach(?:else|q)?)\b[^}]*\}>""")

    /** Offset where this loop's item scope ends: its own `<{foreachelse}>` or `<{/foreach}>`. */
    private fun loopBodyEnd(text: String, from: Int): Int? {
        var depth = 1
        for (m in loopTag.findAll(text, from)) {
            val closing = m.groupValues[1] == "/"
            val isElse = m.groupValues[2] == "foreachelse"
            when {
                isElse && depth == 1 -> return m.range.first
                isElse -> {}
                closing -> if (--depth == 0) return m.range.first
                else -> depth++
            }
        }
        return null
    }

    /** Offset just past the `<{/foreach}>` closing a loop whose body starts at [from]. */
    private fun matchingClose(text: String, from: Int): Int? {
        var depth = 1
        for (m in loopTag.findAll(text, from)) {
            if (m.groupValues[2] == "foreachelse") continue
            if (m.groupValues[1] == "/") {
                if (--depth == 0) return m.range.last + 1
            } else {
                depth++
            }
        }
        return null
    }
}

/** A template rule: comments and `<{literal}>` are masked, the core must run Smarty 3+. */
abstract class SmartyUpgradeInspection : LocalInspectionTool() {
    abstract fun check(text: String, report: (IntRange, String, LocalQuickFix?) -> Unit)

    open fun fixAvailable(file: PsiFile): Boolean = true

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                if (!SmartyUpgrade.isTemplate(file)) return
                val withFix = fixAvailable(file)
                check(SmartyText.mask(file.text)) { range, message, fix ->
                    val fixes = if (fix != null && withFix) arrayOf(fix) else LocalQuickFix.EMPTY_ARRAY
                    holder.registerProblem(
                        file, "XOOPS: $message", ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        TextRange(range.first, range.last + 1), *fixes,
                    )
                }
            }
        }
    }
}

/** Replaces the descriptor's range when it still matches [pattern]; the range follows earlier batch edits. */
abstract class SmartyRewriteFix(private val pattern: Regex) : XoopsFix() {
    abstract fun rewrite(match: MatchResult, doc: Document, start: Int): String?

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val file = descriptor.psiElement?.containingFile ?: return
        val doc = PsiDocumentManager.getInstance(project).getDocument(file) ?: return
        val range = descriptor.textRangeInElement ?: return
        val match = pattern.matchEntire(doc.getText(range)) ?: return
        val replacement = rewrite(match, doc, range.startOffset) ?: return
        doc.replaceString(range.startOffset, range.endOffset, replacement)
        PsiDocumentManager.getInstance(project).commitDocument(doc)
    }
}

/** `<{xoAdminIcons home.png}>`: Smarty 3+ reads `home.png` as an expression and the template does not compile. */
class XoopsSmartyUnquotedPluginArgInspection : SmartyUpgradeInspection() {
    override fun getShortName(): String = "XoopsSmartyUnquotedPluginArg"

    override fun check(text: String, report: (IntRange, String, LocalQuickFix?) -> Unit) {
        for (m in SmartyUpgrade.unquotedArg.findAll(text)) {
            report(m.groups[2]!!.range, "<{${m.groupValues[1]}}> argument must be quoted: '${m.groupValues[2]}'", QuotePluginArgFix())
        }
    }
}

class QuotePluginArgFix : SmartyRewriteFix(Regex("""[^\s'"$=}<>]+""")) {
    override fun getFamilyName(): String = "Quote the plugin argument"
    override fun rewrite(match: MatchResult, doc: Document, start: Int): String = "'${match.value}'"
}

/** `<{includeq}>` / `<{foreachq}>` are gone; `<{php}>` / `<{include_php}>` do not compile on Smarty 3+. */
class XoopsSmartyRemovedTagInspection : SmartyUpgradeInspection() {
    override fun getShortName(): String = "XoopsSmartyRemovedTag"

    override fun check(text: String, report: (IntRange, String, LocalQuickFix?) -> Unit) {
        for (m in SmartyUpgrade.removedTag.findAll(text)) {
            val tag = m.groups[1]!!
            report(tag.range, "<{${tag.value}}> was removed: use <{${tag.value.dropLast(1)}}>", DropQSuffixFix())
        }
        for (m in SmartyUpgrade.phpTag.findAll(text)) {
            report(m.range, "${m.value.take(20)} does not compile on Smarty 3+: move the code into PHP", null)
        }
    }
}

class DropQSuffixFix : SmartyRewriteFix(Regex("""(include|foreach)q""")) {
    override fun getFamilyName(): String = "Use include / foreach"
    override fun rewrite(match: MatchResult, doc: Document, start: Int): String = match.groupValues[1]
}

/** `<{foreach item=x from=$x}>`: Smarty 3+ refuses an item named like its array. */
class XoopsSmartyForeachItemShadowsFromInspection : SmartyUpgradeInspection() {
    override fun getShortName(): String = "XoopsSmartyForeachItemShadowsFrom"

    override fun check(text: String, report: (IntRange, String, LocalQuickFix?) -> Unit) {
        for (m in SmartyUpgrade.foreachTag.findAll(text)) {
            val name = SmartyUpgrade.itemAttr.find(m.groupValues[1])?.groupValues?.get(1) ?: continue
            if (SmartyUpgrade.fromAttr.find(m.groupValues[1])?.groupValues?.get(1) != name) continue
            report(m.range, "foreach item=$name has the same name as from=\$$name: rename it to ${name}_item", RenameForeachItemFix())
        }
    }
}

class RenameForeachItemFix : XoopsFix() {
    override fun getFamilyName(): String = "Rename the foreach item"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val file = descriptor.psiElement?.containingFile ?: return
        val doc = PsiDocumentManager.getInstance(project).getDocument(file) ?: return
        val range = descriptor.textRangeInElement ?: return
        // Masking keeps offsets, so a commented-out `<{/foreach}>` cannot close the loop.
        val offsets = SmartyUpgrade.foreachItemRenameOffsets(SmartyText.mask(doc.text), range.startOffset) ?: return
        for (offset in offsets.sortedDescending()) doc.insertString(offset, "_item")
        PsiDocumentManager.getInstance(project).commitDocument(doc)
    }
}

/** `<{block_parent}>` / `<{block_child}>` → `<{$smarty.block.parent}>` / `<{$smarty.block.child}>`. */
class XoopsSmartyBlockShorthandInspection : SmartyUpgradeInspection() {
    override fun getShortName(): String = "XoopsSmartyBlockShorthand"

    override fun check(text: String, report: (IntRange, String, LocalQuickFix?) -> Unit) {
        for (m in SmartyUpgrade.blockShorthand.findAll(text)) {
            report(m.range, "${m.value} is not Smarty: use <{\$smarty.block.${m.groupValues[1]}}>", BlockShorthandFix())
        }
    }
}

class BlockShorthandFix : SmartyRewriteFix(SmartyUpgrade.blockShorthand) {
    override fun getFamilyName(): String = "Use \$smarty.block"
    override fun rewrite(match: MatchResult, doc: Document, start: Int): String = "<{\$smarty.block.${match.groupValues[1]}}>"
}

/** `|date_format:'%d.%m.%Y'` runs strftime(), deprecated since PHP 8.1 and gone in Smarty 5. */
class XoopsSmartyDateFormatStrftimeInspection : SmartyUpgradeInspection() {
    override fun getShortName(): String = "XoopsSmartyDateFormatStrftime"

    override fun check(text: String, report: (IntRange, String, LocalQuickFix?) -> Unit) {
        for (m in SmartyUpgrade.dateFormat.findAll(text)) {
            val format = m.groupValues[3]
            val fixed = SmartyUpgrade.dateFormatFix(format)
            if (fixed != null) {
                report(m.range, "date_format '$format' uses strftime() (deprecated in PHP 8.1): use '$fixed'", DateFormatFix())
            } else {
                report(m.range, "date_format '$format' uses strftime() (deprecated in PHP 8.1); its locale tokens or literal text need a manual rewrite", null)
            }
        }
    }
}

class DateFormatFix : SmartyRewriteFix(SmartyUpgrade.dateFormat) {
    override fun getFamilyName(): String = "Use date() format codes"
    override fun rewrite(match: MatchResult, doc: Document, start: Int): String? {
        val fixed = SmartyUpgrade.dateFormatFix(match.groupValues[3]) ?: return null
        return match.groupValues[1] + match.groupValues[2] + fixed + match.groupValues[2]
    }
}

/** Smarty 5 rework with no mechanical fix; off by default, for a tree being prepared for Smarty 5. */
class XoopsSmarty5TemplatePrepInspection : SmartyUpgradeInspection() {
    override fun getShortName(): String = "XoopsSmarty5TemplatePrep"

    override fun check(text: String, report: (IntRange, String, LocalQuickFix?) -> Unit) {
        for ((pattern, message) in SmartyUpgrade.smarty5Prep) {
            for (m in pattern.findAll(text)) report(m.range, "Smarty 5: $message", null)
        }
    }
}
