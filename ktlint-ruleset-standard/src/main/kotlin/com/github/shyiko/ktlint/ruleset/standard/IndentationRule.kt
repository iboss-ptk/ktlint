package com.github.shyiko.ktlint.ruleset.standard

import com.github.shyiko.ktlint.core.Rule
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.lang.FileASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiComment
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtTypeConstraintList
import org.jetbrains.kotlin.psi.psiUtil.children
import org.jetbrains.kotlin.psi.stubs.elements.KtStubElementTypes

class IndentationRule : Rule("indent") {

    private var indentSize = -1

    override fun visit(
        node: ASTNode,
        autoCorrect: Boolean,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit
    ) {
        if (node.elementType == KtStubElementTypes.FILE) {
            val ec = EditorConfig.from(node as FileASTNode)
            indentSize = gcd(maxOf(ec.indentSize, 1), maxOf(ec.continuationIndentSize, 1))

            val traverse = makeTraverser(emit, autoCorrect)
            exploreTree(node.children(), traverse)
            return
        }
        if (indentSize <= 1) {
            return
        }
    }

    private fun makeTraverser(
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
        autoCorrect: Boolean
    ) = { node: ASTNode, nestedLevel: Int ->
        if (node is PsiWhiteSpace &&
            !node.isPartOf(PsiComment::class) &&
            !node.isPartOf(KtTypeConstraintList::class)
        ) {
            val lines = node.getText().split("\n")
            if (lines.size > 1) {
                var offset = node.startOffset + lines.first().length + 1
                val previousIndentSize = node.previousIndentSize()

                lines.tail().forEach { indent ->
                    if (indent.isNotEmpty() &&
                        // parameter list wrapping enforced by ParameterListWrappingRule
                        !node.isPartOf(KtParameterList::class) &&
                        (indent.length - previousIndentSize) % indentSize != 0) {
                            emit(
                                offset,
                                wrongIndentSizeMessage(
                                    indent.length,
                                    previousIndentSize + indentSize
                                ),
                                false
                            )
                    }
                    offset += indent.length + 1
                }
            }

            if (node.textContains('\t')) {
                val text = node.getText()

                emit(
                    node.startOffset + text.indexOf('\t'),
                    "Unexpected Tab character(s)",
                    true
                )

                if (autoCorrect) {
                    (node as LeafPsiElement)
                        .rawReplaceWithText(text.replace("\t", " ".repeat(indentSize)))
                }
            }
        }
    }

    private fun wrongIndentSizeMessage(actual: Int, expected: Int) =
        "Unexpected indentation ($actual) (it should be $expected)"

    private fun exploreTree(
        children: Sequence<ASTNode>,
        traverse: (ASTNode, Int) -> Unit,
        nestedLevel: Int = 0
    ): List<ASTNode> {
        return children.toList().map {
            // condition
            // binary operator (nested)
            // function literal
            // Block
            // when
            // dot qualified exp
            // parenthesize
            // when ()
            // when {}
            // prevSibling = EQ, immediate white space
            // prevSibling = arrow under when entry, immediate white space

            traverse(it, nestedLevel)

            if (!it.children().none()) {
                exploreTree(it.children(), traverse, nestedLevel)
            }

            it
        }
    }

    private fun gcd(a: Int, b: Int): Int = when {
        a > b -> gcd(a - b, b)
        a < b -> gcd(a, b - a)
        else -> a
    }

    // todo: calculating indent based on the previous line value is wrong (see IndentationRule.testLint)
    private fun ASTNode.previousIndentSize(): Int {
        var node = this.treeParent?.psi
        while (node != null) {
            val nextNode = node.nextSibling?.node?.elementType
            if (node is PsiWhiteSpace &&
                nextNode != KtStubElementTypes.TYPE_REFERENCE &&
                nextNode != KtStubElementTypes.SUPER_TYPE_LIST &&
                nextNode != KtNodeTypes.CONSTRUCTOR_DELEGATION_CALL &&
                node.textContains('\n') &&
                node.nextLeaf()?.isPartOf(PsiComment::class) != true) {
                return node.text.length - node.text.lastIndexOf('\n') - 1
            }
            node = node.prevSibling ?: node.parent
        }
        return 0
    }
}
