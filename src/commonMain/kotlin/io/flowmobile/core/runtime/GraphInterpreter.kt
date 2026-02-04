package io.flowmobile.core.runtime

import io.flowmobile.core.domain.*

/**
 * Interpretador de grafos responsável por resolver o próximo componente a executar
 * e validar a estrutura do grafo de fluxo.
 *
 * O GraphInterpreter implementa algoritmos de travessia de grafos para:
 * - Determinar qual componente deve executar em seguida baseado no estado atual
 * - Validar se o grafo é um DAG (Directed Acyclic Graph)
 * - Detectar ciclos que poderiam causar loops infinitos
 * - Encontrar caminhos entre componentes
 */
class GraphInterpreter {
    
    /**
     * Resolve qual é o próximo componente a executar baseado no contexto atual.
     *
     * @param flow O fluxo sendo executado
     * @param context O contexto de execução atual
     * @return O próximo componente a executar, ou null se a execução deve terminar
     */
    fun resolveNext(flow: Flow, context: ExecutionContext): Component? {
        val currentComponentId = context.currentComponentId ?: return null
        val currentComponent = flow.getComponentById(currentComponentId)
            ?: return null
        
        // Se for um EndComponent, a execução termina
        if (currentComponent is EndComponent) {
            return null
        }
        
        // Obter as conexões de saída do componente atual
        val outgoingConnections = flow.getOutgoingConnections(currentComponentId)
        
        return when (currentComponent) {
            is StartComponent -> {
                // Start sempre tem uma única conexão de saída
                outgoingConnections.firstOrNull()?.let { connection ->
                    flow.getComponentById(connection.targetComponentId)
                }
            }
            
            is ActionComponent -> {
                // Action sempre segue para o próximo componente conectado
                outgoingConnections.firstOrNull()?.let { connection ->
                    flow.getComponentById(connection.targetComponentId)
                }
            }
            
            is DecisionComponent -> {
                // Decision avalia a condição e escolhe o caminho
                val condition = currentComponent.properties[DecisionComponent.PROPERTY_CONDITION] as? ComponentProperty.StringValue
                val conditionValue = condition?.value ?: ""
                
                // Avaliar condição (simplificado - usar variável booleana)
                val result = evaluateCondition(conditionValue, context)
                
                // Escolher a porta de saída baseado no resultado
                val targetPort = if (result) {
                    DecisionComponent.PORT_TRUE
                } else {
                    DecisionComponent.PORT_FALSE
                }
                
                // Encontrar a conexão que sai da porta correspondente
                outgoingConnections.find { it.sourcePortId == targetPort }?.let { connection ->
                    flow.getComponentById(connection.targetComponentId)
                }
            }
            
            is ForkComponent -> {
                // Fork segue para o primeiro branch por padrão em execução sequencial
                // Em execução paralela (AsyncFlowExecutor), todos os branches são executados
                outgoingConnections.firstOrNull()?.let { connection ->
                    flow.getComponentById(connection.targetComponentId)
                }
            }
            
            is JoinComponent -> {
                // Join segue para o componente após convergência
                outgoingConnections.firstOrNull()?.let { connection ->
                    flow.getComponentById(connection.targetComponentId)
                }
            }
            
            is EndComponent -> null
        }
    }
    
    /**
     * Avalia uma condição booleana no contexto atual.
     * Implementação simplificada que busca variável booleana.
     *
     * @param condition Expressão da condição (nome da variável)
     * @param context Contexto com as variáveis
     * @return true se a condição é verdadeira, false caso contrário
     */
    private fun evaluateCondition(condition: String, context: ExecutionContext): Boolean {
        // Buscar variável pelo nome
        val variable = context.variables[condition]
        return when (variable) {
            is VariableValue.BooleanValue -> variable.value
            else -> false
        }
    }
    
    /**
     * Valida se o grafo é um DAG (Directed Acyclic Graph).
     * Usa DFS para detectar ciclos.
     *
     * @param flow O fluxo a validar
     * @return Resultado da validação
     */
    fun validateDAG(flow: Flow): ValidationResult {
        val errors = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()
        
        // Começar pela StartComponent
        val startComponent = flow.getStartComponent()
        
        if (hasCycle(flow, startComponent.id, visited, recursionStack)) {
            errors.add("Ciclo detectado no grafo começando pelo componente: ${startComponent.id}")
        }
        
        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }
    
