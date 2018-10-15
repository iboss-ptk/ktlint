package com.github.shyiko.ktlint.ruleset.standard

import com.github.shyiko.ktlint.core.Rule
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.lang.FileASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiComment
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.com.intellij.util.containers.Stack
import org.jetbrains.kotlin.lexer.KtToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtTypeConstraintList
import org.jetbrains.kotlin.psi.psiUtil.children
import org.jetbrains.kotlin.psi.psiUtil.nextLeafs
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.psi.stubs.elements.KtStubElementType
import org.jetbrains.kotlin.psi.stubs.elements.KtStubElementTypes

class IndentationRule : Rule("indent"), Rule.Modifier.RestrictToRoot {
    private var indentSize = -1
    private var continuationIndentSize = -1
    override fun visit(
        node: ASTNode,
        autoCorrect: Boolean,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit
    ) {
        val ec = EditorConfig.from(node as FileASTNode)
        indentSize = ec.indentSize
        continuationIndentSize = ec.continuationIndentSize

        if (indentSize <= 1) {
            return
        }

        exploreTree(node, emit, autoCorrect)
    }

    private fun exploreTree(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
        autoCorrect: Boolean
    ) {
        val scopeStack = Stack<IndentationScope>()
        val stack = Stack<ASTNode>()

        val scopeSet = setOf(
            ExpressionScope(
                listOf(KtStubElementTypes.DOT_QUALIFIED_EXPRESSION, KtNodeTypes.SAFE_ACCESS_EXPRESSION),
                listOf(KtTokens.DOT, KtTokens.SAFE_ACCESS)
            ),
            ExpressionScope(listOf(KtNodeTypes.BINARY_EXPRESSION), listOf(KtNodeTypes.OPERATION_REFERENCE)),
            InternalNodeScopeWithTriggerToken(
                KtStubElementTypes.PROPERTY,
                KtTokens.EQ
            ),
            InternalNodeScopeWithTriggerToken(
                KtStubElementTypes.FUNCTION,
                KtTokens.EQ
            ),
            LeafNodeScope(KtTokens.LBRACE, KtTokens.RBRACE),
            LeafNodeScope(KtTokens.LBRACKET, KtTokens.RBRACKET),
            LeafNodeScope(KtTokens.LPAR, KtTokens.RPAR),
            LeafNodeScope(KtTokens.LT, KtTokens.GT),
            PropertyAccessorScope
        )

        stack.push(node)

        while (!stack.empty()) {
            val current = stack.pop()
            val children = current.children()
            // prevSibling = arrow under when entry, immediate white space
            // it case

            val poppedScope = mutableSetOf<IndentationScope>()

            while (
                !scopeStack.empty() &&
                !poppedScope.contains(scopeStack.peek()) &&
                isOutOfScope(current, scopeStack.peek())
            ) {
                poppedScope.add(scopeStack.pop())
            }

            scopeSet.forEach {
                if (isInScope(current, it)) {
                    scopeStack.push(it)
                }
            }


            if (children.none()) {
                if (current is PsiWhiteSpace &&
                    current.treeNext !is PsiComment &&
                    current.treeNext.firstChildNode !is PsiComment &&
                    current.treeNext.elementType != KtTokens.WHERE_KEYWORD &&
                    !current.isPartOf(PsiComment::class) &&
                    !current.isPartOf(KtTypeConstraintList::class)
                ) {
                    val continuationCount = scopeStack.count { it.isContinuation }
                    val normalIndentationCount = scopeStack.count { !it.isContinuation }
                    handleNewline(current, emit, autoCorrect, normalIndentationCount, continuationCount)

                    if (current.textContains('\t')) {
                        handleTab(current, emit, autoCorrect)
                    }
                }
            } else {
                children
                    .toList()
                    .reversed()
                    .forEach { stack.push(it) }
            }
        }
    }

    private fun handleNewline(
        node: PsiWhiteSpace,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
        autoCorrect: Boolean,
        normalIndentationCount: Int,
        continuationIndentationCount: Int
    ) {
        val lines = node.text.split("\n")
        if (lines.size > 1) {
            val offset = node.startOffset + lines.first().length + 1

            lines.take(lines.count() - 1).forEach { indent ->
                if (indent != "") {
                    emit(
                        offset,
                        wrongIndentSizeMessage(
                            indent.length,
                            0
                        ),
                        false
                    )
                }
            }

            val indent = lines.last()

            val totalIndentationSize = normalIndentationCount * indentSize +
                continuationIndentationCount * continuationIndentSize

            if (
            // parameter list wrapping enforced by ParameterListWrappingRule
                !node.isPartOf(KtParameterList::class) &&
                indent.length != totalIndentationSize
            ) {
                emit(
                    offset,
                    wrongIndentSizeMessage(
                        indent.length,
                        totalIndentationSize
                    ),
                    false
                )
            }
        }
    }

