package org.xoops.support.inspections

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.ParenthesizedExpression
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpTypedElement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.UnaryExpression
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor
import org.xoops.support.fix.Edits
import org.xoops.support.fix.XoopsFix
import org.xoops.support.psi.Php

/**
 * `getVar()` is not an escaper: the field's DTYPE decides. `XOBJ_DTYPE_OTHER` / `SOURCE`
 * come back raw whatever the format, format `'n'` is raw for every DTYPE, and a numeric
 * DTYPE needs no escaping at all. The DTYPE is read from the `initVar()` call of the
 * receiver's class (PhpStorm's type of the receiver, superclasses included); when it cannot
 * be resolved the finding stays, except for an id-like key inside a URL query.
 *
 * Context is decided from the string literal that precedes the accessor in the same
 * concatenation — PHP code such as `$title = …` or an array key is never an attribute.
 * Inside a URL query (`'?src=' . …`) the right tool is rawurlencode(). The fix wraps the
 * complete call expression, receiver included.
 */
class XoopsAttributeEscapeInspection : XoopsInspection() {

    enum class Context { ATTRIBUTE, URL }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {
            override fun visitPhpMethodReference(reference: MethodReference) {
                if (!"getVar".equals(reference.name, true) || skip(reference.containingFile)) return
                if (alreadyEscaped(reference)) return
                val context = contextOf(reference) ?: return
                val key = Php.firstStringArg(reference) ?: ""
                val dtype = fieldDtype(reference) ?: moduleDtype(reference, key)
                val format = (reference.parameters.getOrNull(1) as? StringLiteralExpression)?.contents?.lowercase() ?: "s"
                if (dtype != null && dtype in numeric) return
                if (dtype == null && format != "n" && idLike.containsMatchIn(key)) return
                // OTHER is raw in every format; SOURCE is raw for show/preview but htmlspecialchars'd for 'e'.
                val raw = format == "n" || dtype == "XOBJ_DTYPE_OTHER" ||
                    (dtype == "XOBJ_DTYPE_SOURCE" && format in setOf("s", "show", "p", "preview"))
                val label = if (format == "n") "format 'n'" else dtype ?: "unknown DTYPE"
                when (context) {
                    Context.ATTRIBUTE -> {
                        if (dtype != null && dtype in sanitised && !raw) return
                        // Unknown DTYPE: most string DTYPEs already come back htmlspecialchars'd (ENT_QUOTES), so
                        // wrapping them again would print &amp;#039; — the fix is offered only for a raw value.
                        if (raw) holder.registerProblem(
                            reference,
                            "XOOPS: getVar() returns this field raw ($label) — escape it at the attribute",
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                            EscapeAccessorFix(Context.ATTRIBUTE),
                        ) else holder.registerProblem(
                            reference as PsiElement,
                            "XOOPS: getVar() inside an HTML attribute, DTYPE unknown — TXTBOX/TXTAREA/URL/EMAIL are already " +
                                "escaped by getVar(); OTHER/SOURCE are raw: check the field's initVar()",
                            ProblemHighlightType.WEAK_WARNING,
                        )
                    }
                    Context.URL -> {
                        holder.registerProblem(
                            reference,
                            "XOOPS: getVar() output inside a URL query string should be rawurlencode()d",
                            ProblemHighlightType.WEAK_WARNING,
                            EscapeAccessorFix(Context.URL),
                        )
                    }
                }
            }
        }
    }

    companion object {
        private val attribute = Regex("""(?i)\b(?:src|href|value|title|alt|action|data-[\w-]+)\s*=\s*(["'])""")
        private val urlParam = Regex("""[?&](?:amp;)?[A-Za-z_][\w\[\]-]*=$""")
        private val idLike = Regex("""(?i)(id|(^|_)(uid|gid|mid|cid|pid|bid|tid|count|weight|order|status|page|limit|start|time|date|hits|counter))$""")
        private val escapers = setOf(
            "htmlspecialchars", "htmlentities", "rawurlencode", "urlencode", "intval", "floatval",
            "htmlSpecialChars", "escape", "e",
        )
        val numeric = setOf("XOBJ_DTYPE_INT", "XOBJ_DTYPE_FLOAT", "XOBJ_DTYPE_DECIMAL", "XOBJ_DTYPE_LTIME", "XOBJ_DTYPE_STIME", "XOBJ_DTYPE_MTIME")
        val rawTypes = setOf("XOBJ_DTYPE_OTHER", "XOBJ_DTYPE_SOURCE")
        /** getVar('x') on these runs htmlSpecialChars (ENT_QUOTES) for the default and 'e' formats. */
        val sanitised = setOf("XOBJ_DTYPE_TXTBOX", "XOBJ_DTYPE_TXTAREA", "XOBJ_DTYPE_URL", "XOBJ_DTYPE_EMAIL", "XOBJ_DTYPE_UNICODE_TXTBOX", "XOBJ_DTYPE_UNICODE_TXTAREA", "XOBJ_DTYPE_UNICODE_URL", "XOBJ_DTYPE_UNICODE_EMAIL")

        fun contextOf(reference: MethodReference): Context? {
            val (operands, index) = Php.concatOperands(reference) ?: return null
            // Only the accessor's own value reaches the output: `($o->getVar('x') ? $a : $b)` prints $a or $b.
            val direct = Php.unwrap(operands[index])
            val printed = direct == reference ||
                (direct is UnaryExpression && direct.operation?.text?.startsWith("(") == true && Php.unwrap(direct.value) == reference)
            if (!printed) return null
            val literal = (index - 1 downTo 0).asSequence()
                .map { operands[it] }
                .filterIsInstance<StringLiteralExpression>()
                .firstOrNull() ?: return null
            val text = literal.contents
            if (urlParam.containsMatchIn(text)) return Context.URL
            val match = attribute.findAll(text).lastOrNull() ?: return null
            // HTML context only: `"UPDATE t SET title='"` also ends in `title='` but there is no open tag before it.
            val tagStart = Regex("<[A-Za-z]").findAll(text.substring(0, match.range.first)).lastOrNull() ?: return null
            if (text.substring(tagStart.range.first, match.range.first).contains('>')) return null
            val quote = match.groupValues[1]
            return if (text.indexOf(quote, match.range.last + 1) < 0) Context.ATTRIBUTE else null
        }

        /** The `XOBJ_DTYPE_*` constant the receiver's class declares for the accessed key, or null. */
        fun fieldDtype(reference: MethodReference): String? {
            val key = Php.firstStringArg(reference) ?: return null
            val receiver = Php.unwrap(reference.classReference) as? PhpTypedElement ?: return null
            val index = PhpIndex.getInstance(reference.project)
            val seen = HashSet<String>()
            val queue = ArrayDeque<PhpClass>()
            for (t in receiver.type.types) {
                val fqn = t.trimStart('\\')
                if (fqn.isEmpty() || fqn.startsWith("?") || fqn == "mixed") continue
                index.getAnyByFQN(fqn).forEach(queue::add)
                if (queue.isEmpty()) index.getClassesByName(fqn.substringAfterLast('\\')).forEach(queue::add)
            }
            while (queue.isNotEmpty()) {
                val clazz = queue.removeFirst()
                if (!seen.add(clazz.fqn)) continue
                for (call in Php.methodCalls(clazz, "initVar")) {
                    if (Php.firstStringArg(call) != key) continue
                    val constant = call.parameters.getOrNull(1)?.text?.trimStart('\\') ?: continue
                    return constant.substringAfterLast("::").uppercase()
                }
                clazz.superClass?.let(queue::add)
                if (seen.size > 8) break
            }
            return null
        }

        /**
         * Fallback when PhpStorm cannot type the receiver (`foreach ($handler->getObjects() as $obj)`):
         * the one `initVar('key', …)` declared under the module's `class/` or `src/` tree. Two
         * different declarations for the same key mean the answer is unknown.
         */
        fun moduleDtype(reference: MethodReference, key: String): String? {
            if (key.isEmpty()) return null
            val file = reference.containingFile.virtualFile
            val root = org.xoops.support.psi.XoopsPaths.moduleRoot(file)
            val inModule = root?.let { declared(listOfNotNull(it.findChild("class"), it.findChild("src")), key) }.orEmpty()
            if (inModule.isNotEmpty()) return inModule.singleOrNull()
            // Not declared by the module (or a core page such as findusers.php / userinfo.php): the object is
            // a core one — `uname`, `com_email`, block `title` — declared once under kernel/.
            var core = file?.parent
            while (core != null && core.findFileByRelativePath("kernel/object.php") == null) core = core.parent
            return core?.findChild("kernel")?.let { declared(listOf(it), key).singleOrNull() }
        }

        private fun declared(dirs: List<com.intellij.openapi.vfs.VirtualFile>, key: String): Set<String> {
            val needle = "initVar('$key'"
            val needle2 = "initVar(\"$key\""
            val found = LinkedHashSet<String>()
            for (dir in dirs) {
                com.intellij.openapi.vfs.VfsUtilCore.iterateChildrenRecursively(dir, null) { vf ->
                    if (!vf.isDirectory && vf.name.endsWith(".php") && vf.length < 400_000) {
                        val text = runCatching { com.intellij.openapi.vfs.VfsUtilCore.loadText(vf) }.getOrNull()
                        if (text != null && (text.contains(needle) || text.contains(needle2))) {
                            Regex("""initVar\(\s*['"]${Regex.escape(key)}['"]\s*,\s*\\?([A-Z_]+)""").findAll(text).forEach { found.add(it.groupValues[1]) }
                        }
                    }
                    found.size < 2
                }
            }
            return found
        }

        fun alreadyEscaped(reference: PsiElement): Boolean {
            var e: PsiElement = reference
            while (true) {
                val p = e.parent ?: return false
                when {
                    p is ParenthesizedExpression -> e = p
                    p is UnaryExpression && p.operation?.text?.startsWith("(") == true -> e = p
                    p is ParameterList -> {
                        val call = p.parent as? FunctionReference ?: return false
                        return call.name?.let { n -> escapers.any { it.equals(n, true) } } == true
                    }
                    else -> return false
                }
            }
        }
    }
}

