package io.flowmobile.core.loading

import io.flowmobile.core.domain.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Documento JSON completo contendo um fluxo.
 * Esta é a estrutura de nível mais alto do schema JSON.
 *
 * @property schemaVersion Versão do schema JSON (semântica: MAJOR.MINOR.PATCH)
 * @property flow Definição do fluxo contida no documento
 */
@Serializable
data class FlowDocument(
    val schemaVersion: String,
    val flow: FlowDefinition
)

/**
 * Definição intermediária de um fluxo durante o parsing.
 * Representa a estrutura JSON do fluxo antes da conversão para o modelo de domínio.
 *
 * @property id Identificador único do fluxo (UUID)
 * @property name Nome legível do fluxo
 * @property version Versão do fluxo
 * @property description Descrição do fluxo
 * @property components Lista de definições de componentes
 * @property connections Lista de definições de conexões
 * @property metadata Metadados adicionais
 */
@Serializable
data class FlowDefinition(
    val id: String,
    val name: String,
    val version: String,
    val description: String = "",
    val components: List<ComponentDefinition>,
    val connections: List<ConnectionDefinition>,
    val metadata: FlowMetadataDefinition = FlowMetadataDefinition()
)

/**
 * Definição intermediária de um componente durante o parsing.
 *
 * @property id Identificador único do componente
 * @property type Tipo do componente (START, END, ACTION, DECISION, etc.)
 * @property name Nome legível do componente
 * @property properties Mapa de propriedades de configuração
 * @property position Posição visual do componente
 * @property metadata Metadados adicionais
 */
@Serializable
data class ComponentDefinition(
    val id: String,
    val type: String,
    val name: String,
    val properties: Map<String, PropertyDefinition> = emptyMap(),
    val position: PositionDefinition? = null,
    val metadata: ComponentMetadataDefinition = ComponentMetadataDefinition()
)

/**
 * Definição intermediária de uma conexão durante o parsing.
 *
 * @property id Identificador único da conexão
 * @property source Referência ao componente e porta de origem
 * @property target Referência ao componente e porta de destino
 * @property metadata Metadados da conexão
 */
@Serializable
data class ConnectionDefinition(
    val id: String,
    val source: PortReference,
    val target: PortReference,
    val metadata: ConnectionMetadataDefinition = ConnectionMetadataDefinition()
)

/**
 * Referência a uma porta em um componente.
 *
 * @property componentId ID do componente
 * @property portId ID da porta no componente
 */
@Serializable
data class PortReference(
    val componentId: String,
    val portId: String
)

/**
 * Definição de uma propriedade de componente.
 * Pode ser um valor literal ou uma expressão.
 */
@Serializable
sealed class PropertyDefinition {
    
    /**
     * Valor literal string.
     */
    @Serializable
    data class StringValue(val value: String) : PropertyDefinition()
    
    /**
     * Valor literal numérico.
     */
    @Serializable
    data class NumberValue(val value: Double) : PropertyDefinition()
    
    /**
     * Valor literal booleano.
     */
    @Serializable
    data class BooleanValue(val value: Boolean) : PropertyDefinition()
    
    /**
     * Expressão que será avaliada em tempo de execução.
     */
    @Serializable
    data class Expression(val expression: String) : PropertyDefinition()
    
    /**
     * Valor nulo.
     */
    @Serializable
    data object NullValue : PropertyDefinition()
    
    /**
     * Objeto/mapa de propriedades.
     */
    @Serializable
    data class ObjectValue(val value: Map<String, PropertyDefinition>) : PropertyDefinition()
    
    /**
     * Array/lista de propriedades.
     */
    @Serializable
    data class ArrayValue(val value: List<PropertyDefinition>) : PropertyDefinition()
    
    /**
     * Converte para ComponentProperty do domínio.
     */
    fun toComponentProperty(): ComponentProperty = when (this) {
        is StringValue -> ComponentProperty.StringValue(value)
        is NumberValue -> ComponentProperty.NumberValue(value)
        is BooleanValue -> ComponentProperty.BooleanValue(value)
        is Expression -> ComponentProperty.Expression(expression)
        is NullValue -> ComponentProperty.StringValue("") // Representado como string vazia
        is ObjectValue -> ComponentProperty.ObjectValue(
            value.mapValues { it.value.toComponentProperty() }
        )
        is ArrayValue -> ComponentProperty.ArrayValue(
            value.map { it.toComponentProperty() }
        )
    }
}

/**
 * Definição de posição visual de um componente.
 */
@Serializable
data class PositionDefinition(
    val x: Double,
    val y: Double
)

/**
 * Metadados de fluxo na definição JSON.
 */
@Serializable
data class FlowMetadataDefinition(
    val description: String = "",
    val author: String = "",
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val tags: List<String> = emptyList(),
    val customData: Map<String, String> = emptyMap()
)

/**
 * Metadados de componente na definição JSON.
 */
@Serializable
data class ComponentMetadataDefinition(
    val description: String = "",
    val tags: List<String> = emptyList(),
    val customData: Map<String, String> = emptyMap()
)

/**
 * Metadados de conexão na definição JSON.
 */
@Serializable
data class ConnectionMetadataDefinition(
    val label: String = "",
    val customData: Map<String, String> = emptyMap()
)
