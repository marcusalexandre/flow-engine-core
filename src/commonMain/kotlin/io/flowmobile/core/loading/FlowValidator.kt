package io.flowmobile.core.loading

import io.flowmobile.core.domain.*

/**
 * Validador de fluxos que verifica regras estruturais e semânticas.
 *
 * O FlowValidator implementa todas as regras de validação definidas no schema:
 *
 * Regras Estruturais (Severidade: Error):
 * - Exatamente 1 StartComponent
 * - Ao menos 1 EndComponent
 * - Todos os componentes têm ID único
 * - Todas as conexões têm ID único
 *
 * Regras Semânticas (Severidade: Error):
 * - Todas as conexões têm source e target válidos
 * - Grafo é um DAG (sem ciclos)
 * - Tipos de porta compatíveis nas conexões
 * - Componentes referenciados nas conexões existem
 * - Portas referenciadas nas conexões existem nos componentes
 *
 * Regras Semânticas (Severidade: Warning):
 * - Não há componentes órfãos (desconectados)
 * - Todos os EndComponents são alcançáveis a partir do StartComponent
 */
class FlowValidator {
    
    /**
     * Valida um fluxo completo.
     *
     * @param flow Fluxo a validar
     * @return ValidationResult com erros e avisos
     */
    fun validate(flow: Flow): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()
        
        // Validações estruturais
        errors.addAll(validateStructure(flow))
        
        // Validações semânticas
        errors.addAll(validateSemantics(flow))
        
        // Validações de conexões
        errors.addAll(validateConnections(flow))
        
        // Validações de componentes
        errors.addAll(validateComponents(flow))
        
        // Validações de avisos (não bloqueantes)
        warnings.addAll(validateWarnings(flow))
        
        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
    
    /**
     * Valida apenas a estrutura do fluxo.
     */
    fun validateStructure(flow: Flow): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        
        // 1. Exatamente 1 StartComponent
        val startComponents = flow.components.filterIsInstance<StartComponent>()
        if (startComponents.isEmpty()) {
            errors.add(ValidationError(
                code = "MISSING_START_COMPONENT",
                message = "Fluxo deve ter exatamente 1 StartComponent, encontrado 0",
                path = "flow.components",
                severity = ValidationSeverity.ERROR
            ))
        } else if (startComponents.size > 1) {
            errors.add(ValidationError(
                code = "MULTIPLE_START_COMPONENTS",
                message = "Fluxo deve ter exatamente 1 StartComponent, encontrado ${startComponents.size}",
                path = "flow.components",
                severity = ValidationSeverity.ERROR
            ))
        }
        
        // 2. Ao menos 1 EndComponent
        val endComponents = flow.components.filterIsInstance<EndComponent>()
        if (endComponents.isEmpty()) {
            errors.add(ValidationError(
                code = "MISSING_END_COMPONENT",
                message = "Fluxo deve ter ao menos 1 EndComponent, encontrado 0",
                path = "flow.components",
                severity = ValidationSeverity.ERROR
            ))
        }
        
        // 3. IDs de componentes únicos
        val componentIds = flow.components.map { it.id }
        val duplicateComponentIds = componentIds.groupBy { it }
            .filter { it.value.size > 1 }
            .keys
        
        for (duplicateId in duplicateComponentIds) {
            errors.add(ValidationError(
                code = "DUPLICATE_COMPONENT_ID",
                message = "ID de componente duplicado: '$duplicateId'",
                path = "flow.components",
                severity = ValidationSeverity.ERROR
            ))
        }
        
        // 4. IDs de conexões únicos
        val connectionIds = flow.connections.map { it.id }
        val duplicateConnectionIds = connectionIds.groupBy { it }
            .filter { it.value.size > 1 }
            .keys
        
        for (duplicateId in duplicateConnectionIds) {
            errors.add(ValidationError(
                code = "DUPLICATE_CONNECTION_ID",
                message = "ID de conexão duplicado: '$duplicateId'",
                path = "flow.connections",
                severity = ValidationSeverity.ERROR
            ))
        }
        