class EscapeAccessorFix(private val context: XoopsAttributeEscapeInspection.Context) : XoopsFix() {
    override fun getFamilyName(): String = when (context) {
        XoopsAttributeEscapeInspection.Context.ATTRIBUTE -> "Wrap in htmlspecialchars((string) …, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8')"
        XoopsAttributeEscapeInspection.Context.URL -> "Wrap in rawurlencode((string) …)"
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val reference = descriptor.psiElement as? MethodReference ?: return
        if (XoopsAttributeEscapeInspection.alreadyEscaped(reference)) return
        val expr = reference.text
        val replacement = when (context) {
            XoopsAttributeEscapeInspection.Context.ATTRIBUTE ->
                "htmlspecialchars((string) $expr, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8')"
            // URL-encode the raw value: the default format already holds `&amp;`, which would become `%26amp%3B`.
            XoopsAttributeEscapeInspection.Context.URL -> "rawurlencode((string) ${rawFormat(reference)})"
        }
        Edits.replace(project, reference, replacement)
    }

    /** `$o->getVar('x')` / `$o->getVar('x', 'e')` → `$o->getVar('x', 'n')`; anything else is left as written. */
    private fun rawFormat(reference: MethodReference): String {
        val receiver = reference.classReference?.text ?: return reference.text
        val args = reference.parameters
        val format = (args.getOrNull(1) as? StringLiteralExpression)?.contents?.lowercase()
        val newArgs: List<String> = when {
            args.size == 1 -> listOf(args[0].text, "'n'")
            args.size >= 2 && format in setOf("s", "show", "e", "edit") -> args.mapIndexed { i, a -> if (i == 1) "'n'" else a.text }
            else -> return reference.text
        }
        return "$receiver->${reference.name}(${newArgs.joinToString(", ")})"
    }
}
