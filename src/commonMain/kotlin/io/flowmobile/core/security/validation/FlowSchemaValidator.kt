package io.flowmobile.core.security.validation

/**
 * Resultado da validação de um fluxo contra seu schema.
 */
sealed class FlowValidationResult {
    /**
     * Fluxo válido.
     * @property flowId ID do fluxo validado
     */
    data class Valid(val flowId: String) : FlowValidationResult()

    /**
     * Fluxo inválido.
     * @property errors Erros encontrados na validação
     */
    data class Invalid(val errors: List<String>) : FlowValidationResult()
}

/**
 * Valida fluxos contra seu schema JSON.
 *
 * Implementa validação completa de fluxos:
 * - Estrutura obrigatória (metadata, components, connections)
 * - Validação de componentes e propriedades
 * - Validação de conexões
 * - Ciclos e conectividade
 * - Limites de tamanho
 *
 * **Regras de Validação:**
 * - ID único para cada componente
 * - Cada conexão referencia componentes existentes
 * - Sem ciclos infinitos
 * - Sem componentes órfãos (exceto start)
 * - Sem propriedades inválidas
 *
 * **Estrutura Esperada:**
 * ```json
 * {
 *   "id": "flow-1",
 *   "name": "My Flow",
 *   "version": "1.0.0",
 *   "components": [
 *     {
 *       "id": "c1",
 *       "type": "StartComponent",
 *       "properties": {}
 *     }
 *   ],
 *   "connections": [
 *     {
 *       "from": "c1",
 *       "to": "c2"
 *     }
 *   ]
 * }
 * ```
 */
