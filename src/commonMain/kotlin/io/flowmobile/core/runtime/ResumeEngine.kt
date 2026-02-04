package io.flowmobile.core.runtime

import io.flowmobile.core.domain.*

/**
 * Motor de retomada que permite continuar a execução de um fluxo a partir
 * de um contexto persistido.
 */
class ResumeEngine {
    
    /**
     * Verifica se é possível retomar a execução com o contexto fornecido.
     */
    fun canResume(flow: Flow, context: ExecutionContext): ResumeValidation {
        val errors = mutableListOf<String>()
        
        if (context.flowId != flow.id) {
            errors.add("Flow ID não corresponde: contexto=${context.flowId}, flow=${flow.id}")
        }
        
        val currentComponent = context.currentComponentId?.let { flow.getComponentById(it) }
        if (currentComponent == null && context.currentComponentId != null) {
            errors.add("Componente atual não encontrado no fluxo: ${context.currentComponentId}")
        }
        
        if (context.status == ExecutionStatus.COMPLETED) {
            errors.add("Contexto já está em estado COMPLETED")
        }
        
        if (context.status == ExecutionStatus.FAILED) {
            errors.add("Contexto está em estado FAILED")
        }
        
        return ResumeValidation(
            canResume = errors.isEmpty(),
            errors = errors
        )
    }
    
    /**
     * Retoma a execução de um fluxo a partir de um contexto persistido.
     */
    suspend fun resume(
        flow: Flow,
        context: ExecutionContext,
        executor: FlowExecutor
    ): ExecutionResult {
        val validation = canResume(flow, context)
        if (!validation.canResume) {
            return ExecutionResult.failure(
                ExecutionError(
                    code = "CANNOT_RESUME",
                    message = "Não é possível retomar execução: ${validation.errors.joinToString(", ")}",
                    componentId = context.currentComponentId
                )
            )
        }
        
        val resumedContext = context.appendAuditEntry(
            componentId = context.currentComponentId ?: context.flowId,
            action = AuditAction.COMPONENT_STARTED,
            message = "Retomando execução"
        )
        
        return executor.execute(flow, resumedContext, ExecutionMode.RUN_TO_COMPLETION)
    }
    
    /**
     * Valida compatibilidade entre fluxo e contexto em detalhes.
     */
    fun validateContextCompatibility(flow: Flow, context: ExecutionContext): CompatibilityValidation {
        val issues = mutableListOf<CompatibilityIssue>()
        
        if (context.flowId != flow.id) {
            issues.add(
                CompatibilityIssue(
                    severity = IssueSeverity.ERROR,
                    category = "FLOW_MISMATCH",
                    message = "Flow ID não corresponde",
                    details = "Contexto: ${context.flowId}, Flow: ${flow.id}"
                )
            )
        }
        
        val currentComponent = context.currentComponentId?.let { flow.getComponentById(it) }
        if (currentComponent == null && context.currentComponentId != null) {
            issues.add(
                CompatibilityIssue(
                    severity = IssueSeverity.ERROR,
                    category = "COMPONENT_NOT_FOUND",
                    message = "Componente atual não existe no fluxo",
                    details = "ComponentId: ${context.currentComponentId}"
                )
            )
        }
        
        if (context.status == ExecutionStatus.COMPLETED) {
            issues.add(
                CompatibilityIssue(
                    severity = IssueSeverity.WARNING,
                    category = "ALREADY_COMPLETED",
                    message = "Contexto já está completo",
                    details = "Status: ${context.status}"
                )
            )
        }
        
        val hasErrors = issues.any { it.severity == IssueSeverity.ERROR }
        
        return CompatibilityValidation(
            isCompatible = !hasErrors,
            issues = issues
        )
    }
}

/**
 * Resultado da validação de retomada.
 */
data class ResumeValidation(
    val canResume: Boolean,
    val errors: List<String>
)

/**
 * Resultado detalhado da validação de compatibilidade.
 */
data class CompatibilityValidation(
    val isCompatible: Boolean,
    val issues: List<CompatibilityIssue>
)

/**
 * Representa um problema de compatibilidade.
 */
data class CompatibilityIssue(
    val severity: IssueSeverity,
    val category: String,
    val message: String,
    val details: String
)

/**
 * Severidade de um problema de compatibilidade.
 */
enum class IssueSeverity {
    ERROR,
    WARNING,
    INFO
}
