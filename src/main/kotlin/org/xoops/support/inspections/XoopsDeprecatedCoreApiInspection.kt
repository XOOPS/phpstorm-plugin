package org.xoops.support.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.php.lang.psi.elements.ClassReference
import com.jetbrains.php.lang.psi.elements.ConstantReference
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.Include
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.NewExpression
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor
import org.xoops.support.psi.Php

/**
 * Core APIs the 2.7.4 tree marks `@deprecated` (list generated from the real
 * `class/`, `kernel/` and `include/` docblocks, 2026-10-22) plus the pre-2.5 idioms that
 * still surface in old modules. PhpStorm's own deprecation inspection needs the core in the
 * project; this one works on a standalone module and says what to use instead. A rename fix
 * is offered only where the replacement takes the same arguments.
 */
class XoopsDeprecatedCoreApiInspection : XoopsInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PhpElementVisitor() {

            override fun visitPhpClassReference(reference: ClassReference) {
                if (skipConvention(reference.containingFile)) return
                val name = reference.name ?: return
                val advice = classes[name] ?: return
                report(reference, "XOOPS: $name is deprecated — $advice")
            }

            override fun visitPhpConstantReference(constantReference: ConstantReference) {
                if (skipConvention(constantReference.containingFile)) return
                val name = constantReference.name ?: return
                if (!name.startsWith("XOBJ_DTYPE_UNICODE_")) return
                report(
                    constantReference,
                    "XOOPS: $name is deprecated since 2.7.3 — the UNICODE dtypes still decide how stored rows are decoded; decode and re-save existing values before switching to ${name.replace("UNICODE_", "")}",
                )
            }

            override fun visitPhpNewExpression(expression: NewExpression) {
                if (skipConvention(expression.containingFile)) return
                val fqn = expression.classReference?.fqn ?: return
                if (!fqn.equals("\\ModuleAdmin", true) || expression.parameters.isNotEmpty()) return
                report(
                    expression,
                    "XOOPS: new ModuleAdmin() is the Frameworks/moduleclasses admin; use \\Xmf\\Module\\Admin::getInstance()",
                    ReplaceTextFix(expression.text, "\\Xmf\\Module\\Admin::getInstance()"),
                )
            }

            override fun visitPhpInclude(include: Include) {
                if (skipConvention(include.containingFile)) return
                // Only a core-rooted path: a module may ship its own class/xoopsuser.php under __DIR__.
                if (!include.text.contains("XOOPS_ROOT_PATH") && !include.text.contains("->path(")) return
                for (literal in PsiTreeUtil.findChildrenOfType(include, StringLiteralExpression::class.java)) {
                    val path = literal.contents.removePrefix("/")
                    val (old, new) = movedIncludes.entries.firstOrNull { path == it.key }?.toPair() ?: continue
                    report(
                        literal,
                        "XOOPS: $old is a deprecated stub since 2.5.4 that only includes $new; include $new",
                        ReplaceTextFix(literal.text, literal.text.replace(old, new)),
                    )
                }
            }

            override fun visitPhpFunctionCall(reference: FunctionReference) {
                if (reference is MethodReference || skipConvention(reference.containingFile)) return
                val name = reference.name ?: return
                if (name.equals("xoops_load", true)) {
                    checkLoadAlias(reference)
                    return
                }
                val entry = functions.entries.firstOrNull { it.key.equals(name, true) } ?: return
                val (advice, replacement) = entry.value
                val fixes = if (replacement != null) arrayOf<LocalQuickFix>(RenameCallFix(entry.key, replacement)) else emptyArray()
                report(reference, "XOOPS: ${entry.key}() is deprecated — $advice", *fixes)
            }

            override fun visitPhpMethodReference(reference: MethodReference) {
                if (skipConvention(reference.containingFile)) return
                val name = reference.name ?: return
                val receiver = Php.unwrap(reference.classReference)?.text ?: ""
                if (name.startsWith("Xoops") && (receiver == "parent" || receiver == "\$this" || receiver == name)) {
                    report(
                        reference,
                        "XOOPS: PHP 4 constructor call $receiver::$name() — PHP 8 runs it as an ordinary method; call parent::__construct()",
                        RenameCallFix(name, "__construct"),
                    )
                    return
                }
                val entry = methods.entries.firstOrNull { it.key.equals(name, true) } ?: return
                val (advice, replacement) = entry.value
                // The sanitiser family takes extra flags the replacement does not: rename only the one-argument form.
                val sanitiser = entry.key.startsWith("make") || entry.key.startsWith("oops")
                val sameArity = !sanitiser || reference.parameters.size <= 1
                val fixes = if (replacement != null && sameArity) arrayOf<LocalQuickFix>(RenameCallFix(entry.key, replacement)) else emptyArray()
                report(reference, "XOOPS: ${entry.key}() is deprecated — $advice", *fixes)
            }

