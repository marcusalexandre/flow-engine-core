package io.flowmobile.core.domain

import kotlinx.serialization.Serializable

/**
 * Representa uma conexão direcionada entre dois componentes no grafo de fluxo.
 * Conexões ligam uma porta de saída de um componente a uma porta de entrada de outro.
 * 
 * Conexões são imutáveis e definem o fluxo de controle e dados através do grafo.
 *
 * @property id Identificador único para esta conexão
 * @property sourceComponentId ID do componente onde esta conexão se origina
 * @property sourcePortId ID da porta de saída no componente de origem
 * @property targetComponentId ID do componente onde esta conexão termina
 * @property targetPortId ID da porta de entrada no componente de destino
 * @property metadata Metadados adicionais
 */
@Serializable
data class Connection(
    val id: String,
    val sourceComponentId: String,
    val sourcePortId: String,
    val targetComponentId: String,
    val targetPortId: String,
    val metadata: ConnectionMetadata = ConnectionMetadata()
) {
    init {
        require(id.isNotBlank()) { "Connection id cannot be blank" }
        require(sourceComponentId.isNotBlank()) { "Source component id cannot be blank" }
        require(sourcePortId.isNotBlank()) { "Source port id cannot be blank" }
        require(targetComponentId.isNotBlank()) { "Target component id cannot be blank" }
        require(targetPortId.isNotBlank()) { "Target port id cannot be blank" }
        require(sourceComponentId != targetComponentId) { 
            "Connection cannot connect a component to itself" 
        }
    }
}

/**
 * Metadados associados a uma conexão.
 */
@Serializable
data class ConnectionMetadata(
    val label: String = "",
    val customData: Map<String, String> = emptyMap()
)