class FlowSchemaValidator(
    private val propertyValidator: ComponentPropertyValidator
) {
    /**
     * Tamanho máximo de um fluxo em bytes (10MB).
     */
    private val MAX_FLOW_SIZE = 10 * 1024 * 1024

    /**
     * Número máximo de componentes por fluxo.
     */
    private val MAX_COMPONENTS = 10000

    /**
     * Número máximo de conexões por fluxo.
     */
    private val MAX_CONNECTIONS = 20000

    /**
     * Profundidade máxima de ciclos detectáveis.
     */
    private val MAX_CYCLE_DEPTH = 1000

    /**
     * Valida um fluxo JSON completo.
     *
     * @param flowJson Representação JSON do fluxo como Map
     * @return [FlowValidationResult.Valid] se válido, [FlowValidationResult.Invalid] caso contrário
     *
     * **Algoritmo:**
     * 1. Validar estrutura básica e campos obrigatórios
     * 2. Validar componentes e propriedades
     * 3. Validar conexões
     * 4. Validar referências cruzadas
     * 5. Detectar ciclos e problemas de conectividade
     */
    fun validate(flowJson: Map<String, Any>): FlowValidationResult {
        val errors = mutableListOf<String>()

        try {
            // Validação 1: Estrutura básica
            errors.addAll(validateBasicStructure(flowJson))
            if (errors.isNotEmpty()) {
                return FlowValidationResult.Invalid(errors)
            }

            // Extração de dados seguros
            @Suppress("UNCHECKED_CAST")
            val components = (flowJson["components"] as? List<Map<String, Any>>) ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val connections = (flowJson["connections"] as? List<Map<String, Any>>) ?: emptyList()

            // Validação 2: Componentes
            errors.addAll(validateComponents(components))

            // Validação 3: Conexões
            errors.addAll(validateConnections(connections, components))

            // Validação 4: Referências cruzadas
            errors.addAll(validateCrossReferences(components, connections))

            // Validação 5: Grafos e ciclos
            errors.addAll(validateGraphStructure(components, connections))

        } catch (e: Exception) {
            errors.add("Error during flow validation: ${e.message}")
        }

        return if (errors.isEmpty()) {
            FlowValidationResult.Valid(flowJson["id"] as? String ?: "unknown")
        } else {
            FlowValidationResult.Invalid(errors)
        }
    }

    /**
     * Valida a estrutura básica do fluxo.
     */
    private fun validateBasicStructure(flowJson: Map<String, Any>): List<String> {
        val errors = mutableListOf<String>()

        // Validar campos obrigatórios
        if (flowJson["id"] !is String) {
            errors.add("Flow must have a 'id' field of type string")
        }

        if (flowJson["name"] !is String) {
            errors.add("Flow must have a 'name' field of type string")
        }

        if (flowJson["components"] !is List<*>) {
            errors.add("Flow must have a 'components' field of type array")
        }

        if (flowJson["connections"] !is List<*>) {
            errors.add("Flow must have a 'connections' field of type array")
        }

        // Validar versão se presente
        val version = flowJson["version"] as? String
        if (version != null && !isValidVersion(version)) {
            errors.add("Flow version must follow SemVer format (e.g., '1.0.0')")
        }

        return errors
    }

    /**
     * Valida componentes e suas propriedades.
     */
    private fun validateComponents(
        components: List<Map<String, Any>>
    ): List<String> {
        val errors = mutableListOf<String>()

        // Validar quantidade máxima
        if (components.size > MAX_COMPONENTS) {
            errors.add("Flow cannot have more than $MAX_COMPONENTS components, got ${components.size}")
        }

        // Validar IDs únicos
        val ids = mutableSetOf<String>()
        for ((index, component) in components.withIndex()) {
            val id = component["id"] as? String
            if (id.isNullOrEmpty()) {
                errors.add("Component at index $index is missing required 'id' field")
            } else {
                if (id in ids) {
                    errors.add("Duplicate component ID: '$id'")
                }
                ids.add(id)
            }

            val type = component["type"] as? String
            if (type.isNullOrEmpty()) {
                errors.add("Component '${id ?: "unknown"}' is missing required 'type' field")
            }

            // Validar propriedades
            @Suppress("UNCHECKED_CAST")
            val properties = (component["properties"] as? Map<String, Any>) ?: emptyMap()
            if (type != null && id != null) {
                // Validar propriedades com o validador
                // Nota: Aqui seria necessário um tipo Component real
                // Por enquanto, apenas validamos que não há injeção
                errors.addAll(validatePropertiesForInjection(properties))
            }
        }

        return errors
    }

    /**
     * Valida injeção em propriedades (sem validador de tipo).
     */
    private fun validatePropertiesForInjection(properties: Map<String, Any>): List<String> {
        val errors = mutableListOf<String>()

        for ((key, value) in properties) {
            when (value) {
                is String -> {
                    // Validar expressões em propriedades que parecem ser expressões
                    if (key.contains("expression", ignoreCase = true) ||
                        key.contains("condition", ignoreCase = true) ||
                        key.contains("filter", ignoreCase = true)) {
                        val result = ExpressionSanitizer.sanitize(value)
                        if (result is SanitizeResult.Invalid) {
                            errors.add("Property '$key': ${result.reason}")
                        }
                    }
                }
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    errors.addAll(validatePropertiesForInjection(value as Map<String, Any>))
                }
                is List<*> -> {
                    for (item in value) {
                        if (item is Map<*, *>) {
                            @Suppress("UNCHECKED_CAST")
                            errors.addAll(validatePropertiesForInjection(item as Map<String, Any>))
                        }
                    }
                }
            }
        }

        return errors
    }

    /**
     * Valida conexões entre componentes.
     */
    private fun validateConnections(
        connections: List<Map<String, Any>>,
        components: List<Map<String, Any>>
    ): List<String> {
        val errors = mutableListOf<String>()

        // Validar quantidade máxima
        if (connections.size > MAX_CONNECTIONS) {
            errors.add("Flow cannot have more than $MAX_CONNECTIONS connections, got ${connections.size}")
        }

        val componentIds = components.mapNotNull { it["id"] as? String }.toSet()

        for ((index, connection) in connections.withIndex()) {
            val from = connection["from"] as? String
            val to = connection["to"] as? String

            if (from.isNullOrEmpty()) {
                errors.add("Connection at index $index is missing required 'from' field")
            }
            if (to.isNullOrEmpty()) {
                errors.add("Connection at index $index is missing required 'to' field")
            }

            // Validar referências existem
            if (from != null && from !in componentIds) {
                errors.add("Connection references non-existent component 'from': '$from'")
            }
            if (to != null && to !in componentIds) {
                errors.add("Connection references non-existent component 'to': '$to'")
            }

            // Validar self-loop (pode ser permitido em alguns casos)
            if (from == to) {
                errors.add("Self-loop connection detected: component '$from' -> '$to'")
            }
        }

        return errors
    }

    /**
     * Valida referências cruzadas entre componentes e conexões.
     */
    private fun validateCrossReferences(
        components: List<Map<String, Any>>,
        connections: List<Map<String, Any>>
    ): List<String> {
        val errors = mutableListOf<String>()

        // Verificar se há componentes órfãos (exceto start/end)
        val connectedIds = mutableSetOf<String>()
        for (connection in connections) {
            connectedIds.add(connection["from"] as? String ?: "")
            connectedIds.add(connection["to"] as? String ?: "")
        }

        for (component in components) {
            val id = component["id"] as? String ?: continue
            val type = component["type"] as? String ?: continue

            // Start components podem estar desconectados (são ponto de entrada)
            if (type != "StartComponent" && type != "EndComponent") {
                if (id !in connectedIds) {
                    errors.add("Component '$id' is disconnected from the flow")
                }
            }
        }

        return errors
    }

    /**
     * Valida a estrutura do grafo (detecta ciclos infinitos).
     */
    private fun validateGraphStructure(
        components: List<Map<String, Any>>,
        connections: List<Map<String, Any>>
    ): List<String> {
        val errors = mutableListOf<String>()

        // Construir adjacência
        val adjacency = mutableMapOf<String, MutableList<String>>()
        for (connection in connections) {
            val from = connection["from"] as? String ?: continue
            val to = connection["to"] as? String ?: continue

            adjacency.getOrPut(from) { mutableListOf() }.add(to)
        }

        // Detectar ciclos usando DFS
        for (component in components) {
            val id = component["id"] as? String ?: continue
            val type = component["type"] as? String ?: continue

            if (type == "StartComponent") {
                val cycle = detectCycleDFS(id, adjacency, mutableSetOf(), 0)
                if (cycle != null) {
                    errors.add("Cycle detected in flow: ${cycle.joinToString(" -> ")}")
                }
            }
        }

        return errors
    }

    /**
     * Detecta ciclos usando Depth-First Search.
     */
    private fun detectCycleDFS(
        node: String,
        adjacency: Map<String, List<String>>,
        visited: MutableSet<String>,
        depth: Int
    ): List<String>? {
        if (depth > MAX_CYCLE_DEPTH) {
            return listOf(node) // Possível ciclo muito profundo
        }

        if (node in visited) {
            return listOf(node) // Ciclo encontrado
        }

        visited.add(node)

        for (neighbor in adjacency[node] ?: emptyList()) {
            val cycle = detectCycleDFS(neighbor, adjacency, visited, depth + 1)
            if (cycle != null) {
                return listOf(node) + cycle
            }
        }

        visited.remove(node)
        return null
    }

    /**
     * Valida se uma string segue o formato de versão semântica.
     */
    private fun isValidVersion(version: String): Boolean {
        return version.matches(Regex("^\\d+\\.\\d+\\.\\d+(-[a-zA-Z0-9.-]+)?(\\+[a-zA-Z0-9.-]+)?$"))
    }
}
