package io.flowmobile.core.domain

import kotlinx.serialization.Serializable

/**
 * Um componente que executa uma ação chamando um serviço do host.
 * 
 * ActionComponent interage com a plataforma através da interface de serviços do host.
 * Especifica qual serviço e método chamar, juntamente com parâmetros.
 * O resultado da ação pode ser armazenado em variáveis de contexto.
 *
 * @property id Identificador único para este componente
 * @property name Nome legível
 * @property properties Propriedades de configuração (serviço, método, parâmetros, etc.)
 * @property metadata Metadados adicionais
 */
@Serializable
data class ActionComponent(
    override val id: String,
    override val name: String,
    override val properties: Map<String, ComponentProperty> = emptyMap(),
    override val metadata: ComponentMetadata = ComponentMetadata()
) : Component {
    
    override val type: ComponentType = ComponentType.ACTION
    
    init {
        require(id.isNotBlank()) { "ActionComponent id cannot be blank" }
        require(name.isNotBlank()) { "ActionComponent name cannot be blank" }
        require(properties.containsKey(PROPERTY_SERVICE)) { 
            "ActionComponent must have a '$PROPERTY_SERVICE' property" 
        }
        require(properties.containsKey(PROPERTY_METHOD)) { 
            "ActionComponent must have a '$PROPERTY_METHOD' property" 
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
                id = "success",
                name = "Success",
                type = PortType.CONTROL,
                direction = PortDirection.OUTPUT,
                required = false
            ),
            Port(
                id = "error",
                name = "Error",
                type = PortType.CONTROL,
                direction = PortDirection.OUTPUT,
                required = false
            )
        )
    }
    
    companion object {
        /**
         * Chave de propriedade para o nome do serviço do host.
         * Exemplo: "storage", "network", "navigation"
         */
        const val PROPERTY_SERVICE = "service"
        
        /**
         * Chave de propriedade para o nome do método do serviço.
         * Exemplo: "get", "set", "request"
         */
        const val PROPERTY_METHOD = "method"
        
        /**
         * Chave de propriedade para parâmetros do método (mapa ou array).
         */
        const val PROPERTY_PARAMETERS = "parameters"
        
        /**
         * Chave de propriedade para o nome da variável para armazenar o resultado.
         */
        const val PROPERTY_RESULT_VARIABLE = "resultVariable"
        
        /**
         * Chave de propriedade para o nome da variável para armazenar informações de erro.
         */
        const val PROPERTY_ERROR_VARIABLE = "errorVariable"
    }
}
