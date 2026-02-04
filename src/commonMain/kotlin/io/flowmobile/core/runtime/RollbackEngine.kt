package io.flowmobile.core.runtime

import io.flowmobile.core.domain.AuditAction
import io.flowmobile.core.domain.ExecutionContext
import io.flowmobile.core.domain.ExecutionStatus

/**
 * Motor de rollback que permite reverter a execução para estados anteriores
 * sem mutação de estado.
 */
class RollbackEngine {
    
    /**
     * Verifica se é possível fazer rollback de N passos.
     */
    fun canRollback(context: ExecutionContext, steps: Int): Boolean {
        if (steps <= 0) return false
        
        val completedSteps = context.auditTrail.count { 
            it.action == AuditAction.COMPONENT_COMPLETED 
        }
        
        return steps <= completedSteps
    }
    
    /**
     * Faz rollback de N passos de execução.
     */
    fun rollback(context: ExecutionContext, steps: Int): ExecutionContext? {
        if (!canRollback(context, steps)) {
            return null
        }
        
        val completedIndices = context.auditTrail
            .mapIndexedNotNull { index, entry ->
                if (entry.action == AuditAction.COMPONENT_COMPLETED) index else null
            }
        
        if (steps > completedIndices.size) return null
        
        val targetIndex = completedIndices[completedIndices.size - steps]
        return rollbackToAuditIndex(context, targetIndex)
    }
    
    /**
     * Faz rollback até um componente específico.
     */
    fun rollbackTo(context: ExecutionContext, componentId: String): ExecutionContext? {
        val targetIndex = context.auditTrail.indexOfLast { entry ->
            entry.componentId == componentId && 
            entry.action == AuditAction.COMPONENT_COMPLETED
        }
        
        if (targetIndex == -1) return null
        
        return rollbackToAuditIndex(context, targetIndex)
    }
    
    /**
     * Faz rollback para um índice específico no audit trail.
     */
    private fun rollbackToAuditIndex(context: ExecutionContext, targetIndex: Int): ExecutionContext? {
        if (targetIndex < 0 || targetIndex >= context.auditTrail.size) return null
        
        val targetEntry = context.auditTrail[targetIndex]
        val auditSublist = context.auditTrail.subList(0, targetIndex + 1)
        
        return ExecutionContext(
            flowId = context.flowId,
            executionId = context.executionId,
            currentComponentId = targetEntry.componentId,
            variables = targetEntry.contextSnapshot,
            executionStack = emptyList(),
            auditTrail = auditSublist,
            metadata = context.metadata,
            status = ExecutionStatus.RUNNING
        ).appendAuditEntry(
            componentId = targetEntry.componentId,
            action = AuditAction.COMPONENT_STARTED,
            message = "Rollback para ${targetEntry.componentId}"
        )
    }
    
    /**
     * Obtém todos os pontos de rollback disponíveis.
     */
    fun getAvailableRollbackPoints(context: ExecutionContext): List<RollbackPoint> {
        return context.auditTrail.mapIndexedNotNull { index, entry ->
            if (entry.action == AuditAction.COMPONENT_COMPLETED) {
                RollbackPoint(
                    componentId = entry.componentId,
                    auditIndex = index,
                    timestamp = entry.timestamp,
                    description = "Após executar componente ${entry.componentId}"
                )
            } else null
        }
    }
}

/**
 * Representa um ponto no qual é possível fazer rollback.
 */
data class RollbackPoint(
    val componentId: String,
    val auditIndex: Int,
    val timestamp: Long,
    val description: String
)
