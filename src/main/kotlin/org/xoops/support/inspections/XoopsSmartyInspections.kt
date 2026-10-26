package org.xoops.support.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.xoops.support.fix.XoopsFix
import org.xoops.support.psi.Php
import org.xoops.support.psi.XoopsPaths

/**
 * Template text rules. XOOPS compiles Smarty with `<{` `}>` delimiters, so a bare `{if}` or
 * `{$var}` is printed literally; and `$smarty.get.*` / `$smarty.request.*` is the request,
 * unescaped unless the template says `|escape`. Comments (`<{* *}>`) and `<{literal}>` blocks
 * are masked before matching; nothing else in a template is parsed.
 */
object SmartyText {
    /** Keyword tags need a space or `}` right after the keyword: `{if $x}`, `{else}`, never JavaScript's `{if(x)`. */
    private val smartyTag = Regex("""(?<![<\\$])\{(\$[A-Za-z_][\w.\[\]'"$>-]*(?:\|[^}\n]*)?|/?(?:if|elseif|else|foreach|foreachelse|section|sectionelse|include|include_once|assign|capture|strip|while|literal|php)(?=[\s}])[^}\n]*)\}""")
    private val safeModifiers = Regex("""\|\s*(escape|json_encode|intval|int|float|floatval|urlencode|rawurlencode|number_format|date_format|count)\b""")
    private val requestVar = Regex("""<\{\s*(\${'$'}smarty\.(?:get|post|request|cookie|server)\.[\w.\[\]'"-]+)((?:\|[^}]*)?)\s*\}>""")

    fun mask(text: String): String {
        val sb = StringBuilder(XoopsUnregisteredTemplateInspection.maskSmartyComments(text))
        for (m in Regex("""(?s)<\{\s*literal\s*\}>.*?<\{\s*/literal\s*\}>""").findAll(sb)) {
            for (k in m.range) if (sb[k] != '\n' && sb[k] != '\r') sb.setCharAt(k, ' ')
        }
        return sb.toString()
    }

    /** Script and style blocks are JavaScript / CSS: braces there are never Smarty. */
    private fun maskScripts(text: String): String {
        val sb = StringBuilder(text)
        for (m in Regex("""(?is)<(script|style)\b.*?</\1\s*>""").findAll(sb)) {
            for (k in m.range) if (sb[k] != '\n' && sb[k] != '\r') sb.setCharAt(k, ' ')
        }
        return sb.toString()
    }

    fun bareTags(text: String): Sequence<MatchResult> = smartyTag.findAll(maskScripts(mask(text)))

    /**
     * A `.tpl` the template rules read. Test trees are not: upgrade tests write broken templates on
     * purpose (`<{php}>`, `{$x}`) and copy whole themes into a scratch `tests/tmp/` — 1,150 of the
     * 2,241 findings on XoopsCore27 came from there. The PHP rules skip `tests/` the same way.
     */
    fun isInspectedTemplate(file: PsiFile): Boolean =
        file.name.lowercase().endsWith(".tpl") && Php.isPrimary(file) && !XoopsPaths.isExcluded(file) && !XoopsPaths.isTestTree(file)

    /** Request variables with no escaping or encoding modifier; `|json_encode`, `|intval` … already fix the output. */
    fun unescapedRequestVars(text: String): Sequence<MatchResult> =
        requestVar.findAll(mask(text)).filter { m -> !safeModifiers.containsMatchIn(m.groupValues[2]) }
}

/** `{if …}` / `{$var}` in a XOOPS template: Smarty never sees it. */
class XoopsSmartyDelimiterInspection : LocalInspectionTool() {
    override fun getShortName(): String = "XoopsWrongSmartyDelimiter"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                if (!SmartyText.isInspectedTemplate(file)) return
                for (m in SmartyText.bareTags(file.text)) {
                    holder.registerProblem(
                        file,
                        "XOOPS: Smarty tag without the XOOPS delimiters — write <{${m.groupValues[1].take(20)}…}>; a bare { } is printed as text",
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        TextRange(m.range.first, m.range.last + 1),
                        WrapSmartyDelimiterFix(),
                    )
                }
            }
        }
    }
}

class WrapSmartyDelimiterFix : XoopsFix() {
    override fun getFamilyName(): String = "Wrap in <{ }>"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val file = descriptor.psiElement?.containingFile ?: return
        val doc = PsiDocumentManager.getInstance(project).getDocument(file) ?: return
        val range = descriptor.textRangeInElement ?: return
        val text = doc.getText(range)
        if (!text.startsWith("{") || !text.endsWith("}")) return
        doc.replaceString(range.startOffset, range.endOffset, "<$text>")
        PsiDocumentManager.getInstance(project).commitDocument(doc)
    }
}

/** `<{$smarty.get.x}>` without `|escape`: the request printed into the page. */
class XoopsSmartyRequestEscapeInspection : LocalInspectionTool() {
    override fun getShortName(): String = "XoopsSmartyRequestEscape"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                if (!SmartyText.isInspectedTemplate(file)) return
                for (m in SmartyText.unescapedRequestVars(file.text)) {
                    holder.registerProblem(
                        file,
                        "XOOPS: ${m.groupValues[1]} is printed unescaped — add |escape",
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        TextRange(m.range.first, m.range.last + 1),
                        AddEscapeModifierFix(),
                    )
                }
            }
        }
    }
}

class AddEscapeModifierFix : XoopsFix() {
    override fun getFamilyName(): String = "Add |escape"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val file = descriptor.psiElement?.containingFile ?: return
        val doc = PsiDocumentManager.getInstance(project).getDocument(file) ?: return
        val range = descriptor.textRangeInElement ?: return
        val text = doc.getText(range)
        if (!text.endsWith("}>") || text.contains("|escape")) return
        val close = range.endOffset - 2
        var insertAt = close
        while (insertAt > range.startOffset && doc.getText(TextRange(insertAt - 1, insertAt)).isBlank()) insertAt--
        doc.insertString(insertAt, "|escape")
        PsiDocumentManager.getInstance(project).commitDocument(doc)
    }
}
