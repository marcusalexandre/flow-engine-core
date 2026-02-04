package io.flowmobile.core.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Representa o resultado da execução de um componente ou fluxo completo.
 * ExecutionResult é imutável e contém o resultado e quaisquer dados produzidos.
 *
 * @property status Status da execução
 * @property outputVariables Variáveis produzidas pela execução
 * @property error Informações de erro se a execução falhou
 * @property metrics Métricas de performance e diagnóstico
 */
@Serializable
data class ExecutionResult(
    val status: ResultStatus,
    val outputVariables: Map<String, VariableValue> = emptyMap(),
    val error: ExecutionError? = null,
    val metrics: ExecutionMetrics = ExecutionMetrics()
) {
    /**
     * Retorna true se a execução foi bem-sucedida.
     */
    fun isSuccess(): Boolean = status == ResultStatus.SUCCESS
    
    /**
     * Retorna true se a execução falhou.
     */
    fun isFailure(): Boolean = status == ResultStatus.FAILURE
    
    companion object {
        /**
         * Cria um resultado de execução bem-sucedido.
         */
        fun success(
            outputVariables: Map<String, VariableValue> = emptyMap(),
            metrics: ExecutionMetrics = ExecutionMetrics()
        ): ExecutionResult {
            return ExecutionResult(
                status = ResultStatus.SUCCESS,
                outputVariables = outputVariables,
                metrics = metrics
            )
        }
        
        /**
         * Cria um resultado de execução falho.
         */
        fun failure(
            error: ExecutionError,
            metrics: ExecutionMetrics = ExecutionMetrics()
        ): ExecutionResult {
            return ExecutionResult(
                status = ResultStatus.FAILURE,
                error = error,
                metrics = metrics
            )
        }
        
        /**
         * Cria um resultado parcial (execução não completa).
         */
        fun partial(context: ExecutionContext): ExecutionResult {
            return ExecutionResult(
                status = ResultStatus.PARTIAL,
                outputVariables = context.variables,
                metrics = ExecutionMetrics()
            )
        }
    }
}

/**
 * Status de um resultado de execução.
 */
@Serializable
enum class ResultStatus {
    /** Execução completada com sucesso */
    SUCCESS,
    
    /** Execução falhou com um erro */
    FAILURE,
    
    /** Execução foi pulada (ex: em uma ramificação condicional) */
    SKIPPED,
    
    /** Execução parcial (step-by-step, não completa) */
    PARTIAL
}

/**
 * Informações sobre um erro que ocorreu durante a execução.
 */
@Serializable
data class ExecutionError(
    val code: String,
    val message: String,
    val componentId: String?,
    val details: Map<String, String> = emptyMap(),
    val stackTrace: List<String> = emptyList()
)

/**
 * Métricas coletadas durante a execução.
 */
@Serializable
data class ExecutionMetrics(
    val startTime: Instant? = null,
    val endTime: Instant? = null,
    val duration: Long = 0,
    val componentsExecuted: Int = 0,
    val variablesCreated: Int = 0,
    val customMetrics: Map<String, Double> = emptyMap()
)
