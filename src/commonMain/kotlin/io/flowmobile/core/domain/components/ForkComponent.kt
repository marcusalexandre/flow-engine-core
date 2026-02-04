package io.flowmobile.core.domain

import kotlinx.serialization.Serializable

/**
 * Um componente que divide a execução em múltiplas ramificações paralelas.
 *
 * ForkComponent tem uma porta de entrada e múltiplas portas de saída (uma para cada branch).
 * Cada branch pode ser executado em paralelo de forma independente.
 * O componente seguinte deve ser um JoinComponent para convergir as ramificações.
 *
 * Exemplo de uso:
 * ```
 *     ┌──► [Branch 1] ──┐
 * [Fork] ┤                 ├──► [Join] ──► [End]
 *     └──► [Branch 2] ──┘
 * ```
 *
 * @property id Identificador único
 * @property name Nome legível
 * @property properties Propriedades incluindo número de branches
 * @property metadata Metadados adicionais
 */
@Serializable
data class ForkComponent(
    override val id: String,
    override val name: String,
    override val properties: Map<String, ComponentProperty> = emptyMap(),
    override val metadata: ComponentMetadata = ComponentMetadata()
) : Component {
    
    override val type: ComponentType = ComponentType.FORK
    
    init {
        require(id.isNotBlank()) { "ForkComponent id cannot be blank" }
        require(name.isNotBlank()) { "ForkComponent name cannot be blank" }
        require(properties.containsKey(PROPERTY_BRANCH_COUNT)) {
            "ForkComponent must have a '$PROPERTY_BRANCH_COUNT' property"
        }
    }
    
    /**
     * Obtém o número de ramificações a partir das propriedades.
     */
    fun getBranchCount(): Int {
        val prop = properties[PROPERTY_BRANCH_COUNT]
        return when (prop) {
            is ComponentProperty.NumberValue -> prop.value.toInt()
            else -> 2 // Padrão: 2 ramificações
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
        val branchCount = getBranchCount()
        return (0 until branchCount).map { index ->
            Port(
                id = "branch_$index",
                name = "Branch $index",
                type = PortType.CONTROL,
                direction = PortDirection.OUTPUT,
                required = false
            )
        }
    }
    
    companion object {
        /**
         * Propriedade que define o número de ramificações.
         */
        const val PROPERTY_BRANCH_COUNT = "branchCount"
        
        /**
         * ID da porta de entrada.
         */
        const val PORT_INPUT = "in"
        
        /**
         * Padrão de ID para portas de saída: "branch_{index}".
         */
        fun getBranchPortId(index: Int): String = "branch_$index"
    }
}
