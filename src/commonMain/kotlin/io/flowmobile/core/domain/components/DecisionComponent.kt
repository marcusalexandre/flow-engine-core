package io.flowmobile.core.domain

import kotlinx.serialization.Serializable

/**
 * Um componente que avalia uma condição e roteia a execução para uma de duas ramificações.
 * 
 * DecisionComponent tem uma porta de entrada e duas portas de saída: "true" e "false".
 * A expressão de condição é avaliada contra o contexto de execução atual,
 * e a execução prossegue pela porta de saída apropriada.
 *
 * @property id Identificador único para este componente
 * @property name Nome legível
 * @property properties Propriedades de configuração (deve incluir expressão de condição)
 * @property metadata Metadados adicionais
 */
@Serializable
data class DecisionComponent(
    override val id: String,
    override val name: String,
    override val properties: Map<String, ComponentProperty> = emptyMap(),
    override val metadata: ComponentMetadata = ComponentMetadata()
) : Component {
    
    override val type: ComponentType = ComponentType.DECISION
    
    init {
        require(id.isNotBlank()) { "DecisionComponent id cannot be blank" }
        require(name.isNotBlank()) { "DecisionComponent name cannot be blank" }
        require(properties.containsKey(PROPERTY_CONDITION)) { 
            "DecisionComponent must have a '$PROPERTY_CONDITION' property" 
        }
    }
    
    override fun getInputPorts(): List<Port> {
        return listOf(
            Port(
                id = "in",
                name = "Input",
                type = PortType.CONTROL,
                direction = PortDirection.INPUT,
                required = true
            )
        )
    }
    
    override fun getOutputPorts(): List<Port> {
        return listOf(
            Port(
                id = "true",
                name = "True",
                type = PortType.CONTROL,
                direction = PortDirection.OUTPUT,
                required = false
            ),
            Port(
                id = "false",
                name = "False",
                type = PortType.CONTROL,
                direction = PortDirection.OUTPUT,
                required = false
            )
        )
    }
    
    companion object {
        /**
         * Chave de propriedade para a expressão de condição booleana.
         * A expressão deve avaliar para um valor booleano.
         */
        const val PROPERTY_CONDITION = "condition"
        
        /**
         * ID da porta de entrada.
         */
        const val PORT_INPUT = "in"
        
        /**
         * ID da porta de saída para condição verdadeira.
         */
        const val PORT_TRUE = "true"
        
        /**
         * ID da porta de saída para condição falsa.
         */
        const val PORT_FALSE = "false"
    }
}
