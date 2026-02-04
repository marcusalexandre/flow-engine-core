package io.flowmobile.core.services.mock

import io.flowmobile.core.services.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Implementação mock do NetworkService para testes e sandbox.
 * 
 * Capacidades:
 * - Predefined Responses: Respostas configuradas por URL
 * - Operation Recording: Todas as chamadas são gravadas
 * - Failure Injection: Simula falhas de rede
 * - Latency Simulation: Adiciona delay artificial
 * - Connectivity Simulation: Simula estados de conectividade
 */
class MockNetworkService(
    /** Respostas pré-configuradas por URL pattern */
    predefinedResponses: Map<String, NetworkResponse> = emptyMap(),
    
    /** Se deve simular falha de conexão */
    var shouldFail: Boolean = false,
    
    /** Mensagem de erro quando shouldFail é true */
    var failureMessage: String = "Falha de conexão simulada",
    
    /** Latência artificial em milissegundos */
    var latencyMs: Long = 0,
    
    /** Se está conectado à internet */
    var isConnectedState: Boolean = true,
    
    /** Tipo de conexão simulada */
    var connectionTypeState: ConnectionType = ConnectionType.WIFI
) : NetworkService {
    
    private val _predefinedResponses = predefinedResponses.toMutableMap()
    
    private val _recordedRequests = mutableListOf<NetworkOperation>()
    
    /** Lista de requisições gravadas para verificação em testes */
    val recordedRequests: List<NetworkOperation> get() = _recordedRequests.toList()
    
    private val _connectivityState = MutableStateFlow(
        ConnectivityState(
            isConnected = isConnectedState,
            connectionType = connectionTypeState
        )
    )
    
    private suspend fun simulateLatency() {
        if (latencyMs > 0) {
            delay(latencyMs)
        }
    }
    
    private fun recordRequest(operation: NetworkOperation) {
        _recordedRequests.add(operation)
    }
    
    override suspend fun request(config: RequestConfig): NetworkResponse {
        simulateLatency()
        
        recordRequest(NetworkOperation.Request(config))
        
        if (shouldFail || !isConnectedState) {
            val response = NetworkResponse.connectionError(failureMessage)
            recordRequest(NetworkOperation.Response(config.url, response))
            return response
        }
        
        // Procura por resposta pré-configurada
        val predefinedResponse = findPredefinedResponse(config.url)
        if (predefinedResponse != null) {
            recordRequest(NetworkOperation.Response(config.url, predefinedResponse))
            return predefinedResponse
        }
        
        // Resposta padrão de sucesso
        val defaultResponse = NetworkResponse.success(
            statusCode = 200,
            body = """{"status": "ok", "mock": true}""",
            headers = mapOf("Content-Type" to "application/json")
        )
        recordRequest(NetworkOperation.Response(config.url, defaultResponse))
        return defaultResponse
    }
    
    private fun findPredefinedResponse(url: String): NetworkResponse? {
        // Procura por match exato primeiro
        _predefinedResponses[url]?.let { return it }
        
        // Procura por pattern (suporta wildcards simples)
        for ((pattern, response) in _predefinedResponses) {
            if (pattern.contains("*")) {
                val regex = pattern.replace("*", ".*").toRegex()
                if (regex.matches(url)) {
                    return response
                }
            }
        }
        
        return null
    }
    
    override suspend fun isConnected(): Boolean {
        simulateLatency()
        recordRequest(NetworkOperation.ConnectivityCheck(isConnectedState))
        return isConnectedState
    }
    
    override suspend fun getConnectionType(): ConnectionType {
        simulateLatency()
        recordRequest(NetworkOperation.ConnectionTypeCheck(connectionTypeState))
        return connectionTypeState
    }
    
    override fun observeConnectivity(): Flow<ConnectivityState> {
        recordRequest(NetworkOperation.ObserveConnectivity)
        return _connectivityState.asStateFlow()
    }
    
    override suspend fun download(
        url: String,
        destination: String?,
        progressCallback: ((DownloadProgress) -> Unit)?
    ): DownloadResult {
        simulateLatency()
        
        recordRequest(NetworkOperation.Download(url, destination))
        
        if (shouldFail || !isConnectedState) {
            return DownloadResult.Failure(failureMessage)
        }
        
        // Simula progresso
        if (progressCallback != null) {
            for (i in 0..100 step 20) {
                progressCallback(DownloadProgress(
                    bytesDownloaded = i.toLong() * 100,
                    totalBytes = 10000,
                    percentage = i
                ))
                delay(10) // Pequeno delay entre atualizações de progresso
            }
        }
        
        // Simula download bem-sucedido
        return DownloadResult.Success(
            data = "Mock download content".encodeToByteArray(),
            localPath = destination
        )
    }
    
    override suspend fun cancel(requestId: String): Boolean {
        recordRequest(NetworkOperation.Cancel(requestId))
        return true
    }
    
    // Métodos auxiliares para testes
    
    /**
     * Atualiza o estado de conectividade (emite para observers).
     */
    fun updateConnectivity(isConnected: Boolean, connectionType: ConnectionType = ConnectionType.WIFI) {
        isConnectedState = isConnected
        connectionTypeState = connectionType
        _connectivityState.value = ConnectivityState(
            isConnected = isConnected,
            connectionType = connectionType
        )
    }
    
    /**
     * Adiciona uma resposta pré-configurada.
     */
    fun addPredefinedResponse(urlPattern: String, response: NetworkResponse) {
        _predefinedResponses[urlPattern] = response
    }
    
    /**
     * Limpa o registro de operações.
     */
    fun clearRecordedRequests() {
        _recordedRequests.clear()
    }
    
    /**
     * Verifica se uma requisição para determinada URL foi feita.
     */
    fun hasRequestTo(url: String): Boolean {
        return _recordedRequests.any { 
            it is NetworkOperation.Request && it.config.url == url 
        }
    }
    
    /**
     * Conta requisições por método HTTP.
     */
    fun countRequestsByMethod(method: HttpMethod): Int {
        return _recordedRequests.count { 
            it is NetworkOperation.Request && it.config.method == method 
        }
    }
    
    /**
     * Reseta o mock para o estado inicial.
     */
    fun reset() {
        _recordedRequests.clear()
        shouldFail = false
        latencyMs = 0
        isConnectedState = true
        connectionTypeState = ConnectionType.WIFI
        _connectivityState.value = ConnectivityState(
            isConnected = true,
            connectionType = ConnectionType.WIFI
        )
    }
}

/**
 * Representa uma operação de rede gravada.
 */
sealed class NetworkOperation {
    abstract val timestamp: Long
    
    data class Request(
        val config: RequestConfig,
        override val timestamp: Long = currentTimeMillis()
    ) : NetworkOperation()
    
    data class Response(
        val url: String,
        val response: NetworkResponse,
        override val timestamp: Long = currentTimeMillis()
    ) : NetworkOperation()
    
    data class ConnectivityCheck(
        val result: Boolean,
        override val timestamp: Long = currentTimeMillis()
    ) : NetworkOperation()
    
    data class ConnectionTypeCheck(
        val result: ConnectionType,
        override val timestamp: Long = currentTimeMillis()
    ) : NetworkOperation()
    
    data object ObserveConnectivity : NetworkOperation() {
        override val timestamp: Long = currentTimeMillis()
    }
    
    data class Download(
        val url: String,
        val destination: String?,
        override val timestamp: Long = currentTimeMillis()
    ) : NetworkOperation()
    
    data class Cancel(
        val requestId: String,
        override val timestamp: Long = currentTimeMillis()
    ) : NetworkOperation()
}
