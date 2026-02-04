package io.flowmobile.core.domain

import kotlinx.serialization.Serializable

/**
 * Representa o estado atual de uma execução de fluxo.
 * ExecutionContext é imutável - todas as modificações retornam uma nova instância.
 * 
 * O contexto contém:
 * - Posição atual no fluxo (qual componente está executando)
 * - Variáveis e seus valores
 * - Pilha de execução para rastrear o caminho percorrido
 * - Trilha de auditoria de todas as operações
 * - Metadados sobre a execução
 *
 * @property flowId ID do fluxo sendo executado
 * @property executionId Identificador único para esta instância de execução
 * @property currentComponentId ID do componente sendo executado atualmente (null se não iniciado)
 * @property variables Mapa de nomes de variáveis para seus valores
 * @property executionStack Frames de pilha rastreando o caminho de execução
 * @property auditTrail Lista imutável de todas as entradas de auditoria
 * @property metadata Metadados de execução
 * @property status Status atual da execução
 */
@Serializable
data class ExecutionContext(
    val flowId: String,
    val executionId: String,
    val currentComponentId: String? = null,
    val variables: Map<String, VariableValue> = emptyMap(),
    val executionStack: List<StackFrame> = emptyList(),
    val auditTrail: List<AuditEntry> = emptyList(),
    val metadata: ExecutionMetadata = ExecutionMetadata(),
    val status: ExecutionStatus = ExecutionStatus.NOT_STARTED
) {
    init {
        require(flowId.isNotBlank()) { "Flow id cannot be blank" }
        require(executionId.isNotBlank()) { "Execution id cannot be blank" }
    }
    
    /**
     * Cria um novo contexto com uma variável atualizada.
     * Usa compartilhamento estrutural para eficiência.
     */
    fun withVariable(name: String, value: VariableValue): ExecutionContext {
        return copy(variables = variables + (name to value))
    }
    
    /**
     * Cria um novo contexto com múltiplas variáveis atualizadas.
     */
    fun withVariables(newVariables: Map<String, VariableValue>): ExecutionContext {
        return copy(variables = variables + newVariables)
    }
    
    /**
     * Cria um novo contexto com um componente atual atualizado.
     */
    fun withCurrentComponent(componentId: String): ExecutionContext {
        return copy(currentComponentId = componentId)
    }
    
    /**
     * Cria um novo contexto com um frame de pilha adicionado.
     */
    fun pushStackFrame(frame: StackFrame): ExecutionContext {
        return copy(executionStack = executionStack + frame)
    }
    
    /**
     * Cria um novo contexto com o frame de pilha do topo removido.
     */
    fun popStackFrame(): ExecutionContext {
        require(executionStack.isNotEmpty()) { "Cannot pop from empty stack" }
        return copy(executionStack = executionStack.dropLast(1))
    }
    
    /**
     * Cria um novo contexto com uma entrada de auditoria adicionada.
     */
    fun appendAuditEntry(entry: AuditEntry): ExecutionContext {
        return copy(auditTrail = auditTrail + entry)
    }
    
    /**
     * Método auxiliar para adicionar uma entrada de auditoria de forma conveniente.
     */
    fun appendAuditEntry(
        componentId: String,
        action: AuditAction,
        message: String = ""
    ): ExecutionContext {
        val entry = AuditEntry(
            timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
            componentId = componentId,
            action = action,
            contextSnapshot = variables,
            result = null,
            message = message
        )
        return appendAuditEntry(entry)
    }
    
    /**
     * Cria um novo contexto com um status atualizado.
     */
    fun withStatus(newStatus: ExecutionStatus): ExecutionContext {
        return copy(status = newStatus)
    }
    
    /**
     * Obtém o valor de uma variável.
     */
    fun getVariable(name: String): VariableValue? {
        return variables[name]
    }
    
    /**
     * Estima o tamanho deste contexto em bytes (para limitação de recursos).
     */
    fun estimateSize(): Long {
        // Estimativa simplificada - em produção deve ser mais precisa
        return (variables.size * 100 + auditTrail.size * 200).toLong()
    }
    
    companion object {
        /**
         * Cria um novo contexto de execução inicial.
         */
        fun create(flowId: String, initialComponentId: String, executionId: String = generateExecutionId()): ExecutionContext {
            return ExecutionContext(
                flowId = flowId,
                executionId = executionId,
                currentComponentId = initialComponentId,
                status = ExecutionStatus.RUNNING
            )
        }
        
        /**
         * Gera um ID único para execução.
         */
        private fun generateExecutionId(): String {
            return "exec-${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}"
        }
    }
}

/**
 * Status de uma execução de fluxo.
 */
@Serializable
enum class ExecutionStatus {
    /** Execução ainda não iniciada */
    NOT_STARTED,
    
    /** Execução está rodando atualmente */
    RUNNING,
    
    /** Execução está pausada (para depuração ou passo a passo) */
    PAUSED,
    
    /** Execução completada com sucesso */
    COMPLETED,
    
    /** Execução falhou com um erro */
    FAILED,
    
    /** Execução foi cancelada */
    CANCELLED
}

/**
 * Representa um valor armazenado em variáveis do contexto de execução.
 */
@Serializable
sealed interface VariableValue {
    @Serializable
    data class StringValue(val value: String) : VariableValue
    
    @Serializable
    data class NumberValue(val value: Double) : VariableValue
    
    @Serializable
    data class BooleanValue(val value: Boolean) : VariableValue
    
    @Serializable
    data class ObjectValue(val value: Map<String, VariableValue>) : VariableValue
    
    @Serializable
    data class ArrayValue(val value: List<VariableValue>) : VariableValue
    
    @Serializable
    data object NullValue : VariableValue
}

/**
 * Um frame de pilha representando um passo no caminho de execução.
 */
@Serializable
data class StackFrame(
    val componentId: String,
    val componentType: ComponentType,
    val enteredAt: Long,
    val exitedAt: Long? = null
)

/**
 * Uma entrada na trilha de auditoria registrando uma ação durante a execução.
 */
@Serializable
data class AuditEntry(
    val timestamp: Long,
    val componentId: String,
    val action: AuditAction,
    val contextSnapshot: Map<String, VariableValue>,
    val result: ExecutionResult? = null,
    val message: String = ""
)

/**
 * Tipo de ação registrada na trilha de auditoria.
 */
@Serializable
enum class AuditAction {
    /** Iniciou execução do fluxo */
    EXECUTION_STARTED,
    
    /** Entrou em um componente */
    COMPONENT_ENTER,
    
    /** Saiu de um componente */
    COMPONENT_EXIT,
    
    /** Componente iniciou execução */
    COMPONENT_STARTED,
    
    /** Componente completou execução */
    COMPONENT_COMPLETED,
    
    /** Componente falhou */
    COMPONENT_FAILED,
    
    /** Uma variável foi alterada */
    VARIABLE_CHANGED,
    
    /** Uma variável foi atualizada */
    VARIABLE_UPDATED,
    
    /** Uma decisão foi avaliada */
    DECISION_EVALUATED,
    
    /** Execução completada */
    EXECUTION_COMPLETED,
    
    /** Um erro ocorreu */
    ERROR_OCCURRED
}

/**
 * Metadados sobre a execução.
 */
@Serializable
data class ExecutionMetadata(
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val userId: String? = null,
    val sessionId: String? = null,
    val customData: Map<String, String> = emptyMap()
)
