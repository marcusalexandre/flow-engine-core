package io.flowmobile.core.domain

import kotlinx.serialization.Serializable

/**
 * O ponto inicial de um fluxo.
 * Todo fluxo deve ter exatamente um StartComponent.
 * 
 * StartComponent não tem portas de entrada e uma porta de saída para fluxo de controle.
 * Ele define o contexto de execução inicial com variáveis que estarão disponíveis
 * durante toda a execução do fluxo.
 *
 * @property id Identificador único para este componente
 * @property name Nome legível
 * @property properties Propriedades de configuração (ex: variáveis iniciais)
 * @property metadata Metadados adicionais
 */
@Serializable
data class StartComponent(
    override val id: String,
    override val name: String,
    override val properties: Map<String, ComponentProperty> = emptyMap(),
    override val metadata: ComponentMetadata = ComponentMetadata()
) : Component {
    
    override val type: ComponentType = ComponentType.START
    
    init {
        require(id.isNotBlank()) { "StartComponent id cannot be blank" }
        require(name.isNotBlank()) { "StartComponent name cannot be blank" }
    }
    
    override fun getInputPorts(): List<Port> {
        // Start component has no input ports
        return emptyList()
    }
    
    override fun getOutputPorts(): List<Port> {
        return listOf(
            Port(
                id = "out",
                name = "Output",
                type = PortType.CONTROL,
                direction = PortDirection.OUTPUT,
                required = true
            )
        )
    }
    
    companion object {
        /**
         * ID da porta de saída.
         */
        const val PORT_OUTPUT = "out"
        
        /**
         * Chave de propriedade para o mapa de variáveis iniciais.
         */
        const val PROPERTY_INITIAL_VARIABLES = "initialVariables"
    }
}
