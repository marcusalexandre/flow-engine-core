package io.flowmobile.core.domain

import kotlinx.serialization.Serializable

/**
 * Um componente que aguarda múltiplas ramificações paralelas convergirem.
 *
 * JoinComponent é sempre seguido de um ForkComponent no fluxo.
 * Pode operar em modo AND (aguarda todas as ramificações) ou OR (aguarda qualquer uma).
 *
 * Modo AND (padrão):
 * - Aguarda todas as ramificações completarem antes de prosseguir
 * - Procede apenas se todas tiverem sucesso
 *
 * Modo OR:
 * - Procede assim que a primeira ramificação completar com sucesso
 * - Cancela as outras ramificações em execução
 *
 * @property id Identificador único
 * @property name Nome legível
 * @property properties Propriedades incluindo modo de join (AND/OR)
 * @property metadata Metadados adicionais
 */
@Serializable
data class JoinComponent(
    override val id: String,
    override val name: String,
    override val properties: Map<String, ComponentProperty> = emptyMap(),
    override val metadata: ComponentMetadata = ComponentMetadata()
) : Component {
    
    override val type: ComponentType = ComponentType.JOIN
    
    init {
        require(id.isNotBlank()) { "JoinComponent id cannot be blank" }
        require(name.isNotBlank()) { "JoinComponent name cannot be blank" }
    }
    
    /**
     * Obtém o modo de join (AND ou OR) a partir das propriedades.
     */
    fun getJoinMode(): JoinMode {
        val prop = properties[PROPERTY_JOIN_MODE]
        return when {
            prop is ComponentProperty.StringValue && prop.value == "OR" -> JoinMode.OR
            else -> JoinMode.AND
        }
    }
    
    /**
     * Obtém o timeout em milissegundos, se configurado.
     */
    fun getTimeoutMs(): Long? {
        val prop = properties[PROPERTY_TIMEOUT_MS]
        return when (prop) {
            is ComponentProperty.NumberValue -> prop.value.toLong()
            else -> null
        }
    }
    
    override fun getInputPorts(): List<Port> {
        // JoinComponent tem múltiplas portas de entrada, uma para cada branch
        // O número é determinado em tempo de validação do grafo
        return listOf(
            Port(
                id = "in",
                name = "Input (múltiplas ramificações)",
                type = PortType.CONTROL,
                direction = PortDirection.INPUT,
                required = true
            )
        )
    }
    
    override fun getOutputPorts(): List<Port> {
        return listOf(
            Port(
                id = "out",
                name = "Output",
                type = PortType.CONTROL,
                direction = PortDirection.OUTPUT,
                required = false
            )
        )
    }
    
    companion object {
        /**
         * Propriedade que define o modo de join: "AND" ou "OR".
         */
        const val PROPERTY_JOIN_MODE = "joinMode"
        
        /**
         * Propriedade para timeout em milissegundos.
         * Se definida, o join será cancelado após esse período.
         */
        const val PROPERTY_TIMEOUT_MS = "timeoutMs"
        
        /**
         * ID da porta de entrada.
         */
        const val PORT_INPUT = "in"
        
        /**
         * ID da porta de saída.
         */
        const val PORT_OUTPUT = "out"
    }
}

/**
 * Modo de operação para o JoinComponent.
 */
@Serializable
enum class JoinMode {
    /**
     * AND: Aguarda todas as ramificações completarem.
     * Procede apenas se todas forem bem-sucedidas.
     */
    AND,
    
    /**
     * OR: Procede assim que a primeira ramificação completar com sucesso.
     * Cancela as outras ramificações.
     */
    OR
}
