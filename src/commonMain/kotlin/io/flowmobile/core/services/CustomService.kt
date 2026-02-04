package io.flowmobile.core.services

import io.flowmobile.core.domain.VariableValue

/**
 * Interface base para serviços customizados.
 * 
 * Permite que aplicações host registrem serviços específicos do domínio
 * que não são cobertos pelas interfaces padrão.
 */
interface CustomService {
    /**
     * Nome único do serviço.
     */
    val name: String
    
    /**
     * Descrição do serviço.
     */
    val description: String
    
    /**
     * Lista de métodos disponíveis neste serviço.
     */
    val availableMethods: List<ServiceMethod>
    
    /**
     * Executa um método do serviço.
     *
     * @param method Nome do método a executar
     * @param parameters Parâmetros para o método
     * @return Resultado da execução
     */
    suspend fun execute(
        method: String,
        parameters: Map<String, VariableValue>
    ): CustomServiceResult
}

/**
 * Descrição de um método de serviço.
 */
data class ServiceMethod(
    /** Nome do método */
    val name: String,
    
    /** Descrição do que o método faz */
    val description: String,
    
    /** Parâmetros esperados pelo método */
    val parameters: List<ServiceParameter>,
    
    /** Tipo de retorno do método */
    val returnType: String,
    
    /** Se o método é assíncrono (suspend) */
    val isAsync: Boolean = true
)

/**
 * Descrição de um parâmetro de método.
 */
data class ServiceParameter(
    /** Nome do parâmetro */
    val name: String,
    
    /** Descrição do parâmetro */
    val description: String,
    
    /** Tipo esperado (String, Number, Boolean, Object, Array) */
    val type: String,
    
    /** Se o parâmetro é obrigatório */
    val required: Boolean = true,
    
    /** Valor padrão (se não obrigatório) */
    val defaultValue: String? = null
)

/**
 * Resultado da execução de um serviço customizado.
 */
sealed class CustomServiceResult {
    /**
     * Execução bem-sucedida.
     *
     * @param result Valor de retorno (pode ser null)
     * @param metadata Metadados adicionais sobre a execução
     */
    data class Success(
        val result: VariableValue?,
        val metadata: Map<String, String> = emptyMap()
    ) : CustomServiceResult()
    
    /**
     * Execução falhou.
     *
     * @param error Descrição do erro
     * @param code Código de erro (para identificação programática)
     * @param details Detalhes adicionais do erro
     */
    data class Failure(
        val error: String,
        val code: String = "UNKNOWN_ERROR",
        val details: Map<String, String> = emptyMap()
    ) : CustomServiceResult()
    
    /**
     * Método não encontrado no serviço.
     *
     * @param method Nome do método que não foi encontrado
     * @param availableMethods Lista de métodos disponíveis
     */
    data class MethodNotFound(
        val method: String,
        val availableMethods: List<String>
    ) : CustomServiceResult()
    
    companion object {
        /**
         * Cria um resultado de sucesso.
         */
        fun success(result: VariableValue?) = Success(result)
        
        /**
         * Cria um resultado de falha.
         */
        fun failure(error: String, code: String = "UNKNOWN_ERROR") = Failure(error, code)
        
        /**
         * Cria um resultado de método não encontrado.
         */
        fun methodNotFound(method: String, availableMethods: List<String>) =
            MethodNotFound(method, availableMethods)
    }
}

/**
 * Classe base abstrata para facilitar a implementação de serviços customizados.
 */
abstract class BaseCustomService(
    override val name: String,
    override val description: String
) : CustomService {
    
    protected val methodHandlers = mutableMapOf<String, suspend (Map<String, VariableValue>) -> CustomServiceResult>()
    
    override val availableMethods: List<ServiceMethod>
        get() = methodHandlers.keys.map { methodName ->
            ServiceMethod(
                name = methodName,
                description = "",
                parameters = emptyList(),
                returnType = "Any"
            )
        }
    
    /**
     * Registra um handler para um método.
     */
    protected fun registerMethod(
        name: String,
        handler: suspend (Map<String, VariableValue>) -> CustomServiceResult
    ) {
        methodHandlers[name] = handler
    }
    
    override suspend fun execute(
        method: String,
        parameters: Map<String, VariableValue>
    ): CustomServiceResult {
        val handler = methodHandlers[method]
            ?: return CustomServiceResult.methodNotFound(method, methodHandlers.keys.toList())
        
        return try {
            handler(parameters)
        } catch (e: Exception) {
            CustomServiceResult.failure(
                error = e.message ?: "Erro desconhecido ao executar $method",
                code = "EXECUTION_ERROR"
            )
        }
    }
}
