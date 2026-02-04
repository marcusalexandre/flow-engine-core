package io.flowmobile.core.domain

import kotlinx.serialization.Serializable

/**
 * Representa um ponto de conexão em um componente onde conexões podem ser anexadas.
 * Portas definem as entradas e saídas de um componente no grafo de fluxo.
 *
 * @property id Identificador único para esta porta dentro do componente
 * @property name Nome legível para a porta
 * @property type Tipo de dado que esta porta aceita/produz
 * @property direction Se esta é uma porta de entrada ou saída
 * @property required Se uma conexão para esta porta é obrigatória
 */
@Serializable
data class Port(
    val id: String,
    val name: String,
    val type: PortType,
    val direction: PortDirection,
    val required: Boolean = false
) {
    init {
        require(id.isNotBlank()) { "Port id cannot be blank" }
        require(name.isNotBlank()) { "Port name cannot be blank" }
    }
}

/**
 * Direção de uma porta - se ela aceita conexões de entrada ou produz conexões de saída.
 */
@Serializable
enum class PortDirection {
    /** Porta aceita dados de entrada de outros componentes */
    INPUT,
    
    /** Porta produz dados de saída para outros componentes */
    OUTPUT
}

/**
 * Tipo de dado que flui através de uma porta.
 * Isso habilita verificação de tipos de conexões em tempo de design.
 */
@Serializable
enum class PortType {
    /** Fluxo de controle - determina o caminho de execução */
    CONTROL,
    
    /** Dados do tipo string */
    STRING,
    
    /** Dados numéricos (inteiro ou decimal) */
    NUMBER,
    
    /** Dados booleanos */
    BOOLEAN,
    
    /** Dados de objeto/mapa */
    OBJECT,
    
    /** Dados de array/lista */
    ARRAY,
    
    /** Qualquer tipo - sem verificação de tipo */
    ANY
}
