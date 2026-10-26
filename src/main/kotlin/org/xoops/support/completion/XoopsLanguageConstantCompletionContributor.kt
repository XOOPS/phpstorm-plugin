package org.xoops.support.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

/**
 * Completes _MI_ / _AM_ / _MD_ / _MB_ / _CO_ language constants discovered
 * from define() lines in the project's language/ files.
 *
 * The scan result lives in [XoopsLanguageConstantCache], a project service that is
 * invalidated on PSI change and disposed with the plugin.
 */
class XoopsLanguageConstantCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet,
                ) {
                    if (!result.prefixMatcher.prefix.startsWith("_")) {
                        return
                    }
                    val project = parameters.position.project
                    for ((name, origin) in XoopsLanguageConstantCache.getInstance(project).constants()) {
                        if (result.prefixMatcher.prefixMatches(name)) {
                            result.addElement(
                                LookupElementBuilder.create(name)
                                    .withTypeText(origin, true),
                            )
                        }
                    }
                }
            },
        )
    }
}
