package io.flowmobile.core.domain

import kotlinx.serialization.Serializable

/**
 * O ponto final de um fluxo.
 * Um fluxo pode ter múltiplos EndComponents representando diferentes pontos de saída
 * (ex: sucesso, erro, diferentes resultados).
 * 
 * EndComponent tem uma ou mais portas de entrada e nenhuma porta de saída.
 * Pode opcionalmente especificar variáveis de saída que farão parte do resultado final.
 *
 * @property id Identificador único para este componente
 * @property name Nome legível
 * @property properties Propriedades de configuração (ex: variáveis de saída)
 * @property metadata Metadados adicionais
 */
@Serializable
data class EndComponent(
    override val id: String,
    override val name: String,
    override val properties: Map<String, ComponentProperty> = emptyMap(),
    override val metadata: ComponentMetadata = ComponentMetadata()
) : Component {
    
    override val type: ComponentType = ComponentType.END
    
    init {
        require(id.isNotBlank()) { "EndComponent id cannot be blank" }
        require(name.isNotBlank()) { "EndComponent name cannot be blank" }
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
        // End component has no output ports
        return emptyList()
    }
    
    companion object {
        /**
         * ID da porta de entrada.
         */
        const val PORT_INPUT = "in"
        
        /**
         * Chave de propriedade para variáveis de saída que serão incluídas no resultado final.
         */
        const val PROPERTY_OUTPUT_VARIABLES = "outputVariables"
        
        /**
         * Chave de propriedade para o nome do status/resultado deste componente final.
         */
        const val PROPERTY_STATUS = "status"
    }
}
