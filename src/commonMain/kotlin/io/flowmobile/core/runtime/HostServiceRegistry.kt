package io.flowmobile.core.runtime

import io.flowmobile.core.domain.*

/**
 * Interface para serviços do host application que podem ser chamados por ActionComponents.
 * 
 * Cada aplicação host (Android, iOS, Web) deve implementar esta interface para
 * fornecer funcionalidade específica que pode ser invocada durante a execução do fluxo.
 */
interface HostService {
    /**
     * Nome único do serviço.
     */
    val name: String
    
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
    ): ServiceResult
}

/**
 * Registro de serviços do host que podem ser chamados por ActionComponents.
 * 
 * O HostServiceRegistry mantém um mapa de serviços disponíveis para execução.
 * Cada aplicação host (Android, iOS, Web) registra seus serviços aqui.
 */
class HostServiceRegistry {
    
    private val services = mutableMapOf<String, HostService>()
    
    /**
     * Registra um serviço no registry.
     *
     * @param name Nome do serviço
     * @param service Implementação do serviço
     */
    fun register(name: String, service: HostService) {
        services[name] = service
    }
    
    /**
     * Remove um serviço do registry.
     *
     * @param name Nome do serviço a remover
     */
    fun unregister(name: String) {
        services.remove(name)
    }
    
    /**
     * Verifica se um serviço está disponível.
     *
     * @param name Nome do serviço
     * @return true se o serviço está registrado
     */
    fun isAvailable(name: String): Boolean {
        return services.containsKey(name)
    }
    
    /**
     * Retorna a lista de serviços disponíveis.
     */
    fun listAvailable(): List<String> {
        return services.keys.toList()
    }
    
    /**
     * Limpa todos os serviços registrados.
     */
    fun clear() {
        services.clear()
    }
    
    /**
     * Executa um serviço do host.
     *
     * @param serviceName Nome do serviço a executar
     * @param methodName Nome do método dentro do serviço
     * @param parameters Parâmetros para o método
     * @return Resultado da execução do serviço
     */
    suspend fun executeService(
        serviceName: String,
        methodName: String,
        parameters: Map<String, VariableValue>
    ): ServiceResult {
        val service = services[serviceName]
            ?: return ServiceResult.failure("Serviço '$serviceName' não encontrado")
        
        return try {
            service.execute(methodName, parameters)
        } catch (e: Exception) {
            ServiceResult.failure("Erro ao executar $serviceName.$methodName: ${e.message}")
        }
    }
}

/**
 * Resultado da execução de um serviço do host.
 *
 * @property success true se a execução foi bem-sucedida
 * @property result Valor de retorno do serviço
 * @property error Mensagem de erro se a execução falhou
 */
data class ServiceResult(
    val success: Boolean,
    val result: VariableValue?,
    val error: String? = null
) {
    companion object {
        fun success(result: VariableValue?) = ServiceResult(
            success = true,
            result = result,
            error = null
        )
        
        fun failure(error: String) = ServiceResult(
            success = false,
            result = null,
            error = error
        )
    }
}
