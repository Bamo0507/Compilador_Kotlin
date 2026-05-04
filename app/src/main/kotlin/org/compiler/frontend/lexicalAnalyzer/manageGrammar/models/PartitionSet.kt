package org.compiler.frontend.lexicalAnalyzer.manageGrammar.models

data class PartitionSet(
    var tag: Int,
    val members: Set<Int>,
    val isAcceptedBlock: Boolean
)
