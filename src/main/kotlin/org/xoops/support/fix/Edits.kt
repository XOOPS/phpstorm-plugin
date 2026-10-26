package org.xoops.support.fix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.FunctionReference

/**
 * Document edits whose offsets are computed from the live PSI at apply time.
 * A fix never stores an offset: the problem descriptor hands it the element,
 * the element knows where it is now, after whatever earlier fixes did.
 */
object Edits {

    private fun document(project: Project, element: PsiElement): Document? =
        PsiDocumentManager.getInstance(project).getDocument(element.containingFile)

    private fun commit(project: Project, doc: Document) =
        PsiDocumentManager.getInstance(project).commitDocument(doc)

    /**
     * Inserts [code] (one or more lines, unindented) as its own line(s) directly before
     * the line that starts with [anchor]. If [anchor] shares its line with earlier code,
     * the code goes in front of [anchor] and the anchor is pushed to a fresh line.
     */
    fun insertLinesBefore(project: Project, anchor: PsiElement, code: String) {
        val doc = document(project, anchor) ?: return
        val start = anchor.textRange.startOffset
        val line = doc.getLineNumber(start)
        val lineStart = doc.getLineStartOffset(line)
        val prefix = doc.getText(TextRange(lineStart, start))
        val indent = prefix.takeWhile { it == ' ' || it == '\t' }
        val block = code.removeSuffix("\n").lines().joinToString("\n") { if (it.isEmpty()) it else indent + it }
        if (prefix.isBlank()) {
            doc.insertString(lineStart, "$block\n")
        } else {
            doc.insertString(start, block.trimStart() + "\n" + indent)
        }
        commit(project, doc)
    }

    /** Inserts [code] as its own line directly after the line containing the end of [anchor]. */
    fun insertLinesAfter(project: Project, anchor: PsiElement, code: String) {
        val doc = document(project, anchor) ?: return
        val end = anchor.textRange.endOffset
        val line = doc.getLineNumber(end)
        val lineEnd = doc.getLineEndOffset(line)
        val lineStart = doc.getLineStartOffset(line)
        val indent = doc.getText(TextRange(lineStart, lineEnd)).takeWhile { it == ' ' || it == '\t' }
        val block = code.trimEnd('\n').lines().joinToString("\n") { if (it.isEmpty()) it else indent + it }
        doc.insertString(lineEnd, "\n$block")
        commit(project, doc)
    }

    fun insertAt(project: Project, element: PsiElement, offset: Int, text: String) {
        val doc = document(project, element) ?: return
        doc.insertString(offset, text)
        commit(project, doc)
    }

    fun deleteRange(project: Project, element: PsiElement, start: Int, end: Int) {
        val doc = document(project, element) ?: return
        doc.deleteString(start, end)
        commit(project, doc)
    }

    fun replace(project: Project, element: PsiElement, text: String) {
        val doc = document(project, element) ?: return
        val range = element.textRange
        doc.replaceString(range.startOffset, range.endOffset, text)
        commit(project, doc)
    }

    /** Removes a statement together with its line when nothing else shares that line. */
    fun deleteStatementLine(project: Project, statement: PsiElement) {
        val range = statement.textRange
        deleteLineRange(project, statement, range.startOffset, range.endOffset)
    }

    /** Removes [start, end) together with its line when nothing else shares that line. */
    fun deleteLineRange(project: Project, element: PsiElement, start: Int, end: Int) {
        val doc = document(project, element) ?: return
        val range = TextRange(start, end)
        val line = doc.getLineNumber(range.startOffset)
        val lineStart = doc.getLineStartOffset(line)
        val lineEnd = doc.getLineEndOffset(line)
        val before = doc.getText(TextRange(lineStart, range.startOffset))
        val after = doc.getText(TextRange(range.endOffset, lineEnd))
        if (before.isBlank() && after.isBlank()) {
            val deleteEnd = if (lineEnd < doc.textLength) lineEnd + 1 else lineEnd
            doc.deleteString(lineStart, deleteEnd)
        } else {
            doc.deleteString(range.startOffset, range.endOffset)
        }
        commit(project, doc)
    }

    /** `foo(` → `bar(`, keeping arguments. */
    fun renameCall(project: Project, call: FunctionReference, newName: String) {
        val nameNode = call.nameNode ?: return
        val range = nameNode.textRange
        val doc = document(project, call) ?: return
        doc.replaceString(range.startOffset, range.endOffset, newName)
        commit(project, doc)
    }
}

/** Quick-fix base: batch availability is an explicit decision per fix. */
abstract class XoopsFix(private val batchSafe: Boolean = true) : LocalQuickFix {
    override fun availableInBatchMode(): Boolean = batchSafe
}
