package io.flowmobile.core.domain

import kotlinx.serialization.Serializable

/**
 * Interface base para todos os componentes em um fluxo.
 * Componentes são os nós no grafo de fluxo que executam operações ou controlam o fluxo.
 * Todos os componentes são imutáveis por design.
 *
 * @property id Identificador único para este componente dentro do fluxo
 * @property type Tipo do componente (determina o comportamento)
 * @property name Nome legível para este componente
 * @property properties Propriedades de configuração específicas para este tipo de componente
 * @property metadata Metadados adicionais (posição, descrição, etc.)
 */
@Serializable
sealed interface Component {
    val id: String
    val type: ComponentType
    val name: String
    val properties: Map<String, ComponentProperty>
    val metadata: ComponentMetadata
    
    /**
     * Retorna as portas de entrada para este componente.
     * Vazio para componentes que não aceitam entradas (ex: StartComponent).
     */
    fun getInputPorts(): List<Port>
    
    /**
     * Retorna as portas de saída para este componente.
     * Vazio para componentes que não produzem saídas (ex: EndComponent).
     */
    fun getOutputPorts(): List<Port>
}

/**
 * Tipo de componente, determinando seu comportamento e papel no fluxo.
 */
@Serializable
enum class ComponentType {
    /** Ponto inicial da execução do fluxo */
    START,
    
    /** Ponto final da execução do fluxo */
    END,
    
    /** Executa uma ação usando um serviço do host */
    ACTION,
    
    /** Toma uma decisão e ramifica a execução */
    DECISION,
    
    /** Itera sobre uma coleção ou condição */
    LOOP,
    
    /** Alterna entre múltiplas ramificações */
    SWITCH,
    
    /** Transforma dados */
    TRANSFORM,
    
    /** Define ou obtém uma variável */
    VARIABLE,
    
    /** Chama outro fluxo como subfluxo */
    SUBFLOW,
    
    /** Divide a execução em múltiplas ramificações paralelas */
    FORK,
    
    /** Aguarda múltiplas ramificações paralelas convergem */
    JOIN
}

/**
 * Metadados associados a um componente.
 * Esta informação não afeta a execução mas é útil para UI e documentação.
 */
@Serializable
data class ComponentMetadata(
    val position: Position? = null,
    val description: String = "",
    val tags: List<String> = emptyList(),
    val customData: Map<String, String> = emptyMap()
)

/**
 * Posição de um componente em um editor visual.
 */
@Serializable
data class Position(
    val x: Double,
    val y: Double
)

/**
 * Um valor de propriedade para configuração de componente.
 * Propriedades podem ser valores literais ou expressões que referenciam variáveis de contexto.
 */
@Serializable
sealed interface ComponentProperty {
    /** Valor literal string */
    @Serializable
    data class StringValue(val value: String) : ComponentProperty
    
    /** Valor literal numérico */
    @Serializable
    data class NumberValue(val value: Double) : ComponentProperty
    
    /** Valor literal booleano */
    @Serializable
    data class BooleanValue(val value: Boolean) : ComponentProperty
    
    /** Expressão que é avaliada em tempo de execução */
    @Serializable
    data class Expression(val expression: String) : ComponentProperty
    
    /** Valor de objeto/mapa */
    @Serializable
    data class ObjectValue(val value: Map<String, ComponentProperty>) : ComponentProperty
    
    /** Valor de array/lista */
    @Serializable
    data class ArrayValue(val value: List<ComponentProperty>) : ComponentProperty
}