    private fun handleTab(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
        autoCorrect: Boolean
    ) {
        emit(
            node.startOffset + node.text.indexOf('\t'),
            "Unexpected Tab character(s)",
            true
        )

        if (autoCorrect) {
            val tabToSpaceString = node.text.replace("\t", " ".repeat(indentSize))
            (node as LeafPsiElement).rawReplaceWithText(tabToSpaceString)
        }
    }

    private fun wrongIndentSizeMessage(actual: Int, expected: Int) =
        "Unexpected indentation ($actual) (it should be $expected)"
}

sealed class IndentationScope(val isContinuation: Boolean = false)

data class ExpressionScope(
    val elementTypes: List<IElementType>,
    val chainingElements: List<IElementType>
) : IndentationScope(isContinuation = true)

data class InternalNodeScopeWithTriggerToken(
    val elementType: KtStubElementType<*, *>,
    val trigger: KtToken
) : IndentationScope()

data class LeafNodeScope(val startToken: KtToken, val endToken: KtToken) : IndentationScope()

object PropertyAccessorScope : IndentationScope()

fun isInScope(node: ASTNode, indentationScope: IndentationScope): Boolean =
    when (indentationScope) {
        is ExpressionScope ->
            indentationScope.elementTypes.contains(node.elementType) &&
                !indentationScope.elementTypes.contains(node.treeParent?.elementType)

        is InternalNodeScopeWithTriggerToken ->
            node.elementType == indentationScope.elementType &&
                node.children().zip(node.children().drop(1))
                    .any { (a, b) ->
                        a.elementType == indentationScope.trigger &&
                            b is PsiWhiteSpace &&
                            b.textContains('\n')
                    }

        is LeafNodeScope -> {
            val lookAhead = node
                .psi
                .nextLeafs
                .takeWhile { !(it is PsiWhiteSpace && it.textContains('\n')) }

            val whatever =
                listOf(
                    KtTokens.LPAR to KtTokens.RPAR,
                    KtTokens.LBRACKET to KtTokens.RBRACKET,
                    KtTokens.LBRACE to KtTokens.RBRACE
                )
                    .all { (open, close) ->
                        (lookAhead.find { it.node.elementType == open } == null ||
                            (lookAhead.find { it.node.elementType == open } != null &&
                                lookAhead.find { it.node.elementType == close } != null))
                    }

            node.elementType == indentationScope.startToken && whatever
        }

        is PropertyAccessorScope -> {
            val firstPropAccessor = node
                .findOutermostConsecutiveParent(KtStubElementTypes.PROPERTY)
                ?.findChildByType(KtStubElementTypes.PROPERTY_ACCESSOR)

            firstPropAccessor != null && node.treeNext == firstPropAccessor
        }
    }

fun isOutOfScope(node: ASTNode, indentationScope: IndentationScope): Boolean =
    when (indentationScope) {
        is ExpressionScope -> {
            val isPrevTreeContainsExpression = indentationScope
                .elementTypes
                .any { node.treePrev?.findDeepChildByType(it) != null }

            val isPrevTreeExpression = indentationScope.elementTypes.contains(node.treePrev?.elementType)

            val currentAndNextAreNotChainingElement =
                !indentationScope.chainingElements.contains(node.elementType) &&
                    !indentationScope.chainingElements.contains(node.treeNext?.elementType)

            (isPrevTreeExpression && currentAndNextAreNotChainingElement) ||
                (!isPrevTreeExpression && isPrevTreeContainsExpression)
        }

        is InternalNodeScopeWithTriggerToken ->
            node.isSucceeding(indentationScope.elementType)

        is LeafNodeScope -> {
            val isNewline: (ASTNode?) -> Boolean = { it is PsiWhiteSpace && it.textContains('\n') }
            val isNewLineThenEndToken = isNewline(node) && node.isPreceding(indentationScope.endToken)
            val isEndTokenAfterNonNewLine = !isNewline(node.treePrev) && node.elementType == indentationScope.endToken

            isNewLineThenEndToken || isEndTokenAfterNonNewLine
        }

        is PropertyAccessorScope ->
            node.isSucceeding(KtStubElementTypes.PROPERTY)
    }

private fun ASTNode.isSucceeding(elementType: IElementType) =
    this.treePrev?.elementType == elementType

private fun ASTNode.isPreceding(elementType: IElementType) =
    this.treeNext?.elementType == elementType

private fun ASTNode.findDeepChildByType(elementType: IElementType): ASTNode? =
    findChildByType(elementType) ?: if (!this.children().none()) {
        val targetParent = this.children().find {
            it.findDeepChildByType(elementType) != null
        }
        targetParent?.findDeepChildByType(elementType)
    } else {
        null
    }

private fun ASTNode.findOutermostConsecutiveParent(elementType: IElementType): ASTNode? =
    this
        .parents()
        .asSequence()
        .dropWhile { it.elementType != elementType }
        .takeWhile { it.elementType == elementType }
        .lastOrNull()