            /** `xoops_load('cache')`: the core maps the short name and logs a deprecation on every load. */
            private fun checkLoadAlias(call: FunctionReference) {
                val params = call.parameters
                val literal = params.firstOrNull() as? StringLiteralExpression ?: return
                val type = (params.getOrNull(1) as? StringLiteralExpression)?.contents?.lowercase()
                if (params.size > 1 && type != "core" && type != "class") return
                val target = loadAliases[literal.contents.lowercase()] ?: return
                val quote = literal.text.first()
                report(
                    literal,
                    "XOOPS: xoops_load('${literal.contents}') is deprecated; use xoops_load('$target')",
                    ReplaceTextFix(literal.text, "$quote$target$quote"),
                )
            }

            private fun report(element: PsiElement, message: String, vararg fixes: LocalQuickFix) =
                holder.registerProblem(element, message, ProblemHighlightType.LIKE_DEPRECATED, *fixes)
        }
    }

    companion object {
        /** `XoopsLoad::load()`'s own `$deprecated` map (2.7.4 `class/xoopsload.php`). */
        val loadAliases = mapOf(
            "uploader" to "xoopsmediauploader", "utility" to "xoopsutility", "captcha" to "xoopscaptcha",
            "cache" to "xoopscache", "file" to "xoopsfile", "model" to "xoopsmodelfactory",
            "calendar" to "xoopscalendar", "userutility" to "xoopsuserutility",
        )

        /**
         * `class/` stubs that log a deprecation and only include their replacement (2.7.4). `errorhandler.php`
         * still declares XoopsErrorHandler and `xoopscommments.php` is gone, so neither is a plain move.
         */
        val movedIncludes = mapOf(
            "class/xoopsobject.php" to "kernel/object.php",
            "class/xoopsmodule.php" to "kernel/module.php",
            "class/xoopsuser.php" to "kernel/user.php",
            "class/mimetypes.inc.php" to "include/mimetypes.inc.php",
        )

        val classes = mapOf(
            "XoopsTree" to "use XoopsObjectTree (class/tree.php) with makeSelectElement()",
            "XoopsTopic" to "use the module's own category handler; class/xoopstopic.php is a 2.0 leftover",
            "XoopsComments" to "use the XoopsComment handler (kernel/comment.php) since 2.5.4",
            "Snoopy" to "use cURL or a PSR-18 client; class/snoopy.php will be removed",
            "XoopsErrorHandler" to "use XoopsLogger::getInstance()",
        )

        /** name → (advice, same-signature replacement or null). */
        val functions: Map<String, Pair<String, String?>> = mapOf(
            "xoops_load_lang_file" to ("use xoops_loadLanguage(\$name, \$module)" to null),
            "xoops_refcheck" to ("referer checks are done by XoopsSecurity; delete the call" to null),
            "xoops_getLinkedUnameFromId" to ("use XoopsUserUtility::getUnameFromId(\$uid, true)" to null),
            "showbanner" to ("use echo xoops_getbanner() (showbanner() echoes, xoops_getbanner() returns)" to null),
            "getTheme" to ("read \$GLOBALS['xoopsConfig']['theme_set']" to null),
            "getcss" to ("use xoTheme->addStylesheet()" to null),
            "getMailer" to ("use xoops_getMailer()" to "xoops_getMailer"),
            "OpenWaitBox" to ("2.0 theme function; render a template instead" to null),
            "CloseWaitBox" to ("2.0 theme function; render a template instead" to null),
            "xoops_getOption" to ("read \$GLOBALS['xoopsConfig'][…] directly" to null),
            "xoops_getConfigOption" to ("use the config handler: xoops_getHandler('config')->getConfigsByCat()" to null),
            "xoops_setConfigOption" to ("configs are not set at runtime; use the config handler" to null),
            "xoops_getModuleOption" to ("use \$helper->getConfig(\$option) (Xmf\\Module\\Helper)" to null),
            "xoops_getUrlDomain" to ("use parse_url(\$url, PHP_URL_HOST)" to null),
            "openTable" to ("2.0 theme function; use a template" to null),
            "closeTable" to ("2.0 theme function; use a template" to null),
            "themecenterposts" to ("2.0 theme function; use a template" to null),
        )

        /** method name → (advice, same-signature replacement or null). Names must be unique to the core API. */
        val methods: Map<String, Pair<String, String?>> = mapOf(
            // XoopsObject
            "destoryVars" to ("use destroyVars()" to "destroyVars"),
            "setFormVars" to ("set the variables explicitly with setVar()" to null),
            "getByLimit" to ("use getObjects() with a Criteria that carries setLimit()/setStart()" to null),
            "convertResultSet" to ("use getObjects() / getAll()" to null),
            // MyTextSanitizer
            "stripSlashesGPC" to ("magic quotes are gone; use the value as is" to null),
            "codeSanitizer" to ("use displayTarea()" to null),
            "sanitizeForDisplay" to ("use displayTarea()" to null),
            "sanitizeForPreview" to ("use previewTarea()" to null),
            "makeTboxData4Save" to ("quote SQL values with \$db->quote() instead" to null),
            "makeTboxData4Show" to ("use htmlSpecialChars()" to "htmlSpecialChars"),
            "makeTboxData4Edit" to ("use htmlSpecialChars()" to "htmlSpecialChars"),
            "makeTboxData4Preview" to ("use htmlSpecialChars()" to "htmlSpecialChars"),
            "makeTboxData4PreviewInForm" to ("use htmlSpecialChars()" to "htmlSpecialChars"),
            "makeTareaData4Save" to ("quote SQL values with \$db->quote() instead" to null),
            "makeTareaData4Show" to ("use displayTarea(\$text, 1, …) — its \$html default is 0 where this one defaults to 1" to null),
            "makeTareaData4Edit" to ("use htmlSpecialChars()" to "htmlSpecialChars"),
            "makeTareaData4Preview" to ("use previewTarea(\$text, 1, …) — its \$html default is 0 where this one defaults to 1" to null),
            "makeTareaData4PreviewInForm" to ("use htmlSpecialChars()" to "htmlSpecialChars"),
            "makeTareaData4InsideQuotes" to ("use htmlSpecialChars()" to "htmlSpecialChars"),
            "oopsStripSlashesGPC" to ("magic quotes are gone; use the value as is" to null),
            "oopsStripSlashesRT" to ("use stripslashes()" to null),
            "oopsAddSlashes" to ("quote SQL values with \$db->quote() instead" to null),
            "oopsHtmlSpecialChars" to ("use htmlSpecialChars()" to "htmlSpecialChars"),
            "oopsNl2Br" to ("use nl2br()" to null),
            "addSlashes" to ("quote SQL values with \$db->quote() instead of addSlashes()" to null),
            // XoopsObjectTree
            "makeSelBox" to ("use makeSelectElement() since 2.5.9" to null),
            // XoopsLogger
            "dumpAll" to ("use dump('all')" to null),
            "dumpBlocks" to ("use dump('blocks')" to null),
            "dumpExtra" to ("use dump('extra')" to null),
            "dumpQueries" to ("use dump('queries')" to null),
            // XoopsUser
            "incrementPost" to ("use the member handler: updateUserByField(\$user, 'posts', …)" to null),
            "getProfile" to ("use the profile module handler" to null),
            // XoopsFormElement
            "setNocolspan" to ("avoid; column spans are a theme concern" to null),
            "getNocolspan" to ("avoid; column spans are a theme concern" to null),
            "setFormType" to ("avoid; the element type is fixed by its class" to null),
            "getFormType" to ("avoid; the element type is fixed by its class" to null),
            // config / blocks / model
            "deleteConfigOption" to ("use the config handler's deleteConfig()" to null),
            "getCatByModule" to ("use getConfigsByModule()" to null),
            "synchronization" to ("use the model's getAll()/getObjects()" to null),
            "getAllBlocksByGroup" to ("use XoopsBlockHandler::getAllBlocksByGroup() through xoops_getHandler('block')" to null),
            // Smarty 2 names on XoopsTpl (Smarty 3/4/5)
            "assign_by_ref" to ("use assign()" to "assign"),
            "append_by_ref" to ("use append()" to "append"),
            "assignByRef" to ("Smarty 5 removed it; use assign()" to "assign"),
            "appendByRef" to ("Smarty 5 removed it; use append()" to "append"),
            "clear_assign" to ("use clearAssign()" to "clearAssign"),
            "clear_all_assign" to ("use clearAllAssign()" to "clearAllAssign"),
            "get_template_vars" to ("use getTemplateVars()" to "getTemplateVars"),
            "get_config_vars" to ("use getConfigVars()" to "getConfigVars"),
            "template_exists" to ("use templateExists()" to "templateExists"),
            "is_cached" to ("use isCached()" to "isCached"),
            "clear_cache" to ("use clearCache()" to "clearCache"),
            "clear_all_cache" to ("use clearAllCache()" to "clearAllCache"),
            "clear_compiled_tpl" to ("use clearCompiledTemplate()" to "clearCompiledTemplate"),
            "config_load" to ("use configLoad()" to "configLoad"),
            "register_function" to ("use registerPlugin('function', \$name, \$callback)" to null),
            "register_modifier" to ("use registerPlugin('modifier', \$name, \$callback)" to null),
            "register_block" to ("use registerPlugin('block', \$name, \$callback)" to null),
            "register_resource" to ("use registerResource()" to null),
            "register_object" to ("use registerObject()" to null),
            "register_prefilter" to ("use registerFilter('pre', \$callback)" to null),
            "register_postfilter" to ("use registerFilter('post', \$callback)" to null),
            "register_outputfilter" to ("use registerFilter('output', \$callback)" to null),
            "xoops_setTemplateDir" to ("set the property: \$tpl->template_dir" to null),
            "xoops_getTemplateDir" to ("read the property: \$tpl->template_dir" to null),
            "xoops_setDebugging" to ("set the property: \$tpl->debugging" to null),
            "xoops_setCaching" to ("set the property: \$tpl->caching" to null),
            "xoops_setCompileDir" to ("set the property: \$tpl->compile_dir" to null),
            "xoops_setCacheDir" to ("set the property: \$tpl->cache_dir" to null),
            "xoops_setCacheTime" to ("set the property: \$tpl->cache_lifetime" to null),
            "xoops_canUpdateFromFile" to ("read the property: \$tpl->compile_check" to null),
        )
    }
}
