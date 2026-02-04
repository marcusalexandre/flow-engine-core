package io.flowmobile.core.domain

import kotlinx.serialization.Serializable

/**
 * Representa uma definição completa de fluxo.
 * Um fluxo é um grafo direcionado de componentes conectados por arestas.
 * 
 * Invariantes:
 * - Deve ter exatamente um StartComponent
 * - Deve ter pelo menos um EndComponent
 * - Todos os IDs de componentes devem ser únicos
 * - Todas as referências de conexão devem apontar para componentes e portas válidos
 *
 * @property id Identificador único para este fluxo
 * @property name Nome legível
 * @property version Versão semântica desta definição de fluxo
 * @property components Lista de todos os componentes no fluxo
 * @property connections Lista de todas as conexões entre componentes
 * @property metadata Metadados adicionais
 */
@Serializable
data class Flow(
    val id: String,
    val name: String,
    val version: String,
    val components: List<Component>,
    val connections: List<Connection>,
    val metadata: FlowMetadata = FlowMetadata()
) {
    init {
        require(id.isNotBlank()) { "Flow id cannot be blank" }
        require(name.isNotBlank()) { "Flow name cannot be blank" }
        require(version.isNotBlank()) { "Flow version cannot be blank" }
        require(components.isNotEmpty()) { "Flow must have at least one component" }
        
        // Validate exactly one StartComponent
        val startComponents = components.filterIsInstance<StartComponent>()
        require(startComponents.size == 1) { 
            "Flow must have exactly one StartComponent, found ${startComponents.size}" 
        }
        
        // Validate at least one EndComponent
        val endComponents = components.filterIsInstance<EndComponent>()
        require(endComponents.isNotEmpty()) { 
            "Flow must have at least one EndComponent, found ${endComponents.size}" 
        }
        
        // Validate unique component IDs
        val componentIds = components.map { it.id }
        require(componentIds.size == componentIds.distinct().size) { 
            "All component IDs must be unique" 
        }
        
        // Validate unique connection IDs
        val connectionIds = connections.map { it.id }
        require(connectionIds.size == connectionIds.distinct().size) { 
            "All connection IDs must be unique" 
        }
    }
    
    /**
     * Retorna o StartComponent deste fluxo.
     * Garantido existir devido à validação de inicialização.
     */
    fun getStartComponent(): StartComponent {
        return components.filterIsInstance<StartComponent>().first()
    }
    
    /**
     * Retorna todos os EndComponents neste fluxo.
     * Garantido ter pelo menos um devido à validação de inicialização.
     */
    fun getEndComponents(): List<EndComponent> {
        return components.filterIsInstance<EndComponent>()
    }
    
    /**
     * Encontra um componente pelo seu ID.
     */
    fun getComponentById(id: String): Component? {
        return components.find { it.id == id }
    }
    
    /**
     * Encontra todas as conexões originadas de um componente e porta específicos.
     */
    fun getOutgoingConnections(componentId: String, portId: String): List<Connection> {
        return connections.filter { 
            it.sourceComponentId == componentId && it.sourcePortId == portId 
        }
    }
    
    /**
     * Encontra todas as conexões originadas de um componente (qualquer porta).
     */
    fun getOutgoingConnections(componentId: String): List<Connection> {
        return connections.filter { 
            it.sourceComponentId == componentId
        }
    }
    
    /**
     * Encontra todas as conexões terminando em um componente e porta específicos.
     */
    fun getIncomingConnections(componentId: String, portId: String): List<Connection> {
        return connections.filter { 
            it.targetComponentId == componentId && it.targetPortId == portId 
        }
    }
    
    /**
     * Encontra todas as conexões terminando em um componente (qualquer porta).
     */
    fun getIncomingConnections(componentId: String): List<Connection> {
        return connections.filter { 
            it.targetComponentId == componentId
        }
    }
}

/**
 * Metadados associados a um fluxo.
 */
@Serializable
data class FlowMetadata(
    val description: String = "",
    val author: String = "",
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val tags: List<String> = emptyList(),
    val customData: Map<String, String> = emptyMap()
)