    /**
     * Detecta ciclos usando DFS com pilha de recursão.
     *
     * @param flow O fluxo
     * @param componentId ID do componente atual
     * @param visited Conjunto de componentes já visitados
     * @param recursionStack Pilha de recursão para detectar back edges
     * @return true se há ciclo, false caso contrário
     */
    private fun hasCycle(
        flow: Flow,
        componentId: String,
        visited: MutableSet<String>,
        recursionStack: MutableSet<String>
    ): Boolean {
        // Se já está na pilha de recursão, encontramos um ciclo
        if (recursionStack.contains(componentId)) {
            return true
        }
        
        // Se já foi visitado e não está na pilha, não há ciclo neste caminho
        if (visited.contains(componentId)) {
            return false
        }
        
        // Marcar como visitado e adicionar à pilha de recursão
        visited.add(componentId)
        recursionStack.add(componentId)
        
        // Visitar todos os componentes adjacentes
        val outgoing = flow.getOutgoingConnections(componentId)
        for (connection in outgoing) {
            if (hasCycle(flow, connection.targetComponentId, visited, recursionStack)) {
                return true
            }
        }
        
        // Remover da pilha de recursão ao retornar
        recursionStack.remove(componentId)
        
        return false
    }
    
    /**
     * Encontra todos os ciclos no grafo.
     *
     * @param flow O fluxo a analisar
     * @return Lista de ciclos detectados (cada ciclo é uma lista de IDs de componentes)
     */
    fun detectCycles(flow: Flow): List<List<String>> {
        val cycles = mutableListOf<List<String>>()
        val visited = mutableSetOf<String>()
        val recursionStack = mutableListOf<String>()
        
        for (component in flow.components) {
            if (!visited.contains(component.id)) {
                findCycles(flow, component.id, visited, recursionStack, cycles)
            }
        }
        
        return cycles
    }
    
    /**
     * Busca ciclos recursivamente e armazena os caminhos.
     */
    private fun findCycles(
        flow: Flow,
        componentId: String,
        visited: MutableSet<String>,
        recursionStack: MutableList<String>,
        cycles: MutableList<List<String>>
    ) {
        visited.add(componentId)
        recursionStack.add(componentId)
        
        val outgoing = flow.getOutgoingConnections(componentId)
        for (connection in outgoing) {
            val targetId = connection.targetComponentId
            
            if (!visited.contains(targetId)) {
                findCycles(flow, targetId, visited, recursionStack, cycles)
            } else if (recursionStack.contains(targetId)) {
                // Encontramos um ciclo
                val cycleStart = recursionStack.indexOf(targetId)
                val cycle = recursionStack.subList(cycleStart, recursionStack.size).toList()
                cycles.add(cycle)
            }
        }
        
        recursionStack.removeAt(recursionStack.lastIndex)
    }
    
    /**
     * Encontra um caminho entre dois componentes usando BFS.
     *
     * @param flow O fluxo
     * @param fromId ID do componente de origem
     * @param toId ID do componente de destino
     * @return Lista de componentes no caminho, ou null se não houver caminho
     */
    fun findPath(flow: Flow, fromId: String, toId: String): List<Component>? {
        if (fromId == toId) {
            return listOf(flow.getComponentById(fromId)!!)
        }
        
        val queue = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        val parent = mutableMapOf<String, String>()
        
        queue.add(fromId)
        visited.add(fromId)
        
        while (queue.isNotEmpty()) {
            val currentId = queue.removeAt(0)
            
            if (currentId == toId) {
                // Reconstruir o caminho
                return reconstructPath(flow, fromId, toId, parent)
            }
            
            val outgoing = flow.getOutgoingConnections(currentId)
            for (connection in outgoing) {
                val nextId = connection.targetComponentId
                if (!visited.contains(nextId)) {
                    visited.add(nextId)
                    parent[nextId] = currentId
                    queue.add(nextId)
                }
            }
        }
        
        return null // Não há caminho
    }
    
    /**
     * Reconstrói o caminho a partir do mapa de pais.
     */
    private fun reconstructPath(
        flow: Flow,
        fromId: String,
        toId: String,
        parent: Map<String, String>
    ): List<Component> {
        val path = mutableListOf<String>()
        var current = toId
        
        while (current != fromId) {
            path.add(0, current)
            current = parent[current]!!
        }
        path.add(0, fromId)
        
        return path.mapNotNull { flow.getComponentById(it) }
    }
}

/**
 * Resultado de validação de grafo.
 *
 * @property isValid true se o grafo é válido
 * @property errors Lista de erros encontrados
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String>
)