        return errors
    }
    
    /**
     * Valida a semântica do fluxo.
     */
    fun validateSemantics(flow: Flow): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        
        // 1. Verificar se o grafo é um DAG (sem ciclos)
        val cycleErrors = validateNoCycles(flow)
        errors.addAll(cycleErrors)
        
        return errors
    }
    
    /**
     * Valida as conexões do fluxo.
     */
    fun validateConnections(flow: Flow): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        val componentIds = flow.components.map { it.id }.toSet()
        
        for ((index, connection) in flow.connections.withIndex()) {
            val basePath = "flow.connections[$index]"
            
            // 1. Componente de origem existe
            if (connection.sourceComponentId !in componentIds) {
                errors.add(ValidationError(
                    code = "INVALID_SOURCE_COMPONENT",
                    message = "Componente de origem não existe: '${connection.sourceComponentId}'",
                    path = "$basePath.source.componentId",
                    severity = ValidationSeverity.ERROR
                ))
            } else {
                // 2. Porta de origem existe no componente
                val sourceComponent = flow.getComponentById(connection.sourceComponentId)
                if (sourceComponent != null) {
                    val outputPorts = sourceComponent.getOutputPorts()
                    if (outputPorts.none { it.id == connection.sourcePortId }) {
                        errors.add(ValidationError(
                            code = "INVALID_SOURCE_PORT",
                            message = "Porta de origem '${connection.sourcePortId}' não existe no componente '${connection.sourceComponentId}'",
                            path = "$basePath.source.portId",
                            severity = ValidationSeverity.ERROR
                        ))
                    }
                }
            }
            
            // 3. Componente de destino existe
            if (connection.targetComponentId !in componentIds) {
                errors.add(ValidationError(
                    code = "INVALID_TARGET_COMPONENT",
                    message = "Componente de destino não existe: '${connection.targetComponentId}'",
                    path = "$basePath.target.componentId",
                    severity = ValidationSeverity.ERROR
                ))
            } else {
                // 4. Porta de destino existe no componente
                val targetComponent = flow.getComponentById(connection.targetComponentId)
                if (targetComponent != null) {
                    val inputPorts = targetComponent.getInputPorts()
                    if (inputPorts.none { it.id == connection.targetPortId }) {
                        errors.add(ValidationError(
                            code = "INVALID_TARGET_PORT",
                            message = "Porta de destino '${connection.targetPortId}' não existe no componente '${connection.targetComponentId}'",
                            path = "$basePath.target.portId",
                            severity = ValidationSeverity.ERROR
                        ))
                    }
                }
            }
            
            // 5. Conexão não conecta componente a si mesmo
            if (connection.sourceComponentId == connection.targetComponentId) {
                errors.add(ValidationError(
                    code = "SELF_CONNECTION",
                    message = "Conexão não pode conectar um componente a si mesmo",
                    path = basePath,
                    severity = ValidationSeverity.ERROR
                ))
            }
            
            // 6. Tipos de porta compatíveis
            val sourceComponent = flow.getComponentById(connection.sourceComponentId)
            val targetComponent = flow.getComponentById(connection.targetComponentId)
            if (sourceComponent != null && targetComponent != null) {
                val sourcePort = sourceComponent.getOutputPorts().find { it.id == connection.sourcePortId }
                val targetPort = targetComponent.getInputPorts().find { it.id == connection.targetPortId }
                
                if (sourcePort != null && targetPort != null) {
                    if (!arePortTypesCompatible(sourcePort.type, targetPort.type)) {
                        errors.add(ValidationError(
                            code = "INCOMPATIBLE_PORT_TYPES",
                            message = "Tipos de porta incompatíveis: ${sourcePort.type} -> ${targetPort.type}",
                            path = basePath,
                            severity = ValidationSeverity.ERROR
                        ))
                    }
                }
            }
        }
        
        return errors
    }
    
    /**
     * Valida os componentes do fluxo.
     */
    fun validateComponents(flow: Flow): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        
        for ((index, component) in flow.components.withIndex()) {
            val basePath = "flow.components[$index]"
            
            // Validar ID não vazio
            if (component.id.isBlank()) {
                errors.add(ValidationError(
                    code = "BLANK_COMPONENT_ID",
                    message = "ID do componente não pode estar em branco",
                    path = "$basePath.id",
                    severity = ValidationSeverity.ERROR
                ))
            }
            
            // Validar nome não vazio
            if (component.name.isBlank()) {
                errors.add(ValidationError(
                    code = "BLANK_COMPONENT_NAME",
                    message = "Nome do componente não pode estar em branco",
                    path = "$basePath.name",
                    severity = ValidationSeverity.ERROR
                ))
            }
            
            // Validações específicas por tipo
            when (component) {
                is ActionComponent -> {
                    // ActionComponent deve ter service e method
                    if (component.properties[ActionComponent.PROPERTY_SERVICE] == null) {
                        errors.add(ValidationError(
                            code = "MISSING_SERVICE_PROPERTY",
                            message = "ActionComponent deve ter propriedade 'service'",
                            path = "$basePath.properties.service",
                            severity = ValidationSeverity.ERROR
                        ))
                    }
                    if (component.properties[ActionComponent.PROPERTY_METHOD] == null) {
                        errors.add(ValidationError(
                            code = "MISSING_METHOD_PROPERTY",
                            message = "ActionComponent deve ter propriedade 'method'",
                            path = "$basePath.properties.method",
                            severity = ValidationSeverity.ERROR
                        ))
                    }
                }
                is DecisionComponent -> {
                    // DecisionComponent deve ter condition
                    if (component.properties[DecisionComponent.PROPERTY_CONDITION] == null) {
                        errors.add(ValidationError(
                            code = "MISSING_CONDITION_PROPERTY",
                            message = "DecisionComponent deve ter propriedade 'condition'",
                            path = "$basePath.properties.condition",
                            severity = ValidationSeverity.ERROR
                        ))
                    }
                }
                else -> { /* StartComponent e EndComponent não têm validações extras */ }
            }
        }
        
        return errors
    }
    
    /**
     * Gera warnings (não bloqueantes) para o fluxo.
     */
    private fun validateWarnings(flow: Flow): List<ValidationWarning> {
        val warnings = mutableListOf<ValidationWarning>()
        
        // 1. Componentes órfãos (não conectados)
        val connectedComponents = mutableSetOf<String>()
        for (connection in flow.connections) {
            connectedComponents.add(connection.sourceComponentId)
            connectedComponents.add(connection.targetComponentId)
        }
        
        // StartComponent não precisa ter conexão de entrada
        val startComponent = flow.components.filterIsInstance<StartComponent>().firstOrNull()
        if (startComponent != null) {
            connectedComponents.add(startComponent.id)
        }
        
        for (component in flow.components) {
            if (component.id !in connectedComponents) {
                // Exceção: StartComponent com conexão de saída não é órfão
                val hasOutgoing = flow.connections.any { it.sourceComponentId == component.id }
                if (!hasOutgoing) {
                    warnings.add(ValidationWarning(
                        code = "ORPHAN_COMPONENT",
                        message = "Componente '${component.name}' (${component.id}) está desconectado do fluxo",
                        path = "flow.components"
                    ))
                }
            }
        }
        
        // 2. EndComponents não alcançáveis
        if (startComponent != null) {
            val reachable = findReachableComponents(flow, startComponent.id)
            val endComponents = flow.components.filterIsInstance<EndComponent>()
            
            for (endComponent in endComponents) {
                if (endComponent.id !in reachable) {
                    warnings.add(ValidationWarning(
                        code = "UNREACHABLE_END_COMPONENT",
                        message = "EndComponent '${endComponent.name}' (${endComponent.id}) não é alcançável a partir do StartComponent",
                        path = "flow.components"
                    ))
                }
            }
        }
        
        // 3. Portas obrigatórias sem conexão
        for ((index, component) in flow.components.withIndex()) {
            val basePath = "flow.components[$index]"
            
            // Verificar portas de entrada obrigatórias
            for (port in component.getInputPorts().filter { it.required }) {
                val hasIncoming = flow.connections.any { 
                    it.targetComponentId == component.id && it.targetPortId == port.id 
                }
                if (!hasIncoming && component !is StartComponent) {
                    warnings.add(ValidationWarning(
                        code = "REQUIRED_PORT_NOT_CONNECTED",
                        message = "Porta de entrada obrigatória '${port.id}' do componente '${component.name}' não está conectada",
                        path = basePath
                    ))
                }
            }
            
            // Verificar portas de saída obrigatórias
            for (port in component.getOutputPorts().filter { it.required }) {
                val hasOutgoing = flow.connections.any { 
                    it.sourceComponentId == component.id && it.sourcePortId == port.id 
                }
                if (!hasOutgoing && component !is EndComponent) {
                    warnings.add(ValidationWarning(
                        code = "REQUIRED_PORT_NOT_CONNECTED",
                        message = "Porta de saída obrigatória '${port.id}' do componente '${component.name}' não está conectada",
                        path = basePath
                    ))
                }
            }
        }
        
        return warnings
    }
    
    /**
     * Valida que o grafo não contém ciclos.
     */
    private fun validateNoCycles(flow: Flow): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()
        val path = mutableListOf<String>()
        
        fun hasCycle(componentId: String): Boolean {
            if (recursionStack.contains(componentId)) {
                // Encontrou ciclo - extrair o caminho do ciclo
                val cycleStart = path.indexOf(componentId)
                val cyclePath = if (cycleStart >= 0) {
                    path.subList(cycleStart, path.size) + componentId
                } else {
                    listOf(componentId)
                }
                
                errors.add(ValidationError(
                    code = "CYCLE_DETECTED",
                    message = "Ciclo detectado no grafo: ${cyclePath.joinToString(" -> ")}",
                    path = "flow.connections",
                    severity = ValidationSeverity.ERROR
                ))
                return true
            }
            
            if (visited.contains(componentId)) {
                return false
            }
            
            visited.add(componentId)
            recursionStack.add(componentId)
            path.add(componentId)
            
            val outgoing = flow.getOutgoingConnections(componentId)
            for (connection in outgoing) {
                if (hasCycle(connection.targetComponentId)) {
                    return true
                }
            }
            
            recursionStack.remove(componentId)
            path.removeAt(path.size - 1)
            return false
        }
        
        // Começar pelo StartComponent
        val startComponent = flow.components.filterIsInstance<StartComponent>().firstOrNull()
        if (startComponent != null) {
            hasCycle(startComponent.id)
        }
        
        // Verificar componentes não alcançados
        for (component in flow.components) {
            if (component.id !in visited) {
                hasCycle(component.id)
            }
        }
        
        return errors
    }
    
    /**
     * Encontra todos os componentes alcançáveis a partir de um componente inicial.
     */
    private fun findReachableComponents(flow: Flow, startId: String): Set<String> {
        val reachable = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(startId)
        
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current in reachable) continue
            reachable.add(current)
            
            val outgoing = flow.getOutgoingConnections(current)
            for (connection in outgoing) {
                if (connection.targetComponentId !in reachable) {
                    queue.add(connection.targetComponentId)
                }
            }
        }
        
        return reachable
    }
    
    /**
     * Verifica se dois tipos de porta são compatíveis para conexão.
     */
    private fun arePortTypesCompatible(sourceType: PortType, targetType: PortType): Boolean {
        // ANY é compatível com qualquer tipo
        if (sourceType == PortType.ANY || targetType == PortType.ANY) {
            return true
        }
        
        // CONTROL é especial - só conecta com CONTROL
        if (sourceType == PortType.CONTROL || targetType == PortType.CONTROL) {
            return sourceType == targetType
        }
        
        // Tipos exatos devem ser iguais ou destino é ANY
        return sourceType == targetType
    }
}

/**
 * Resultado de uma validação.
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<ValidationError> = emptyList(),
    val warnings: List<ValidationWarning> = emptyList()
)

/**
 * Erro de validação.
 */
data class ValidationError(
    val code: String,
    val message: String,
    val path: String? = null,
    val severity: ValidationSeverity = ValidationSeverity.ERROR
) {
    override fun toString(): String {
        val location = path?.let { " at $it" } ?: ""
        return "[$severity][$code] $message$location"
    }
}

/**
 * Aviso de validação (não bloqueante).
 */
data class ValidationWarning(
    val code: String,
    val message: String,
    val path: String? = null
) {
    override fun toString(): String {
        val location = path?.let { " at $it" } ?: ""
        return "[WARNING][$code] $message$location"
    }
}

/**
 * Severidade de validação.
 */
enum class ValidationSeverity {
    ERROR,
    WARNING,
    INFO
}
