package io.flowmobile.core.services

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * Serviço de rede para requisições HTTP e monitoramento de conectividade.
 * 
 * Implementações específicas por plataforma:
 * - Android: OkHttp, Ktor Client, ConnectivityManager
 * - iOS: URLSession, Alamofire, NWPathMonitor
 * - Web: Fetch API, Navigator.onLine
 */
interface NetworkService {
    
    /**
     * Executa uma requisição HTTP.
     *
     * @param config Configuração da requisição
     * @return Resposta da requisição
     */
    suspend fun request(config: RequestConfig): NetworkResponse
    
    /**
     * Verifica se há conectividade com a internet.
     *
     * @return true se há conexão disponível
     */
    suspend fun isConnected(): Boolean
    
    /**
     * Retorna o tipo de conexão atual.
     *
     * @return Tipo de conexão (wifi, mobile, none, etc.)
     */
    suspend fun getConnectionType(): ConnectionType
    
    /**
     * Observa mudanças no estado de conectividade.
     *
     * @return Flow que emite o estado de conectividade quando há mudanças
     */
    fun observeConnectivity(): Flow<ConnectivityState>
    
    /**
     * Faz download de um arquivo.
     *
     * @param url URL do arquivo
     * @param destination Caminho de destino (se aplicável à plataforma)
     * @param progressCallback Callback para progresso do download
     * @return Resultado do download
     */
    suspend fun download(
        url: String,
        destination: String? = null,
        progressCallback: ((DownloadProgress) -> Unit)? = null
    ): DownloadResult
    
    /**
     * Cancela uma requisição em andamento.
     *
     * @param requestId ID da requisição a cancelar
     * @return true se a requisição foi cancelada com sucesso
     */
    suspend fun cancel(requestId: String): Boolean
}

/**
 * Configuração para uma requisição HTTP.
 */
@Serializable
data class RequestConfig(
    /** URL da requisição */
    val url: String,
    
    /** Método HTTP (GET, POST, PUT, DELETE, etc.) */
    val method: HttpMethod = HttpMethod.GET,
    
    /** Headers da requisição */
    val headers: Map<String, String> = emptyMap(),
    
    /** Body da requisição (para POST, PUT, etc.) */
    val body: String? = null,
    
    /** Content-Type do body */
    val contentType: String = "application/json",
    
    /** Timeout em milissegundos */
    val timeoutMs: Long = 30_000,
    
    /** Query parameters */
    val queryParams: Map<String, String> = emptyMap(),
    
    /** ID único para esta requisição (para cancelamento) */
    val requestId: String? = null
)

/**
 * Método HTTP.
 */
@Serializable
enum class HttpMethod {
    GET,
    POST,
    PUT,
    DELETE,
    PATCH,
    HEAD,
    OPTIONS
}

/**
 * Resposta de uma requisição HTTP.
 */
@Serializable
data class NetworkResponse(
    /** Código de status HTTP */
    val statusCode: Int,
    
    /** Headers da resposta */
    val headers: Map<String, String>,
    
    /** Body da resposta como string */
    val body: String?,
    
    /** Se a requisição foi bem-sucedida (status 2xx) */
    val isSuccess: Boolean,
    
    /** Mensagem de erro (se houver) */
    val error: String? = null,
    
    /** Tempo de resposta em milissegundos */
    val responseTimeMs: Long = 0
) {
    companion object {
        /**
         * Cria uma resposta de sucesso.
         */
        fun success(statusCode: Int, body: String?, headers: Map<String, String> = emptyMap()) =
            NetworkResponse(
                statusCode = statusCode,
                headers = headers,
                body = body,
                isSuccess = true
            )
        
        /**
         * Cria uma resposta de erro.
         */
        fun error(statusCode: Int, error: String, headers: Map<String, String> = emptyMap()) =
            NetworkResponse(
                statusCode = statusCode,
                headers = headers,
                body = null,
                isSuccess = false,
                error = error
            )
        
        /**
         * Cria uma resposta de erro de conexão.
         */
        fun connectionError(error: String) =
            NetworkResponse(
                statusCode = -1,
                headers = emptyMap(),
                body = null,
                isSuccess = false,
                error = error
            )
    }
}

/**
 * Tipo de conexão de rede.
 */
@Serializable
enum class ConnectionType {
    /** Sem conexão */
    NONE,
    
    /** Conexão WiFi */
    WIFI,
    
    /** Conexão móvel (3G, 4G, 5G) */
    MOBILE,
    
    /** Conexão Ethernet */
    ETHERNET,
    
    /** Tipo desconhecido */
    UNKNOWN
}

/**
 * Estado de conectividade.
 */
@Serializable
data class ConnectivityState(
    /** Se está conectado */
    val isConnected: Boolean,
    
    /** Tipo de conexão */
    val connectionType: ConnectionType,
    
    /** Se a conexão é medida (dados limitados) */
    val isMetered: Boolean = false,
    
    /** Qualidade estimada da conexão */
    val quality: ConnectionQuality = ConnectionQuality.UNKNOWN
)

/**
 * Qualidade estimada da conexão.
 */
@Serializable
enum class ConnectionQuality {
    /** Conexão excelente */
    EXCELLENT,
    
    /** Conexão boa */
    GOOD,
    
    /** Conexão moderada */
    MODERATE,
    
    /** Conexão fraca */
    POOR,
    
    /** Qualidade desconhecida */
    UNKNOWN
}

/**
 * Progresso de download.
 */
@Serializable
data class DownloadProgress(
    /** Bytes já baixados */
    val bytesDownloaded: Long,
    
    /** Tamanho total em bytes (-1 se desconhecido) */
    val totalBytes: Long,
    
    /** Percentual de progresso (0-100) */
    val percentage: Int
)

/**
 * Resultado de um download.
 */
sealed class DownloadResult {
    /**
     * Download bem-sucedido.
     *
     * @param data Dados baixados como ByteArray
     * @param localPath Caminho local onde o arquivo foi salvo (se aplicável)
     */
    data class Success(
        val data: ByteArray,
        val localPath: String? = null
    ) : DownloadResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as Success
            return data.contentEquals(other.data) && localPath == other.localPath
        }
        
        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + (localPath?.hashCode() ?: 0)
            return result
        }
    }
    
    /**
     * Download falhou.
     *
     * @param error Descrição do erro
     * @param exception Exceção original (se disponível)
     */
    data class Failure(
        val error: String,
        val exception: Throwable? = null
    ) : DownloadResult()
    
    /**
     * Download cancelado.
     */
    data object Cancelled : DownloadResult()
}
